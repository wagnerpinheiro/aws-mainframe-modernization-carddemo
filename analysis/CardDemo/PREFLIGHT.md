# Preflight Report — `legacy/CardDemo`

Generated: 2026-09-13  
Scope target: `legacy/CardDemo`

---

## Check 0 — Human Answers (verbatim)

These five questions were asked before the automated checks ran.
No answers were received before the report was written.
**Each is an open item that must be filled in by the human before downstream commands run.**

> **Q1 — Scope:** Is `legacy/CardDemo` the complete system, or one slice of a larger codebase?
> If a slice: what outside it depends on code inside it, and is breaking those consumers acceptable?
>
> **OPEN — answer required.**

> **Q2 — Build & test locally:** Can this environment restore, build, and run the tests?
> Roughly how long does the full CI pipeline take?
>
> **OPEN — answer required.**

> **Q3 — Bespoke build infrastructure:** Is there organization-specific build or dependency-resolution
> machinery (internal package feed, custom binary store, code generator, wrapper around a standard
> build tool) that someone new would not guess? Where is it documented?
>
> **OPEN — answer required.**  
> (Automated check found an FTP tunnel to "Ensono" — a mainframe managed-services provider — in
> `scripts/remote_compile.sh`. If this is the build path, note it here explicitly.)

> **Q4 — Prior attempts:** Has anyone tried to modernize any of this before? What went wrong?
>
> **OPEN — answer required.**

> **Q5 — Off limits:** Is anything under `legacy/CardDemo` not allowed to change in this pass
> (another team owns it, frozen branch, generated code)?
>
> **OPEN — answer required.**

---

## Check 6 — Scope Boundary (read before everything else)

`legacy/CardDemo` shares the parent git repo `aws-mainframe-modernization-carddemo` with
`diagrams/` and `modernized/` directories. It is **not** inside a monorepo with sibling source
modules.

**Outbound references from inside `CardDemo`:**
- READMEs in `app/app-authorization-ims-db2-mq/` reference `../../diagrams/*.png` — image assets
  only, not source code. No source code references cross the boundary.
- `scripts/local_compile.sh` references `../cpy/` which resolves to within the `scripts/` tree,
  not outside `CardDemo`.

**Inbound references from outside `CardDemo`:**
- `modernized/` is empty. No external code references anything inside `CardDemo`.

**Verdict:** `legacy/CardDemo` is **effectively a standalone system** within this repository.
No scope-boundary hazard exists. Downstream commands can treat it as the whole world.

---

## Check 1 — Stack Detection

| Dimension | Detected |
|---|---|
| Primary language | IBM Enterprise COBOL (fixed-column format, APOST mode, IBM-strict dialect) |
| Online subsystem | CICS/BMS (21 BMS map files, CICS EXEC calls in online programs) |
| Batch subsystem | z/OS JES2 JCL (47 JCL files, 6 JCL procs) |
| Data stores | VSAM (primary), DB2 (optional module), IMS DB (optional module) |
| Messaging | MQ (optional modules: app-vsam-mq, app-authorization-ims-db2-mq) |
| Scheduler | CA7 (`CardDemo.ca7`) and Control-M (`CardDemo.controlm`) |
| Optional modules | `app-vsam-mq`, `app-authorization-ims-db2-mq` (IMS+DB2+MQ), `app-transaction-type-db2` |
| Build/compile | IBM COBOL compiler `IGYCRCTL` v6.3, run via z/OS JES batch |
| Target platform | z/OS mainframe (Ensono managed service) |

**File split (cloc 2.08):**

| Type | Files | Code LOC | Comment LOC |
|---|---|---|---|
| COBOL programs (.cbl/.CBL) | 31 | ~19,000 | ~3,500 |
| COBOL copybooks (.cpy/.CPY) | 54 | ~8,000 | ~2,400 |
| JCL (.jcl/.JCL) | 47 | 1,589 | 1,391 |
| BMS maps (.bms) | 21 | — | — |
| **Total (COBOL+JCL)** | **132** | **28,659** | — |

---

## Check 2 — Analysis Tooling

| Tool | Status | Version | Used by | Without it |
|---|---|---|---|---|
| `scc` | ❌ Missing | — | `assess` | LOC/complexity fall back to `cloc`; COCOMO index is coarser |
| `cloc` | ✅ Available | 2.08 | `assess` | — |
| `lizard` | ⚠️ Available but COBOL-blind | 1.23.0 | `assess --portfolio` | Produced 0 files on COBOL; cyclomatic complexity estimated from PERFORM/IF/EVALUATE keyword counts instead |
| `glow` | ❌ Missing | — | all commands | Markdown artifacts render as plain text in terminal |
| `delta` | ❌ Missing | — | `transform` | Side-by-side diffs fall back to `diff -y` |

