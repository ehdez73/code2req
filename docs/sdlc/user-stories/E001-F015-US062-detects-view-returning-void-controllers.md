# US062 — Developer detects view-returning void controllers

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F015 — JSP/Thymeleaf Template Analysis
**Priority:** should | **Estimate:** 2 SP
**Depends on:** US006 | **Blocks:** —

> As a **Developer**, I want **the tool to detect `@Controller` methods that
  return `void` and render an implicit view from the request path**, so that
  **legacy Spring MVC view endpoints are captured**.

### Acceptance Criteria

- [ ] `@Controller` methods with `void` return type are marked as serving a view
- [ ] `@RestController` methods with `void` return type are not marked as serving a view
- [ ] The view name is inferred from the request path where no explicit return value exists
