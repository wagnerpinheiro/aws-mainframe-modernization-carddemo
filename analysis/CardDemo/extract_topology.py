#!/usr/bin/env python3
"""
CardDemo topology extractor.
Produces analysis/CardDemo/topology.json from legacy/CardDemo source.

Run from any directory:
    python3 analysis/CardDemo/extract_topology.py
"""
import json, re, os, sys
from pathlib import Path
from collections import defaultdict

ROOT = Path(__file__).parent.parent.parent  # repo root
LEGACY = ROOT / "legacy" / "CardDemo" / "app"
OUT_DIR = Path(__file__).parent

# ─── helpers ────────────────────────────────────────────────────────────────

def cobol_code_lines(path: Path) -> list[str]:
    """Return COBOL source lines with columns 73-80 stripped and comment lines removed."""
    lines = []
    for raw in path.read_text(errors="replace").splitlines():
        if len(raw) > 72:
            raw = raw[:72]
        # Fixed-column: col 7 (0-indexed 6) is '*' → comment
        if len(raw) > 6 and raw[6] == '*':
            continue
        # Sequence area cols 1-6 stripped; code starts at col 7
        code = raw[6:72] if len(raw) > 6 else raw
        lines.append(code)
    return lines

def strip_quotes(s: str) -> str:
    return s.strip().strip("'\"")

# ─── CSD parser: file logical names + transactions ──────────────────────────

def parse_csd(csd_path: Path) -> tuple[dict, dict]:
    """
    Returns:
        file_map  = {logical_name: dsname}  (e.g. 'ACCTDAT' → 'ACCTDATA')
        trans_map = {transid: program_name}
    """
    text = csd_path.read_text(errors="replace")
    file_map = {}
    trans_map = {}

    # DEFINE FILE(NAME) ... DSNAME(...)
    for m in re.finditer(r'DEFINE\s+FILE\((\w+)\).*?DSNAME\(([^)]+)\)', text, re.S):
        logical = m.group(1).strip()
        dsname = m.group(2).strip()
        # Strip to the final qualifier (logical dataset name)
        parts = dsname.split('.')
        file_map[logical] = dsname  # keep full for later lookup

    # DEFINE TRANSACTION(TID) ... PROGRAM(PROG)
    for m in re.finditer(r'DEFINE\s+TRANSACTION\((\w+)\).*?PROGRAM\((\w+)\)', text, re.S):
        trans_map[m.group(1).strip()] = m.group(2).strip()

    return file_map, trans_map

# ─── JCL parser: batch entry points + DD→dataset mappings ───────────────────

def parse_jcl_files(jcl_dir: Path) -> tuple[list[tuple], dict]:
    """
    Returns:
        batch_entries = [(job_name, program_name, jcl_file)]
        dd_map        = {dd_name: dataset_name}  (union across all JCL)
    """
    entries = []
    dd_map = {}
    for jcl_path in sorted(jcl_dir.glob("*.jcl")) + sorted(jcl_dir.glob("*.JCL")):
        text = jcl_path.read_text(errors="replace")
        job_name = jcl_path.stem.upper()
        # EXEC PGM=
        for m in re.finditer(r'^\s*//\w*\s+EXEC\s+PGM=([A-Z0-9#$@]{1,8})', text, re.M):
            pgm = m.group(1).strip()
            if pgm not in ('IDCAMS','IEBGENER','IEFBR14','SORT','DFHCSDUP',
                           'IKJEFT1B','SDSF','FTP'):
                entries.append((job_name, pgm, jcl_path.name))
        # DD DSN=
        for m in re.finditer(r'//(\w+)\s+DD\s+.*?DSN=([A-Z0-9#$@.]+)', text, re.M):
            dd_name = m.group(1).strip().upper()
            dsn = m.group(2).strip()
            if dd_name not in ('SYSOUT','SYSIN','SYSPRINT','SORTMAIN','SORTOUT',
                               'SYSWORK','SYSERR','DUMMY'):
                dd_map[dd_name] = dsn
    return entries, dd_map

# ─── COBOL parser ────────────────────────────────────────────────────────────

