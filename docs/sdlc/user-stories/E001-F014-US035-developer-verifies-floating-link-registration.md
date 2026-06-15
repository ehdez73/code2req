# US035 — Developer verifies floating link registration

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F014 — Structured Trace SQLite Persistence
**Priority:** should | **Estimate:** 3 SP
**Depends on:** US032 | **Blocks:** nothing

> As a **Developer**, I want **to confirm that detected outbound HTTP calls are correctly registered in the floating_links table**, so that **I can review inter-service dependencies without manually scanning REST client code**.

### Acceptance Criteria

- [ ] RestTemplate calls are registered with method, URL pattern, and isExpression flag
- [ ] WebClient calls are registered with method and URL pattern
- [ ] FeignClient interfaces are registered with composite URL (base + path)
- [ ] Multiple calls in the same file create multiple floating_link rows
- [ ] Dynamic or expression-based URLs have isExpression = true
