## Why

When a file is modified and re-scanned, `IndexingOrchestrator.analyzeSingleFile()` computes a new content-dependent task ID (per ADR-004) but only deletes findings matching that new ID. Old findings — still keyed by the previous task ID — are never removed from SQLite. `buildStructuralGraph()` then loads ALL findings regardless of age, corrupting the call graph with edges from previous scan runs. This causes `extract --flow` to trace stale call chains that no longer exist in the current code.

## What Changes

- Add `deleteByFilePath(filePath)` to `ExecutionFindingStore` that removes all findings associated with a given file path (joining on the `tasks` table), regardless of task ID.
- Call `deleteByFilePath` in `IndexingOrchestrator.analyzeSingleFile()` before persisting new findings, ensuring the scan-to-SQLite path is idempotent — re-scanning a file always produces a clean state with only the latest findings.
- Add a corresponding integration test verifying that a re-scanned file's old findings are purged and only current findings remain.

## Capabilities

### New Capabilities
<!-- None — this is a data integrity bug fix, not a new behavior -->

### Modified Capabilities
<!-- None — no spec-level behavior changes. Re-scan already implies reflecting current code -->

## Impact

- **Modified files**: `ExecutionFindingStore.java` (+`deleteByFilePath` method), `IndexingOrchestrator.java` (+1 call before `deleteByTaskId`)
- **No API changes**, no dependency changes, no ADR changes
- **ADR-004 remains fully honored** — task IDs stay content-dependent; the fix only adds cleanup for stale IDs
- **No breaking changes** — existing scan, extract, and generate workflows are unaffected

## Non-goals

- Changing the task ID structure or `TaskIdHasher` contract
- Adding general-purpose SQLite garbage collection
- Detecting content-based staleness in Phase 2/3 (deferred)
- Deleting orphaned `task` rows (harmless metadata, left for a future cleanup pass)