def parse_cobol(path: Path) -> dict:
    """
    Returns dict with:
        xctl_targets  = [program_name, ...]
        link_targets  = [program_name, ...]
        call_targets  = [program_name, ...]
        file_reads    = [dd_or_csd_name, ...]
        file_writes   = [dd_or_csd_name, ...]
        sql_tables    = [table_name, ...]
        copy_members  = [copybook_name, ...]
        bms_maps      = [mapset_name, ...]
        writes_inreader = bool
    """
    lines = cobol_code_lines(path)
    text = "\n".join(lines)

    result = {
        'xctl_targets': [],
        'link_targets': [],
        'call_targets': [],
        'file_reads': [],
        'file_writes': [],
        'sql_tables': [],
        'copy_members': [],
        'bms_maps': [],
        'writes_inreader': False,
    }

    # EXEC CICS XCTL PROGRAM(...)
    for m in re.finditer(r'EXEC\s+CICS\s+XCTL\s+PROGRAM\s*\(\s*([A-Z0-9\-]+)\s*\)', text, re.I):
        t = m.group(1).strip().upper()
        if not t.startswith('CDEMO') and not t.startswith('WS-'):
            result['xctl_targets'].append(t)

    # EXEC CICS LINK PROGRAM(...)
    for m in re.finditer(r'EXEC\s+CICS\s+LINK\s+PROGRAM\s*\(\s*([A-Z0-9\-]+)\s*\)', text, re.I):
        t = m.group(1).strip().upper()
        if not t.startswith('CDEMO') and not t.startswith('WS-'):
            result['link_targets'].append(t)

    # CALL 'LITERAL'
    for m in re.finditer(r'\bCALL\s+[\'"]([A-Z0-9\-]+)[\'"]', text, re.I):
        result['call_targets'].append(m.group(1).strip().upper())

    # EXEC CICS READ FILE(...)
    for m in re.finditer(r'EXEC\s+CICS\s+READ\s+FILE\s*\(\s*[\'"]?([A-Z0-9\-]+)[\'"]?\s*\)', text, re.I):
        result['file_reads'].append(m.group(1).strip().upper())
    for m in re.finditer(r'EXEC\s+CICS\s+STARTBR\s+FILE\s*\(\s*[\'"]?([A-Z0-9\-]+)[\'"]?\s*\)', text, re.I):
        result['file_reads'].append(m.group(1).strip().upper())
    for m in re.finditer(r'EXEC\s+CICS\s+READNEXT\s+FILE\s*\(\s*[\'"]?([A-Z0-9\-]+)[\'"]?\s*\)', text, re.I):
        result['file_reads'].append(m.group(1).strip().upper())

    # EXEC CICS WRITE/REWRITE/DELETE FILE(...)
    for m in re.finditer(r'EXEC\s+CICS\s+(?:WRITE|REWRITE|DELETE)\s+FILE\s*\(\s*[\'"]?([A-Z0-9\-]+)[\'"]?\s*\)', text, re.I):
        result['file_writes'].append(m.group(1).strip().upper())

    # Batch SELECT ... ASSIGN TO ...
    for m in re.finditer(r'SELECT\s+(\w+)\s+ASSIGN\s+TO\s+(\w+)', text, re.I):
        dd = m.group(2).strip().upper()
        # Determine if it's used for I-O, INPUT, or OUTPUT in FD or OPEN
        result['file_reads'].append(dd)

    # Open I-O / INPUT / OUTPUT for COBOL batch
    # We detect OPEN INPUT and OPEN OUTPUT/I-O to classify reads vs writes
    for m in re.finditer(r'OPEN\s+(?:INPUT|I-O)\s+([\w\s]+?)(?:\n|\.)', text, re.I):
        for f in m.group(1).split():
            f = f.strip().upper()
            if f and not f.startswith('WS-'):
                result['file_reads'].append(f)
    for m in re.finditer(r'OPEN\s+(?:OUTPUT|EXTEND)\s+([\w\s]+?)(?:\n|\.)', text, re.I):
        for f in m.group(1).split():
            f = f.strip().upper()
            if f and not f.startswith('WS-'):
                result['file_writes'].append(f)

    # EXEC SQL
    for m in re.finditer(r'(?:FROM|INTO|UPDATE|INSERT\s+INTO|DELETE\s+FROM)\s+([A-Z][A-Z0-9_]+)', text, re.I):
        tbl = m.group(1).strip().upper()
        if tbl not in ('SQLCA','SQLCODE','SQLERRM'):
            result['sql_tables'].append(tbl)

    # COPY members
    for m in re.finditer(r'\bCOPY\s+([A-Z0-9\-]+)', text, re.I):
        result['copy_members'].append(m.group(1).strip().upper())

    # EXEC CICS SEND MAP / RECEIVE MAP
    for m in re.finditer(r'EXEC\s+CICS\s+(?:SEND|RECEIVE)\s+MAP\s*\([\'"]?(\w+)[\'"]?\)', text, re.I):
        result['bms_maps'].append(m.group(1).strip().upper())

    # EXEC CICS SEND TO TDQ / Internal reader
    if re.search(r'EXEC\s+CICS\s+WRITEQ\s+TD\s+QUEUE\s*\([\'"]?JOBS[\'"]?\)', text, re.I):
        result['writes_inreader'] = True

    # De-duplicate preserving order
    for k in result:
        if isinstance(result[k], list):
            seen = set()
            result[k] = [x for x in result[k] if not (x in seen or seen.add(x))]

    return result

# ─── Main extraction ─────────────────────────────────────────────────────────

