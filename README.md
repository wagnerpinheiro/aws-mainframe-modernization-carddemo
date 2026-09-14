# CardDemo — Mainframe Modernization PoC

**Status: COMPLETE ✅** — todos os 5 phases implementados, 90/90 testes GREEN.

**Target:** Java 21 + Spring Boot 3.3.5 (Spring Batch, Spring MVC, Spring Security 6, Spring Data JPA, Thymeleaf)
**Source:** IBM Enterprise COBOL v6.3 / CICS / VSAM / z/OS JES2 batch
**Scope:** Exploratory proof-of-concept — demonstra que a lógica de negócio COBOL pode ser corretamente expressa em Java antes de qualquer investimento em produção.

---

## Quick start

**Pré-requisitos:** Java 21+, Maven 3.9+

```bash
# Build e testes completos (todos os 5 modules)
cd modernized/CardDemo
mvn test
# Expected: Tests run: 90, Failures: 0 — BUILD SUCCESS

# Aplicação online (Spring MVC + Spring Security)
mvn -pl carddemo-web spring-boot:run

# Batch EOD
mvn -pl carddemo-batch-eod spring-boot:run \
    -Dspring-boot.run.arguments="--spring.batch.job.enabled=false"

# Batch Reporting
mvn -pl carddemo-batch-reporting spring-boot:run \
    -Dspring-boot.run.arguments="--spring.batch.job.enabled=false"
```

---

## Estrutura do repositório

```
legacy/CardDemo/          ← COBOL original (106 programas + copybooks, 33.876 LOC)
analysis/CardDemo/        ← Artefatos de assessment
  ASSESSMENT.md           ← Riscos, complexidade, findings de segurança
  BUSINESS_RULES.md       ← 70+ regras em Given/When/Then
  DATA_OBJECTS.md         ← Inventário de estruturas VSAM
  MODERNIZATION_BRIEF.md  ← Plano de execução em 5 phases (documento vinculante)
  PREFLIGHT.md            ← Relatório de readiness do ambiente
  BASELINE.md             ← Saídas esperadas (golden master) por programa batch
modernized/CardDemo/      ← Target Java (Maven multi-módulo, Spring Boot 3.3.5)
  POC_COMPLETE.md         ← Declaração de conclusão do PoC
```

---

## Projeto Java — estrutura de módulos

```
modernized/CardDemo/
├── pom.xml                          Parent POM (Java 21, Spring Boot 3.3.5)
├── carddemo-common/                 Parsers de formato COBOL DISPLAY, utilitários
├── carddemo-domain/                 Entidades JPA + repositórios (H2 in-memory no PoC)
├── carddemo-batch-eod/              Phase 1 — Pipeline EOD batch
├── carddemo-batch-reporting/        Phase 2 — Relatórios, extratos, migração de dados
└── carddemo-web/                    Phases 3–5 — Spring MVC + Spring Security online
```

### Modelo de domínio (`carddemo-domain`)

| Entidade | Mapeado de | Campos principais |
|---|---|---|
| `AccountEntity` | ACCTDATA VSAM KSDS | id, activeStatus, currentBalance, creditLimit, cycleCredit/Debit, expirationDate, `@Version` (RULE-053) |
| `CardXRefEntity` | CARDXREF VSAM KSDS | cardNumber (PK), customerId, accountId |
| `CardEntity` | CARDDATA VSAM KSDS | cardNumber (PK) — CVV **ausente** (SEC-005 / PCI DSS) |
| `CustomerEntity` | CUSTFILE VSAM KSDS | id, firstName/middleName/lastName, campos de endereço, ficoCreditScore |
| `TransactionEntity` | TRANSACT VSAM KSDS | id (String PK), typeCode, categoryCode, amount, cardNumber, timestamps |
| `TranCatBalanceEntity` | TCATBAL VSAM KSDS | chave composta (accountId, typeCode, categoryCode), balance |
| `DiscountGroupEntity` | DISCGRP VSAM KSDS | chave composta (groupId, typeCode, categoryCode), interestRate |
| `UserEntity` | USRSEC VSAM KSDS | userId (PK), password (BCrypt hash), userType ('A'/'U') |

---

## Status dos phases — todos completos ✅

### Phase 1 — Batch Foundation + EOD Pipeline ✅

