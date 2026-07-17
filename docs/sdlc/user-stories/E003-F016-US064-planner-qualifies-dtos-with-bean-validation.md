# US064 — Planner qualifies DTOs/records with bean validation annotations

**Epic:** E003 — Semantic Enrichment
**Feature:** F016 — Planner
**Priority:** should | **Estimate:** 2 SP
**Depends on:** Phase 1 complete (SQLite populated with VALIDATOR findings for records) | **Blocks:** US043

> As a **Developer**, I want **the planner to qualify files containing DTOs/records with built-in bean validation annotations (e.g., @NotBlank, @Email, @Size) when those files are used in a flow**, so that **the LLM enriches the file with constraint-aware functional requirements**.

### Acceptance Criteria

- [ ] A file with `VALIDATOR` findings from built-in bean validation annotations on a record/DTO qualifies if the same file also has at least one flow-relevant finding (`ENDPOINT`, `COMPONENT`, `DB_ACCESS`, `SCHEDULED_TASK`, `KAFKA_LISTENER`, `RABBITMQ_LISTENER`, `ACTIVEMQ_LISTENER`, `EVENT_LISTENER`)
- [ ] A file with `VALIDATOR` findings but no flow-relevant findings does NOT qualify
- [ ] The qualification reason is `BEAN_VALIDATION`
- [ ] A file can qualify from multiple rules simultaneously (e.g., `BEAN_VALIDATION` + `CUSTOM_CONSTRAINT_VALIDATOR`)
- [ ] The rule makes zero LLM calls — pure query against `execution_findings` table

### INVEST Flags

- testable
