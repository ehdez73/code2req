# US036 — Developer upgrades pipeline to two-pass orchestration

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F010 — Two-Pass Pipeline Orchestration & Call Graph Resolution
**Priority:** must | **Estimate:** 5 SP
**Depends on:** US001, US006 | **Blocks:** US013, US030

> As a **Developer**, I want **the scan pipeline to use a two-pass orchestration:
  Pass 1 collects declarations into a global registry, Pass 2 resolves against
  that registry**, so that **cross-file method resolution is accurate without
  forward-referencing issues**.

### Acceptance Criteria

- [ ] Pass 1 visits all files and collects declarations into `GlobalDeclarationRegistry` without performing resolution
- [ ] Pass 2 runs the full visitor suite against the populated registry
- [ ] Post-pass resolvers (`TopicLinkResolver`, `FloatingLinkResolver`) run after all files are analyzed
- [ ] A parse error in one file does not block other files from being collected
- [ ] An empty scan target produces an empty registry and valid empty output
