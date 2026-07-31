## Context

`EnrichFlowAction.enrich()` processes flows sequentially. For each flow, `enrichOneFlow()` collects all files reachable from flow steps plus linked files (AOP advice targets, validator targets), filters them through `shouldEnrich()` and the SQLite-backed knowledge cache, then submits each remaining file for LLM enrichment via its own per-flow `Executors.newFixedThreadPool()`.

The knowledge cache (`CodebaseKnowledge.semanticEnrichment()`) is loaded from SQLite once at the start of Phase 3 and is immutable. Its `findByFilePath()` check at line 122 of `EnrichFlowAction` catches files enriched in prior runs, but NOT files enriched by an earlier flow in the current run — because the cache is only refreshed after ALL flows complete (via `rebuildKnowledge()` at line 137 of `FunctionalRequirementAgent`).

See `proposal.md` for motivation.

## Goals / Non-Goals

**Goals:**
- Eliminate duplicate LLM enrichment calls for files shared across multiple flows within a single `enrich()` call
- Keep the change contained to `EnrichFlowAction` — no modifications to `LlmEnrichmentService`, `FunctionalRequirementAgent`, `EnrichedFlowResult`, or any other class
- Maintain the existing threading model and per-flow thread pool lifecycle

**Non-Goals:**
- Changing the per-flow thread pool to a shared pool (separate change)
- Batching all flows' files into a single enrichment pass (separate change)
- Modifying `LlmEnrichmentService` retry logic or call structure
- Updating the in-memory `SemanticEnrichment` object mid-run (the cache only prevents duplicate submissions; it does not make enrichment results available to `AnalyzeFlowAction` sooner)

## Decisions

### Decision 1: Use `ConcurrentHashMap.computeIfAbsent` with `CompletableFuture<Void>`

**Rationale:** The cache key is `filePath` (String). The value is a `CompletableFuture<Void>` representing the enrichment work. `computeIfAbsent` is atomic — if two calls race on the same file path, only one creates and submits the future; the other retrieves the existing one.

**Alternatives considered:**
- **Synchronized block with plain HashMap**: Error-prone; easier to introduce deadlocks.
- **Compute the key as `filePath + contentHash`**: Adds ~1ms of SHA-256 hashing and requires reading the file before the cache lookup, defeating the purpose. The file path alone uniquely identifies the file during a single run.
- **Cache `ExecutionFinding` directly instead of `CompletableFuture`**: Would require completing the enrichment synchronously (defeating parallelism) or using a callback to populate the map after completion (more complex, no benefit since callers don't need the value).

### Decision 2: Cache failures

**Rationale:** If `enrichFile()` throws, the exception is caught inside the `runAsync` lambda and logged; the `CompletableFuture` completes normally (no exceptional completion). Subsequent flows that encounter the same file path retrieve this completed future and skip enrichment. Since `LlmEnrichmentService.callLlm()` already retries 3 times internally, a failure at the `enrichFile()` level means the file is genuinely unenrichable for this run.

**Alternatives considered:**
- **Remove failed entries from cache** to allow retry in subsequent flows: Would re-attempt enrichment for a file that already failed 3 times at the LLM level. No benefit — the file will fail again.

### Decision 3: Cache lifecycle tied to `EnrichFlowAction` instance

**Rationale:** `EnrichFlowAction` is created fresh per `enrichFlows()` call (line 132-134 of `FunctionalRequirementAgent`). The cache is an instance field initialized in the field declaration and cleared at the end of `enrich()`. This naturally scopes the cache to one enrichment session without additional lifecycle management.

### Decision 4: Keep the knowledge cache check before the inline cache check

**Rationale:** The existing check at line 122 (`knowledge.semanticEnrichment().findByFilePath()` ) runs BEFORE files are added to `toEnrich`. The inline cache check runs AFTER, inside the submission loop. This ordering is deliberate: knowledge cache hits are cheaper (no `computeIfAbsent` overhead) and represent the long-term persistence layer. The inline cache only catches intra-run duplication.

## Risks / Trade-offs

- **[Risk] A file enriched in flow 1 that fails at the LLM level (after 3 retries) will be skipped by flow 2 without a retry.** → **Mitigation:** The LLM call already exhausts its internal retries. If all 3 fail, the file is persistently unenrichable. A second attempt would also fail.

- **[Risk] `ConcurrentHashMap` adds slight memory overhead per enrichment call.** → **Mitigation:** For typical runs (< 1000 files), the cache footprint is negligible (< 100 KB). The map is cleared after `enrich()`.

- **[Trade-off] This does not make enrichment results available to `AnalyzeFlowAction` sooner.** Even with the cache, `AnalyzeFlowAction` still sees only the pre-loaded knowledge from the start of Phase 3. → **Accepted:** `AnalyzeFlowAction` runs after `enrichFlows()` completes and `rebuildKnowledge()` refreshes from SQLite. No change in behavior.
