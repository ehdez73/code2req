## Why

The `extract` phase currently processes all entry points as a monolith — the user has no visibility into individual flows, no way to reprocess a single flow after code changes, and no way to incrementally analyze newly added endpoints. This forces a full re-extraction every time, wasting LLM tokens and losing the ability to iterate on specific flows.

## What Changes

- **New `flow list` shell command**: displays all detected flows (entry points from SQLite merged with analyzed flows from the extraction cache), showing short ID, type, status, and name. Supports `--status`, `--type`, `--verbose`, and `--filter` options.
- **New `--flow <short-id>` option on `extract`**: scopes the extraction pipeline to a single flow. Traces, enriches, and analyzes only the specified entry point, then merges the result into the existing extraction cache without overwriting other flows.
- **New `--regroup` flag on `extract --flow`**: forces re-grouping and cross-referencing of all flows after the individual flow is analyzed. Implicit when the flow is new (not already in cache).
- **Short flow IDs via SHA-256**: each `EntryPoint` exposes a deterministic 8-char hex identifier (`SHA-256(flowId)[:8]`) for compact user-facing reference. Replaces the ad-hoc `hashCode()` usage in `GroupFlowsAction`.
- **`extract` without `--flow` requires `--force` when cache exists**: prevents accidental overwrite of flows processed individually. Displays a warning listing how many cached flows would be lost.
- **`generate` command is unchanged**: always processes the complete extraction cache.
- **Centralized `sha256Hex` utility**: extracts duplicated SHA-256 hashing from 4 scattered locations into a shared utility class.

## Capabilities

### New Capabilities
- `flow-list`: display all detected and analyzed flows with filtering and verbose mode
- `per-flow-extraction`: process, re-analyze, and merge individual flows into the extraction cache

### Modified Capabilities
<!-- No existing spec-level capabilities are modified. -->

## Impact

- **New files**: `FlowCommand.java` (shell), `FlowSummary.java` (record), `FlowSummaryAssembler.java` (SQLite + cache merge logic), `HashUtils.java` (shared SHA-256 utility)
- **Modified files**: `ExtractCommand.java` (+`--flow`, `--regroup` options), `ExtractionOrchestrator.java` (+`execute` overload with flowId, cache merge logic), `FunctionalRequirementAgent.java` (optional: conditional skip of group/crossRef actions), `GroupFlowsAction.java` (use shared `HashUtils`), `EntryPoint.java` (+`shortId()` default method), `ExtractionCache.java` (+`mergeFlow` method), `EnrichFlowAction.java` (use shared `HashUtils`), `IndexingOrchestrator.java` (use shared `HashUtils`), `ScanCommand.java` (use shared `HashUtils`)
- **No API/dependency changes**: all changes are internal to the CLI and extraction domain
- **No breaking changes**: existing `extract`, `scan`, and `generate` commands retain their current signatures

## Non-goals

- Modifying `generate` to accept `--flow` flag
- Adding a `flow inspect` or `flow diff` command beyond listing
- Changing the persistence format of `extraction-cache.json`
- Adding content-based staleness detection in `flow list` (deferred)
