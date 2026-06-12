# Tech Stack — code2req

**Version:** 1.0.0
**Last Updated:** 2026-06-12
**Status:** Draft

## Languages

| Technology | Version | Purpose | ADR |
|---|---|---|---|
| Java | 21 | Primary application language | — |
| YAML | — | Project manifest configuration | — |

## Frameworks & Libraries

| Technology | Version | Purpose | ADR |
|---|---|---|---|
| Spring Boot | 3.4.x | Application framework | — |
| Spring JDBC (JdbcTemplate) | 3.4.x | Database access (no ORM) | ADR-002 |
| JavaParser | latest | Java source AST analysis | ADR-001 |

## Data & Persistence

| Technology | Version | Purpose | ADR |
|---|---|---|---|
| SQLite | latest | Embedded task store with WAL mode | ADR-003 |
| JSON | — | Intermediate index output format | — |

## Build & Tooling

| Technology | Version | Purpose | ADR |
|---|---|---|---|
| Maven | latest | Build and dependency management | — |

## Infrastructure

| Technology | Version | Purpose | ADR |
|---|---|---|---|
| None (CLI-only) | — | No external services; fully offline | — |

## Security

| Technology | Version | Purpose | ADR |
|---|---|---|---|
| In-memory redaction | — | Secret redaction before any outbound processing | ADR-005 |

## Architecture Decisions

| Decision | Mechanism | ADR |
|---|---|---|
| AST parsing via JavaParser (no native bindings) | JavaParser library | ADR-001 |
| Data access via Spring JDBC, no ORM | JdbcTemplate | ADR-002 |
| Embedded SQLite with WAL mode | SQLite via JDBC | ADR-003 |
| Idempotent task identification | SHA-256 composite hash | ADR-004 |
| In-memory secret redaction | Pattern-based placeholder replacement | ADR-005 |

## Related ADRs

| ADR | Title | Status |
|---|---|---|
| [ADR-001](adrs/ADR-001-javaparser-for-ast-analysis.md) | JavaParser for AST Analysis | Accepted |
| [ADR-002](adrs/ADR-002-spring-jdbc-over-orm.md) | Spring JDBC (JdbcTemplate) over ORM | Accepted |
| [ADR-003](adrs/ADR-003-sqlite-with-wal-mode.md) | SQLite with WAL Mode Embedded Database | Accepted |
| [ADR-004](adrs/ADR-004-deterministic-sha256-task-ids.md) | Deterministic SHA-256 Task IDs | Accepted |
| [ADR-005](adrs/ADR-005-in-memory-secret-redaction.md) | In-Memory Secret Redaction Strategy | Accepted |
