# US057 — Interactive mode for agent-user clarification

**Epic:** E004 — Agentic Functional Requirement Extraction
**Feature:** F028 — Interactive Mode
**Priority:** should | **Estimate:** 3 SP
**Depends on:** US051 (Embabel agent) | **Blocks:** —

> As a **Developer**, I want **the agent to ask me questions when it encounters ambiguity**, so that **I can provide business context the agent cannot infer from code analysis alone**.

### Acceptance Criteria

- [ ] `UserInteractionService` SPI is defined with methods: `ask(prompt, context)` → `String`, `confirm(message)` → `boolean`, `select(options, prompt)` → `String`
- [ ] `NoOpUserInteractionService` is the default implementation — returns `null` for `ask`, `true` for `confirm`, `null` for `select` (headless mode)
- [ ] `InteractiveUserInteractionService` uses stdin/stdout for terminal prompts
- [ ] Agent actions (`TraceFlow`, `AnalyzeFlow`, `CrossReferenceFlows`) call `UserInteractionService` when confidence drops below `ambiguity-confidence-threshold` (0.7)
- [ ] The `AmbiguityGap` record is created even when the user provides an answer — for audit trail
- [ ] Agent defers to user for ambiguous method names, unclear business rules, and external service purposes
- [ ] User answers are cached in a new `user_responses` SQLite table (session_id, question, answer, created_at)
- [ ] Interactive mode is opt-in via `run --interactive` flag — default is headless with `NoOpUserInteractionService`

### INVEST Flags

- usability
- observability
