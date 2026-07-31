## Context

Phase 3 `analyzeFlows` (in `AnalyzeFlowAction.analyze()`, line 71) processes traced flows one-by-one in a plain `for` loop. Each `analyzeFlow()` call is independent: it reads from the shared but immutable `CodebaseKnowledge`, performs an LLM call via Embabel's `context.ai().createObject()`, and persists the result to SQLite with a unique per-flow key. The `enrichFlows` action already uses a `CompletableFuture`-based pattern with an ad-hoc `FixedThreadPool`; this design applies the same concurrency model to analysis.

The existing `orchestratorTaskExecutor` bean (size governed by `max-concurrent-llm-calls=5`) is available but currently unused by `AnalyzeFlowAction`.

## Goals / Non-Goals

**Goals:**
- Parallelize flow analysis within a single `analyzeFlows` action invocation
- Reuse the existing `orchestratorTaskExecutor` thread pool
- Maintain identical per-flow analysis output (same prompt, same `FlowAnalysisResponse`)
- Graceful degradation: single flow failure does not abort the batch

**Non-Goals:**
- No change to `enrichFlows` parallelism architecture
- No change to GOAP world state transitions or action ordering
- No new configuration keys

## Decisions

### Decision 1: Use `orchestratorTaskExecutor` instead of creating ad-hoc pools

**Rationale:** `EnrichFlowAction` creates a fresh `FixedThreadPool` per flow, which means pools are created and destroyed rapidly (one per flow). For `AnalyzeFlowAction`, we need a pool that spans all flows in the batch. The existing `orchestratorTaskExecutor` bean already has the correct sizing (`max-concurrent-llm-calls`), proper lifecycle (Spring-managed shutdown), and thread naming (`c2r-orchestrator-`). Using it avoids pool creation overhead and gives consistent concurrency control.

**Alternatives considered:**
- *Ad-hoc pool per analyze() call*: Simpler but duplicates pool lifecycle management and doesn't benefit from Spring's shutdown hooks.
- *New dedicated `analysisTaskExecutor` bean*: Over-engineered for a single consumer; same concurrency config applies.
- *Embabel-native parallelism*: Would require GOAP action changes that go beyond the agent's sequential world-state model.

### Decision 2: `CompletableFuture.supplyAsync()` with `join()` on individual futures

**Rationale:** Results are collected by iterating the futures in order with `.join()`, which naturally handles exceptions (the first failing future's exception propagates). Each flow's future is independently joinable, so a slow flow doesn't block progress tracking.

**Alternatives considered:**
- *`CompletableFuture.allOf().thenApply()`*: Requires separate result collection and complicated exception handling.
- *`ExecutorService.invokeAll()`*: Blocks until all complete, no incremental progress visibility.

### Decision 3: Optional `Executor` constructor parameter with sequential fallback

**Rationale:** When `analysisExecutor` is null, the code falls back to the original sequential `for` loop. This preserves backward compatibility for tests and callers that construct `AnalyzeFlowAction` manually without an executor reference.

### Decision 4: Single-flow failure logs warning but continues

**Rationale:** Following the existing contract that "single-file failures never block the full scan", a failed flow analysis should not block the batch. The `CompletableFuture` is wrapped in a try-catch that logs the error and returns null, which is filtered out of the result list.

**Alternatives considered:**
- *Fail the entire batch*: Too aggressive; one flaky API call shouldn't discard 17 successful analyses.
- *Retry failed flows*: Adds complexity without clear benefit given `resume` mode already provides re-run capability.

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| `OperationContext` from Embabel may not be thread-safe for concurrent `createObject()` calls | Each `createObject()` creates an independent HTTP call through Spring AI's `OpenAiChatModel`, which is thread-safe. The `OperationContext` is only read (never mutated) during analysis. Embabel's own tool loop already runs on a separate thread (`embabel-platform-1`) from the response handler (`embabel-platform-0`). |
| SQLite concurrent writes may exceed HikariCP pool (max=10) | Maximum concurrent writes = `max-concurrent-llm-calls` (default 5), well within the 10-connection pool. WAL mode supports concurrent readers + single writer without blocking. |
| Non-deterministic result ordering | `GroupFlowsAction` groups by LLM semantics, not insertion order, so result order is irrelevant. Downstream consumers (`GroupedFlowsResult`) do not depend on ordering. |
| Executor saturation if `analyzeFlows` and `enrichFlows` share the pool | `analyzeFlows` runs strictly after `enrichFlows` completes (GOAP world state enforces `enrichFlows → analyzeFlows` ordering), so there is no contention. |

## Migration Plan

1. Deploy with existing `max-concurrent-llm-calls` setting (no config change needed)
2. Verify identical output on a known codebase (spring-petclinic) by comparing extraction caches before/after
3. Rollback: remove the executor parameter from `AnalyzeFlowAction` constructor call in `FunctionalRequirementAgent` — behavior reverts to sequential

## Open Questions

None.
