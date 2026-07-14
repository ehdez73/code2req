# ADR-006: Pipeline Orchestration — Extract/Generate Command Split

**Status:** Accepted
**Date:** 2026-06-30
**Author:** Solo Developer

## Context

Phase 3 originally bundled all agentic analysis and output generation into a single `extract` Shell command. Internally, the Embabel GOAP agent ran seven sequential actions:

1. DiscoverEntryPoints
2. TraceFlow
3. AnalyzeFlow
4. AllFlowsAnalyzed
5. GroupFlows
6. CrossReferenceFlows
7. SynthesizeSpec (writes spec.md + semantic_manifest.json)

All intermediate results (CrossReferencedResult, orphaned methods, ambiguity gaps) were held in-memory within the agent process and discarded after completion.

This created two pain points:
- **Regenerating spec.md** after tweaking output formatting required re-running the entire Embabel agent (minutes of LLM calls)
- **No separation of concerns** — the agent was responsible for both analysis and presentation, making it harder to iterate on output formatting independently

## Decision

Split Phase 3 into two standalone Shell commands: `extract` and `generate`.

### `extract` — Agentic Analysis
Runs the Embabel GOAP agent through actions 1–6 (discover → trace → analyze → group → cross-reference). After the agent completes, persists the intermediate results to `spec-output/extraction-cache.json` — a single JSON snapshot containing:

- CrossReferencedResult (features + cross-flow relationships)
- OrphanedMethod list
- AmbiguityGap list (quarantined flows)

The agent no longer calls SynthesizeSpecAction — it writes only the cache.

### `generate` — Output Synthesis
Reads `spec-output/extraction-cache.json`, deserializes the three result sets, and calls `SynthesizeSpecAction.synthesize()` to produce:

- `spec-output/spec.md` — human-readable Markdown specification
- `spec-output/semantic_manifest.json` — machine-readable manifest (version 3.0.0)

This step is pure Java, makes zero agent/LLM calls, and is idempotent.

### Full Pipeline
The `run` command now orchestrates five phases:

```
scan → plan → enrich → extract → generate
```

### Cache Storage Rationale

Chose a JSON file over SQLite for the cache because:

- The cache is consumed as an atomic whole-snapshot — no querying individual entities
- No schema changes needed (no new finding types or tables)
- Human-readable for debugging
- Fast single-file read/write with the existing ObjectMapper

## Consequences

### Positive
- **Fast regeneration**: `generate` runs in milliseconds — no agent/LLM calls
- **Separation of concerns**: analysis logic lives in the agent actions; output formatting lives in SynthesizeSpecAction
- **Independent execution**: `generate` can be re-run any number of times; `extract` can be re-run without necessarily overwriting spec files
- **Observable intermediate state**: the cache file can be inspected or diffed between runs

### Negative
- **Cache invalidation**: if the codebase changes between `extract` and `generate`, the cache is stale — user must re-run `extract`
- **Extra disk artifact**: `extraction-cache.json` is a new output file that must be cleaned up
- **One extra step in `run`**: the pipeline adds a fifth command call

### Neutral
- The `generate` command reads but does not require the `--manifest` flag — it operates entirely from the cache

## Alternatives Considered

- **Single command with config flag (`extract --generate-only`)**: Rejected — two explicit commands better communicate the conceptual split and make tab-completion clearer
- **SQLite persistence for cache**: Rejected — whole-snapshot access pattern doesn't benefit from SQL; adds schema migration complexity
- **Keep agent producing spec.md + also write cache**: Rejected — doesn't solve the separation of concerns; agent still handles presentation

## Related

- F019 — CLI Commands — plan and run (run pipeline updated to include generate)
- F023 — Embabel Agent — Entry-Point-Driven Extraction (agent no longer produces spec files)
- F027 — Domain Model + Output Writers (SynthesizeSpecAction now invoked by generate command)
- US047 — Developer runs full Phase 2 + Phase 3 pipeline
- US056 — Domain model and output writers
