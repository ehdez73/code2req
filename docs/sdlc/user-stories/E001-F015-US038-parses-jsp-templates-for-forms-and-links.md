# US038 — Developer parses JSP templates for forms and links

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F015 — JSP/Thymeleaf Form & View Detection
**Priority:** should | **Estimate:** 5 SP
**Depends on:** US006, US037 | **Blocks:** US040

> As a **Developer**, I want **JSP template files to be scanned for HTML forms and anchor links**, so that **frontend-to-backend HTTP interactions are captured for end-to-end tracing**.

### Acceptance Criteria

- [ ] `.jsp` files under scan targets are discovered and parsed
- [ ] `<form action="..." method="...">` elements are extracted with HTTP method, URL, and field names
- [ ] `<form action="...">` without method defaults to GET
- [ ] `<a href="...">` anchor links are extracted
- [ ] `<input name="...">` field names within forms are collected
- [ ] External URLs (`http://...`, `https://...`) and fragment links (`#...`) are excluded
- [ ] Findings are written to `template_forms` and `template_anchor_links` arrays in JSON output
