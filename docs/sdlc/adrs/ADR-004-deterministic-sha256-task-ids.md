# ADR-004: Deterministic SHA-256 Task IDs

**Status:** Accepted
**Date:** 2026-06-12
**Author:** Solo Developer

## Context

Each processing task in the scan pipeline needs a unique identifier for tracking state through the processing DAG. This ID is used to persist task status (PENDING → INDEXED → ENRICH_PENDING → ENRICHING → ENRICHED/FAILED), cache results for idempotent re-scans, and detect orphaned tasks during crash recovery.

Key requirements:
- Re-running against an unchanged workspace must consume zero tokens (reuse cached results)
- Task IDs must be stable across process restarts
- Must prevent redundant processing of the same file with the same configuration

## Decision

Compute task IDs as **deterministic SHA-256 hashes** composed from:
- File path (relative to workspace root)
- File content hash (SHA-256 of source file bytes)
- Test content hash (if applicable)
- Model ID (for Phase 2 LLM tasks)
- Prompt version (for Phase 2)

Example composite: `SHA-256(filePath + contentHash + modelId + promptVersion)`

This means the same file with the same configuration always produces the same task ID, enabling automatic cache hit detection without a separate indexing step.

## Consequences

### Positive
- Idempotent re-scans — unchanged files are automatically skipped; task store hit returns cached result
- No separate cache key management — the task ID is the cache key
- Deterministic across machines — same workspace + same config yields same IDs
- Orphan detection is straightforward — any task ID present in the DB but absent from the current scan can be classified as stale

### Negative
- Renaming or moving a file produces a new task ID (even if content is identical) — the old task becomes orphaned
- No sequential ordering — cannot infer processing order from IDs (requires a separate DAG)
- SHA-256 computation adds marginal CPU overhead per file

### Neutral
- GUID/UUID users find the non-random IDs unusual, but the determinism is deliberate for idempotency

## Alternatives Considered

- **UUID v4 (random)**: Rejected — non-deterministic; cannot detect cache hits without a separate content-addressed lookup
- **Auto-increment integer**: Rejected — not stable across runs; varies with processing order and parallelism
- **Path-only hash**: Rejected — does not detect content changes; would incorrectly cache stale results after file modification
- **Content-only hash**: Rejected — would miss the same content in different paths (multiple copies)

## Related
- NFR004 — Idempotent task identification (acceptance criteria: SHA-256 of file path, content hash, test content hash, model ID, and prompt version)
- US014 — Developer persists scan state locally
- US017 — Developer recovers from crash mid-scan
