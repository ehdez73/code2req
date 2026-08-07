## Context

See proposal.md for motivation and specs for behavioral contracts. This document covers technical decisions.

The extraction pipeline currently uses Embabel GOAP agent (`FunctionalRequirementAgent`) with 7 sequential actions. The agent always processes all entry points discovered from `StructuralGraph`. There is no mechanism to scope extraction to a subset of flows or to merge individual results into an existing cache.

Short flow IDs exist incidentally as `Integer.toHexString(hashCode())` inside `GroupFlowsAction` for LLM prompt formatting, but are not exposed to users or to other components.

The `sha256Hex` method is duplicated across `IndexingOrchestrator`, `ScanCommand`, `EnrichFlowAction`, and `TaskIdHasher`.

## Goals / Non-Goals

**Goals:**
- Give users visibility into all flows via a single `flow list` command
- Allow scoped re-extraction of individual flows without touching others
- Protect the cache from accidental full overwrite
- Centralize SHA-256 hashing into a shared utility
- Use SHA-256-based short IDs consistently across user-facing and internal code

**Non-Goals:**
- Content-based staleness detection in `flow list` (deferred)
- Per-flow `generate` (stays full-cache)
- Changing the extraction cache JSON schema
- A flow inspection/diff command

## Decisions

### Decision 1: Short ID via SHA-256[:8] on EntryPoint sealed interface

**Choice:** Add `default String shortId()` to `EntryPoint` using `SHA-256(flowId).substring(0, 8)`.

**Alternatives considered:**
- `Integer.toHexString(hashCode())` — already used in `GroupFlowsAction`, but produces variable-length output (1-8 chars), has higher collision probability, and is inconsistent with the project's SHA-256 convention for task IDs.
- UUID-based — would require storing a mapping; non-deterministic.

**Rationale:** SHA-256 is already the project's hashing standard (task IDs, content hashing). 8 hex chars provide ~4 billion values — collision probability negligible for expected flow counts (<10^4). Deterministic property means same flowId always yields same short ID.

**Impact:** `GroupFlowsAction.shortId()` must be updated to delegate to `EntryPoint.shortId()` for consistency between user-facing display and LLM prompts.

### Decision 2: Bypass GOAP agent for single-flow extraction

**Choice:** Single-flow extraction (`extract --flow`) calls `TraceFlowAction`, `QuarantineFlowAction`, `EnrichFlowAction`, and `AnalyzeFlowAction` directly from `ExtractionOrchestrator`, without launching the GOAP agent. The GOAP agent is only invoked for group/cross-reference after merge, and only when needed (new flow or `--regroup`).

**Alternatives considered:**
- Modify the GOAP agent to conditionally skip `groupFlows` and `crossReferenceFlows` — would require changes to the agent's action selection logic and `WorldState`. More complex than direct invocation.
- Always run the full agent but with a filtered entry point list — the agent would still attempt grouping with a single flow, producing a misleading or incomplete result.

**Rationale:** The GOAP agent's value is in sequencing interdependent actions. For a single flow, there is no inter-flow dependency — the pipeline degenerates to a linear sequence of trace → quarantine → enrich → analyze. Direct invocation is simpler, more testable, and doesn't couple flow-scoping logic to the agent.

### Decision 3: Cache merge instead of full cache replacement

**Choice:** When `--flow` processes a single flow, read the existing `extraction-cache.json`, update the matching flow in-place (or append if new), and write back. This is handled by a new `mergeFlow` method on `ExtractionCache` (or a dedicated `CacheMergeService`).

**Alternatives considered:**
- Always rewrite the full cache — would lose individually processed flows from previous sessions.
- Keep per-flow cache files — would require a directory of JSON files, complicating the `generate` phase and `flow list`.

**Rationale:** Since `generate` always processes the full cache, a single merged file is the simplest contract. The merge only touches the targeted flow; all other features, relationships, orphans, and gaps are preserved.

### Decision 4: Full extract requires --force when cache exists

**Choice:** `extract` (without `--flow`) checks for `extraction-cache.json` existence. If present, it displays a warning with the count of cached flows and refuses to proceed unless `--force` is provided.

**Rationale:** Without this guard, a user who has carefully processed 5 flows individually would lose all of them by accidentally running a bare `extract`. This is the default-safe pattern: destructive operations require explicit intent.

### Decision 5: FlowSummaryAssembler unifies two data sources

**Choice:** A new `FlowSummaryAssembler` component takes a `StructuralGraph` (for entry point data and `flowId` construction) and deserialized `ExtractionCache` (for analyzed flow data). Cache flows take precedence (their status overrides `PENDING`).

**Why `StructuralGraph` is needed:** The `ExecutionFindingStore` stores raw `EndpointInfo`, `KafkaInfo`, etc. — objects that do not carry a `flowId`. The `flowId` is constructed by `StructuralGraph.getEntryPoints()` using the pattern `filePath:className:methodName + discriminator`. The assembler delegates to `StructuralGraph.getEntryPoints()` rather than duplicating the ID construction logic.

