# US066 — Developer discovers scheduled tasks and JMS listeners from XML

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F029 — Spring XML Configuration Analysis
**Priority:** should | **Estimate:** 2 SP
**Depends on:** US065 | **Blocks:** US013

> As a **Developer**, I want **the tool to detect scheduled tasks, JMS listeners, and AOP/transaction/cache configuration declared in Spring XML files**, so that **infrastructure concerns defined outside Java annotations are not missed in the analysis**.

### Acceptance Criteria

- [ ] `<task:scheduled ref="..." method="..." cron="..." fixed-rate="..." fixed-delay="...">` elements are extracted with ref, method, cron/fixed-rate/fixed-delay values, and task type classification (cron, fixed-rate, fixed-delay)
- [ ] `<jms:listener destination="..." ref="..." method="..." response-destination="...">` elements are captured with destination, bean reference, method name, and optional response destination
- [ ] `<aop:config>` elements are flagged as AOP infrastructure configuration
- [ ] `<tx:*>` elements (annotation-driven, advice, jta-transaction-manager) are flagged as transaction infrastructure
- [ ] `<cache:*>` elements (annotation-driven, cache-manager) are flagged as caching infrastructure

### INVEST Flags

- testable
