# E001 — Deterministic Multi-Language Indexing

## Quick Start
- Build: `mvn clean compile`
- Test: `mvn test`
- Run: `mvn spring-boot:run`

## Manual Test Target
- Path: `/Users/ehdez/workspace/scratch/spring-petclinic`
- Configured in: `project-manifest.yaml`
- Run: launch shell, then `scan --manifest project-manifest.yaml`

## Decisions Made
- F002 placed before F003 (feeds `CombinedTypeSolver`)
- F005 Part 1 (TaskStore) in Phase 1 — needed by ScanCommand mid-scan progress and F003 test verification
- F003 split: 4a = US006+US007+US010, 4b = US008, 4c = US009
- Command priority: `scan` → `resume` → `validate` → `status`
- No `@Transactional` — manual JDBC updates for SQLite

## Verification Guide
- Unit: `mvn test`
- Manual: run `scan` against petclinic → inspect `spec-output/code-graph-index.json` for components, endpoints, etc.
- JSON schema expected: `{ "version": "1.0", "generated_at": "...", "targets": [...] }`

---

## Progress

### Phase 1 — Foundation

#### 1.1 F001: Manifest Parsing (US001, US002, US003)
- [x] Gherkin: [`docs/sdlc/features/E001-F001-project-configuration-and-manifest-parsing.feature`](docs/sdlc/features/E001-F001-project-configuration-and-manifest-parsing.feature)
- [x] Depends on: nothing
- [x] Classes: `ScanTarget`, `ProjectManifest`, `ManifestLoader`, `ManifestValidator`
- [x] Verify: `mvn test` + `mvn spring-boot:run` then `validate --manifest project-manifest.yaml`
- [x] Manual: point manifest at petclinic, run `validate`, confirm targets resolved

#### 1.2 F005 Part 1: SQLite Task Store (US013, US014)
- [x] Gherkin: [`docs/sdlc/features/E001-F005-index-output-and-sqlite-persistence.feature`](docs/sdlc/features/E001-F005-index-output-and-sqlite-persistence.feature)
- [x] Depends on: nothing
- [x] Classes: `Task`, `TaskStore`, `TaskStoreSchema`, `TaskIdHasher`
- [x] Verify: `mvn test`
- [x] Manual: `scan` against petclinic → check `.code2req_cache.db` has tasks table with WAL mode

### Phase 2 — Dependency Resolution

#### 2.1 F002: Maven Dep Graph (US004, US005)
- [x] Gherkin: [`docs/sdlc/features/E001-F002-dependency-graph-resolution.feature`](docs/sdlc/features/E001-F002-dependency-graph-resolution.feature)
- [x] Depends on: 1.1, 1.2
- [x] Classes: `MavenDependencyResolver`, `Dependency`, `DependencyGraph`
- [x] Verify: `mvn test` (12 tests — parse tree, heuristic fallback, maven availability check)
- [x] Manual: run scan with/without `mvn` on PATH, confirm warning logged when absent

### Phase 3 — Pre-processing

#### 3.1 F004: Redaction & Exclude Filtering (US011, US012)
- [x] Gherkin: [`docs/sdlc/features/E001-F004-secret-redaction-and-exclude-filtering.feature`](docs/sdlc/features/E001-F004-secret-redaction-and-exclude-filtering.feature)
- [x] Depends on: nothing (can parallel with 1.1)
- [x] Classes: `SecretRedactor`, `RedactionResult`, `ExcludeFilter`, `ExcludeResult`
- [x] Verify: `mvn test`
- [x] Manual: inspect JSON output for `[REDACTED:*]` placeholders in source snippets — tested via unit tests covering password, API key, token, secret, connection string redaction + `.gitignore`-style glob exclusion with `{,**/}` normalization

### Phase 4 — Core Analysis

#### 4a F003: Components + Endpoints + Scheduled Tasks (US006, US007, US010)
- [x] Gherkin: [`docs/sdlc/features/E001-F003-java-source-ast-analysis.feature`](docs/sdlc/features/E001-F003-java-source-ast-analysis.feature)
- [x] Depends on: 1.1, 1.2, 2.1, 3.1
- [x] Classes: `JavaAstAnalyzer`, `AnalysisContext`, `AnalysisResult`, `ComponentVisitor`, `EndpointVisitor`, `ScheduledTaskVisitor`
- [x] Verify: `mvn test` (28 new tests — 9 ComponentVisitor, 9 EndpointVisitor, 4 ScheduledTaskVisitor, 6 JavaAstAnalyzer)
- [x] Manual: scan petclinic → inspect JSON for: component list, endpoint paths (e.g. `/api/owners`), scheduled tasks with cron

#### 4b F003: Event Listeners (US008)
- [x] Gherkin: [`docs/sdlc/features/E001-F003-java-source-ast-analysis.feature`](docs/sdlc/features/E001-F003-java-source-ast-analysis.feature)
- [x] Depends on: 4a
- [x] Classes: `EventListenerVisitor`, `EventListenerInfo`, `EventPublisherInfo`, `MethodCallInfo`
- [x] Verify: `mvn test` (10 new tests)
- [x] Manual: scan petclinic → inspect JSON for event listeners and call chains