| COBOL | LOC | Java | Regras provadas |
|---|---|---|---|
| CBTRN01C | 494 | `TransactionValidationJob` | RULE-063, RULE-064 |
| CBTRN02C | 731 | `TransactionPostingJob` | RULE-063–066 (rejects 100–103), RULE-059, RULE-061, RULE-065, RULE-066 |
| CBACT04C | 652 | `InterestCalculationJob` | RULE-007 (RoundingMode.DOWN), RULE-008/009 |

**25 testes** | módulo `carddemo-batch-eod`

### Phase 2 — Reporting, Extratos & Migração ✅

| COBOL | LOC | Java |
|---|---|---|
| CBSTM03A + CBSTM03B | 1.154 | `StatementGenerationJob` + `StatementFormatter` (texto + HTML) |
| CBACT01C | ~200 | `AccountListReportJob` |
| CBACT02C | ~200 | `CardReportJob` |
| CBACT03C | ~200 | `CrossRefReportJob` |
| CBCUS01C | ~200 | `CustomerReportJob` |
| CBTRN03C | ~649 | `TransactionReportJob` (RULE-021/022/023 — totaling, paginação, filtro por data) |
| CBEXPORT | ~582 | `DataExportJob` (CSV multi-arquivo) |
| CBIMPORT | ~487 | `DataImportJob` (SEC-014 fix — Bean Validation: SSN, limite, datas) |

**18 testes** | módulo `carddemo-batch-reporting`

### Phase 3 — Authentication & Navigation ✅

| COBOL | LOC | Java |
|---|---|---|
| COSGN00C | 260 | `AuthController` + `UserDetailsServiceImpl` + `SecurityConfig` |
| COUSR00-03C | 1.767 | `UserListController`, `UserAddController`, `UserUpdateController`, `UserDeleteController` |
| COMEN01C + COADM01C | 596 | `MenuController` + `AdminMenuController` |
| — (novo design) | — | `CicsAid`, `BmsAttr`, `CicsContext` (camada de adaptação CICS) |

**20 testes** | módulo `carddemo-web/auth`, `carddemo-web/navigation`, `carddemo-web/user`

Regras: RULE-001–006 (auth), SEC-003 (BCrypt), SEC-009 (@PreAuthorize), RULE-055 (sessão)

### Phase 4 — Account & Card Online ✅

| COBOL | LOC | Java |
|---|---|---|
| COACTVWC | 941 | `AccountViewController` + `AccountQueryService` (pilot) |
| COACTUPC | 4.236 | `AccountUpdateController` + `AccountUpdateService` + `AccountUpdateValidator` + `PhoneValidator` + `ScreenRenderer` |
| COCRDLIC + COCRDSLC | ~2.346 | `CardListController` + `CardDetailController` |
| COCRDUPC | 1.560 | `CardUpdateController` + `CardUpdateService` + `CardUpdateValidator` |

**14 testes** | módulo `carddemo-web/account`, `carddemo-web/card`

Regras: RULE-024–045 (validação), RULE-053 (locking otimista → HTTP 409), RULE-061 (rollback atômico), RULE-060 (status do cartão), SEC-005 (CVV ausente), TD-09/TD-10

> **COACTUPC decomposição** (ordem mandatória do brief):
> 1. `ScreenRenderer` → 2. `AccountUpdateValidator` → 3. `AccountUpdateService @Transactional` → 4. `PhoneValidator` → 5. `AccountUpdateController`

### Phase 5 — Transactions & Billing Online ✅

| COBOL | LOC | Java |
|---|---|---|
| COBIL00C | 572 | `BillPaymentController` + `BillPaymentService` (pilot) |
| COTRN00C + COTRN01C | ~1.200 | `TransactionListController` + `TransactionDetailController` |
| COTRN02C | 612 | `TransactionAddController` + `TransactionService` |
| CORPT00C | 649 | `ReportTriggerController` (REST POST → `JobLauncher` async → HTTP 202) |

**13 testes** | módulo `carddemo-web/billing`, `carddemo-web/transaction`, `carddemo-web/report`

Regras: RULE-010/011 (pagamento), RULE-012 (ID por sequência DB — sem duplicatas), RULE-047–054 (validação de transação), RULE-051 (confirmação em dois passos + CSRF), Q10 (CORPT00C → REST sem reconstrução de JCL), Q11 (LocalDate strict mode)

---

## Cobertura de testes

