# US023 — Developer traces Kafka event flows

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F003 — Java Source AST Analysis
**Priority:** should | **Estimate:** 5 SP
**Depends on:** US006 | **Blocks:** US013

> As a **Developer**, I want **the tool to capture @KafkaListener subscriptions and KafkaTemplate.send() outbound publications**, so that **I can trace event-driven message flows across my system**.

### Acceptance Criteria

- [ ] Methods annotated with @KafkaListener are identified with their topic subscriptions (topics, topicPattern, topicPartitions)
- [ ] Topic values are extracted as literal strings where possible
- [ ] SpEL expressions in topic attributes are captured as-is without resolution
- [ ] Calls to KafkaTemplate.send() are identified with their destination topic
- [ ] Each Kafka listener and publication is linked to its owning component
- [ ] Components with Kafka imports but no Kafka usage produce no false positives