#### 4c F003: Custom Validators (US009)
- [x] Gherkin: [`docs/sdlc/features/E001-F003-java-source-ast-analysis.feature`](docs/sdlc/features/E001-F003-java-source-ast-analysis.feature)
- [x] Depends on: 4a
- [x] Classes: `ValidatorVisitor`, `ValidatorInfo`
- [x] Verify: `mvn test` (10 new tests + 2 integration in JavaAstAnalyzerTest + 2 updated assertions)
- [x] Manual: scan petclinic → inspect JSON for `@Constraint` validators with `isValid` body

### Phase 5 — Output

#### 5.1 F005 Part 2: Index Output + Orphan Recovery (US013 rest, US017)
- [ ] Gherkin: [`docs/sdlc/features/E001-F005-index-output-and-sqlite-persistence.feature`](docs/sdlc/features/E001-F005-index-output-and-sqlite-persistence.feature)
- [ ] Depends on: 1.2, 4a
- [ ] Classes: `IndexWriter`, `OrphanRecovery`
- [ ] Verify: `mvn test` + kill process mid-scan, restart, confirm `resume` recovers
- [ ] Manual: inspect `spec-output/code-graph-index.json` — valid JSON, contains all analysis findings

### Phase 6 — CLI Commands

#### 6.1 F006: `scan` command (US015, US016)
- [ ] Gherkin: [`docs/sdlc/features/E001-F006-cli-scan-orchestration.feature`](docs/sdlc/features/E001-F006-cli-scan-orchestration.feature)
- [ ] Depends on: 1.1, 2.1, 3.1, 4a, 5.1
- [ ] Classes: `ScanCommand` (key: `scan`)
- [ ] Verify: `mvn test` + `mvn spring-boot:run` then `scan` against petclinic
- [ ] Manual: confirm per-stage progress output, zero network calls, exits 0

#### 6.2 F006: `resume`, `validate`, `status` commands (US002, US017)
- [ ] Gherkin: [`docs/sdlc/features/E001-F006-cli-scan-orchestration.feature`](docs/sdlc/features/E001-F006-cli-scan-orchestration.feature)
- [ ] Depends on: 1.1, 1.2, 6.1
- [ ] Classes: `ResumeCommand`, `ValidateCommand`, `StatusCommand`
- [ ] Verify: `mvn test`
- [ ] Manual: `validate --manifest ...`, `status` after scan, `resume` after killed scan

## Story Index
| Story | Feature | Priority | Phase |
|-------|---------|----------|-------|
| US001 | F001 | must | 1.1 |
| US002 | F001 | should | 1.1 |
| US003 | F001 | should | 1.1 |
| US004 | F002 | should | 2.1 |
| US005 | F002 | should | 2.1 |
| US006 | F003 | must | 4a |
| US007 | F003 | must | 4a |
| US008 | F003 | should | 4b |
| US009 | F003 | should | 4c |
| US010 | F003 | should | 4a |
| US011 | F004 | must | 3.1 |
| US012 | F004 | should | 3.1 |
| US013 | F005 | must | 1.2, 5.1 |
| US014 | F005 | should | 1.2 |
| US015 | F006 | must | 6.1 |
| US016 | F006 | should | 6.1 |
| US017 | F005/F006 | should | 5.1, 6.2 |

## Completed

- 2026-06-12 — **Phase 1.1 F001** (Manifest Parsing): `ScanTarget`, `ProjectManifest`, `ManifestLoader`, `ManifestValidator`, `ValidateCommand`, `ManifestValidationResult` — 17 tests ✓
- 2026-06-12 — **Phase 1.2 F005 Part 1** (SQLite Task Store): `Task`, `TaskStatus`, `TaskIdHasher`, `TaskStoreSchema`, `TaskStore` — 18 tests ✓
- 2026-06-12 — **Phase 2.1 F002** (Maven Dependency Resolution): `MavenDependencyResolver`, `Dependency`, `DependencyGraph` — 12 tests ✓
- 2026-06-12 — **Phase 3.1 F004** (Secret Redaction & Exclude Filtering): `SecretRedactor`, `RedactionResult`, `ExcludeFilter`, `ExcludeResult` — 18 tests (10 SecretRedactor + 8 ExcludeFilter) ✓
- 2026-06-13 — **Phase 4a F003** (Components + Endpoints + Scheduled Tasks): `JavaAstAnalyzer`, `ComponentVisitor`, `EndpointVisitor`, `ScheduledTaskVisitor`, `AnalysisContext`, `AnalysisResult`, `ComponentInfo`, `EndpointInfo`, `ScheduledTaskInfo` — 28 new tests ✓
- 2026-06-14 — **Phase 4b F003** (Event Listeners): `EventListenerVisitor`, `EventListenerInfo`, `EventPublisherInfo`, `MethodCallInfo` — 10 new tests ✓
- 2026-06-14 — **Phase 4c F003** (Custom Validators): `ValidatorVisitor`, `ValidatorInfo` — 10 new tests ✓
