# code2req — Documentation ↔ Implementation Consistency Audit

**Audited:** 2026-07-01  
**Sources:** `docs/PRD.md` (v5.4, 1590 lines) · `docs/sdlc/sdlc-context.json` · `docs/sdlc/tech-stack.md` · 6 ADRs · 54 user-story files · 28 Gherkin feature files (222 scenarios) · `features/CHANGELOG.md` · full `src/main/java` + `src/test/java` (76 test files) · `pom.xml` · 4 `application*.properties` · `project-manifest.yaml`.

**Limitation:** Runtime behavior of LLM/Embabel paths was not executed (would require live API keys). Items so affected are flagged **[Requires Manual Validation]**.

---

## 1. Executive Summary

**Overall alignment assessment — Moderate, with a systematic "documentation lag" pattern.**  
The project's design intent is well-traceable (PRD → epics → features → stories → Gherkin is largely coherent for the catalogued scope), but **documentation has not kept pace with implementation**, and two whole phases documented as "future" are in fact substantially implemented. The codebase is ahead of several of its own documents.

**Headline numbers:**
- 27 documented features (F001–F027, incl. uncatalogued F025): **16 Correctly Implemented, 4 Partial, 6 Missing, 1 Implemented-but-Not-Documented-at-catalog-level (F025).**
- 222 Gherkin scenarios across 28 `.feature` files; **221 carry `@draft`** — only F014 is marked `# Implemented`. Implementation reality is far ahead of this status signal.
- ~~6 ADRs; **ADR-006 is not referenced in `tech-stack.md`**.~~
- ~~**76 test files exist** — `AGENTS.md`'s claim "No tests exist yet" is **false**~~.

### Major Risks

| Sovled | # | Risk | Impact | Source |
|--------|------|--------|--------|
|    [x] | ~~R1~~ | ~~**Output contract drift (resolved).** `semantic_manifest.json` is now schema-conformant. The manifest is serialized from typed Java POJOs (`generation/domain/model/manifest/`) that mirror the schema via `@JsonNaming(SnakeCaseStrategy)`, guaranteeing structural conformance at compile time. Runtime schema validation is not needed.~~ | ~~Critical~~ | ~~`ManifestMapper`, `generation/domain/model/manifest/`~~ |
|    [x] | ~~R2~~ | ~~**Quality audit (F024) — superseded by typed manifest POJOs.** The manifest schema is enforced at compile time via Java records annotated with `@JsonNaming(SnakeCaseStrategy)`, guaranteeing structural conformance to PRD §6.2 by construction. Runtime schema validation is not required. `SemanticManifestValidator` removed.~~ | ~~High~~ | ~~`generation/domain/model/manifest/` (16 records) + `ManifestMapper`~~ |
|    [] | R3 | **F026 review command entirely missing.** No `ReviewCommand`, no `--force-phase3` flags. PRD and US055 + 6 Gherkin scenarios describe them in detail. | High | Absent from `infrastructure/cli/command/` |
|    [] | R4 | **Parser SPI (F007/F008/F009) missing.** The "Language Extension Framework" epic E002 has stories, Gherkin, and no code. Java-only, hardcoded extension routing. | Medium | No `LanguageParser` interface |

### Areas with Highest Divergence (doc vs. code)

1. ~~`semantic_manifest.json` output shape (F024/F027).~~
2. ~~Phase 2/3 "future" status labels in `README.md`/`AGENTS.md` vs. implemented code.~~
3. ~~`application.properties` vs. PRD §Appendix — 5 concrete deviations (datasource, prefix, base-url, model, spring-ai version).~~
4. ~~`tasks` SQLite schema vs. PRD §2.1.6 (`source_hash`→`content_hash`, missing `json_payload`).~~
5. ~~Maven dependency resolution (F002): class exists but **not wired** and uses `dependency:tree` not the PRD-mandated `depgraph-maven-plugin:4.0.3`.~~

### Documentation Quality Assessment — Mixed

- *PRD*: high quality, internally consistent, detailed schemas/state machines; but §Appendix pom/properties stale.
- *User stories*: consistent format, good acceptance criteria; but **no `Status:` field on any of 54 files** — status only inferable from checkbox state (1 fully done, 1 partial, 52 unchecked).
- *Gherkin*: strong coverage (222 scenarios, every scenario `@US`-tagged); but `@draft` is over-applied and `CHANGELOG.md` is stale (stops 2026-06-12, covers only F001–F009, wrong scenario counts).
- ~~*ADRs*: clean and well-reasoned; but `tech-stack.md` missing ADR-006.~~
- *Catalog (`sdlc-context.json`)*: internally contradictory (scope says Phase 2/3 out of scope while features[] lists F016–F027) and missing entire E005/F025 cluster.

### Traceability Maturity Assessment — Low-to-Medium

Traceability exists by *convention* (filenames encode `E00x-F0xx-US0xx`; Gherkin tags `@US0xx`; catalog cross-links), but it is **not enforced and has decayed**: broken links (~~US036 no file~~, ~~US056 dup~~, E005/F025 orphan), stale status, no automated check.

---

## 2. Traceability Matrix

**Legend:** ✅ Correctly Implemented · ◑ Partially Implemented · ❌ Not Implemented · ⚠ Implemented-but-Not-Documented(at catalog) · ⊘ Documented-but-Not-Implemented · ≢ Implemented Differently · ? Requires Manual Validation

