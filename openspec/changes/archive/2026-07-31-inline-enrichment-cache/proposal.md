## Why

During a single `extract` run, the enrichment phase processes files per-flow sequentially. When a source file (e.g., a shared `OwnerRepository` or `UserService`) appears in multiple execution flows, it is sent to the LLM for semantic enrichment once per flow — producing duplicate LLM calls for the same file. The in-memory knowledge cache is only rebuilt after **all** flows complete, so it cannot prevent cross-flow duplication. This wastes API credits and wall-clock time proportional to the overlap between flows.

## What Changes

- Add an in-memory enrichment cache (`Map<filePath, CompletableFuture>`) to `EnrichFlowAction` that lives for the duration of one `enrich()` call
- Before enriching a file, check the cache: if a future already exists (from a prior flow), reuse it instead of submitting a new LLM call
- Clear the cache after `enrich()` completes

## Capabilities

### New Capabilities

- `enrichment-deduplication`: During a single extract run, each source file is enriched via LLM at most once, regardless of how many execution flows reference it.

### Modified Capabilities

None. This is a pure performance optimization within the enrichment pipeline; no spec-level behavior changes.

## Impact

- **`EnrichFlowAction.java`**: add cache field, modify file-submission loop in `enrichOneFlow()` to check cache via `computeIfAbsent`, clear cache at end of `enrich()`
- **No changes** to `LlmEnrichmentService`, `FunctionalRequirementAgent`, `EnrichedFlowResult`, or any other class
- New test case verifying same file in two flows only triggers one enrichment call
