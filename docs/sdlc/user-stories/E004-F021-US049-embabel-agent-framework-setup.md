# US049 — Developer configures Embabel agent framework

**Epic:** E004 — Agentic Functional Requirement Extraction
**Feature:** F021 — Embabel Agent Framework Setup
**Priority:** must | **Estimate:** 1 SP
**Depends on:** Phase 2 completion, Embabel repo availability | **Blocks:** US050, US051

> As a **Developer**, I want **the Embabel framework to be configured and available on the classpath**, so that **Phase 3 can use GOAP planning for functional requirement extraction**.

### Acceptance Criteria

- [x] `embabel-agent-starter` dependency is uncommented in pom.xml
- [x] `mvn compile` succeeds with Embabel on classpath
- [ ] Embabel initializes at application startup without errors
- [ ] The `AgentPlatform` is available for Phase 3 invocation
- [ ] If the Embabel repo is unreachable, the build fails with a clear Maven error

### INVEST Flags

- infrastructure
