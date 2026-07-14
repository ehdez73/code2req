# US026 — Developer traces RabbitMQ event flows

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F003 — Java Source AST Analysis
**Priority:** should | **Estimate:** 5 SP
**Depends on:** US006 | **Blocks:** US013

> As a **Developer**, I want **the tool to capture @RabbitListener subscriptions and RabbitTemplate.convertAndSend() / RabbitTemplate.send() outbound publications**, so that **I can trace RabbitMQ-based event-driven message flows across my system**.

### Acceptance Criteria

- [ ] Methods annotated with @RabbitListener are identified with their queue subscriptions (queues attribute)
- [ ] Queue values are extracted as literal strings where possible
- [ ] SpEL expressions in queue attributes are captured as-is without resolution
- [ ] Calls to RabbitTemplate.convertAndSend() are identified with their exchange and routing key
- [ ] Calls to RabbitTemplate.send() are identified with their exchange and routing key
- [ ] Each RabbitMQ listener and publication is linked to its owning component
- [ ] Components with RabbitMQ imports but no RabbitMQ usage produce no false positives
