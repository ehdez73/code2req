# US008 — Developer traces event listeners

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F003 — Java Source AST Analysis
**Priority:** should | **Estimate:** 5 SP
**Depends on:** US006 | **Blocks:** US011

> As a **Developer**, I want **the tool to capture event listeners and their method call chains**, so that **I can understand how events propagate through my system**.

### Acceptance Criteria

- [ ] Spring event listeners (`@EventListener`, `ApplicationListener`) are identified with their event type
- [ ] Method calls within event handlers are captured as call chains (depth up to 3 levels)
- [ ] Method calls are recorded with target type and method name
- [ ] Event publishers (`ApplicationEventPublisher`) are linked to the events they publish