| Requirement / Capability | PRD | User Story | Feature (Gherkin) | ADR | Code Reference | Status |
|---|---|---|---|---|---|---|
| Manifest parsing (F001) | §3.1, §2.1.7 | US001/US002/US003 | E001-F001 (10 scen) | — | `ManifestLoader`, `ManifestValidator` | ✅ |
| ~~Maven depgraph (F002)~~ | ~~§2.1.1~~ | ~~US004/US005~~ | ~~E001-F002 (4 scen)~~ | ~~—~~ | ~~**REMOVED** — `MavenDependencyResolver` was dead code (zero callers, never wired)~~ | ~~❌~~ |
| Java AST analysis (F003) | §2.1.2 | US006/7/8/9/10/23/26/27 | E001-F003 (26 scen) | ADR-001 | `AstAnalysisVisitor`, `EndpointVisitor`, broker visitors, etc. | ✅ |
| Secret redaction + exclude (F004) | §2.1.4/5 | US011/US012 | E001-F004 (6 scen) | ADR-005 | `SecretRedactor`, `ExcludeFilter` | ✅ |
| Index output + SQLite (F005) | §2.1.3/6 | US013/US014/US017 | E001-F005 (7 scen) | ADR-003/004 | `TaskStore`, `TaskStoreSchema` (JsonIndexWriter removed — SQLite is canonical) | ✅ |
| CLI scan orchestration (F006) | §5.7 | US015/US016/US028 | E001-F006 (10 scen) | — | `ScanCommand`, `IndexingOrchestrator` | ✅ |
| Parser SPI (F007) | §2.1.2 | US018/US019 | E002-F007 (4 scen) | ADR-001 | **NONE** (no `LanguageParser` interface) | ⊘ |
| Parser discovery (F008) | — | US020/US021 | E002-F008 (5 scen) | — | **NONE** | ⊘ |
| Shared pipeline (F009) | — | US022 | E002-F009 (3 scen) | — | **NONE** | ⊘ |
| Two-pass + CallGraph (F010) | §2.1 | ~~US030 (**US036 no file**)~~ | E001-F010 (13 scen) | — | `Pass1DeclarationCollector`, `GlobalDeclarationRegistry`, `CallGraphVisitor` | ✅ |
| DB access detection (F011) | §2.1.2/§3.7 | US031 | E001-F011 (7 scen) | — | `DbAccessDetector` SPI + 8 detectors | ✅ |
| ~~Outbound HTTP (F012)~~ | ~~§2.1.2/§3.2~~ | ~~US032 (all [x])~~ | ~~E001-F012 (15 scen)~~ | ~~—~~ | ~~9 `HttpClientDetector` impls, `FloatingLinkResolver` (confidence 1.0/0.8/0.6/0.4 — ≢ PRD §3.2)~~ | ~~✅ ≢~~ |
| Topic link (F013) | §3.3 | US033 | E001-F013 (6 scen) | — | `TopicLinkResolver` + 3 broker strategies | ✅ |
| ~~SQLite tables (F014)~~ | ~~§2.1.6~~ | ~~US034/US035~~ | ~~E001-F014 (7 scen, only non-`@draft`)~~ | ~~ADR-003~~ | ~~`TaskStoreSchema` (5 tables; extra cols)~~ | ~~✅ ≢~~ |
| JSP/Thymeleaf (F015) | §2.1.2 | US037/8/9/40 | E001-F015 (15 scen) | — | `JspTemplateParser`, `ThymeleafTemplateParser`, `TemplateLinkResolver`, `WebXmlAnalyzer` | ✅ |
| Phase 2 Planner (F016) | §2.2 | US041/US042 + **~~US056 dup~~** | E003-F016 (12 scen) | — | `EnrichmentPlanner` + 11 rules | ✅ (+extra) |
| ~~LLM Executor (F017)~~ | ~~§2.2/§4~~ | ~~US043/US044~~ | ~~E003-F017 (6 scen)~~ | ~~—~~ | ~~`LlmEnrichmentService` (manual executor, not `@Async`; spring-ai 1.1.1)~~ | ~~✅ ≢~~ |
| Orchestrator (F018) | §3.5 | US045 | E003-F018 (7 scen) | ADR-006 | `EnrichmentOrchestrator`, `EnrichmentDag` | ✅ |
| plan + run (F019) | §5.7/§3.5 | US046/US047 | E003-F019 (9 scen) | ADR-006 | `PlanCommand`, `RunCommand` (missing `--force-phase3`; no fail-stop guards) | ◑ |
| Test mining (F020) | §3.4 | US048 | E003-F020 (5 scen) | — | `TestFileMatcher`, `TestAssertionExtractor`, `PairedExecutionResolver` | ✅ |
| Embabel setup (F021) | §2.3/§5.4 | US049 (4/7 [x]) | E004-F021 (3 scen) | — | Dep in `pom.xml`, embabel props, `AppConfig` beans | ✅? |
| CodebaseKnowledge (F022) | §2.3 | US050 | E004-F022 (5 scen) | — | `CodebaseKnowledge`, `ExtractionOrchestrator.buildCodebaseKnowledge()` | ✅ |
| Embabel agent (F023) | §2.3/§3.8 | US051 | E004-F023 (11 scen) | ADR-006 | `FunctionalRequirementAgent` + 6 actions + quarantine (guardrails config-driven) | ◑ |
| ~~Quality audit (F024)~~ | ~~§5.3/§6.2~~ | ~~US052~~ | ~~E004-F024 (2 scen)~~ | ~~—~~ | ~~**SUPERSEDED** — manifest schema enforced at compile time via typed POJOs in `generation/domain/model/manifest/`. Runtime validation not required. `SemanticManifestValidator` removed.~~ | ~~⚠ Superseded~~ |
| Snapshot (F025) | §5.9 | US053/US054 (**uncatalogued**) | E005-F025 (8 scen, uncatalogued) | — | `SnapshotService`, `RefreshableDataSource`, `SnapshotCommand` | ⚠ ✅ |
| Review command (F026) | §5.5/§5.5a | US055 | E004-F026 (6 scen) | — | **NONE** | ⊘ |
| Domain model + writers (F027) | §6.1/§6.2 | ~~US056 (dup ID)~~ | E004-F027 (4 scen) | ADR-006 | records ✅ + manifest POJOs (16 records in `generation/domain/model/manifest/`) + `ManifestMapper` converts extraction domain → manifest POJOs, serialized by Jackson. Manifest now conforms to §6.2 schema. | ✅ |
| `resume` standalone command | §5.7 | — | — | — | **NONE** (only `--resume` flags on scan/enrich/run) | ⊘ |
| ~~CLI commands `task-list`/`task-findings`/`task-set-status`~~ | ~~§5.7~~ | ~~—~~ | ~~—~~ | ~~—~~ | ~~`TaskCommands` (renamed `task list`/`findings`/`set-status`; adds `--from`/`--verbose`; `--delete-findings` defaults true)~~ | ~~≢~~ |
| ~~`extract` / `generate` commands~~ | ~~(folded in `run`)~~ | ~~—~~ | ~~—~~ | ~~ADR-006~~ | ~~`ExtractCommand`, `GenerateCommand` (extras, not in PRD §5.7)~~ | ~~⚠~~ |
| ~~76 test files~~| — | — | — | — | ~~`src/test/java/...` (76 files; AGENTS.md says none)~~ | ~~⚠~~ |

---

## 3. Documentation Findings

### 3.1 PRD Requirements Missing from User Stories

