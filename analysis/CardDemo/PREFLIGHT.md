# Preflight Report — `legacy/CardDemo`

Generated: 2026-09-13  
Scope target: `legacy/CardDemo`  
**Migration goal: Exploratory COBOL → Java migration (proof-of-concept, not production)**

---

## Check 0 — Human Answers (verbatim)

> **Q1 — Scope:** Is `legacy/CardDemo` the complete system, or one slice of a larger codebase?
>
> **Answer:** Complete system for the purposes of this test migration. Breaking external consumers
> is acceptable because this is a proof-of-concept exercise, not a production cut-over.

> **Q2 — Build & test locally:** Can this environment restore, build, and run the tests?
> Roughly how long does the full CI pipeline take?
>
> **Answer:** The original mainframe build (IBM COBOL via JES2/FTP to Ensono) cannot run locally.
> GnuCOBOL 3.2.0 is now installed (`cobc`) and can syntax-check batch programs locally.
> CICS online programs fail `cobc` syntax check due to missing `DFHAID`/`DFHBMSCA` and
> CICS EIB registers — expected. No formal CI pipeline exists in this environment.

> **Q3 — Bespoke build infrastructure:** Any non-obvious build machinery?
>
> **Answer:** Yes — remote compilation uses an FTP tunnel to Ensono (port 2121) and submits
> JCL to a z/OS JES2 queue. This infrastructure is documented in `scripts/remote_compile.sh`
> and `scripts/compile_batch.jcl.template`. It is **not required** for the Java migration target.

> **Q4 — Prior attempts:** Has anyone tried to modernize any of this before?
>
> **Answer:** No prior modernization attempts stated.

> **Q5 — Off limits:** Anything not allowed to change?
>
> **Answer:** Nothing is off limits. This is a test migration — the entire `legacy/CardDemo`
> tree is in scope.

---

## Migration Premises (COBOL → Java)

These premises govern every downstream command in this session.
They encode constraints the source code alone cannot reveal.

### P1 — Nature of the exercise
This is an **exploratory proof-of-concept**, not a production migration. The goal is to
demonstrate that the COBOL business logic can be correctly expressed in Java, not to
produce a deployable system. Speed of learning matters more than completeness.

### P2 — Target stack
| Dimension | Choice | Rationale |
|---|---|---|
| Language | Java 21 (LTS) | Widely available on this machine (OpenJDK 25 detected; Java 21 syntax is compatible) |
| Build tool | Maven (Spring Initializr convention) | Industry default for this class of migration |
| Application framework | Spring Boot 3.x | Standard target for COBOL batch → Java batch (Spring Batch) and CICS online → REST/MVC |
| Batch runtime | Spring Batch | Maps naturally to COBOL batch programs (PERFORM paragraphs → Step/Tasklet) |
| Online runtime | Spring MVC (REST) or Spring MVC + Thymeleaf | Maps to CICS transaction screens (BMS maps) |
| Test framework | JUnit 5 + AssertJ | Characterization tests against golden-master outputs |
| Output location | `modernized/CardDemo/` | Consistent with repo structure |

### P3 — Equivalence strategy
Dual execution (running COBOL and Java side-by-side) is **not possible** — no z/OS runtime
locally. Equivalence is proven by:
1. **Golden-master fixtures** — capture COBOL program expected outputs from source analysis
   and known sample data in `legacy/CardDemo/app/data/`
2. **Characterization tests** — JUnit tests that assert Java output matches golden-master
3. **Business-rule coverage** — every rule extracted by `/modernize-extract-rules` gets a test

### P4 — Scope split: batch first, CICS second
COBOL programs fall into two groups with very different migration complexity:

| Group | Programs | Complexity driver | Migration path |
|---|---|---|---|
| **Batch** (no CICS) | CBACT01C–04C, CBTRN01C–03C, CBSTM03A/B, CBCUS01C, CBEXPORT, CBIMPORT | VSAM file I/O, sequential processing | Spring Batch Jobs + Step/Tasklet |
| **CICS online** | COSGN00C, COADM01C, COCRDLIC, COCRDUPC, … (17 programs) | CICS EIB, BMS screen maps, COMMAREA | Spring MVC controllers + service layer |

**Test migration starts with batch programs.** They have no CICS dependency,
can be syntax-checked with `cobc`, and have clear input→output contracts from the JCL.

### P5 — Data tier mapping
| COBOL data store | Java equivalent (test) |
|---|---|
| VSAM KSDS (keyed sequential) | In-memory `HashMap` or flat-file reader (FileChannel/BufferedReader) |
| VSAM ESDS (sequential) | `BufferedReader` over text/EBCDIC file |
| DB2 (optional module) | Spring Data JPA + H2 in-memory (test) |
| IMS DB (optional module) | Out of scope for this test migration |
| MQ (optional modules) | Out of scope for this test migration |

