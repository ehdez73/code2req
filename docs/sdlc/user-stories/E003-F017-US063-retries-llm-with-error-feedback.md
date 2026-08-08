# US063 — System retries LLM enrichment with error-feedback
**Implementation Status:** implemented  

**Epic:** E003 — Phase 2 LLM Enrichment Orchestration
**Feature:** F017 — LLM Executor
**Priority:** should | **Estimate:** 3 SP
**Depends on:** US043 | **Blocks:** —

> As a **Developer**, I want **the LLM enrichment service to retry failed
  LLM calls with error-feedback**, so that **transient parse errors in
  the LLM response are automatically corrected without manual intervention**.

### Acceptance Criteria

- [ ] When the LLM returns invalid JSON, the error and failed output are fed back in a retry prompt
- [ ] Retries use exponential backoff (initial 2s, multiplier 2x, cap 60s)
- [ ] After max retries (3), the task transitions to FAILED
- [ ] Rate-limited responses (429) use longer backoff but do not include error-feedback