| PRD Requirement | PRD Ref | Detail |
|---|---|---|
| ~~`depgraph-maven-plugin:4.0.3:graph -DgraphFormat=json` exact invocation~~ | ~~§2.1.1~~ | ~~US004/US005 speak only of "Maven dependency resolution" generically.~~ |
| ~~`CombinedTypeSolver` + annotation-driven fallback~~ | ~~§2.1.2~~ | ~~US006 mentions heuristic fallback but no story references `CombinedTypeSolver`. (Code doesn't use it either.)~~ |
| ~~`web.xml` endpoint discovery (`WebXmlAnalyzer`)~~ | ~~§2.1.2~~ | ~~No dedicated story; code implements it.~~ |
| ~~`@NamedQuery`/`@NamedNativeQuery` detection (incl. container forms)~~ | ~~§2.1.2~~ | ~~US031 AC omits these. Code implements via `NamedQueryDetector`.~~ |
| ~~Raw JDBC detection (`Connection.prepareStatement`, `Statement.executeQuery`, etc.)~~ | ~~§2.1.2~~ | ~~US031 omits. Code implements via `RawJdbcDetector`.~~ |
| `max-discovery-depth` default=3 + `HOP_DEPTH` quarantine reason | §5.2 | US045 covers "max hop depth" generically; no story pins default=3 or `HOP_DEPTH`. |
| Phase 3 marker task (`__phase3_marker__`, SHA-256) lifecycle | §5.5 §5 | US051 mentions `__phase3_marker__` but full lifecycle (PENDING/ENRICHING/ENRICHED/FAILED + `--force-phase3` + `.tmp.` cleanup) is PRD-only. **Not implemented in code.** |
| Error-feedback retry (feed failed JSON + parse error back to LLM) | §5.4 | US043 covers rate-limit backoff + invalid→FAILED, but not the immediate error-feedback retry. **Code does implement it** (`LlmEnrichmentService`). |
| CLI Visual Telemetry (progress bar, token/cost counters, backoff warnings) | §5.6 | No story; no feature file. Partially in `StatusCommand`. |
| ~~Snapshot metadata must include git commit hash~~ | ~~§5.9 §5~~ | ~~US053/054 omit this. Code omits it too.~~|

### 3.2 PRD Requirements Missing from Features (Gherkin)

- No `.feature` covers ~~web.xml discovery~~, ~~`@NamedQuery`, raw JDBC detection~~, ~~`NamedParameterJdbcTemplate`/`SimpleJdbcCall`~~, ~~view-returning `void` controllers with implicit view~~, or the Phase 3 marker lifecycle (all in PRD §2.1.2/§5.5).
- No scenario covers `--force-phase3`, ~~error-feedback retry~~, or `validate` of `code-graph-index.json` (now removed — SQLite is canonical) (PRD §5.7 says `validate` should validate both manifest and index structure).

### 3.3 User Stories Without PRD Coverage

All 54 stories trace to a PRD section. However, E005-F025 (US053/US054) Snapshot & Restore — which *does* have PRD §5.9 coverage — ~~is **absent from `sdlc-context.json`** (epics[], features[], stories[], `artifacts.gherkin_files[]`). Implementation exists; catalog registration is the gap.~~

### 3.4 Features Without PRD Coverage

| Feature | Note |
|---|---|
| ~~F025 Snapshot & Restore~~ | ~~Has PRD §5.9 coverage but not in feature catalog (catalog jumps F024→F026).~~ |
| F026 review command | PRD §5.5/§5.5a covers it; catalogued; **not implemented**. |
| ~~`extract` / `generate` CLI commands~~ | ~~Introduced by ADR-006; **not in PRD §5.7 command table** (PRD folds Phase 3 into `run`). ADR-driven extension that PRD never absorbed.~~ |

### 3.5 ADRs Without Clear Functional Justification

None. All six ADRs tie to clear business/architectural drivers. ADR-006 (Extract/Generate split) is functionally justified (regeneration cost, separation of concerns).  
**However**, ADR-006 is **not listed in `tech-stack.md`** (which only maps ADR-001–005).

### 3.6 Documentation Contradictions

| # | Conflict | A | B | Impact |
|---|---|---|---|---|
| C1 | Phase 2/3 scope | `sdlc-context.json scope.out_of_scope` lists them as **out of scope** | Same file `epics[]` includes E003/E004, `features[]` lists F016–F027; PRD v5.4 documents them in-scope; code implements most | Onboarding confusion |
| ~~C2~~ | ~~Phase status~~ | ~~`README.md` (Phase 2/3 "🔜 Future"), `AGENTS.md` ("Phase 2 (future)", "Phase 3 (future)")~~ | ~~PRD v5.4 + code implement Phase 2 (F016–F020) and Phase 3 (F021–F027) substantially~~ | ~~User/developer misinformed~~ |
| ~~C3~~ | ~~US056 identity~~ | ~~Catalog + `E004-F027-US056…md` = "Domain model and output writers"~~ | ~~`E003-F016-US056…md` = "Planner qualifies DTOs with bean validation" (orphan, not in catalog)~~ | ~~Duplicate ID; traceability broken~~ |
| C4 | F006 stories | Feature header says "US015, US016, US017"; catalog says US015/US016 | Actual tagged scenarios: US015/US016/**US028** (clean); US017 belongs to F005 | Mapping broken |
| C5 | F003 stories | Feature tags 5 `@US023` Kafka scenarios + header lists US023 | Catalog F003 `user_stories[]` omits US023 | Kafka story uncatalogued for F003 |
| C6 | FloatingLink confidence | PRD §3.2: literal 1.0, path-variable 0.8 only | US032 + code: 1.0/0.8/0.6/0.4 (`FloatingLinkResolver.java:108-119`) | PRD under-specifies |
| ~~C7~~ | ~~`tasks` schema~~ | ~~PRD §2.1.6: `source_hash`, `json_payload`~~ | ~~`TaskStoreSchema.java:31`: `content_hash`, **no `json_payload`**~~ | ~~Phase 2 output has no reserved column~~ |
| C8 | spring-ai version | PRD §Appendix: 1.0.0 | `pom.xml:22`: 1.1.1 | Version drift |
| C9 | Thread prefix | PRD §Appendix: `c2r-executor-` | `application.properties:25`: `c2r-orchestrator-` | Log parsing confusion |
| C10 | Datasource URL | PRD §Appendix: `jdbc:sqlite:.code2req_cache.db` | `:12`: `jdbc:sqlite:sqlite.db` + `:67`: `db-path=./spec-output/sqlite.db` (two conflicting paths) | DB location ambiguity; `.gitignore` ignores `.code2req_cache.db*` but `sqlite.db*` committed |
| C11 | OpenRouter base URL | PRD §Appendix: `https://openrouter.ai/api/v1` | `:34`: `https://openrouter.ai/api` (no `/v1`) | Auth/routing divergence |
| C12 | Default model | PRD §Appendix: `${OPENROUTER_MODEL:deepseek/deepseek-v4-flash:free}` (env-overridable) | `:37`: hardcoded `openai/gpt-oss-20b:free` | No env override |
| C13 | `@Async` usage | PRD §2.2: `@Async("orchestratorTaskExecutor")` | `LlmEnrichmentService:61,116`: manual `taskExecutor.execute(work)` | Threading model differs |
| ~~C14~~ | ~~Test existence~~ | ~~`AGENTS.md`: "No tests exist yet (src/test/java/ is empty)"~~ | ~~76 test files exist~~ | ~~Onboarding mismatch~~ |

### 3.7 Ambiguous or Incomplete Documentation

- **No `Status:` field on any of 54 user stories.** Status only inferable via checkboxes (US032 14/14 [x], US049 4/7 [x], all else unchecked). Unreliable.
- ~~**`features/CHANGELOG.md` is stale**: stops 2026-06-12 14:30, covers only F001–F009, scenario counts wrong (F001 says 8 → actually 10; F003 says 14 → actually 26). F010–F027 and F025 unlogged~~.
-~~ **F025 header** reads `# Phase 14 draft` — project has Phases 1–3. Likely a typo~~.
- ~~**PRD §2.1.7 checklist** mixes `[ ]`/`[x]` inconsistently (e.g., `EndpointDetector` SPI marked `[x]` while many implemented items remain `[ ]`). Not a reliable progress indicator.~~
- **`sdlc-context.json` `meta.phase = "3"`** vs `scope.out_of_scope` listing Phase 3 — internally ambiguous about current phase.
- **US049** marked 4/7 `[x]` ("Embabel initializes at startup" unchecked), yet `pom.xml` has dep and `AppConfig` builds beans — runtime-init criteria **[Requires Manual Validation]**.
- **Task ID formula** in `AGENTS.md` omits `target_name` from the hash; PRD §5.1 and §2.1.6 SQL comment disagree about whether `target_name` is included. **Code (`TaskIdHasher`) should be audited.**

---

## 4. Implementation Findings

### 4.1 Implemented but Not Documented

| Functionality | Code Evidence | Doc Gap |
|---|---|---|
| ~~Snapshot & Restore (full feature)~~ | ~~`SnapshotService`, `SnapshotCommand`, `RefreshableDataSource`~~ | ~~Present in PRD §5.9 + US053/US054 + `.feature`, but **absent from `sdlc-context.json` catalog** (E005/F025/US053/US054).~~ |
| ~~`extract` and `generate` standalone commands~~ | ~~`ExtractCommand.java:32`, `GenerateCommand.java:24`~~ | ~~Introduced by ADR-006, but **PRD §5.7 command table never updated** (still folds Phase 3 under `run`).~~ |
| `DtoValidationRule` planner rule | `enrichment/domain/planner/rule/DtoValidationRule` (11th rule) | Has orphan `E003-F016-US064` story + 2 Gherkin scenarios, but **not in PRD §2.2 qualification list** (PRD lists 9 rules; code has 11). |
| ~~`WebXmlAnalyzer` ~~| ~~`indexing/domain/analyzer/web/endpoint/WebXmlAnalyzer.java`~~ | ~~PRD §2.1.2 documents; story US058 + 3 Gherkin scenarios added.~~ |
| ~~`NamedQueryDetector`, `RawJdbcDetector`~~ | ~~`indexing/.../db/detector/`~~ | ~~PRD §2.1.2 documents; stories US059/US060 + 7 Gherkin scenarios added.~~ |
| ~~`CommandSuggestionAspect` + `SuggestionService` ("did you mean?" AOP)~~ | ~~`infrastructure/cli/support/`~~ | ~~Not in PRD/US/Feature.~~ |
| ~~76 test files~~ | ~~`src/test/java/...`~~ | ~~`AGENTS.md` claims none (C14).~~ |
| ~~`common/port/*` repository interfaces~~ | ~~`common/port/{Task,ExecutionFinding,TopicLink,FloatingLink,Metrics}Repository`~~ | ~~Not in any doc; **not implemented by the stores** — parallel hierarchy, appears unused.~~ |
| `spring-ai-client-chat` + `spring-ai-autoconfigure-model-chat-client` deps; 3 Maven profiles with `embabel-agent-starter-dockermodels`/`-openai-custom` | `pom.xml:97-104, 159-224` | Not in PRD §Appendix pom blueprint. |
| `strict-response-format`, `X-OpenRouter-Plugins: response-healing` header, `BeanOutputConverter`+`ResponseFormat.JSON_SCHEMA` | `LlmEnrichmentService`, `application-opencode.properties` | Not in PRD. |

### 4.2 Documented but Not Implemented

| Item | Source | Evidence of Absence |
|---|---|---|
| **F026 review command** (`review list/show/accept/accept-all/reset/reset-all`) | PRD §5.5a; US055; E004-F026 (6 scen) | No `ReviewCommand` class; grep across `src/main/java` returns nothing; no `HUMAN_REVIEW_REASON` in `FindingType`. Quarantine gaps live only in `extraction-cache.json`, not as `execution_findings` rows (contradicts PRD §5.5a §3). |

| ~~**F024 Quality audit** (semantic_manifest schema validation)~~ | ~~PRD §5.3/§6.2; US052; E004-F024 (2 scen)~~ | ~~**SUPERSEDED** — manifest schema enforced at compile time via typed POJOs in `generation/domain/model/manifest/`. `SemanticManifestValidator` removed — runtime validation not required.~~ |
| **F007/F008/F009 Parser SPI** | E002; US018–US022; E002-F007/8/9 | No `LanguageParser` interface; `ScanCommand` hardcodes `.java`/`.jsp`/`.html`/`web.xml`. |
| `resume` standalone command | PRD §5.7 | No `ResumeCommand`; only `--resume` flags on scan/enrich/run. |
| `run --force-phase3` | PRD §5.7 | `RunCommand.java:29-39` has only `--manifest/--resume/--dry-run/--force/--llm-threshold`. |
| Phase 3 marker task (`__phase3_marker__`) + `.tmp.` cleanup + `--force-phase3` semantics | PRD §5.5 §5-8 | No marker mechanism; Phase 3 completion inferred from task statuses + cache file existence. |
| PRD §3.5 fail-stop guards on `run` (halt on FAILED after scan; halt if 0 qualified after plan; halt on ENRICH_FAILED after enrich) | PRD §3.5 | `RunCommand` chains outputs and strips suggestions; **no halt logic**. |
| `validate` of `code-graph-index.json` structure | PRD §5.7 | `ValidateCommand.java:27` validates **manifest YAML only**. | **Resolved** — `code-graph-index.json` removed; SQLite is canonical. |
| ~~`tasks.json_payload` column~~ | ~~PRD §2.1.6~~ | ~~`TaskStoreSchema.java:31` has `content_hash` instead of `source_hash` and **no `json_payload`**.~~ |
| ~~Snapshot metadata git commit hash~~ | ~~PRD §5.9 §5~~| ~~`SnapshotService` writes name/date/cli_version/sizes/checksums — **no git hash**.~~ |
| `SemanticManifestWriter` / `MarkdownSpecWriter` as distinct classes | F027/US056-b | Manifest is now serialized from typed POJOs (`ManifestMapper` + Jackson) directly from `SynthesizeSpecAction`. No separate writer classes needed — the POJO structure itself defines the output contract. |

### 4.3 Partially Implemented Items

| Item | Implemented | Missing |
|---|---|---|
| ~~F002 Maven depgraph~~ | ~~**REMOVED** — `MavenDependencyResolver` was dead code (zero callers, never wired); class and tests deleted 2026-07-01~~ | ~~—~~ |
| F019 plan + run | `plan` ✅; `run` chains scan→plan→enrich→extract→generate with `--dry-run/--resume/--llm-threshold/--force` | `--force-phase3`; PRD §3.5 fail-stop guards. |
| F023 Embabel agent | 6 GOAP actions + quarantine; priority scoring; sub-chain cache; orphan detection; progressive disclosure | `SynthesizeSpec` not an agent action (per ADR-006 — intentional); guardrails now config-driven (resolved). |
| F027 Domain model + writers | All PRD §2.3 records present (29 model files); `generate` command ✅; `spec.md` produced; 16 manifest POJOs in `generation/domain/model/manifest/`; `ManifestMapper` + Jackson serialization. | `semantic_manifest.json` **conforms to PRD §6.2** — entry_point is structured object, steps present, acceptance_criteria has given/when/then, traceability_graph/review_required/unresolved_reason/mermaid_diagram all present. Serialized from typed manifest POJOs, not hand-built JSON. |
| F021 Embabel setup | pom dep ✅, embabel model props ✅, `AppConfig` ChatClient beans ✅ | US049's "Embabel initializes at startup" / "AgentPlatform available" unchecked — **[Requires Manual Validation]** at runtime. |

### 4.4 Implementation Deviations

1. ~~**Maven plugin**: `MavenDependencyResolver.java:55` runs `mvn dependency:tree --batch-mode`; PRD §2.1.1 mandates `com.github.ferstl:depgraph-maven-plugin:4.0.3:graph -DgraphFormat=json -DoutputDirectory=.`. **REMOVED** — resolver deleted 2026-07-01 (dead code). (High confidence.)~~
2. ~~**`@Async` vs manual executor**: PRD §2.2 says `@Async("orchestratorTaskExecutor")`. `LlmEnrichmentService:61,116` uses manual `taskExecutor.execute(work)`. `@EnableAsync` present but `@Async` unused. (High.)~~
3. ~~**`tasks` schema**: `source_hash`→`content_hash`; no `json_payload`; extra `content_type`; `target_name` defaults `''`. (High.)~~
4. ~~**`floating_links` schema**: extra `client_type`, `source_method` (not in PRD §2.1.6). (High.)~~
5. ~~**`metrics.phase`**: `INTEGER NOT NULL DEFAULT 1` vs PRD `TEXT NOT NULL`. (High.)~~
6. ~~**FloatingLink confidence**: 4-tier 1.0/0.8/0.6/0.4 (`FloatingLinkResolver:108-119`); PRD §3.2 specifies only 1.0 and 0.8. Code aligns with US032; PRD under-specified. (High.)~~
7. ~~**`application.properties` deviations vs PRD §Appendix** (5): datasource `sqlite.db` (PRD `.code2req_cache.db`); thread prefix `c2r-orchestrator-` (PRD `c2r-executor-`); base-url `https://openrouter.ai/api` (PRD `…/api/v1`); model hardcoded `openai/gpt-oss-20b:free` (PRD env-overridable `deepseek/deepseek-v4-flash:free`); spring-ai `1.1.1` (PRD `1.0.0`). (High.)~~
8. ~~**Config bug**: `application.properties:72` is `code2req.snapshot.dir=./snapshotscode2req.execution.phase3-timeout-minutes=60` (two properties concatenated). `SnapshotService` masks via `@Value` default. (High.)~~
9. **Dual DB path**: `spring.datasource.url=jdbc:sqlite:sqlite.db` vs `code2req.output.db-path=./spec-output/sqlite.db`. (High.)
10. ~~**Command naming**: PRD §5.7 uses hyphenated `task-list`/`task-findings`/`task-set-status`; code uses Spring Shell groups `task list`/`task findings`/`task set-status`. (High.)~~
11. ~~**`CombinedTypeSolver`**: PRD §2.1.2 mandates it with annotation fallback. Code uses `StaticJavaParser` + `LanguageLevel` + heuristics — no `CombinedTypeSolver`. (High.)~~
12. ~~**Embabel `SynthesizeSpec`**: PRD §2.3/§3.8 list as an agent action; ADR-006 deliberately moves it out of the agent. **Intentional, ADR-sanctioned deviation**, but PRD text never updated. (High.)~~

### 4.5 Potentially Obsolete or Dead Functionality

- ~~**`MavenDependencyResolver`** — **REMOVED 2026-07-01** (was dead code, zero callers).~~
- ~~**`common/port/*Repository` interfaces** — 5 ports defined; stores do **not** implement them. Possibly abandoned hexagonal skeleton.~~
- ~~**`indexing/application/port/input/ScanProjectUseCase` + `ScanProjectService`** — DDD skeleton bypassed by `ScanCommand` calling `IndexingOrchestrator` directly. Appears unused.~~
- ~~**`synthesis/` test package** — `LinkRegistryTest`/`SemanticEnrichmentTest` with **no corresponding main-source `synthesis/` package**. Tested classes live in `extraction/domain/model`. Stale package layout.~~
- ~~**`.code2req-history`** (repo root) + `code2req.log`/`spring-shell.log` + committed `sqlite.db*` — runtime artifacts. `sqlite.db*` **not** in `.gitignore` (only `.code2req_cache.db*` are).~~
- ~~**PRD §2.1.7 Phase-1 checklist** — many `[ ]` items are implemented (`GlobalDeclarationRegistry`, `CallGraphVisitor`, `DbAccessVisitor`, `TopicLinkResolver`, `FloatingLinkResolver`, `execution_findings`/`topic_links`/`floating_links`/`metrics` tables) but still unchecked. Stale indicator.~~

---

## 5. Feature (Gherkin) Analysis

Aggregate: 28 files, 222 scenarios (1 `Scenario Outline`), 221 `@draft`, 0 tagged future/TODO.

### Per-feature status

| Feature | Scenarios | PRD Alignment | Story Alignment | Implementation | Missing Scenarios | Key Issue |
|---|---|---|---|---|---|---|
| F001 | 10 | ✅ | ✅ US001-3 | ✅ | web.xml not covered | — |
| ~~F002~~ | ~~4~~ | ~~✅~~ | ~~✅ US004-5~~ | ~~❌ (removed)~~ | ~~—~~ | ~~Class and tests deleted 2026-07-01 (was dead code)~~ |
| F003 | 26 | ✅ | ⚠ US023 uncatalogued | ✅ | @NamedQuery, raw JDBC, web.xml | — |
| F004 | 6 | ✅ | ✅ | ✅ | — | — |
| ~~F005~~ | ~~7~~ | ~~✅~~ | ~~✅~~ | ~~✅ (≢ schema)~~ | ~~`json_payload` persistence~~ | ~~Schema deviations~~ |
| F006 | 10 | ✅ | ⚠ header mislabels US017; real 3rd US028; catalog omits US028 | ✅ | `resume` standalone command | — |
| F007 | 4 | — | ✅ US018-19 | ❌ | All | No `LanguageParser` interface |
| F008 | 5 | — | ✅ US020-21 | ❌ | All | No registry |
| F009 | 3 | — | ✅ US022 | ❌ | All | Consequence of F007/8 |
| ~~F010~~ | ~~13~~ | ~~✅~~ | ~~⚠ US036 tagged (6 scen), **no story file**~~ | ~~✅~~ | ~~—~~ | ~~Missing story file for US036~~ |
| F011 | 14 | ✅ | ✅ US031, US059, US060 | ✅ | — | Stories US059/US060 + 7 scenarios cover NamedQuery and raw JDBC |
| F012 | 15 | ✅ | ✅ US032 (all [x]) | ✅ (≢ confidence) | — | Confidence 0.6/0.4 not in PRD §3.2 |
| F013 | 6 | ✅ | ✅ US033 | ✅ | — | — |
| F014 | 7 | ✅ | ✅ US034-35 | ✅ (only non-`@draft`) | — | Extra cols |
| F015 | 15 | ✅ | ✅ US037-40 | ✅ | — | — |
| F016 | 12 | ✅ | ~~~~⚠ `@US056` dup~~~~ | ✅ (+extra rule) | — | `DtoValidationRule` not in PRD §2.2 |
| F017 | 6 | ✅ | ✅ US043-44 | ✅ (≢ @Async) | Error-feedback retry | Code implements retry, scenario missing |
| F018 | 7 | ✅ | ✅ US045 | ✅ | — | — |
| F019 | 9 | ✅ | ✅ US046-47 | ◑ | `--force-phase3`, fail-stop guards | Scenarios assume guards not implemented |
| F020 | 5 | ✅ | ✅ US048 | ✅ | — | — |
| F021 | 3 | ✅ | ✅ US049 (4/7 [x]) | ✅ | Runtime init [Requires Manual Validation] | Story status stale |
| F022 | 5 | ✅ | ✅ US050 | ✅ | — | — |
| F023 | 11 | ✅ | ✅ US051 | ◑ | — | Guardrails config-driven (resolved) |
| ~~F024~~ | ~~2~~ | ~~✅~~ | ~~✅ US052~~ | ~~⚠ Superseded~~ | ~~Both scenarios~~ | ~~Superseded — manifest schema enforced at compile time via typed POJOs~~ |
| F025 | 8 | ✅ | ⚠ US053-54 uncatalogued | ✅ (missing git hash) | Git commit hash in metadata | Unregistered in catalog |
| F026 | 6 | ✅ | ✅ US055 | ❌ | All | No `ReviewCommand` |
| F027 | 4 | ✅ | ~~~~⚠ `@US056` dup~~~~ | ✅ | Manifest conforms to §6.2 — serialized from typed manifest POJOs | "valid semantic_manifest.json" criterion passes |


---

## 6. User Story Analysis

(54 files; **no `Status:` field** anywhere. Status inferred from checkboxes: US032 fully done, US049 4/7 partial, all others unchecked.)

### Key findings by story group

| Stories | Feature | Coverage | Issues |
|---|---|---|---|
| US001-003 | F001 | ✅ | — |
| ~~US004-005~~ | ~~F002~~ | ~~❌~~ | ~~**REMOVED** — `MavenDependencyResolver` deleted 2026-07-01 (dead code)~~ |
| US006-010, US023, US026-027 | F003 | ✅ | Code implements additional patterns (web.xml, @NamedQuery, raw JDBC) not in AC |
| US011-012 | F004 | ✅ | — |
| ~~US013-014, US017~~ | ~~F005~~ | ~~✅~~ | ~~`source_hash` vs `content_hash` gap; no `json_payload` column~~ |
| US015-016, US028 | F006 | ✅ | US028 uncatalogued in F006 |
| US018-022 | F007-009 | ❌ | **No implementation.** All acceptance criteria unmet. |
| ~~US030 (+ US036)~~ | ~~F010~~ | ~~✅~~ | ~~**US036 has NO story file** (catalog + 6 Gherkin scenarios reference it)~~ |
| US031 | F011 | ✅ | Missing AC for @NamedQuery, raw JDBC — covered by new US059/US060 |
| US032 | F012 | ✅ (14/14 [x]) | Only fully done story; 0.6/0.4 confidence in AC |
| US033 | F013 | ✅ | — |
| US034-035 | F014 | ✅ | — |
| US037-040 | F015 | ✅ | — |
| US041-042 (+ **~~US056 dup~~**) | F016 | ✅ | ~~US056 (F016) is a duplicate ID with F027~~ |
| US043-044 | F017 | ✅ | Error-feedback retry AC missing (code implements) |
| US045 | F018 | ✅ | — |
| US046-047 | F019 | ◑ | US047 assumes fail-stop guards + `--force-phase3` not implemented |
| US048 | F020 | ✅ | — |
| US049 | F021 | ✅? (4/7 [x]) | Runtime init unchecked [**Requires Manual Validation**] |
| US050 | F022 | ✅ | — |
| US051 | F023 | ◑ | — |
| ~~US052~~ | ~~F024~~ | ~~❌~~ | ~~—~~ |
| US053-054 | F025 | ⚠ | Unregistered in catalog; US053 AC omits git commit hash required by PRD §5.9 |
| US055 | F026 | ❌ | — |
| **US056 (F027)** | F027 | ✅ | "Writer produces valid semantic_manifest.json" — now conforms via typed manifest POJOs enforcing the schema at compile time. |


### Missing story IDs
- ~~**US036** — in catalog + Gherkin (6 scenarios), **no story file**.~~
- ~~**US024/US025/US029** — gap IDs (no file, no reference in catalog or Gherkin).~~
- ~~**US056 duplicate** — `E003-F016-US056` (F016, DTO validation) and `E004-F027-US056` (F027, domain model/writers) share the same ID.~~

---

## 7. ADR Analysis

| ADR | Decision | Requirements | Implemented? | Deviations | Still valid? |
|---|---|---|---|---|---|
| **ADR-001** — JavaParser for AST | Pure-Java AST, no JNI; annotation-driven fallback | F003, E002, US006 | ✅ JavaParser 3.25.9 used | `CombinedTypeSolver` not used (ADR doesn't mandate it) | ✅ |
| **ADR-002** — Spring JDBC over ORM | JdbcTemplate, no ORM | F005, F014, US014 | ✅ | — | ✅ |
| **ADR-003** — SQLite + WAL | WAL + busy_timeout=5000 | F005, US014, US017, NFR005 | ✅ PRAGMAs in properties + AppConfig | — | ✅ |
| **ADR-004** — Deterministic SHA-256 IDs | SHA-256(path+content+test+model+prompt) | F005, US014, NFR004 | ✅ `TaskIdHasher` (formula **[RMV]** — PRD §5.1 vs §2.1.6 disagree about target_name inclusion) | — | ✅ |
| **ADR-005** — In-memory secret redaction | Pattern-based `[REDACTED:type]`, disk untouched | F004, US011, NFR002, R005 | ✅ `SecretRedactor` | — | ✅ |
| **ADR-006** — Extract/Generate split | Split Phase 3 into `extract` (agent, cache) + `generate` (pure Java, idempotent) | F019, F023, F027, US047, US056 | ✅ `ExtractCommand`/`GenerateCommand`/`SynthesizeSpecAction` | PRD §2.3/§3.8/§5.7 **not updated** | ✅ (but PRD needs updating) |

---

## 8. Traceability Gaps

- **Requirements with no User Story:** ~~web.xml discovery~~, ~~@NamedQuery/raw JDBC detection~~, NamedParameterJdbcTemplate/SimpleJdbcCall, CLI Visual Telemetry (§5.6), Phase 3 marker lifecycle, error-feedback retry (§5.4), `--force-phase3` semantics.
- **User Stories with no Feature file:** none.
- **Features with no implementation:** F007, F008, F009, ~~F024~~, F026 (5 features).
- **Code with no documented origin:** ~~WebXmlAnalyzer~~, ~~NamedQueryDetector~~, ~~RawJdbcDetector~~, `DtoValidationRule`, ~~CommandSuggestionAspect~~/~~SuggestionService~~, `common/port/*Repository` (unused), ~~`synthesis/` test package~~, `spring-ai-client-chat`/`-autoconfigure-model-chat-client` deps, 3 Maven profiles.
- **ADRs with no implementation evidence:** none.
- **Broken links:**
  - ~~**US036** — catalog + Gherkin (6 scenarios), **no story file**.~~
  - ~~**US056** — duplicate ID across F016 and F027.~~
  - ~~**E005/F025/US053/US054** — implemented, documented in PRD/US/feature, **absent from `sdlc-context.json` catalog**.~~
  - **F006 header** mislabels US017 (should be US028); catalog F006 omits US028.
  - **F003** tags US023; catalog F003 omits US023.
  - ~~**US024/US025/US029** — gap IDs (no file, no reference).~~
  - **ADR-006** — not in `tech-stack.md`; not reflected in PRD §2.3/§3.8/§5.7.

---

## 9. Documentation Update Plan

| # | Artifact | Change | Priority | Impact | Owner |
|---|---|---|---|---|---|
| 1 | `sdlc-context.json` | Add epic E005, feature F025, stories US053/US054; add F025 to `artifacts.gherkin_files` | High | Traceability integrity | Product |
| 2 | `sdlc-context.json` | Resolve `scope.out_of_scope` contradiction (C1): move Phase 2/3 to in_scope or mark explicitly | High | Scope clarity | Product |
| 3 | ~~User stories~~ | ~~Rename `E003-F016-US056` → US058, register under F016; keep US056 for F027~~ | ~~High~~ | ~~Resolve duplicate ID (C3)~~ | ~~Product~~ |
| 4 | ~~User stories~~ | ~~Author missing **US036** story file (or deregister from catalog + F010 Gherkin)~~ | ~~High~~ | ~~Traceability~~ | ~~Product~~ |
| 5 | User stories | Add `Status:` field (Done/In-Progress/Planned) to every story; reconcile with checkbox state | Medium | Progress visibility | Engineering |
| 6 | `README.md` + `AGENTS.md` | Update Phase table: Phase 2 = Active/Partial, Phase 3 = Active/Partial (not "Future") | High | C2 — user expectations | Product |
| 7 | `AGENTS.md` | Remove "No tests exist yet" (C14) | Medium | Dev onboarding | Engineering |
| 8 | `tech-stack.md` | Add ADR-006; update spring-ai to 1.1.1; add profile-specific embabel starters | Medium | Accuracy | Architecture |
| 9 | ~~PRD §Appendix~~ | ~~Update pom blueprint + `application.properties` to match actuals (C8-C12)~~ | ~~Medium~~ | ~~Blueprint accuracy~~ | ~~Architecture~~ |
| 10 | PRD §5.7 | Add `extract`/`generate` commands; reconcile `task list`/`findings`/`set-status` naming; document `--delete-findings` default=true | High | Command-surface accuracy | Product |
| 11 | PRD §2.3/§3.8 | Update `SynthesizeSpec` as non-agent-action per ADR-006 | Medium | Architecture accuracy | Architecture |
| 12 | PRD §2.1.6 | Reconcile `tasks`/`floating_links` schemas (C7) | Medium | Schema accuracy | Engineering |
| 13 | PRD §3.2 | Document 4-tier confidence (1.0/0.8/0.6/0.4) | Low | Spec accuracy | Engineering |
| 14 | `features/CHANGELOG.md` | Add entries for F010–F027 + F025; correct scenario counts | Low | History | Product |
| 15 | ~~`application.properties:72`~~ | ~~Fix concatenated-line config bug~~ | ~~High~~ | ~~Correctness~~ | ~~Engineering~~ |
| 16 | F006 `.feature` header | Fix "US017" → "US028"; catalog F006 add US028 | Low | Traceability | Product |
| 17 | F003 catalog entry | Add US023 to F003 story list | Low | Traceability | Product |
| 18 | F025 `.feature` header | Fix "Phase 14 draft" typo | Low | Clarity | Product |
| 19 | `.gitignore` | Add `sqlite.db*` | Medium | Repo hygiene | Engineering |

---

## 10. Implementation Backlog

(Only documented requirements not fully implemented. Priority: Business impact + dependency ordering.)

| # | Source | Description | Priority | Complexity | Dependencies | Next Action |
|---|---|---|---|---|---|---|
| ~~1~~ | ~~F024/US052/PRD §5.3,§6.2~~ | ~~**RESOLVED — manifest schema enforced at compile time via typed POJOs.** 16 manifest records in `generation/domain/model/manifest/` mirror the schema; `ManifestMapper` converts extraction domain → typed manifest POJOs; Jackson serialization guarantees structural conformance. `SemanticManifestValidator` removed.~~ | ~~High~~ | ~~Medium~~ | ~~—~~ | ~~**Resolved — compile-time enforcement via typed manifest POJOs**~~ |
| 2 | F027/US056/PRD §6.2 | **Done — `semantic_manifest.json` now conforms to §6.2 schema.** `entry_point` is object, `steps` present, `acceptance_criteria` has `given/when/then`, `traceability_graph`/`review_required`/`unresolved_reason`/`mermaid_diagram` all present. | — | — | — | **Resolved.** |
| 3 | F026/US055/PRD §5.5a | **Review command**: `review list/show/accept/accept-all/reset/reset-all`; persist quarantine as `HUMAN_REVIEW_REASON` `execution_findings` | High | Medium | F023 (exists) | Add `ReviewCommand` + `FindingType` |

| 5 | F007-009/US018-022 | **Parser SPI**: `LanguageParser` interface, extension→parser registry, routing, shared pipeline integration | Medium | High | — | Define SPI; refactor `ScanCommand` routing |
| ~~6~~ | ~~F002/US004-005/PRD §2.1.1~~ | ~~**`MavenDependencyResolver` removed** — was dead code, deleted 2026-07-01~~ | ~~—~~ | ~~—~~ | ~~—~~ | ~~Done (removed)~~ |
| 7 | PRD §5.7 | **`resume` standalone command** | Low | Low | — | Add `ResumeCommand` |
| 8 | PRD §5.7/§3.5 | **`run --force-phase3`** + Phase 3 marker task (`__phase3_marker__`) + `.tmp.` cleanup + fail-stop guards | High | Medium | #1 (marker FAILED) | Add marker + flag + guards |
| ~~9~~ | ~~PRD §5.7~~ | ~~**`validate` should validate `code-graph-index.json`**~~ | ~~Low~~ | ~~Low~~ | ~~—~~ | ~~**Closed** — `code-graph-index.json` removed; SQLite is canonical.~~ |
| 10 | ~~F023/PRD §2.3 §9~~ | ~~**Read guardrails from config** instead of hardcoding `MAX_DEPTH=5`/`LOW_CONFIDENCE_THRESHOLD=0.3` (should be 0.7)~~ | ~~Medium~~ | ~~Low~~ | ~~—~~ | ~~Inject `@Value` into actions~~ |

| 12 | F025/PRD §5.9 §5 | **Add git commit hash to `snapshot.json`** | Low | Low | — | `git rev-parse HEAD` in `SnapshotService` |
| 13 | ~~PRD §2.1.6~~ | ~~**Add `tasks.json_payload` column**~~ | ~~Low~~ | ~~Low~~ | ~~—~~ | ~~Migration in `TaskStoreSchema`~~ |
| 14 | ~~PRD §Appendix~~ | ~~**Reconcile `application.properties`** (datasource, prefix, base-url `/v1`, env-overridable model)~~ | ~~Low~~ | ~~Low~~ | ~~—~~ | ~~Decide canonical values; update code or PRD~~ |
| 15 | F021/US049 | **Verify Embabel runtime init** (US049 unchecked criteria) | Medium | Low | — | `mvn -P opencode spring-boot:run` **[RMV]** |

---

## 11. Recommendations

### Restore Alignment

1. **Treat the `semantic_manifest.json` contract as P0.** The product's stated purpose is a "machine-readable Semantic Manifest JSON" for downstream consumption (PRD §1.1). Output is now schema-conformant AND structurally guaranteed by typed manifest POJOs (compile-time enforcement). Runtime schema validation is not required. Backlog #1 resolved.
2. **Reconcile the catalog before adding features.** Fix `sdlc-context.json` (E005/F025, ~~US056 dup~~, US036, US023/US028 mislinks) and the Phase scope contradiction (Update Plan #1-4).
3. **Decide intentionally on each documented-but-unimplemented feature.** ~~F024~~/F026 are detailed in PRD + US + Gherkin but absent in code. Either implement (Backlog #3) or explicitly descope and update docs.

### Improve Traceability

4. **Add a `Status:` field to every user story** and reconcile with checkbox state; make `@draft` on Gherkin scenarios reflect reality (14 implemented features still `@draft`).
5. **Introduce a lightweight traceability check** (script or test) asserting: every catalog feature has a `.feature`; every `@USxxx` tag has a story file; every story file's (E,F) prefix is in the catalog; no US ID is reused. Would have caught C3-C5 and the E005 orphan automatically.
6. ~~**Keep `features/CHANGELOG.md` current** or retire it in favor of git history.~~

### Reduce Documentation Debt

7. ~~**Update `README.md`/`AGENTS.md` Phase status** (C2) and remove false "no tests" claim (C14).~~
8. ~~**Sync PRD §Appendix** pom/properties with actuals, or mark as "illustrative, see `pom.xml` for canonical versions" (C8-C12).~~
9. ~~**Propagate ADR-006 into PRD §2.3/§3.8/§5.7** so the `extract`/`generate` split and `SynthesizeSpec` relocation are reflected at product level.~~

### Reduce Technical Debt

10. ~~**`MavenDependencyResolver` removed** (dead code); also address the `common/port/*Repository` + `ScanProjectService` DDD skeleton.~~
11. ~~**Fix `application.properties:72`** concatenated-line bug and dual DB-path ambiguity (`sqlite.db` vs `./spec-output/sqlite.db`).~~
12. ~~**Add `.gitignore` entries for `sqlite.db*`** and remove committed runtime DB/log files.~~
13. ~~**Make guardrails config-driven** in `TraceFlowAction`/`QuarantineFlowAction` (Backlog #10) — hardcoded 0.3 vs PRD's 0.7 is a latent behavioral bug.~~
14. ~~**Add missing high-value tests**: `RunCommandTest`, `GenerateCommandTest`, `SnapshotServiceTest`/`SnapshotCommandTest`, `RefreshableDataSourceTest`, `ValidateCommandTest` (snapshot VACUUM-INTO/restore-pool path currently untested).~~

### Improve Governance

15. **Adopt a "doc-change-required" rule for ADRs:** when an ADR changes the PRD command surface or architecture (e.g., ADR-006), the PRD must be updated in the same change. ADR-006 shipped without PRD updates — root cause of §3.8/§5.7 staleness.
16. **Single source of truth for status**: pick either story checkboxes, Gherkin `@draft`, or a `Status:` field — not three divergent signals.

---

**Confidence statement:** Findings marked *High* were verified by direct reading of the cited file/line. *Medium* findings rest on subagent cross-analysis plus spot-checks. *Low/[Requires Manual Validation]* items (notably Embabel runtime init, exact `TaskIdHasher` formula, live LLM/Phase-3 behavior) need execution with credentials to confirm. No conflicts were silently resolved — all contradictions are listed in §3.6 for owner adjudication.
