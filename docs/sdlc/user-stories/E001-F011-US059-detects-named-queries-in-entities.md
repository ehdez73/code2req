# US059 — Developer detects @NamedQuery and @NamedNativeQuery in entities

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F011 — Database Access Detection
**Priority:** should | **Estimate:** 2 SP
**Depends on:** US006 | **Blocks:** —

> As a **Developer**, I want **the tool to detect `@NamedQuery` and
  `@NamedNativeQuery` declarations (including container forms)**, so that
  **JPQL/HQL and native SQL queries defined on entity classes are captured
  as database access points**.

### Acceptance Criteria

- [ ] `@NamedQuery` annotations are detected and recorded with type JPQL_HQL
- [ ] `@NamedNativeQuery` annotations are detected and recorded with type NATIVE_SQL
- [ ] Container forms (`@NamedQueries`, `@NamedNativeQueries`) are recursively unwrapped
- [ ] The query string and optional name are extracted from each annotation
