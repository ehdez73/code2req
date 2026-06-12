# Changelog

## 2026-06-12 — Initial draft generation

- Created `features/E001-F001-project-configuration-and-manifest-parsing.feature` — US001, US002, US003 (8 scenarios)
- Created `features/E001-F002-dependency-graph-resolution.feature` — US004, US005 (5 scenarios)
- Created `features/E001-F003-java-source-ast-analysis.feature` — US006, US007, US008, US009, US010 (14 scenarios)
- Created `features/E001-F004-secret-redaction-and-exclude-filtering.feature` — US011, US012 (6 scenarios)
- Created `features/E001-F005-index-output-and-sqlite-persistence.feature` — US013, US014 (5 scenarios)
- Created `features/E001-F006-cli-scan-orchestration.feature` — US015, US016 (6 scenarios)

## 2026-06-12 14:00 — Added crash recovery

- Added US017 to `features/E001-F005-index-output-and-sqlite-persistence.feature` — crash/state recovery (2 scenarios)
- Updated `features/E001-F006-cli-scan-orchestration.feature` — added resume command scenario under US015

## 2026-06-12 14:30 — Added Language Extension Framework (E002)

- Created `features/E002-F007-parser-abstraction-spi.feature` — US018, US019 (4 scenarios)
- Created `features/E002-F008-parser-discovery-and-routing.feature` — US020, US021 (5 scenarios)
- Created `features/E002-F009-shared-pipeline-integration.feature` — US022 (3 scenarios)
- Renamed F003 description to clarify Java-specific scope
