# US037 — Developer detects view-returning controllers

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F015 — JSP/Thymeleaf Form & View Detection
**Priority:** should | **Estimate:** 3 SP
**Depends on:** US006 | **Blocks:** US040

> As a **Developer**, I want **controller methods that return HTML views to be tagged as view-serving endpoints**, so that **I can distinguish REST APIs from UI-serving endpoints in the output**.

### Acceptance Criteria

- [ ] Methods returning `ModelAndView` are detected with `servesView=true` and view name extracted
- [ ] Methods returning `String` in `@Controller` (not `@RestController`) are detected with `servesView=true`
- [ ] Methods returning `View` are detected with `servesView=true`
- [ ] Methods with `void` return type are detected with `servesView=true`
- [ ] Methods in `@RestController` classes are NOT marked as views
- [ ] Methods with `@ResponseBody` are NOT marked as views
- [ ] View name is extracted from `return new ModelAndView("viewName")` or `return "viewName"`
- [ ] EndpointInfo record includes `servesView` boolean and `viewName` string fields