```
┌─────────────────────┐     ┌──────────────────────┐
│ StructuralGraph      │     │ ExtractionCache       │
│ (built from SQLite)  │     │ (JSON file)           │
│                      │     │                       │
│ getEntryPoints() ────┼────▶│ FlowSummary           │
│   → EntryPoint[]     │     │ Assembler             │
│   → EntryPoint       │     │                       │
│     .shortId()       │     │ StructuralGraph →     │
│                      │     │   PENDING             │
└─────────────────────┘     │ Cache  → ANALYZED/     │
                             │          QUARANTINED  │
                             └──────────┬───────────┘
                                        │
                                        ▼
                                 FlowSummary[]
                                        │
                                        ▼
                                 FlowCommand
                                 (formats table)
```

**Rationale:** `flow list` must work both before and after extraction. Pre-extraction shows candidacy; post-extraction shows completion. Reusing `StructuralGraph.getEntryPoints()` avoids duplicating flowId construction logic.

### Decision 6: HashUtils as shared utility

**Choice:** Extract `sha256Hex` into a new `HashUtils` class in the `common` package. Update all existing call sites to use it.

**Existing call sites to update:**
- `IndexingOrchestrator.sha256Hex()`
- `ScanCommand.sha256Hex()`
- `EnrichFlowAction.sha256Hex()`
- `TaskIdHasher` (already a centralized class; either make `HashUtils` the sole utility or have `TaskIdHasher` delegate to it)

**Rationale:** The same method is copy-pasted in 4 files. Centralizing prevents divergence and makes the `EntryPoint.shortId()` implementation natural.

### Decision 7: Short ID resolution with cache fallback

**Choice:** When resolving `--flow <short-id>`, the system searches in two tiers:

1. **Entry points from `StructuralGraph`** (SQLite). Match by `EntryPoint.shortId()` prefix.
2. **FunctionalFlows from `ExtractionCache`** (JSON). Match by `FunctionalFlow.flowId` converted to short ID.

If the short ID appears in both, the StructuralGraph match wins (the source of truth for tracing). If it appears only in the cache, it means the entry point data was purged or `scan` has not re-run — a warning is displayed but the flow can still be re-analyzed using cached analysis data (LLM results only, no re-tracing).

**Rationale:** A flow can exist in the cache without a corresponding entry point in SQLite if the cache was restored or the codebase changed. The two-tier resolution ensures the user can still reference cached flows even after SQLite state changes.

### Decision 8: Stale cross-references indicator in flow list

**Choice:** After `extract --flow` without `--regroup`, mark the flow as having potentially stale cross-references. The `flow list --verbose` output SHALL display `[stale links]` for flows modified by `--flow` since the last `--regroup` or full `extract`.

**Implementation:** `ExtractionCache` gains a `Set<String> flowsWithStaleLinks` field. It is populated on single-flow merge without regroup, and cleared after any regroup or full extract. The `FlowSummaryAssembler` reads this field and propagates it to `FlowSummary`.

**Rationale:** Without an indicator, users have no way to know their `spec.md` might contain stale cross-references. A simple label warns them to run `extract --regroup` (or full `extract --force`) before generating specs.

## Risks / Trade-offs

- **[Risk] SHA-256[:8] prefix collision with very large codebases** → Mitigation: 8 hex chars = 32 bits of entropy. For 1,000 flows, collision probability is ~0.01%. The ambiguous-prefix rejection mechanism (showing all matches) handles the edge case if it occurs.
- **[Risk] Cache merge on corrupted JSON** → Mitigation: If `extraction-cache.json` cannot be deserialized, display an error and refuse to merge. The user can delete it and re-extract with `--force`.
- **[Risk] `FlowSummaryAssembler` depends on `StructuralGraph` for flowId construction** → If `StructuralGraph.getEntryPoints()` changes its ID format, `FlowSummaryAssembler` must match. Mitigation: The assembler reuses `getEntryPoints()` directly rather than re-implementing ID construction, so format changes propagate automatically.
- **[Trade-off] Stale cross-references after re-analysis without regroup** → When existing flows are re-analyzed without `--regroup`, their updated content is in the cache but cross-reference relationships and feature grouping may be stale. Mitigation: `flow list --verbose` shows `[stale links]` indicator. Running `extract --regroup` or full `extract --force` clears it.
- **[Trade-off] `generate` reflects stale grouping until regroup** → The generated `spec.md` will reflect updated flow content within potentially stale feature groups. This is acceptable because the user explicitly chose not to regroup.
- **[Trade-off] Bypassing GOAP agent for single flows** → Direct invocation is simpler but creates a second code path for the extraction pipeline. If the agent's action logic changes (e.g., new step added between analyze and persist), single-flow extraction must be manually updated. Mitigation: single-flow extraction reuses the same action classes (`TraceFlowAction`, `AnalyzeFlowAction`, etc.) — only the orchestration differs.

## Open Questions

None — all design decisions have been resolved during exploration.