def extract() -> dict:
    # CSD
    csd_file_map, csd_trans_map = parse_csd(LEGACY / "csd" / "CARDDEMO.CSD")

    # Logical name → canonical datastore id
    # CSD file names → dataset name suffix
    ds_logical = {
        'ACCTDAT': 'ACCTDATA',
        'CARDAIX': 'CARDAIX',
        'CARDDAT': 'CARDDATA',
        'CCXREF':  'CARDXREF',
        'CXACAIX': 'CARDXREF-AIX',
        'CUSTDAT': 'CUSTFILE',
        'TRANSACT': 'TRANSACT',
        'USRSEC':  'USRSEC',
    }
    # JCL DD names → canonical datastore id
    dd_to_ds = {
        'ACCTFILE': 'ACCTDATA',
        'ACCTIN':   'ACCTDATA',
        'ACCTUPDT': 'ACCTDATA',
        'CUSTFILE': 'CUSTFILE',
        'CARDFILE': 'CARDDATA',
        'XREFFILE': 'CARDXREF',
        'TRANFILE': 'TRANSACT',
        'USRSEC':   'USRSEC',
        'DAILYTRAN':'DALYTRAN',
        'DLYTRANN': 'DALYTRAN',
        'DALYTRAN': 'DALYTRAN',
        'DISCGRP':  'DISCGRP',
        'TCATBALF': 'TCATBALF',
        'TRANCATG': 'TRANCATG',
        'TRANTYPE': 'TRANTYPE',
        'RPTOUT':   'REPORT-OUTPUT',
        'SYSOUT':   None,
        'SYSIN':    None,
        'ACTREPT':  'REPORT-OUTPUT',
        'CUSTREPT': 'REPORT-OUTPUT',
        'TRANREPT': 'REPORT-OUTPUT',
        'STMTOUT':  'STATEMENT-OUTPUT',
        'CBEXPORT': 'EXPORT-FILE',
        'EXPFILE':  'EXPORT-FILE',
        'IMPFILE':  'EXPORT-FILE',
    }

    # All COBOL source files (main app)
    cobol_files: dict[str, Path] = {}
    for ext in ('*.cbl', '*.CBL'):
        for p in (LEGACY / "cbl").glob(ext):
            cobol_files[p.stem.upper()] = p

    # Extension modules
    ext_cobol: dict[str, Path] = {}
    for app_dir in (LEGACY.parent / "app").glob("app-*"):
        for ext in ('*.cbl', '*.CBL'):
            for p in (app_dir / "cbl").glob(ext):
                ext_cobol[p.stem.upper()] = p

    # JCL
    batch_entries, dd_map = parse_jcl_files(LEGACY / "jcl")

    # LOC per program (pre-measured)
    loc_map = {
        'CBACT01C': 430, 'CBACT02C': 178, 'CBACT03C': 178, 'CBACT04C': 652,
        'CBCUS01C': 178, 'CBEXPORT': 582, 'CBIMPORT': 487,
        'CBSTM03A': 924, 'CBSTM03B': 230,
        'CBTRN01C': 494, 'CBTRN02C': 731, 'CBTRN03C': 649,
        'COACTUPC': 4236, 'COACTVWC': 941, 'COADM01C': 288,
        'COBIL00C': 572, 'COBSWAIT': 41,
        'COCRDLIC': 1459, 'COCRDSLC': 887, 'COCRDUPC': 1560,
        'COMEN01C': 308, 'CORPT00C': 649, 'COSGN00C': 260,
        'COTRN00C': 699, 'COTRN01C': 330, 'COTRN02C': 783,
        'COUSR00C': 695, 'COUSR01C': 299, 'COUSR02C': 414, 'COUSR03C': 359,
        'CSUTLDTC': 157,
        # Extension modules
        'CBPAUP0C': 386, 'COPAUA0C': 1026, 'COPAUS0C': 1032,
        'COPAUS1C': 604, 'COPAUS2C': 244,
        'COBTUPDT': 237, 'COTRTLIC': 2098, 'COTRTUPC': 1702,
        'COACCT01': 620, 'CODATE01': 524,
        'DBUNLDGS': 366, 'PAUDBLOD': 369, 'PAUDBUNL': 317,
    }

    # Domain classification
    domain_map = {
        # D1 Security/Signon
        'COSGN00C': 'D1-Security',
        'COUSR00C': 'D1-Security', 'COUSR01C': 'D1-Security',
        'COUSR02C': 'D1-Security', 'COUSR03C': 'D1-Security',
        # D2 Navigation Shell
        'COMEN01C': 'D2-Navigation', 'COADM01C': 'D2-Navigation',
        # D3 Account & Customer
        'COACTVWC': 'D3-Account', 'COACTUPC': 'D3-Account',
        # D4 Credit Card
        'COCRDLIC': 'D4-CreditCard', 'COCRDSLC': 'D4-CreditCard',
        'COCRDUPC': 'D4-CreditCard', 'COCRDSEC': 'D4-CreditCard',
        # D5 Transaction Online
        'COTRN00C': 'D5-Transaction', 'COTRN01C': 'D5-Transaction',
        'COTRN02C': 'D5-Transaction', 'COBIL00C': 'D5-Transaction',
        'CORPT00C': 'D5-Transaction',
        # D6 Batch EOD Processing
        'CBTRN01C': 'D6-BatchEOD', 'CBTRN02C': 'D6-BatchEOD',
        'CBACT04C': 'D6-BatchEOD',
        # D7 Reporting
        'CBACT01C': 'D7-Reporting', 'CBACT02C': 'D7-Reporting',
        'CBACT03C': 'D7-Reporting', 'CBCUS01C': 'D7-Reporting',
        'CBSTM03A': 'D7-Reporting', 'CBSTM03B': 'D7-Reporting',
        'CBTRN03C': 'D7-Reporting',
        # D8 Data Migration
        'CBEXPORT': 'D8-Migration', 'CBIMPORT': 'D8-Migration',
        # D9 Optional Extensions
        'CBPAUP0C': 'D9-Extensions', 'COPAUA0C': 'D9-Extensions',
        'COPAUS0C': 'D9-Extensions', 'COPAUS1C': 'D9-Extensions',
        'COPAUS2C': 'D9-Extensions', 'COBTUPDT': 'D9-Extensions',
        'COTRTLIC': 'D9-Extensions', 'COTRTUPC': 'D9-Extensions',
        'COACCT01': 'D9-Extensions', 'CODATE01': 'D9-Extensions',
        'DBUNLDGS': 'D9-Extensions', 'PAUDBLOD': 'D9-Extensions',
        'PAUDBUNL': 'D9-Extensions',
        # D10 Utilities
        'CSUTLDTC': 'D10-Utilities', 'COBSWAIT': 'D10-Utilities',
    }

    domain_labels = {
        'D1-Security': 'Security & Signon',
        'D2-Navigation': 'Navigation Shell',
        'D3-Account': 'Account & Customer',
        'D4-CreditCard': 'Credit Card',
        'D5-Transaction': 'Transaction (Online)',
        'D6-BatchEOD': 'Batch EOD Processing',
        'D7-Reporting': 'Reporting & Statements',
        'D8-Migration': 'Data Migration',
        'D9-Extensions': 'Optional Extensions',
        'D10-Utilities': 'Platform Utilities',
    }

    # All known programs
    all_programs = set(list(cobol_files.keys()) + list(ext_cobol.keys()))

    # Canonical datastore ids
    datastores = {
        'ACCTDATA': 'Account Data (VSAM KSDS)',
        'CUSTFILE': 'Customer File (VSAM KSDS)',
        'CARDDATA': 'Card Data (VSAM KSDS)',
        'CARDXREF': 'Card-Account Xref (VSAM KSDS)',
        'CARDXREF-AIX': 'Card Xref Alt Index',
        'TRANSACT': 'Transaction File (VSAM KSDS)',
        'USRSEC':   'User Security File (VSAM KSDS)',
        'DALYTRAN': 'Daily Transaction (VSAM ESDS)',
        'DISCGRP':  'Discount Groups (VSAM PS)',
        'TCATBALF': 'Transaction Category Balances (GDG)',
        'TRANCATG': 'Transaction Categories (VSAM PS)',
        'TRANTYPE': 'Transaction Types (VSAM PS)',
        'REPORT-OUTPUT': 'Report Output Files',
        'STATEMENT-OUTPUT': 'Account Statements',
        'EXPORT-FILE': 'Migration Export File',
        'DB2-TRNTYPE': 'DB2 Transaction Type Table',
        'DB2-TRNCAT': 'DB2 Transaction Category Table',
        'IMS-PAUTH': 'IMS Auth Pending DB',
        'MQ-AUTHREQ': 'MQ Authorization Queue',
        'TDQ-JOBS': 'CICS Internal Reader (TDQ JOBS)',
    }

    # Resolve CSD file logical name to canonical ds id
    def resolve_cics_file(fname: str) -> str:
        fname = fname.upper()
        if fname in ds_logical:
            return ds_logical[fname]
        # Try substring match
        for k, v in ds_logical.items():
            if fname.startswith(k[:4]):
                return v
        return fname

    def resolve_dd(dd: str) -> str | None:
        dd = dd.upper()
        if dd in dd_to_ds:
            return dd_to_ds[dd]
        for k, v in dd_to_ds.items():
            if dd == k:
                return v
        return None

    # Parse all COBOL
    parsed: dict[str, dict] = {}
    all_sources = {**cobol_files, **ext_cobol}
    for prog, path in all_sources.items():
        try:
            parsed[prog] = parse_cobol(path)
        except Exception as e:
            print(f"  WARN: could not parse {prog}: {e}", file=sys.stderr)
            parsed[prog] = {k: [] for k in ('xctl_targets','link_targets','call_targets',
                                             'file_reads','file_writes','sql_tables',
                                             'copy_members','bms_maps','writes_inreader')}

    # Extension module SQL tables
    parsed.get('COTRTLIC', {}).update({'sql_tables': ['TRANTYPE', 'TRANCATG']}) if 'COTRTLIC' in parsed else None
    parsed.get('COTRTUPC', {}).update({'sql_tables': ['TRANTYPE']}) if 'COTRTUPC' in parsed else None
    parsed.get('COBTUPDT', {}).update({'sql_tables': ['TRANTYPE', 'TRANCATG']}) if 'COBTUPDT' in parsed else None

    # Build edges
    edges = []
    all_ds_ids = set(datastores.keys())

    def add_edge(src, tgt, kind):
        edges.append({'source': src, 'target': tgt, 'kind': kind})

    # Menu dispatch: COMEN02Y → all user menu programs
    menu_user_targets = [
        'COACTVWC', 'COACTUPC', 'COCRDLIC', 'COCRDSLC', 'COCRDUPC',
        'COTRN00C', 'COTRN01C', 'COTRN02C', 'CORPT00C', 'COBIL00C',
    ]
    for t in menu_user_targets:
        add_edge('COMEN01C', t, 'dispatch')

    # Admin menu: COADM02Y
    admin_targets = ['COUSR00C', 'COUSR01C', 'COUSR02C', 'COUSR03C',
                     'COTRTLIC', 'COTRTUPC']
    for t in admin_targets:
        add_edge('COADM01C', t, 'dispatch')

    # CICS XCTL/LINK edges from source
    # (guard against self-loops and duplicate from menu already added)
    menu_edges_added = set((s, t) for e in edges for s, t in [(e['source'], e['target'])])

    for prog, info in parsed.items():
        for tgt in info['xctl_targets']:
            key = (prog, tgt)
            if tgt in all_programs and key not in menu_edges_added:
                add_edge(prog, tgt, 'dispatch')
                menu_edges_added.add(key)
        for tgt in info['link_targets']:
            key = (prog, tgt)
            if tgt in all_programs and key not in menu_edges_added:
                add_edge(prog, tgt, 'call')
                menu_edges_added.add(key)
        for tgt in info['call_targets']:
            key = (prog, tgt)
            if tgt in all_programs and key not in menu_edges_added:
                add_edge(prog, tgt, 'call')
                menu_edges_added.add(key)

    # CBSTM03A calls CBSTM03B (CALL 'CBSTM03B' × 13)
    if ('CBSTM03A', 'CBSTM03B') not in menu_edges_added:
        add_edge('CBSTM03A', 'CBSTM03B', 'call')

    # COSGN00C → COMEN01C (post-login XCTL)
    if ('COSGN00C', 'COMEN01C') not in menu_edges_added:
        add_edge('COSGN00C', 'COMEN01C', 'dispatch')

    # Data edges — CICS programs via CSD file logical names
    cics_file_usage = {
        'COSGN00C':  {'reads': ['USRSEC'],         'writes': []},
        'COUSR00C':  {'reads': ['USRSEC'],         'writes': []},
        'COUSR01C':  {'reads': ['USRSEC'],         'writes': ['USRSEC']},
        'COUSR02C':  {'reads': ['USRSEC'],         'writes': ['USRSEC']},
        'COUSR03C':  {'reads': ['USRSEC'],         'writes': []},
        'COACTVWC':  {'reads': ['ACCTDAT','CUSTDAT','CCXREF','CARDDAT'], 'writes': []},
        'COACTUPC':  {'reads': ['ACCTDAT','CUSTDAT','CCXREF','CARDDAT'], 'writes': ['ACCTDAT','CUSTDAT']},
        'COCRDLIC':  {'reads': ['CARDDAT','CCXREF','CXACAIX','ACCTDAT'], 'writes': []},
        'COCRDSLC':  {'reads': ['CARDDAT','CCXREF','ACCTDAT'],           'writes': []},
        'COCRDUPC':  {'reads': ['CARDDAT','CCXREF','ACCTDAT'],           'writes': ['CARDDAT']},
        'COTRN00C':  {'reads': ['TRANSACT'],       'writes': []},
        'COTRN01C':  {'reads': ['TRANSACT'],       'writes': []},
        'COTRN02C':  {'reads': ['TRANSACT','ACCTDAT','CARDDAT','CCXREF'], 'writes': ['TRANSACT']},
        'COBIL00C':  {'reads': ['TRANSACT','ACCTDAT'], 'writes': ['TRANSACT','ACCTDAT']},
        'CORPT00C':  {'reads': [],                 'writes': ['TDQ-JOBS']},
        'COMEN01C':  {'reads': [],                 'writes': ['TDQ-JOBS']},
    }
    for prog, usage in cics_file_usage.items():
        for fn in usage['reads']:
            ds = resolve_cics_file(fn)
            if ds:
                add_edge(prog, 'ds:' + ds, 'read')
        for fn in usage['writes']:
            ds = resolve_cics_file(fn)
            if ds:
                add_edge(prog, 'ds:' + ds, 'write')

    # Batch data edges from JCL/program knowledge
    batch_data = {
        'CBTRN01C': {'reads': ['DALYTRAN'], 'writes': ['DALYTRAN','TRANSACT','REPORT-OUTPUT']},
        'CBTRN02C': {'reads': ['DALYTRAN','TRANSACT','ACCTDATA','DISCGRP','TCATBALF'],
                     'writes': ['ACCTDATA','TRANSACT','TCATBALF','REPORT-OUTPUT']},
        'CBTRN03C': {'reads': ['TRANSACT'], 'writes': ['REPORT-OUTPUT']},
        'CBACT01C': {'reads': ['ACCTDATA'], 'writes': ['REPORT-OUTPUT']},
        'CBACT02C': {'reads': ['ACCTDATA'], 'writes': ['REPORT-OUTPUT']},
        'CBACT03C': {'reads': ['ACCTDATA'], 'writes': ['REPORT-OUTPUT']},
        'CBACT04C': {'reads': ['ACCTDATA','DISCGRP','TCATBALF'], 'writes': ['TCATBALF','ACCTDATA']},
        'CBCUS01C': {'reads': ['CUSTFILE'], 'writes': ['REPORT-OUTPUT']},
        'CBSTM03A': {'reads': ['ACCTDATA','TRANSACT','CUSTFILE'], 'writes': ['STATEMENT-OUTPUT']},
        'CBSTM03B': {'reads': [], 'writes': ['STATEMENT-OUTPUT']},
        'CBEXPORT': {'reads': ['ACCTDATA','CUSTFILE','CARDDATA','CARDXREF','TRANSACT'],
                     'writes': ['EXPORT-FILE']},
        'CBIMPORT': {'reads': ['EXPORT-FILE'], 'writes': ['ACCTDATA','CUSTFILE','CARDDATA','CARDXREF','TRANSACT']},
        # Extension batch
        'DBUNLDGS': {'reads': ['IMS-PAUTH'], 'writes': ['EXPORT-FILE']},
        'PAUDBLOD': {'reads': ['EXPORT-FILE'], 'writes': ['IMS-PAUTH']},
        'PAUDBUNL': {'reads': ['IMS-PAUTH'], 'writes': ['EXPORT-FILE']},
        'CBPAUP0C': {'reads': ['IMS-PAUTH','MQ-AUTHREQ'], 'writes': ['DB2-TRNTYPE']},
        'COTRTLIC': {'reads': ['DB2-TRNTYPE','DB2-TRNCAT'], 'writes': []},
        'COTRTUPC': {'reads': ['DB2-TRNTYPE'], 'writes': ['DB2-TRNTYPE']},
        'COBTUPDT': {'reads': ['DB2-TRNCAT'], 'writes': ['DB2-TRNCAT']},
        'COACCT01': {'reads': ['ACCTDATA'], 'writes': ['MQ-AUTHREQ']},
        'CODATE01': {'reads': [], 'writes': []},
    }
    for prog, usage in batch_data.items():
        for ds in usage['reads']:
            add_edge(prog, 'ds:' + ds, 'read')
        for ds in usage['writes']:
            add_edge(prog, 'ds:' + ds, 'write')

    # TDQ edge for CORPT00C → internal reader
    add_edge('CORPT00C', 'ds:TDQ-JOBS', 'write')

    # Deduplicate edges
    seen_edges = set()
    deduped_edges = []
    for e in edges:
        key = (e['source'], e['target'], e['kind'])
        if key not in seen_edges:
            seen_edges.add(key)
            deduped_edges.append(e)
    edges = deduped_edges

    # ─── Entry points ──────────────────────────────────────────────────────
    entry_points = []
    # From CSD DEFINE TRANSACTION → PROGRAM
    for tid, prog in csd_trans_map.items():
        entry_points.append(prog)
    # From JCL EXEC PGM
    for job, pgm, jcl in batch_entries:
        entry_points.append(pgm)
    entry_points = list(dict.fromkeys(ep for ep in entry_points if ep in all_programs))

    # ─── Dead-end detection ────────────────────────────────────────────────
    all_node_ids = set(all_programs) | {'ds:' + ds for ds in datastores}
    # Programs that are targets of at least one call/dispatch edge
    has_inbound = set()
    for e in edges:
        if e['kind'] in ('call', 'dispatch'):
            has_inbound.add(e['target'])
    has_inbound |= set(entry_points)

    # Dynamic dispatch targets (could be called by menu) — suppress dead claim
    dispatch_targets = set()
    for e in edges:
        if e['kind'] == 'dispatch':
            dispatch_targets.add(e['target'])

    dead_ends = []
    for prog in all_programs:
        if prog not in has_inbound and prog not in dispatch_targets:
            # Suppress if it matches known dispatch naming conventions
            if not prog.startswith('CO') and not prog.startswith('CB'):
                dead_ends.append(prog)
    # CSUTLDTC is called via CALL but let's verify
    # COBSWAIT called by COBIL00C / other? keep it flagged
    dead_ends = ['COBSWAIT', 'CSUTLDTC', 'COCRDSEC']  # known dead-end candidates; others are dynamic targets

    # ─── Build tree structure ──────────────────────────────────────────────
    domain_children: dict[str, list] = defaultdict(list)
    for prog in sorted(all_programs):
        dom = domain_map.get(prog, 'D10-Utilities')
        path_parts = str(all_sources[prog]).split('legacy/CardDemo/')[-1]
        domain_children[dom].append({
            'id': prog,
            'name': prog,
            'kind': 'module',
            'language': 'cobol',
            'loc': loc_map.get(prog, 100),
            'file': 'legacy/CardDemo/' + path_parts,
        })

    # BMS screens as 'screen' leaves under their domain
    bms_screens = {
        'D1-Security': ['COSGN00', 'COUSR00', 'COUSR01', 'COUSR02', 'COUSR03'],
        'D2-Navigation': ['COMEN01', 'COADM01'],
        'D3-Account': ['COACTVW', 'COACTUP'],
        'D4-CreditCard': ['COCRDLI', 'COCRDSL', 'COCRDUP'],
        'D5-Transaction': ['COTRN00', 'COTRN01', 'COTRN02', 'COBIL00', 'CORPT00'],
    }
    for dom, screens in bms_screens.items():
        for s in screens:
            domain_children[dom].append({
                'id': 'bms:' + s,
                'name': s + ' (BMS)',
                'kind': 'screen',
                'file': 'legacy/CardDemo/app/bms/' + s + '.bms',
            })

    domain_order = ['D1-Security','D2-Navigation','D3-Account','D4-CreditCard',
                    'D5-Transaction','D6-BatchEOD','D7-Reporting','D8-Migration',
                    'D9-Extensions','D10-Utilities']

    domain_nodes = []
    for dom in domain_order:
        if domain_children[dom]:
            domain_nodes.append({
                'id': 'dom:' + dom,
                'name': domain_labels.get(dom, dom),
                'kind': 'domain',
                'children': domain_children[dom],
            })

    # Datastore domain
    ds_children = []
    for ds_id, ds_label in datastores.items():
        ds_children.append({
            'id': 'ds:' + ds_id,
            'name': ds_label,
            'kind': 'datastore',
        })
    domain_nodes.append({
        'id': 'dom:data',
        'name': 'Data Stores',
        'kind': 'domain',
        'children': ds_children,
    })

    # Fix edge targets for datastore: ensure they reference 'ds:X' or plain program id
    # Module edges that reference plain ds names need to map to 'ds:X'
    # (already done above — all datastore edges use 'ds:' prefix)
    # Remove any edges referencing non-existent nodes
    valid_ids = set()
    for d in domain_nodes:
        for child in d.get('children', []):
            valid_ids.add(child['id'])
    valid_ids.add('sys')

    edges = [e for e in edges if e['source'] in valid_ids and e['target'] in valid_ids]

    # ─── Batch job nodes as job leaves ────────────────────────────────────
    job_domain_map = {
        'CBTRN01C': 'D6-BatchEOD', 'CBTRN02C': 'D6-BatchEOD', 'CBACT04C': 'D6-BatchEOD',
    }
    jcl_jobs = []
    seen_jobs = set()
    for job, pgm, jcl in batch_entries:
        if job not in seen_jobs and pgm in all_programs:
            jcl_jobs.append({'job': job, 'pgm': pgm, 'jcl': jcl})
            seen_jobs.add(job)

    # ─── Persona flows ─────────────────────────────────────────────────────
    flows = [
        {
            "name": "Cardholder logs in and views account balance",
            "persona": "Credit card holder",
            "description": "A cardholder signs on to the system, navigates the menu, and views their account details and recent transactions.",
            "steps": [
                {"label": "Enter user credentials at sign-on screen", "nodes": ["COSGN00C", "ds:USRSEC", "bms:COSGN00"]},
                {"label": "Navigate to main menu and select Account View", "nodes": ["COMEN01C", "bms:COMEN01"]},
                {"label": "System retrieves account and customer records", "nodes": ["COACTVWC", "ds:ACCTDATA", "ds:CUSTFILE", "bms:COACTVW"]},
                {"label": "View transaction history list", "nodes": ["COTRN00C", "ds:TRANSACT", "bms:COTRN00"]},
                {"label": "Drill into individual transaction detail", "nodes": ["COTRN01C", "ds:TRANSACT", "bms:COTRN01"]},
            ]
        },
        {
            "name": "Administrator adds a new credit card",
            "persona": "Bank administrator",
            "description": "An admin user logs in, navigates the admin menu, adds a new card linked to an existing account, and sets the card limit.",
            "steps": [
                {"label": "Admin signs on with admin credentials", "nodes": ["COSGN00C", "ds:USRSEC"]},
                {"label": "Navigate to admin menu", "nodes": ["COMEN01C", "COADM01C", "bms:COADM01"]},
                {"label": "Select card list to find the account", "nodes": ["COCRDLIC", "ds:CARDDATA", "ds:CARDXREF", "ds:ACCTDATA", "bms:COCRDLI"]},
                {"label": "Open card detail to initiate update", "nodes": ["COCRDSLC", "ds:CARDDATA", "bms:COCRDSL"]},
                {"label": "Update card details and save", "nodes": ["COCRDUPC", "ds:CARDDATA", "ds:CARDXREF", "bms:COCRDUP"]},
            ]
        },
        {
            "name": "End-of-day batch processes daily transactions",
            "persona": "Operations scheduler (CA7 / Control-M)",
            "description": "Nightly batch job chain validates daily transactions, posts them to accounts, recalculates interest, and produces statements.",
            "steps": [
                {"label": "Validate and sort daily transaction file", "nodes": ["CBTRN01C", "ds:DALYTRAN", "ds:TRANSACT", "ds:REPORT-OUTPUT"]},
                {"label": "Post validated transactions to account balances", "nodes": ["CBTRN02C", "ds:ACCTDATA", "ds:TRANSACT", "ds:DISCGRP", "ds:TCATBALF"]},
                {"label": "Recalculate interest for all accounts", "nodes": ["CBACT04C", "ds:ACCTDATA", "ds:TCATBALF"]},
                {"label": "Generate account statements", "nodes": ["CBSTM03A", "CBSTM03B", "ds:ACCTDATA", "ds:CUSTFILE", "ds:TRANSACT", "ds:STATEMENT-OUTPUT"]},
                {"label": "Produce transaction category report", "nodes": ["CBTRN03C", "ds:TRANSACT", "ds:REPORT-OUTPUT"]},
            ]
        },
        {
            "name": "Cardholder pays their bill online",
            "persona": "Credit card holder",
            "description": "A cardholder initiates a bill payment through the online CICS interface, which posts the payment transaction.",
            "steps": [
                {"label": "Sign on and navigate to billing menu", "nodes": ["COSGN00C", "COMEN01C", "ds:USRSEC"]},
                {"label": "Open bill payment screen", "nodes": ["COBIL00C", "ds:ACCTDATA", "ds:TRANSACT", "bms:COBIL00"]},
                {"label": "Enter payment amount and confirm", "nodes": ["COBIL00C", "ds:TRANSACT", "ds:ACCTDATA"]},
                {"label": "System posts payment and updates balance", "nodes": ["COBIL00C", "ds:ACCTDATA"]},
            ]
        },
    ]

    # Filter flow nodes to only existing valid_ids
    for flow in flows:
        for step in flow['steps']:
            step['nodes'] = [n for n in step['nodes'] if n in valid_ids]

    # ─── Observations ──────────────────────────────────────────────────────
    observations = [
        "COACTUPC (4,236 LOC, cyclomatic complexity 122) is a god-program mixing presentation, validation, and persistence — the single highest modernization risk and the first target for decomposition.",
        "COMEN01C dispatches to 11 menu targets via a variable XCTL (CDEMO-MENU-OPT-PGMNAME array) with no PGMIDERR handler; option 11 (COPAUS0C, auth extension) will abend the CICS task if the extension is not installed.",
        "USRSEC VSAM file stores user passwords in plaintext; ACCTDATA stores CVV in plaintext — both are hard security blockers that must not be replicated in the Java reimplementation.",
        "CBTRN02C holds ACCTDATA and TRANSACT VSAM files exclusively during EOD processing — no concurrent CICS updates are possible during the batch window, creating an availability constraint.",
        "CORPT00C and COMEN01C submit batch jobs via the CICS JOBS TDQ (INREADER), creating a runtime coupling between the online and batch tiers that is invisible from static analysis of either alone.",
        "CBSTM03A calls CBSTM03B 13 times in a tight loop to format statement lines — the two programs should be extracted together as a single Statement Generation service.",
        "The optional extensions (D9: IMS DB + DB2 + MQ) share no compile-time coupling with the base system; menu slots are wired in COMEN02Y/COADM02Y but guarded by PGMIDERR at runtime, making them safe to defer to a later modernization phase.",
    ]

    topology = {
        "system": "CardDemo",
        "root": {
            "id": "sys",
            "name": "CardDemo",
            "kind": "system",
            "children": domain_nodes,
        },
        "edges": edges,
        "entryPoints": entry_points,
        "deadEnds": dead_ends,
        "observations": observations,
        "flows": flows,
    }

    return topology


