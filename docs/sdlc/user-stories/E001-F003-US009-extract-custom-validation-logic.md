# US009 — Developer extracts custom validation logic

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F003 — Java Source AST Analysis
**Priority:** should | **Estimate:** 5 SP
**Depends on:** US006 | **Blocks:** US011

> As a **Developer**, I want **the tool to identify fields with custom validators and their `isValid` logic**, so that **business rules hidden in annotation processors are visible**.

### Acceptance Criteria

- [ ] Fields annotated with custom `@Constraint` validators are captured with the validator class
- [ ] The `isValid` method body from the validator class is extracted as a text slice
- [ ] Fields validated by built-in Jakarta annotations (`@NotNull`, `@Size`, `@Pattern`, etc.) are noted but don't require `isValid` extraction
- [ ] Each validation constraint is linked to the field and class it belongs to
