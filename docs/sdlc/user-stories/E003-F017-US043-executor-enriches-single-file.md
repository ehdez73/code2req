# US043 — Executor enriches a single file with LLM
**Implementation Status:** implemented  

**Epic:** E003 — Semantic Enrichment
**Feature:** F017 — LLM Executor Framework
**Priority:** must | **Estimate:** 5 SP
**Depends on:** US041 (Planner), `@EnableAsync`, Spring AI | **Blocks:** US045

> As a **Developer**, I want **the executor to call an LLM with the file content and pre-resolved structural context**, so that **the file gets semantically enriched with business purpose, validation rules, and edge cases**.

### Acceptance Criteria

- [ ] Executor receives pre-resolved structural context (call graph edges, endpoints, database access, link registrations)
- [ ] Executor receives the raw source file content
- [ ] LLM prompt instructs the model NOT to resolve structural dependencies
- [ ] LLM prompt focuses on: business purpose, implicit validation rules, inferred SQL, stored procedure logic, edge cases
- [ ] Output is a valid `ExecutionFinding` JSON conforming to §4 schema
- [ ] Invalid output (schema violation) transitions task to FAILED with error classification
- [ ] Discovered dependencies are attached to the output and registered in SQLite
- [ ] On HTTP 429, exponential backoff is applied: 2s initial, 2.0 multiplier, 60s cap, 3 max retries
- [ ] After 3 retries, task transitions to FAILED
- [ ] Output is persisted to `execution_findings` table with type `SEMANTIC_ENRICHMENT`
- [ ] Task transitions to ENRICHED on valid completion

### INVEST Flags

- performance
- error-handling
