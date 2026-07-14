# Feature: Structured Trace SQLite Persistence
# Epic: E001 — Deterministic Multi-Language Indexing
# Feature ID: F014
# Stories: US034, US035
# Implemented: 2026-06-16
# Last updated: 2026-06-16

Feature: Structured Trace SQLite Persistence
  Extended SQLite schema with execution_findings, topic_links, floating_links, and metrics tables.

  Background:
    Given Phase 1 linker has completed all passes and link resolution

  Rule: Call graph edges are persisted in execution_findings table

    @US034 @E001 @F014 @must
    Scenario: Resolved call graph edge is persisted
      Given a resolved call graph edge from OrderController.createOrder to OrderService.createOrder
      When the linker persists findings to SQLite
      Then an execution_findings row is created with finding_type = CALL_GRAPH_EDGE
      And the finding_json contains source file, target file, and method signatures
      And resolved = 1

    @US034 @E001 @F014 @should
    Scenario: Unresolved signature is persisted
      Given an unresolved call to thirdparty-sdk.calculateScore()
      When the linker persists findings to SQLite
      Then an execution_findings row is created with finding_type = CALL_GRAPH_EDGE
      And resolved = 0

  Rule: Topic links are persisted in topic_links table

    @US034 @E001 @F014 @should
    Scenario: Resolved topic link is persisted
      Given a resolved topic link for KAFKA topic "order-events"
      When the linker persists findings to SQLite
      Then a topic_links row is created with broker = KAFKA and topic_or_queue = "order-events"
      And producer_task_id and consumer_task_id are populated
      And resolved_status = RESOLVED

  Rule: Floating links are persisted in floating_links table

    @US035 @E001 @F014 @should
    Scenario: Outbound HTTP call is registered as floating link
      Given a detected RestTemplate POST call to "${payment.url}/charges"
      When the linker persists findings to SQLite
      Then a floating_links row is created with method = POST
      And url_or_path = "${payment.url}/charges"
      And is_expression = 1
      And resolved_status = PENDING

    @US035 @E001 @F014 @should
    Scenario: Literal URL floating link is pre-resolved
      Given a RestTemplate GET call to "http://localhost:8080/api/orders"
      And a known endpoint GET /api/orders exists in the same manifest
      When FloatingLinkResolver processes the link
      Then resolved_status = RESOLVED
      And confidence = 1.0

  Rule: Metrics are collected after full Phase 1 execution

    @US034 @E001 @F014 @should
    Scenario: Metrics row is created after scan
      Given Phase 1 has completed with N files analysed, E edges resolved, T topic links, F floating links
      When the linker writes metrics
      Then a metrics row is created with phase = 1
      And edges_resolved and edges_unresolved reflect actual counts
      And topic_links_resolved and floating_links_registered reflect actual counts

  Rule: All tables survive a restart via idempotent upsert

    @US034 @E001 @F014 @should
    Scenario: Re-running a scan overwrites existing rows
      Given a previous scan with persisted execution_findings
      When a new scan completes for the same workspace
      Then the old execution_findings rows are replaced or updated
      And no duplicate task_id entries exist
