# Feature: CodebaseKnowledge Builder + Phase 3 Orchestrator
# Epic: E004 — Agentic Functional Requirement Extraction
# Feature ID: F022
# Stories: US050
# Phase 3 updated: 2026-06-26

Feature: CodebaseKnowledge Builder + Phase 3 Orchestrator
  Pure-Java services that aggregate Phase 1 and Phase 2 data into an in-memory CodebaseKnowledge domain model, pass it to the Embabel agent, and handle output after completion.

  Background:
    Given Phase 1 indexing is complete with structural findings in SQLite
    And enrichment findings may or may not have ExecutionFinding records in SQLite

  Rule: The orchestrator aggregates Phase 1 and Phase 2 data into a queryable CodebaseKnowledge model

    @US050 @E004 @F022 @must @final
    Scenario: Orchestrator aggregates all Phase 1 structural data
      Given call graph edges, endpoint registries, topic links, floating links, and database access patterns exist in SQLite
      When the orchestrator builds CodebaseKnowledge
      Then StructuralGraph contains all call graph edges
      And LinkRegistry contains all topic and floating links
      And endpoint maps are available by module

    @US050 @E004 @F022 @must @final
    Scenario: Orchestrator aggregates all Phase 2 enriched data
      Given ExecutionFinding records exist for enriched files
      When the orchestrator builds CodebaseKnowledge
      Then SemanticEnrichment contains all records keyed by file path
      And business rules are queryable by file and module

    @US050 @E004 @F022 @must @final
    Scenario: CodebaseKnowledge provides entry point queries
      Given CodebaseKnowledge is built with endpoint, scheduled task, and event listener data
      When getEntryPoints() is called
      Then entry points of all types (HTTP, SCHEDULED, KAFKA, RABBITMQ, ACTIVEMQ, EVENT_LISTENER) are returned
      And each entry point includes finding JSON (httpMethod, path, schedule, topicOrQueue, etc.)

    @US050 @E004 @F022 @must @final
    Scenario: CodebaseKnowledge provides call graph queries
      Given CodebaseKnowledge is built with call graph edges
      When getCallersOf("OrderService.createOrder") is called
      Then the result includes "OrderController.createOrder"
      When getCalleesOf("OrderController.createOrder") is called
      Then the result includes "OrderService.createOrder"

    @US050 @E004 @F022 @must @final
    Scenario: CodebaseKnowledge provides component queries
      Given CodebaseKnowledge is built with component data
      When getComponentsByType("RestController") is called
      Then all RestController components are returned
      When getAllKnownMethods() is called
      Then all methods defined across all components are returned for orphaned method detection
