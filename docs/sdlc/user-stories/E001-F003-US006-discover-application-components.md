# US006 — Developer discovers application components

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F003 — Java Source AST Analysis
**Priority:** must | **Estimate:** 5 SP
**Depends on:** US001 | **Blocks:** US007, US008, US009, US010

> As a **Developer**, I want **the tool to recognize Spring components, controllers, services, and repositories**, so that **I get a complete inventory of my application's structural building blocks**.

### Acceptance Criteria

- [ ] Spring stereotypes (`@Component`, `@Service`, `@Repository`, `@Controller`, `@RestController`) are classified with their annotation type
- [ ] Non-Spring classes are classified as "other" with their package and name
- [ ] Failed file parses are logged with the file path and error, and processing continues
- [ ] Classification uses annotation presence as the primary strategy, falling back to structural heuristics when type resolution fails
