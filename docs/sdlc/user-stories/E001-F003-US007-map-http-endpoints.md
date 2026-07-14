# US007 — Developer maps HTTP endpoints

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F003 — Java Source AST Analysis
**Priority:** must | **Estimate:** 5 SP
**Depends on:** US006 | **Blocks:** US011

> As a **Developer**, I want **the tool to find all request mappings in my controllers**, so that **I can document the full surface area of my service**.

### Acceptance Criteria

- [ ] `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping` are captured with HTTP method and path
- [ ] Class-level and method-level mappings are combined into full endpoints
- [ ] Path variables and query parameters are extracted where present
- [ ] Each endpoint is linked to its controller component
