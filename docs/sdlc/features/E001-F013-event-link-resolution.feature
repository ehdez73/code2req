# Feature: Event Link Resolution
# Epic: E001 — Deterministic Multi-Language Indexing
# Feature ID: F013
# Stories: US033
# Phase 1 draft generated: 2026-06-15
# Last updated: 2026-06-15

Feature: Event Link Resolution
  Deterministic matching of event producers to consumers by broker type and topic/queue/destination.

  Background:
    Given Pass 2 analysis is complete for all files in the scan targets
    And producer and consumer findings are persisted in the analysis results

  Rule: Producer and consumer sharing the same topic are linked

    @US033 @E001 @F013 @should @final
    Scenario: Kafka publisher and listener share the same topic
      Given a file containing KafkaTemplate.send("order-events", key, value)
      And another file containing @KafkaListener(topics = "order-events")
      When TopicLinkResolver processes the findings
      Then a topic_link is created with broker KAFKA and topic "order-events"
      And the producer and consumer task IDs are linked
      And resolved_status is set to RESOLVED

    @US033 @E001 @F013 @should @final
    Scenario: RabbitMQ publisher and listener share the same queue
      Given a file containing RabbitTemplate.convertAndSend("order.exchange", "order.routing.key", msg)
      And another file containing @RabbitListener(queues = "order.queue")
      When TopicLinkResolver processes the findings
      Then a topic_link is created with broker RABBITMQ and queue "order.queue"
      And resolved_status is set to RESOLVED

    @US033 @E001 @F013 @should @final
    Scenario: ActiveMQ publisher and listener share the same destination
      Given a file containing JmsTemplate.convertAndSend("order.queue", msg)
      And another file containing @JmsListener(destination = "order.queue")
      When TopicLinkResolver processes the findings
      Then a topic_link is created with broker ACTIVEMQ and destination "order.queue"
      And resolved_status is set to RESOLVED

  Rule: Cross-target matching within the same manifest

    @US033 @E001 @F013 @should @final
    Scenario: Producer and consumer in different scan targets
      Given target A contains RabbitTemplate.convertAndSend("order.exchange", "order.routing.key", msg)
      And target B contains @RabbitListener(queues = "order.queue")
      When TopicLinkResolver processes all targets
      Then a topic_link is created connecting producer from target A to consumer from target B
      And the link includes both target names

  Rule: Non-matching topics remain PENDING

    @US033 @E001 @F013 @should @final
    Scenario: Producer without matching consumer
      Given a file containing JmsTemplate.convertAndSend("unmatched.queue", msg)
      And no @JmsListener with destination "unmatched.queue" exists
      When TopicLinkResolver processes the findings
      Then no topic_link is created for "unmatched.queue"
      And the unresolved publication is logged as an orphan producer

    @US033 @E001 @F013 @should @final
    Scenario: Consumer without matching producer
      Given a file containing @KafkaListener(topics = "standalone-topic")
      And no KafkaTemplate.send() call to "standalone-topic" exists
      When TopicLinkResolver processes the findings
      Then no topic_link is created for "standalone-topic"
      And the unresolved listener is logged as an orphan consumer
