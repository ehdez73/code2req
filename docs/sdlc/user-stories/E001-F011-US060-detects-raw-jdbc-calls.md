# US060 — Developer detects raw JDBC calls in service code

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F011 — Database Access Detection
**Priority:** should | **Estimate:** 2 SP
**Depends on:** US006 | **Blocks:** —

> As a **Developer**, I want **the tool to detect raw JDBC calls like
  `Connection.prepareStatement` and `Statement.executeQuery`**, so that
  **legacy database access patterns are captured alongside Spring Data
  and JdbcTemplate usage**.

### Acceptance Criteria

- [ ] `Connection.prepareStatement` and `Connection.prepareCall` calls are detected
- [ ] `Statement.executeQuery`, `executeUpdate`, and `execute` calls are detected
- [ ] JdbcTemplate calls are excluded to avoid double-counting
- [ ] The SQL string and inferred table hint are extracted where possible