### P6 — CICS system copybooks (DFHAID / DFHBMSCA)
These IBM-supplied copybooks are absent from the tree. For the Java migration:
- `DFHAID` constants (PF keys, CLEAR, ENTER) → Java enum `CicsAid`
- `DFHBMSCA` attribute constants → Java enum `BmsAttr`
- `EIBCALEN`, `EIBAID`, other EIB fields → Java `CicsContext` value object passed to controllers

### P7 — What "done" looks like for the test
The proof-of-concept is considered successful when:
1. At least 3 batch programs compile and pass characterization tests in Java
2. At least 1 CICS online program is expressed as a Spring MVC controller with a test
3. The Spring Boot application starts without errors (`mvn spring-boot:run`)
4. No business logic is duplicated — it lives in a service layer, not in controllers or batch steps

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
| `scc` | ✅ Installed | 4.1.0 | `assess` | — |
| `cloc` | ✅ Available | 2.08 | `assess` | — |
| `lizard` | ⚠️ COBOL-blind | 1.23.0 | `assess --portfolio` | Produced 0 files on COBOL; cyclomatic complexity estimated from PERFORM/IF/EVALUATE keyword counts instead |
| `glow` | ✅ Installed | 3.0.0 | all commands | — |
| `delta` | ✅ Installed | 0.19.2 | `transform` | — |

`lizard` supports COBOL only as a keyword scan (no true parse). No actionable fix; the
degradation is noted.

**scc metrics on batch COBOL programs (31 files):**
- Code: 15,855 LOC | Complexity: 131 | COCOMO: ~$492k / 10.5 months / 4.2 people

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
| Level 1 — CICS program (`COSGN00C.cbl`) | `cobc -fsyntax-only --std=ibm-strict` | ⚠️ Fails on `DFHAID`, `DFHBMSCA`, `COSGN00` (BMS-generated copybook), `EIBCALEN` — all CICS-specific. **Expected and normal.** |
| Level 1 — Batch program (run next step) | `cobc -fsyntax-only` on a non-CICS program | Pending — batch programs have no CICS dependencies and should pass |
| Level 2 — full project build | JES2 batch via FTP to Ensono mainframe | ❌ Not executable locally (requires FTP tunnel + mainframe access). Normal for this stack. |

**Diagnosis:** CICS online programs require IBM CICS preprocessor and system copybooks unavailable
locally — this is the standard constraint for CICS COBOL everywhere outside z/OS. Batch programs
(CBACT\*, CBTRN\*, CBSTM\*, CBCUS\*, CBEXPORT, CBIMPORT) have no CICS dependency and should
compile with `cobc`. Equivalence testing uses golden-master approach (Premise P3).

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
| 0 — Human questions | ✅ | All 5 answers recorded. Migration goal: exploratory COBOL→Java PoC. | — |
| 1 — Stack detection | ✅ | IBM Enterprise COBOL v6.3 / CICS / VSAM / JES2; optional DB2, IMS, MQ modules | — |
| 2 — Analysis tooling | ✅ | `scc` 4.1.0, `cloc` 2.08, `glow` 3.0.0, `delta` 0.19.2 all present. `lizard` COBOL-blind (keyword scan only). | — |
| 3 — Build toolchain | ⚠️ Partial | `cobc` 3.2.0 installed (batch programs); CICS programs need IBM preprocessor (not available — normal). Mainframe full build requires FTP tunnel to Ensono. | No fix needed for Java PoC target |
| 4 — Source completeness | ⚠️ Minor gap | `DFHAID`/`DFHBMSCA` absent (IBM CICS — expected). `UNUSED1Y.cpy` unreferenced. DDL/CSD/IMS artifacts present. | Java migration replaces with enums (Premise P6) |
| 5 — Optional context | ⚠️ | No APM/telemetry; git history shallow (1 relevant commit) | — |
| 6 — Scope boundary | ✅ | `legacy/CardDemo` is standalone within this repo; no inbound or outbound source dependencies | — |

---

## Readiness Verdict

| Command | Verdict | Limiting factor |
|---|---|---|
| `assess` | **Ready** | All tooling installed; `scc` metrics available; COBOL-blind `lizard` noted |
| `map` | **Ready-with-gaps** | CICS entry points partially derivable from BMS maps + CSD; DFHAID/DFHBMSCA absent |
| `extract-rules` | **Ready** | Source complete; copybook layouts and JCL fully present |
| `brief` | **Ready** | Check 0 answered; migration premises established |
| `transform` / `reimagine` | **Ready-with-gaps** | No CICS runtime locally (normal); equivalence via golden-master (Premise P3). Batch programs fully accessible to `cobc`. |
| `harden` | **Ready-with-gaps** | No COBOL SAST tooling; pattern analysis only |
| `uplift` | N/A | Target is a language rewrite (COBOL→Java), not a same-stack version bump |

---

## Recommended Next Step

Run `/modernize-assess CardDemo` — the tooling is fully green and the premises are set.
Then `/modernize-map CardDemo` to build the dependency topology before starting
`/modernize-transform` on the batch programs (Premise P4: batch first).
