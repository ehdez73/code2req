## 1. Shared SHA-256 Utility

- [ ] 1.1 Create `HashUtils` class in `common` package with `sha256Hex(String)` static method
- [ ] 1.2 Update `IndexingOrchestrator` to delegate to `HashUtils.sha256Hex` instead of private method
- [ ] 1.3 Update `ScanCommand` to delegate to `HashUtils.sha256Hex` instead of private method
- [ ] 1.4 Update `EnrichFlowAction` to delegate to `HashUtils.sha256Hex` instead of private method
- [ ] 1.5 Update `TaskIdHasher` to delegate to `HashUtils.sha256Hex` instead of private method
- [ ] 1.6 Run `mvn clean test` to verify no regressions

## 2. Short Flow ID on EntryPoint

- [ ] 2.1 Add `default String shortId()` method to `EntryPoint` sealed interface using `HashUtils.sha256Hex(id()).substring(0, 8)`
- [ ] 2.2 Update `GroupFlowsAction.shortId()` to delegate to `EntryPoint.shortId()` — remove the `hashCode()`-based private method
- [ ] 2.3 Add unit tests for `EntryPoint.shortId()` covering determinism and uniqueness
- [ ] 2.4 Run `mvn clean test` to verify no regressions

## 3. FlowSummary Model and Assembler

- [ ] 3.1 Create `FlowSummary` record in `extraction/domain/model`: `shortId`, `fullFlowId`, `type` (EntryPointType), `name`, `status` (PENDING/ANALYZED/QUARANTINED), `complexity`, `stepCount`, `unresolvedLinkCount`, `hasStaleLinks`
- [ ] 3.2 Create `FlowSummaryAssembler` component: takes `StructuralGraph` (for `getEntryPoints()` + `shortId()` construction) and deserialized `ExtractionCache`; merges with cache status taking precedence over SQLite `PENDING`
- [ ] 3.3 Handle empty state: return empty list when `StructuralGraph` has no entry points
- [ ] 3.4 Propagate `hasStaleLinks` from `ExtractionCache.flowsWithStaleLinks` to `FlowSummary.hasStaleLinks`
- [ ] 3.5 Add unit tests for `FlowSummaryAssembler` covering: SQLite-only, cache-only, merged, empty, stale links propagation
- [ ] 3.6 Run `mvn clean test`

## 4. Flow List Shell Command

- [ ] 4.1 Create `FlowCommand` as `@ShellComponent` with `@ShellMethod(key = "flow list")`
- [ ] 4.2 Implement table output: short ID, type, status, name
- [ ] 4.3 Implement `--status` filter (analyzed, quarantined, pending)
- [ ] 4.4 Implement `--type` filter (http, scheduled, kafka, rabbitmq, activemq, event)
- [ ] 4.5 Implement `--filter <text>` for case-insensitive partial match on short ID or name
- [ ] 4.6 Implement `--verbose` mode showing step count, complexity, unresolved link count, and `[stale links]` indicator when `hasStaleLinks` is true
- [ ] 4.7 Display summary line at bottom: "N flows: X analyzed, Y quarantined, Z pending"
- [ ] 4.8 Add shell command integration test verifying output format and filter behavior
- [ ] 4.9 Run `mvn clean test`

## 5. Cache Merge Logic

- [ ] 5.1 Add `mergeFlow(FunctionalFlow, List<AmbiguityGap>)` method to `ExtractionCache`: replaces flow in existing feature or appends new single-flow feature; updates quarantine gaps for the flowId; adds flowId to `flowsWithStaleLinks` Set when merge is without regroup
- [ ] 5.2 Add `flowsWithStaleLinks` field (`Set<String>`) to `ExtractionCache`; cleared by `clearStaleLinks()` called after regroup or full extract
- [ ] 5.2 Preserve all unrelated features, relationships, and orphaned methods during merge
- [ ] 5.3 Handle corrupted cache JSON: throw clear error message, do not overwrite
- [ ] 5.4 Handle case where cache file does not exist: create new cache with single-flow feature
- [ ] 5.5 Add unit tests for `ExtractionCache.mergeFlow` covering: existing flow update, new flow append, quarantine status change, corrupted file, missing file
- [ ] 5.6 Run `mvn clean test`