**Install one-liners for missing tools:**

```bash
brew install scc          # scc
brew install glow         # glow
brew install git-delta    # delta
```

`lizard` supports COBOL only as a keyword scan (no true parse). No actionable fix; the
degradation is noted.

---

## Check 3 — Build Toolchain

### 3a — Build Definition

The authoritative build definition is `scripts/compile_batch.jcl.template`.
Key facts extracted verbatim:

- **Compiler:** `IGYCRCTL` (IBM Enterprise COBOL) at `IGY.SIGYCOMP.V63`
- **Source library:** `AWS.M2.CARDDEMO.CBL` (z/OS partitioned dataset)
- **Copybook library:** `AWS.M2.CARDDEMO.CPY` (z/OS partitioned dataset)
- **Output load library:** `AWS.M2.CARDDEMO.LOADLIB`
- **Compile flags:** `APOST,LIST,MAP,NUMBER`
- **Link-edit:** `HEWL` (IBM Binder)
- **Execution path:** JES2 batch job submitted via FTP to mainframe (`tnftp localhost 2121`)
  — the remote endpoint is labeled **Ensono** in `remote_compile.sh`

**Bespoke infrastructure flag:** compilation requires an active FTP/SSH tunnel to the Ensono
mainframe (`2121:` port forward). A standard `make` invocation (`remote_compile.sh`) presupposes
this tunnel. No local build path exists in CI — the CI *is* the mainframe.

### 3b — Smoke Test

| Level | Test | Result |
|---|---|---|
| Level 1 — local syntax compile | `cobc -fsyntax-only` on `COSGN00C.cbl` | ❌ `command not found: cobc` — GnuCOBOL not installed |
| Level 2 — full project build | JES2 batch via FTP to Ensono mainframe | ❌ Not executable locally (requires FTP tunnel + mainframe access) |

**Diagnosis:** GnuCOBOL (`cobc`) is not installed. The primary build path is mainframe-only
(IBM COBOL v6.3 on z/OS, submitted via FTP). This is **normal and expected** for CICS/IMS
COBOL that has no local runtime — equivalence testing degrades to golden-master fixtures and
recorded traces rather than dual execution.

**Install GnuCOBOL for local syntax checking (optional but recommended):**

```bash
brew install gnu-cobol
# Then syntax-check:
cobc -fsyntax-only --std=ibm-strict -I legacy/CardDemo/app/cpy \
  legacy/CardDemo/app/cbl/COSGN00C.cbl
```

Note: GnuCOBOL will still fail on CICS inline `EXEC CICS` statements without a CICS preprocessor.
Pass `-D NOCICS` or supply a stub copybook for `DFHAID`/`DFHBMSCA` to avoid those errors during
syntax verification of non-CICS logic.

---

## Check 4 — Source Completeness

### Referenced-but-missing includes

Two CICS system copybooks are referenced in source but **not in the tree** — this is expected:
IBM ships them with CICS, not with application source.

| Missing copybook | Referenced by | Impact |
|---|---|---|
| `DFHAID` | Multiple CICS online programs | Contains CICS attention identifier (AID) constants. Must be sourced from IBM CICS install for any local compile. |
| `DFHBMSCA` | Multiple CICS online programs | Contains BMS attribute constants. Same requirement. |

No other unresolvable COPY references were found. All 47 application copybooks
(`COACTUP`, `CVCRD01Y`, `CVTRA01Y`–`CVTRA07Y`, etc.) are present and accounted for.
One copybook — `UNUSED1Y.cpy` — appears in the tree but has no observed COPY reference;
flag for dead-code review in `/modernize-assess`.

### Deployment and config descriptors

| Artifact type | Count | Status |
|---|---|---|
| JCL batch jobs | 47 | ✅ Present |
| JCL procs (`.prc`) | 6 | ✅ Present |
| CICS CSD definitions | 3 (`CRDDEMOM.csd`, `CRDDEMOD.csd`, `CRDDEMO2.csd`) | ✅ Present (per module) |
| DB2 control files (`.ctl`) | 6 | ✅ Present |
| CA7 scheduler definition | 1 (`CardDemo.ca7`) | ✅ Present |
| Control-M scheduler definition | 1 (`CardDemo.controlm`) | ✅ Present |

### Data definitions

