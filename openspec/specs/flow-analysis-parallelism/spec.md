## Purpose

Enables concurrent execution of per-flow LLM analysis within a single Phase 3 extract run, reducing total wall-clock time for the analyze phase by processing multiple independent flows in parallel.

## Requirements

### Requirement: Concurrent flow analysis

The Phase 3 `analyzeFlows` action SHALL submit each non-quarantined traced flow for LLM analysis concurrently, up to a configurable maximum number of simultaneous LLM calls, using a shared thread pool. Results from concurrent analyses SHALL be equivalent to results produced by sequential execution for the same inputs.

#### Scenario: All flows complete with correct count

- **WHEN** an extract run traces 18 flows and an analysis executor pool of size 5 is configured
- **THEN** all 18 flows SHALL be analyzed concurrently (at most 5 at a time)
- **AND** the resulting `AnalyzedFlowResult` SHALL contain exactly 18 `FunctionalFlow` entries
- **AND** the total wall-clock time for the analyze phase SHALL be less than the sequential time

#### Scenario: Single flow failure does not abort other flows

- **WHEN** the analysis executor processes flows concurrently
- **AND** one flow's LLM call fails (e.g., transient API error)
- **THEN** the remaining flows SHALL continue processing to completion
- **AND** the failed flow SHALL be omitted or produce a degraded result without blocking the overall analysis

#### Scenario: Flow analysis cache is respected under concurrency

- **WHEN** resume mode is active and a flow's analysis already exists in the `ExecutionFindingStore` cache
- **THEN** the cached analysis SHALL be reused regardless of whether other flows are being analyzed concurrently
- **AND** no duplicate LLM call SHALL be made for the cached flow

### Requirement: Configurable analysis concurrency

The maximum number of concurrent flow analyses SHALL be governed by the existing `code2req.enrichment.max-concurrent-llm-calls` configuration property, providing a single concurrency control point for all LLM operations within Phase 3.

#### Scenario: Concurrency limit is respected

- **WHEN** `max-concurrent-llm-calls` is set to 3
- **THEN** at most 3 flow analyses SHALL execute concurrently at any given time
- **AND** the 4th flow SHALL wait in the executor queue until a running analysis completes

#### Scenario: Executor absent falls back to sequential

- **WHEN** no analysis executor is provided to `AnalyzeFlowAction`
- **THEN** flow analysis SHALL proceed sequentially in a plain loop
- **AND** behavior SHALL be identical to the pre-parallelization implementation

### Requirement: Thread safety during concurrent analysis

Shared resources accessed during concurrent flow analysis SHALL be safe for multi-threaded access. Each flow's analysis context SHALL be self-contained, with no mutable shared state between concurrent analyses.

#### Scenario: Concurrent writes to execution finding store

- **WHEN** two flows complete their LLM analysis at approximately the same time
- **THEN** each flow's `FlowAnalysisResponse` SHALL be persisted to the `ExecutionFindingStore` using its unique deterministic flow key
- **AND** no write conflicts or data corruption SHALL occur

#### Scenario: Concurrent reads from codebase knowledge

- **WHEN** multiple flow analyses read enrichment data from the in-memory `CodebaseKnowledge`
- **THEN** all reads SHALL return consistent data without interference between concurrent analysis threads
