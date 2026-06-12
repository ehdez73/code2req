# ADR-002: Spring JDBC (JdbcTemplate) over ORM

**Status:** Accepted
**Date:** 2026-06-12
**Author:** Solo Developer

## Context

The tool requires database persistence for the SQLite task store that tracks scan state, task statuses, and results. The team needed to choose between a full ORM (JPA/Hibernate) and direct JDBC access via Spring's JdbcTemplate.

Key drivers:
- SQLite as the target database — no ORM has first-class SQLite support comparable to direct JDBC
- Simple schema (task store, not a complex domain model) — ORM overhead is not justified
- Need explicit control over SQLite-specific features (WAL mode, busy timeout, PRAGMAs)

## Decision

Use **Spring JDBC (JdbcTemplate)** for all database access. No ORM (no Hibernate, no JPA).

JdbcTemplate provides a thin wrapper over raw JDBC that eliminates boilerplate (connection management, exception translation) without introducing the abstraction overhead of an ORM. It gives full control over SQL statements, which is important for SQLite-specific PRAGMA configuration and deterministic task ID queries.

## Consequences

### Positive
- Full control over SQL — can directly set WAL mode, busy timeout, and other SQLite PRAGMAs
- No ORM configuration complexity — no entity mappings, caching, or lazy-loading surprises
- Minimal dependencies — only spring-jdbc and the SQLite JDBC driver
- Predictable performance — no hidden queries or N+1 problems
- SQL schema is explicit and version-controllable

### Negative
- Manual result set mapping — no automatic entity hydration
- More verbose for complex queries with JOINs (not a concern for the simple task store schema)
- No built-in schema migration tooling (Flyway/Liquibase can still be added if needed)

### Neutral
- Development speed is comparable for simple CRUD operations

## Alternatives Considered

- **Hibernate/JPA**: Rejected — overkill for a simple task store; adds complexity for SQLite-specific configuration; hidden query generation conflicts with explicit control needs
- **Spring Data JDBC**: Considered but rejected — sits between JdbcTemplate and JPA; adds abstraction without meaningful benefit for the simple schema; less direct control over SQLite PRAGMAs
- **Raw JDBC**: Rejected — too much boilerplate; Spring's JdbcTemplate provides the right balance of simplicity and control

## Related
- ADR-003 — SQLite with WAL Mode Embedded Database