| Artifact type | Files | Location |
|---|---|---|
| DB2 DDL | 4 (TRNTYPE, XTRNTYPE, TRNTYCAT, XTRNTYCAT, AUTHFRDS, XAUTHFRD) | `app-transaction-type-db2/ddl/`, `app-authorization-ims-db2-mq/ddl/` |
| IMS DBD | 2 (`DBPAUTP0.dbd`, `DBPAUTX0.dbd`) | `app-authorization-ims-db2-mq/ims/` |
| IMS PSB | 2 (`PSBPAUTB.psb`, `PSBPAUTL.psb`) | `app-authorization-ims-db2-mq/ims/` |
| VSAM record layouts | In COBOL copybooks (CVACT\*, CVCRD\*, CUSTREC, etc.) | `app/cpy/` |

VSAM file structures are fully documented in copybooks. DB2/IMS definitions are present only
for the optional extension modules.

### Binary-only artifacts

No load modules, `.jar`, or `.dll` without source were found in `app/`. The `samples/` directory
contains `.zip` archives (`CardDemo_runtime.zip`, `UniKix_CardDemo_runtime_v1.zip`) — these
are runtime environment samples, not application binaries, and are out of modernization scope.

---

## Check 5 — Optional Context

| Signal | Status |
|---|---|
| Production telemetry / APM MCP | ❌ Not connected |
| Batch job logs / runtime exports | Not visible locally |
| Version control history (git) | ⚠️ Shallow — 1 commit touches `legacy/CardDemo/` (`f26cb12 Ajuste dos diretorios`). Root repo has ~15 commits total. Change-frequency data is effectively unavailable; risk ranking in `/modernize-assess` will not have a frequency signal. |

---

## Status Table

| Check | Status | Finding | Fix |
|---|---|---|---|
| 0 — Human questions | ⚠️ Open | 5 of 5 answers outstanding | Human must fill in before `/modernize-brief` |
| 1 — Stack detection | ✅ | IBM Enterprise COBOL v6.3 / CICS / VSAM / JES2; optional DB2, IMS, MQ modules | — |
| 2 — Analysis tooling | ⚠️ Gaps | `cloc` ✅, `lizard` ⚠️ (COBOL-blind), `scc` ❌, `glow` ❌, `delta` ❌ | `brew install scc glow git-delta` |
| 3 — Build toolchain | ❌ No local runtime | Mainframe-only build (IBM COBOL + JES2 via FTP to Ensono). `cobc` not installed. | `brew install gnu-cobol` for syntax-only; full build requires mainframe access |
| 4 — Source completeness | ⚠️ Minor gap | `DFHAID` and `DFHBMSCA` absent (IBM CICS system copybooks — expected). `UNUSED1Y.cpy` unreferenced. | Obtain from IBM CICS install for local compile; no action needed for assessment |
| 5 — Optional context | ⚠️ | No APM/telemetry; git history shallow (1 relevant commit) | — |
| 6 — Scope boundary | ✅ | `legacy/CardDemo` is standalone within this repo; no inbound or outbound source dependencies | — |

---

## Readiness Verdict

| Command | Verdict | Limiting factor |
|---|---|---|
| `assess` | **Ready-with-gaps** | `scc` missing (falls back to `cloc`); `lizard` cannot parse COBOL; git change-frequency signal absent |
| `map` | **Ready-with-gaps** | DFHAID/DFHBMSCA absent — CICS entry points partially derivable from BMS maps and CSD files instead |
| `extract-rules` | **Ready** | Source is complete enough; copybook record layouts and JCL fully present |
| `brief` | **Ready-with-gaps** | Check 0 answers outstanding — those are the most important inputs for `brief`. Run it once answers arrive. |
| `transform` / `reimagine` | **Ready-with-gaps** | No local COBOL runtime; no CICS preprocessor. Equivalence testing degrades to golden-master / recorded traces — **normal and expected** for CICS/IMS mainframe code. `transform` can proceed; dual-execution is not possible. |
| `harden` | **Ready-with-gaps** | No COBOL-specific SAST tooling detected. `cloc` present. Pattern-based analysis possible; deep data-flow analysis is not. |
| `uplift` | N/A | No `[target-stack]` was specified. |

---

## Single Most Important Fix

**Answer the Check 0 questions** — especially Q1 (scope), Q3 (Ensono tunnel dependency), and
Q5 (off-limits components). Every downstream command reads `PREFLIGHT.md` for these; without
them, `/modernize-brief` will produce a strategy based on incomplete constraints.

After that: `brew install scc glow git-delta` and `brew install gnu-cobol` (for local
syntax verification of non-CICS COBOL).
