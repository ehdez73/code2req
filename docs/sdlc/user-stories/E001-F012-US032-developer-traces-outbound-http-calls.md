# US032 — Developer traces outbound HTTP calls

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F012 — Outbound HTTP Client Detection
**Priority:** should | **Estimate:** 5 SP
**Depends on:** US006 | **Blocks:** US013

> As a **Developer**, I want **the tool to capture all outbound HTTP client calls**, so that **I can map inter-service dependencies and identify external API consumers**.

### Acceptance Criteria

- [ ] RestTemplate.exchange(), getForObject(), postForObject(), put(), delete() are detected
- [ ] WebClient fluent builder chains are followed to extract method and URL
- [ ] @FeignClient interfaces are detected with their method-level mappings
- [ ] URL literals are captured as-is
- [ ] SpEL expressions and environment variable references are captured as patterns with isExpression=true
- [ ] Each detection is registered as a floating_link in the SQLite store
- [ ] Unresolved calls with dynamic URLs are marked PENDING
