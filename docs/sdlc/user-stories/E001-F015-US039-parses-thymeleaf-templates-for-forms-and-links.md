# US039 — Developer parses Thymeleaf templates for forms and links

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F015 — JSP/Thymeleaf Form & View Detection
**Priority:** should | **Estimate:** 5 SP
**Depends on:** US006, US037 | **Blocks:** US040

> As a **Developer**, I want **Thymeleaf HTML templates to be scanned for forms and links**, so that **Thymeleaf frontend interactions are captured for end-to-end tracing**.

### Acceptance Criteria

- [ ] `.html` files under scan targets are discovered and parsed
- [ ] `<form th:action="@{...}" th:method="...">` elements are extracted with HTTP method, URL, and field names
- [ ] `<form th:action="@{...}">` without th:method defaults to GET
- [ ] `<a th:href="@{...}">` anchor links are extracted
- [ ] `<input th:field="*{...}">` and `<input name="...">` field names within forms are collected
- [ ] Thymeleaf expressions (`@{...}`) containing `${...}` or `*{...}` are flagged as `isExpression=true`
- [ ] External URLs and fragment links are excluded
- [ ] Findings are written to `template_forms` and `template_anchor_links` arrays in JSON output
