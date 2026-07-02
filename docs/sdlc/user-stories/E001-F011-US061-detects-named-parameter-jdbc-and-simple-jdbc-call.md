# US061 — Developer detects NamedParameterJdbcTemplate and SimpleJdbcCall

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F011 — Database Access Detection
**Priority:** should | **Estimate:** 2 SP
**Depends on:** US006 | **Blocks:** —

> As a **Developer**, I want **the tool to detect `NamedParameterJdbcTemplate`
  and `SimpleJdbcCall` usage**, so that **named parameter queries and stored
  procedure calls via `SimpleJdbcCall` are captured**.

### Acceptance Criteria

- [ ] `NamedParameterJdbcTemplate` queries (via `npjt` scope alias) are detected as `JDBC_TEMPLATE_QUERY`
- [ ] `SimpleJdbcCall.execute` calls are detected as database access entries
- [ ] The SQL string and table hint are extracted where present
