# US033 — Developer links event producers to consumers

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F013 — Event Link Resolution
**Priority:** should | **Estimate:** 5 SP
**Depends on:** US023, US026, US027 | **Blocks:** US013

> As a **Developer**, I want **the tool to automatically match event producers to their corresponding consumers**, so that **I can trace event-driven message flows across my system end-to-end**.

### Acceptance Criteria

- [ ] Kafka publishers and listeners sharing the same topic are linked
- [ ] RabbitMQ publishers and listeners sharing the same queue are linked
- [ ] ActiveMQ/JMS publishers and listeners sharing the same destination are linked
- [ ] Links are stored in the topic_links table with broker and topic/queue/destination
- [ ] Cross-target matching within the same manifest is supported
- [ ] Non-matching producers are logged as orphan publications
- [ ] resolved_status reflects RESOLVED or PENDING appropriately