| Módulo | Testes | Classes de teste |
|---|---|---|
| `carddemo-batch-eod` | 25 | `TransactionValidationJobTest` (7), `TransactionPostingJobTest` (10), `InterestCalculationJobTest` (8) |
| `carddemo-batch-reporting` | 18 | `StatementGenerationJobTest` (5), `Phase2JobsTest` (13) |
| `carddemo-web` | 47 | `AuthControllerTest` (9), `UserManagementSecurityTest` (6), `MenuControllerTest` (5), `AccountControllerTest` (7), `CardControllerTest` (7), `BillingControllerTest` (4), `TransactionControllerTest` (7), `ReportTriggerControllerTest` (2) |
| **Total** | **90** | **90/90 GREEN** |

**Estratégia de equivalência:** testes de caracterização golden-master (trace-based — sem runtime z/OS disponível). Syntax check GnuCOBOL 3.2.0 passa em todos os fontes COBOL com `-I cpy/`.

---

## Decisões de design

| Decisão | Comportamento COBOL | Comportamento Java | Regra |
|---|---|---|---|
| Arredondamento de juros | `COMPUTE` sem `ROUNDED` → trunca | `RoundingMode.DOWN` | RULE-007 |
| Contas inativas no batch | Sem check em `ACCT-ACTIVE-STATUS` | Replicado + comentário TODO | RULE-059 |
| Fórmula de overlimit | Cycle-to-date apenas | `credit - debit + amount` | RULE-065 |
| Check de expiração | Comparação de strings YYYY-MM-DD | `LocalDate.parse()` strict | RULE-066 |
| Rollback atômico | Sem rollback em falha parcial | `@Transactional(SERIALIZABLE)` | RULE-061 |
| Locking concorrente | Sem controle de concorrência | `@Version` → HTTP 409 | RULE-053 |
| ID de transação online | READPREV+incremento (defeito) | DB sequence (sem duplicatas) | RULE-012 fix |
| Armazenamento de CVV | Armazenado em CARDDATA | Campo removido (PCI DSS) | SEC-005 |
| Senha | Comparação em plaintext | BCrypt | SEC-003 |
| Autorização admin | Sem check em COUSR01-03C | `@PreAuthorize("hasRole('ADMIN')")` | SEC-009 |
| Rejeitos DALYREJS | Sem reprocessamento automático | CSV estruturado + TODO(prod) | Q6/DG-5 |
| CORPT00C TDQ/JCL | Reconstrói JCL via TDQ interno | REST POST → `JobLauncher` → HTTP 202 | Q10 |
| CSUTLDTC erro 2513 | Suprimido silenciosamente | `LocalDate.parse()` strict (mais seguro) | Q11 |
| PSA/TCB/TIOT | Endereçamento de memória z/OS | Removido (sem equivalente JVM) | — |
| CODING-TO-BE-DONE | Declarado mas nunca testado/setado | Documentado como no-op em cada classe | TD-10 |

---

## Melhorias de segurança

| Finding | Severidade | Correção |
|---|---|---|
| SEC-003: senha em plaintext | HIGH | ✅ BCrypt em `UserDetailsServiceImpl` |
| SEC-005: CVV armazenado | CRITICAL (PCI DSS) | ✅ Campo ausente de `CardEntity` e todas as APIs |
| SEC-009: sem autorização em user mgmt | HIGH | ✅ `@PreAuthorize("hasRole('ADMIN')")` em todos os endpoints |
| SEC-014: sem validação no import | HIGH | ✅ Bean Validation em `DataImportJob` |

---

## Artefatos

| Artefato | Localização |
|---|---|
| Plano de modernização | `analysis/CardDemo/MODERNIZATION_BRIEF.md` |
| Catálogo de regras de negócio | `analysis/CardDemo/BUSINESS_RULES.md` |
| Inventário de objetos de dados | `analysis/CardDemo/DATA_OBJECTS.md` |
| Topologia de dependências (interativo) | `analysis/CardDemo/TOPOLOGY.html` |
| Golden master batch | `modernized/CardDemo/BASELINE.md` |
| Playbook Phase 1 | `modernized/CardDemo/PHASE1_PLAYBOOK.md` |
| Notas de transformação | `modernized/CardDemo/carddemo-batch-eod/TRANSFORMATION_NOTES.md` |
| Declaração de conclusão do PoC | `modernized/CardDemo/POC_COMPLETE.md` |

---

## Branch

```bash
git checkout modernize    # artefatos de modernização + target Java (branch ativo)
git checkout main         # apenas fonte COBOL original
```
