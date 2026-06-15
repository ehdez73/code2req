# US030 — Developer traces inter-file call chains

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F010 — Inter-File Call Graph Resolution
**Priority:** must | **Estimate:** 8 SP
**Depends on:** US006, US007 | **Blocks:** US013

> As a **Developer**, I want **the tool to resolve method calls across files within the same scan target**, so that **I can see the full call chain from controllers through services to repositories without needing an LLM**.

### Acceptance Criteria

- [ ] Method call expressions are resolved against a global declaration registry built in a first pass
- [ ] Resolved edges include source file, source method, target class, target method, target file
- [ ] Overloaded methods are detected and marked AMBIGUOUS when unresolvable by argument count
- [ ] JDK and Spring framework method calls are silently ignored
- [ ] Third-party or unresolved calls are recorded as unresolved_signatures
- [ ] The resolved call graph is persisted in the execution_findings table
- [ ] The call graph is included in the code-graph-index.json output
