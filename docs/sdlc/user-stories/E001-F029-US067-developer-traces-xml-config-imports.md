# US067 — Developer traces Spring XML configuration across imports

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F029 — Spring XML Configuration Analysis
**Priority:** should | **Estimate:** 3 SP
**Depends on:** US065 | **Blocks:** US013

> As a **Developer**, I want **the tool to follow `<import>` references in Spring XML files and `@ImportResource` annotations on Java classes**, so that **the complete transitive set of XML bean declarations is discovered regardless of how configuration files are organized**.

### Acceptance Criteria

- [ ] `<import resource="...">` references are resolved and the imported files are analyzed recursively
- [ ] Circular import cycles are detected and halted via a visited-set tracker
- [ ] `classpath:` and `classpath*:` prefixed resource paths are resolved against source root and standard resource directories
- [ ] `file:` prefixed absolute paths are resolved against the filesystem
- [ ] Glob patterns (`*`, `?`, `[chars]`) in import paths are expanded to matching files
- [ ] `@ImportResource` annotations on Java classes trigger XML analysis of the referenced configuration files
- [ ] Both single-value (`@ImportResource("file.xml")`) and array-value (`@ImportResource({"a.xml","b.xml"})`) forms are supported
- [ ] Findings from imported XML files are attributed to the importing source file's analysis result

### INVEST Flags

- testable
- edge-cases