## 6. Per-Flow Extraction Pipeline

- [ ] 6.1 Add `execute(String shortId, boolean regroup, boolean dryRun, boolean resume)` overload to `ExtractionOrchestrator` scoped to a single flow
- [ ] 6.2 Implement two-tier short ID resolution: (1) match prefix against `StructuralGraph.getEntryPoints()` short IDs, (2) if no match, fall back to `ExtractionCache` FunctionalFlows converted to short IDs. If 1 match, proceed; if >1, throw with list of ambiguous matches; if 0 in both tiers, throw "not found"
- [ ] 6.3 Guard: if no scan data exists (no entry points in StructuralGraph), throw "No scan data available. Run 'scan' first."
- [ ] 6.4 Single-flow path (no GOAP agent for extract): call `TraceFlowAction.traceFlow(entryPoint)`, `QuarantineFlowAction`, `EnrichFlowAction`, `AnalyzeFlowAction` directly in sequence. In dry-run mode, stop after trace and display steps without LLM calls or cache writes.
- [ ] 6.5 After analysis: call `ExtractionCache.mergeFlow()` to persist the result
- [ ] 6.6 If flow is new (not in cache) OR `--regroup` is set: clear `flowsWithStaleLinks`, launch GOAP agent to execute `groupFlows` + `crossReferenceFlows` on the full merged cache
- [ ] 6.7 Add unit tests for per-flow execution covering: valid single match, ambiguous prefix via StructuralGraph, ambiguous prefix via cache fallback, not found, no scan data, dry-run (traces only, no LLM, no cache write), resume (reuses FLOW_ANALYSIS), resume (no cache found, falls back to LLM), new flow triggers regroup, existing flow skips regroup, `--regroup` override
- [ ] 6.8 Run `mvn clean test`

## 7. Extract Command Updates

- [ ] 7.1 Add `@ShellOption --flow` to `ExtractCommand.extract()` method, default `ShellOption.NULL`
- [ ] 7.2 Add `@ShellOption --regroup` to `ExtractCommand.extract()`, default `false`
- [ ] 7.3 Implement cache-protection check: when `--flow` is absent and `extraction-cache.json` exists and `--force` is absent, display warning with cached flow count and exit with error message
- [ ] 7.4 Route to `ExtractionOrchestrator.execute(flowId, regroup)` when `--flow` is provided, else to existing `execute(dryRun, force, resume)` (with cache-protection enforced)
- [ ] 7.5 Ensure flag composition: `--flow --force` forces LLM re-analysis for that flow (deletes its FLOW_ANALYSIS cache); `--flow --resume` reuses cached FLOW_ANALYSIS; `--flow --dry-run` traces only without LLM or cache writes; `--flow` without `--regroup` preserves stale links state
- [ ] 7.6 Add shell command integration tests for: full extract blocked by cache, full extract with `--force`, single-flow extract, single-flow extract with `--regroup`, single-flow dry-run, single-flow resume, ambiguous prefix, not found, no scan data
- [ ] 7.7 Run `mvn clean test`

## 8. Impact Validation

- [ ] 8.1 Verify `generate` command still works unchanged with merged cache (full pipeline integration test)
- [ ] 8.2 Verify `status` command still reads quarantine gaps correctly from merged cache
- [ ] 8.3 Full pipeline integration test: `scan → extract → flow list → extract --flow X → flow list --verbose (verify [stale links]) → generate → status`
- [ ] 8.4 Stale links lifecycle test: verify `[stale links]` appears after `--flow` without `--regroup`, disappears after `--flow --regroup`, and disappears after full `extract --force`
- [ ] 8.5 Run `mvn clean test` to confirm all existing and new tests pass