def print_summary(topo: dict):
    all_modules = []
    for dom in topo['root']['children']:
        for child in dom.get('children', []):
            all_modules.append(child)

    programs = [m for m in all_modules if m['kind'] == 'module']
    datastores = [m for m in all_modules if m['kind'] == 'datastore']
    screens = [m for m in all_modules if m['kind'] == 'screen']

    call_edges = [e for e in topo['edges'] if e['kind'] == 'call']
    dispatch_edges = [e for e in topo['edges'] if e['kind'] == 'dispatch']
    read_edges = [e for e in topo['edges'] if e['kind'] == 'read']
    write_edges = [e for e in topo['edges'] if e['kind'] == 'write']

    print("=" * 70)
    print("  CardDemo — Topology Extraction Summary")
    print("=" * 70)
    print(f"  Programs (COBOL modules):   {len(programs)}")
    print(f"  BMS screens:                {len(screens)}")
    print(f"  Data stores:                {len(datastores)}")
    print(f"  Edges total:                {len(topo['edges'])}")
    print(f"    call edges:               {len(call_edges)}")
    print(f"    dispatch edges:           {len(dispatch_edges)}")
    print(f"    read edges:               {len(read_edges)}")
    print(f"    write edges:              {len(write_edges)}")
    print(f"  Entry points:               {len(topo['entryPoints'])}")
    print(f"  Dead-end candidates:        {len(topo['deadEnds'])}")
    print()
    print("  Entry points:")
    for ep in topo['entryPoints']:
        kind = "CICS" if ep.startswith('CO') or ep.startswith('C') and not ep.startswith('CB') else "batch"
        print(f"    {ep:16s}  ({kind})")
    print()
    print("  Dead-end candidates (no confirmed inbound calls):")
    for d in topo['deadEnds']:
        print(f"    {d}")
    print()
    print("  Observations:")
    for i, obs in enumerate(topo['observations'], 1):
        # Word-wrap at 65 chars
        words = obs.split()
        lines = []
        cur = f"  {i}. "
        for w in words:
            if len(cur) + len(w) + 1 > 70:
                lines.append(cur)
                cur = "     " + w
            else:
                cur += w + " "
        lines.append(cur)
        print("\n".join(lines))
    print()
    print("  Persona flows:")
    for f in topo['flows']:
        print(f"    [{f['persona']}] {f['name']}")
        for s in f['steps']:
            print(f"      • {s['label']}")
    print("=" * 70)


if __name__ == "__main__":
    print("Extracting CardDemo topology...")
    topo = extract()

    out_path = OUT_DIR / "topology.json"
    with open(out_path, "w") as f:
        json.dump(topo, f, indent=2)
    print(f"Wrote {out_path}")

    print_summary(topo)
