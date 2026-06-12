# US004 — Developer resolves project dependencies automatically

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F002 — Dependency Graph Resolution
**Priority:** should | **Estimate:** 5 SP
**Depends on:** US001 | **Blocks:** —

> As a **Developer**, I want **the tool to discover dependency types of my project's libraries**, so that **the AST analysis can better resolve type references from third-party code**.

### Acceptance Criteria

- [ ] When Maven is available and `pom.xml` is present, dependency metadata is resolved automatically
- [ ] Resolved dependencies include group, artifact, and version coordinates
- [ ] Dependency resolution adds less than 30 seconds to scan startup time
- [ ] Resolution failures for individual dependencies produce warnings but don't stop the pipeline
