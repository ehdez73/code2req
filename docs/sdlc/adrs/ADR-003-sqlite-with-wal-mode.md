# ADR-003: SQLite with WAL Mode Embedded Database

**Status:** Accepted
**Date:** 2026-06-12
**Author:** Solo Developer

## Context

The tool needs local persistence for the scan task store — tracking processing states (PENDING, ENRICHING, INDEXED, ENRICHED, FAILED), caching results for idempotent re-scans, and supporting crash recovery. The database must be embedded (no external service), support concurrent read/write from a configurable thread pool, and survive process crashes without corruption.

Key drivers:
- Zero external infrastructure — fully offline, no database server
- Concurrent access planned for Phase 2 (pool-size of 10)
- Crash resilience — must handle abrupt process termination

## Decision

Use **SQLite** as the embedded persistence engine, configured with **WAL (Write-Ahead Logging) journal mode** and a **5000ms busy timeout**.

SQLite's WAL mode allows concurrent reads while a write is in progress, which is essential for the multi-threaded processing planned in Phase 2. The busy timeout prevents immediate failure when a lock is temporarily held.

Connection initialization configuration:
```
PRAGMA journal_mode=WAL
PRAGMA busy_timeout=5000
```

## Consequences

### Positive
- Zero infrastructure — embedded, no database server to install or manage
- WAL mode enables concurrent reads + single writer — sufficient for pool-size of 10
- Crash-safe — WAL mode is crash-resistant; committed transactions survive process kill
- SQLite database is a single file — easy to inspect, back up, or delete
- Well-tested in production across thousands of embedded-application deployments

### Negative
- Concurrent writes are serialized (single writer) — may become a bottleneck if Phase 2 parallelism increases significantly
- No network access — cannot be shared across processes or machines
- WAL mode creates a companion -wal file that must be cleaned up via checkpoint

### Neutral
- File locking can still occur under extreme concurrency; WAL + busy_timeout mitigates but does not eliminate this risk

## Alternatives Considered

- **H2 Database**: Rejected — embedded but an external dependency; more complex than SQLite for a simple task store
- **Flat JSON file**: Rejected — no transactional guarantees; crash-unsafe; poor concurrent access
- **No persistence (in-memory only)**: Rejected — crash recovery and resume capability are explicit requirements (US017)

## Related
- ADR-002 — Spring JDBC (JdbcTemplate) over ORM (database access layer)
- US014 — Developer persists scan state locally
- US017 — Developer recovers from crash mid-scan
