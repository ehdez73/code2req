# US027 — Developer traces ActiveMQ (JMS) event flows

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F003 — Java Source AST Analysis
**Priority:** should | **Estimate:** 5 SP
**Depends on:** US006 | **Blocks:** US013

> As a **Developer**, I want **the tool to capture @JmsListener subscriptions and JmsTemplate.convertAndSend() / JmsTemplate.send() outbound publications**, so that **I can trace JMS-based event-driven message flows across my system**.

### Acceptance Criteria

- [ ] Methods annotated with @JmsListener are identified with their destination subscriptions (destination attribute)
- [ ] Destination values are extracted as literal strings where possible
- [ ] SpEL expressions in destination attributes are captured as-is without resolution
- [ ] Calls to JmsTemplate.convertAndSend() are identified with their destination
- [ ] Calls to JmsTemplate.send() are identified with their destination
- [ ] Each ActiveMQ listener and publication is linked to its owning component
- [ ] Components with JMS imports but no JMS usage produce no false positives
