# US048 — Developer mines test files for assertion insights

**Epic:** E003 — Semantic Enrichment
**Feature:** F020 — Test Suite Mining
**Priority:** should | **Estimate:** 3 SP
**Depends on:** US043 (Executor Framework) | **Blocks:** —

> As a **Developer**, I want **the tool to extract and translate test assertions into functional edge cases**, so that **validation rules obscured by technical debt in production code are captured from the test suite**.

### Acceptance Criteria

- [ ] Production and test files are paired by file name (strip `Test` suffix)
- [ ] Paired files are sent to the same executor for concurrent processing
- [ ] `assertEquals` calls are translated to validation rules
- [ ] `assertThrows` calls are translated to edge cases with exception types
- [ ] Extracted insights populate the `test_insights` array in `ExecutionFinding`
- [ ] A file without a matching test produces no test_insights (not an error)

### INVEST Flags

- edge-cases
