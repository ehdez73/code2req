## Context

See proposal.md for motivation. The pipeline uses content-dependent task IDs (ADR-004), so modifying a file produces a new task ID. `IndexingOrchestrator.analyzeSingleFile()` calls `executionFindingStore.deleteByTaskId(taskId)` which only removes findings for the new ID — stale findings from the previous scan are never cleaned up. `buildStructuralGraph()` reads all findings globally, so it loads both old and new edges.

The fix is a single cleanup call before persistence, joining on the `tasks` table to find all findings (across all task IDs) for the current file.

Relevant code locations for context:

- `IndexingOrchestrator.analyzeSingleFile():276` — where deletion happens before insert
- `ExecutionFindingStore.deleteByTaskId():58-60` — current targeted deletion
- `ExecutionFindingStore.findAllByType():114-119` — global read that triggers the bug
- `TaskIdHasher.hash():10` — content-dependent ID construction

## Goals / Non-Goals

**Goals:**
- Ensure re-scanning a modified file produces a clean findings state in SQLite
- Keep the change minimal and targeted to the scan-to-persistence path
- Validate with an integration test covering the re-scan workflow

**Non-Goals:**
- Changing `TaskIdHasher` or the task ID contract (ADR-004)
- Cleaning up orphaned `task` rows (harmless metadata)
- Adding periodic SQLite vacuum or garbage collection
- Adding staleness detection in Phase 2/3

## Decisions

### Decision 1: Delete by file path via JOIN instead of sequential delele-all

**Choice:** Add `ExecutionFindingStore.deleteByFilePath(String filePath)` that executes:

```sql
DELETE FROM execution_findings WHERE task_id IN (
    SELECT task_id FROM tasks WHERE file_path = ?
)
```

Call this in `IndexingOrchestrator.analyzeSingleFile()` before the existing `deleteByTaskId(taskId)`.

**Alternatives considered:**
- **Delete all findings before every scan run** (`executionFindingStore.deleteAll()`) — rejected because it defeats the resume feature. The resume path (line 112-122 of `ScanCommand`) filters already-completed files and skips their analysis. Clearing all findings would discard cached enrichments from Phase 2 for files not being re-scanned.
- **Delete by file path in ScanCommand instead of IndexingOrchestrator** — rejected because `ScanCommand` delegates file analysis to the orchestrator. Adding pre-deletion to the command would couple persistence concerns upward when the orchestrator already owns the persist/delete flow.
- **Change task ID to be content-independent** — rejected (see exploration discussion). Would break Phase 2 enrichment skip logic by reusing IDs across different file versions.

**Rationale:** The JOIN-based delete is the surgical fix. It targets exactly the data that should be cleaned (findings for the file being re-analyzed) without affecting findings for other files or the resume path. Running it before `deleteByTaskId(taskId)` is a belt-and-suspenders approach — `deleteByTaskId` handles the edge case where the task ID happens to collide (same content hash), while `deleteByFilePath` handles the common case of content changes.

**Impact:** One new method (~5 lines) on `ExecutionFindingStore`, one new call (~1 line) in `IndexingOrchestrator`. No new imports, no changed signatures, no altered transaction boundaries.

### Decision 2: Integration test via SQLite test fixture

**Choice:** Add a test to `IndexingOrchestratorTest` that simulates two scan passes over the same source file (same path, different content) and asserts that findings from the first pass are not present after the second pass.

**Alternatives considered:**
- **Unit test on ExecutionFindingStore only** — rejected because the bug manifests only when the orchestrator's persistence flow runs with different task IDs. Testing just the store method would test SQL correctness but not the behavioral flow.
- **End-to-end test with full scan pipeline** — rejected as unnecessarily heavy. The integration test using the orchestrator directly with SQLite is faster and more targeted.

**Rationale:** The test captures the exact sequence that triggers the bug: analyze file with content A → assert findings A exist → analyze same file with content B → assert findings A no longer exist, only B.

## Risks / Trade-offs

- **[Risk] Performance impact of JOIN on large scans** → Mitigation: The `tasks` table is indexed on `task_id` and there's a composite index on `file_path`. The sub-select is O(log n) per file. For a 10k-file codebase, this adds negligible overhead compared to AST parsing.
- **[Risk] Transaction boundary: findings deleted but task insert fails** → Mitigation: Both `deleteByFilePath` and the subsequent `persistFindings` run inside the same transaction via `transactionTemplate.executeWithoutResult`. If the transaction rolls back, the delete is undone.
- **[Trade-off] Orphaned `task` rows accumulate over time** → Acceptable. A task row is ~200 bytes of metadata. Even after 100 scan runs, the overhead is negligible (<20KB). A future cleanup pass can add a `VACUUM`-style command, but it's not needed now.

## Open Questions

None.
