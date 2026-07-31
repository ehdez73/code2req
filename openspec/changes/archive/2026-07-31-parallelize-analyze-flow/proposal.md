## Why

Phase 3 flow analysis runs each traced flow sequentially in a plain `for` loop, taking ~17 minutes for 18 flows on spring-petclinic (~56s per LLM call). Since each flow's analysis is fully independent — no shared mutable state — parallelizing this loop would drop analysis time to ~3.5 minutes with the existing 5-thread executor pool. The `enrichFlows` action already uses parallel `CompletableFuture`-based processing; this change applies the same pattern to `analyzeFlows`.

## What Changes

- `AnalyzeFlowAction` gains an optional `Executor` parameter; when present, flow analysis runs via `CompletableFuture.supplyAsync()` with the executor, falling back to sequential processing when absent
- `FunctionalRequirementAgent` injects the existing `orchestratorTaskExecutor` bean and passes it to `AnalyzeFlowAction`
- Flow analysis parallelism is governed by the same `code2req.enrichment.max-concurrent-llm-calls` configuration property, providing a single knob for all LLM concurrency

## Non-goals

- No change to how `enrichFlows` parallelism works (it already uses ad-hoc `FixedThreadPool` per flow)
- No change to the GOAP agent pipeline ordering (actions remain sequential per world state dependency)
- No change to per-flow analysis behavior or `FlowAnalysisResponse` output format
- No new configuration properties — reuses existing concurrency settings

## Capabilities

### New Capabilities

- `flow-analysis-parallelism`: Concurrent execution of per-flow LLM analysis within a single Phase 3 `analyzeFlows` action, governed by the existing `max-concurrent-llm-calls` thread pool

### Modified Capabilities

None. Existing flow analysis behavior, caching, and output format are unchanged.

## Impact

- **Code**: `AnalyzeFlowAction.java` (adds `Executor` field, parallelizes `analyze()` loop), `FunctionalRequirementAgent.java` (injects and passes executor)
- **Thread safety**: `ExecutionFindingStore` writes use unique per-flow keys (no contention). `CodebaseKnowledge` is read-only during Phase 3 analysis. `OperationContext` is read-only during analysis.
- **No breaking changes**: Single-threaded executor produces identical results to sequential path; fallback preserves backward compatibility
- **Performance**: Expected 5x speedup for `analyzeFlows` phase (17min → ~3.5min on spring-petclinic with 18 flows)
