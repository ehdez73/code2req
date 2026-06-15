# US031 — Developer traces database access patterns

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F011 — Database Access Detection
**Priority:** should | **Estimate:** 5 SP
**Depends on:** US006 | **Blocks:** US013

> As a **Developer**, I want **the tool to detect all database access points in my service code**, so that **I can understand exactly which SQL queries, stored procedures, and transaction boundaries the application uses**.

### Acceptance Criteria

- [ ] JdbcTemplate query and update calls are detected with SQL strings extracted
- [ ] @Procedure annotations are captured with procedureName
- [ ] EntityManager persist, merge, find, and createQuery calls are detected
- [ ] @Transactional annotations are recorded as transaction boundaries
- [ ] Inline SQL strings are extracted from the AST
- [ ] Table names are inferred from SQL strings where possible
- [ ] Spring Data JPA repository interfaces are registered as virtual database access points
