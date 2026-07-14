# US040 — Developer links template forms to controller endpoints

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F015 — JSP/Thymeleaf Form & View Detection
**Priority:** could | **Estimate:** 3 SP
**Depends on:** US038, US039 | **Blocks:** none

> As a **Developer**, I want **template form action URLs to be matched against known controller endpoints**, so that **I get an end-to-end trace from the frontend template to the backend method**.

### Acceptance Criteria

- [ ] Template form action URLs are normalized and matched against `EndpointInfo` paths
- [ ] Exact literal matches are linked with confidence 1.0
- [ ] Path-parameterized matches (e.g., `/owners/5` ↔ `/owners/{id}`) are linked with confidence 0.8
- [ ] Same-path-different-prefix matches are linked with confidence 0.5
- [ ] HTTP methods must match (POST ↔ POST, GET ↔ GET) for a link to be created
- [ ] Matched links are written to `template_endpoint_links` array in JSON output
- [ ] Each link records template path, form action, matched endpoint, controller name, and confidence
