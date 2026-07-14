# US058 — Developer discovers servlet endpoints in web.xml

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F003 — Java Source AST Analysis
**Priority:** should | **Estimate:** 2 SP
**Depends on:** US001 | **Blocks:** —

> As a **Developer**, I want **the tool to discover HTTP endpoints declared in
  legacy `web.xml` deployment descriptors**, so that **servlet-based applications
  have complete endpoint coverage without manual annotation mapping**.

### Acceptance Criteria

- [ ] web.xml `<servlet>` + `<servlet-mapping>` pairs produce `EndpointInfo` entries
- [ ] Missing or malformed web.xml files are skipped with a warning
- [ ] Discovered endpoints include the url-pattern and servlet class name
