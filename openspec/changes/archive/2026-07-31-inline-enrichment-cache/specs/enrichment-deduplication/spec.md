## Purpose

Prevents redundant LLM enrichment calls for source files that appear in multiple execution flows during a single extract run.

## ADDED Requirements

### Requirement: Single enrichment per file per run

The system SHALL enrich each source file at most once per extract invocation, regardless of how many execution flows reference the file. If a file has already been submitted for enrichment during the current run, any subsequent flow that includes the same file SHALL reuse the existing enrichment result instead of initiating a new LLM call.

#### Scenario: Shared file across two flows is enriched once

- **WHEN** an extract run traces flows A and B, both referencing `src/main/java/com/example/UserService.java`
- **AND** flow A is processed first and initiates enrichment of `UserService.java`
- **THEN** when flow B encounters `UserService.java`, the system SHALL detect the in-flight or completed enrichment and SHALL NOT submit a second LLM call
- **AND** the enrichment result from flow A SHALL be reused for flow B

#### Scenario: Already-cached file from SQLite is not re-enriched

- **WHEN** an extract run loads existing enrichment data from SQLite via `SemanticEnrichment.reloadFrom()`
- **AND** `UserService.java` already has a stored `ExecutionFinding` of type `SEMANTIC_ENRICHMENT`
- **THEN** the system SHALL skip enrichment for that file entirely, checking the SQLite-backed knowledge cache before the in-memory cache

### Requirement: Cache scoped to a single enrich() call

The in-memory enrichment cache SHALL be scoped to one `enrich()` invocation on `EnrichFlowAction`. When `enrich()` completes, the cache SHALL be cleared so that a subsequent `enrich()` call (e.g., a second Phase 2 run within the same process) starts with a fresh cache.

#### Scenario: Cache is cleared between extract runs

- **WHEN** the first `enrich()` call completes after processing flows A, B, and C
- **AND** a second `enrich()` call is triggered for a new set of flows
- **THEN** the in-memory cache from the first call SHALL NOT affect the second call
- **AND** files enriched in the first call that also appear in the second call SHALL be enriched again if they are not present in the SQLite-backed knowledge cache

### Requirement: Concurrent access safety

The enrichment cache SHALL support concurrent access so that multiple files within the same flow can be submitted for enrichment in parallel without race conditions on cache insertion or lookup.

#### Scenario: Parallel file enrichment within a single flow does not duplicate

- **WHEN** a flow requires enrichment of files `A.java`, `B.java`, and `C.java`
- **AND** file `A.java` is referenced by two different steps within the same flow
- **THEN** the system SHALL submit at most one enrichment task for `A.java`
- **AND** the single enrichment task SHALL serve both steps that reference the file
