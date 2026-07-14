# Changelog

## 2026-07-02 — Documentation alignment and traceability fixes

- Removed `features/E001-F002-dependency-graph-resolution.feature` — US004, US005 (dead code, never wired)
- Removed user stories US004 (resolve dependencies automatically) and US005 (operate without Maven)
- Updated `features/E001-F003-java-source-ast-analysis.feature` — added US058 (web.xml endpoint discovery, 3 scenarios)
- Updated `features/E001-F011-database-access-detection.feature` — added US059 (@NamedQuery, 3 scen), US060 (raw JDBC, 3 scen), US061 (NamedParameterJdbcTemplate, 2 scen)
- Updated `features/E001-F015-template-form-detection.feature` — added US062 (view-returning void controllers, 2 scen)
- Updated `features/E003-F017-llm-executor.feature` — added US063 (error-feedback retry, 2 scen)
- Registered E005/F025 (Snapshot & Restore) in sdlc-context.json catalog
- Updated PRD §2.1.3, §2.1.6, §2.2, §3.2, §5.7, Appendix to match implementation
- Fixed `application.properties` config bug (concatenated snapshot.dir line)
- Made guardrails config-driven in `TraceFlowAction`/`QuarantineFlowAction`

## 2026-06-20 — Added Phase 3 features (F021–F028)

- Created `features/E004-F021-embabel-setup.feature` — US049 (3 scenarios)
- Created `features/E004-F022-codebase-knowledge.feature` — US050 (5 scenarios)
- Created `features/E004-F023-embabel-agent.feature` — US051 (11 scenarios)
- Created `features/E004-F024-quality-audit.feature` — US052 (2 scenarios)
- Created `features/E005-F025-snapshot-restore.feature` — US053, US054 (8 scenarios)
- Created `features/E004-F026-review-command.feature` — US055 (6 scenarios)
- Created `features/E004-F027-domain-model-output-writers.feature` — US056 (4 scenarios)
- Created `features/E004-F028-interactive-mode.feature` — US057 (6 scenarios)

## 2026-06-17 — Added Phase 2 orchestration (F016–F020)

- Created `features/E003-F016-planner.feature` — US041, US042 (12 scenarios)
- Created `features/E003-F017-llm-executor.feature` — US043, US044 (6 scenarios)
- Created `features/E003-F018-orchestrator.feature` — US045 (7 scenarios)
- Created `features/E003-F019-cli-run-command.feature` — US046, US047 (9 scenarios)
- Created `features/E003-F020-test-suite-mining.feature` — US048 (5 scenarios)

## 2026-06-15 — Added Phase 1+2 features (F010–F015)

- Created `features/E001-F010-two-pass-pipeline-and-call-graph.feature` — US030 (13 scenarios)
- Created `features/E001-F011-database-access-detection.feature` — US031 (7 scenarios)
- Created `features/E001-F012-outbound-http-detection.feature` — US032 (15 scenarios)
- Created `features/E001-F013-event-link-resolution.feature` — US033 (6 scenarios)
- Created `features/E001-F014-structured-trace-sqlite-persistence.feature` — US034, US035 (7 scenarios)
- Created `features/E001-F015-template-form-detection.feature` — US037, US038, US039, US040 (15 scenarios)

## 2026-06-12 — Initial draft generation (corrected counts)

- Created `features/E001-F001-project-configuration-and-manifest-parsing.feature` — US001, US002, US003 (8 scenarios)
- Created `features/E001-F002-dependency-graph-resolution.feature` — US004, US005 (4 scenarios)
- Created `features/E001-F003-java-source-ast-analysis.feature` — US006–US010, US023, US026, US027 (26 scenarios)
- Created `features/E001-F004-secret-redaction-and-exclude-filtering.feature` — US011, US012 (6 scenarios)
- Created `features/E001-F005-index-output-and-sqlite-persistence.feature` — US013, US014, US017 (7 scenarios)
- Created `features/E001-F006-cli-scan-orchestration.feature` — US015, US016, US028 (10 scenarios)

## 2026-06-12 14:00 — Added crash recovery

- Added US017 to `features/E001-F005-index-output-and-sqlite-persistence.feature` — crash/state recovery (2 scenarios)
- Updated `features/E001-F006-cli-scan-orchestration.feature` — added resume command scenario under US015

## 2026-06-12 14:30 — Added Language Extension Framework (E002)

- Created `features/E002-F007-parser-abstraction-spi.feature` — US018, US019 (4 scenarios)
- Created `features/E002-F008-parser-discovery-and-routing.feature` — US020, US021 (5 scenarios)
- Created `features/E002-F009-shared-pipeline-integration.feature` — US022 (3 scenarios)
- Renamed F003 description to clarify Java-specific scope
