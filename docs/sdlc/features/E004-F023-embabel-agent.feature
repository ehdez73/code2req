# Feature: Embabel Agent — Entry-Point-Driven Extraction
# Epic: E004 — Agentic Functional Requirement Extraction
# Feature ID: F023
# Stories: US051
# Phase 3 redefinition: 2026-06-26

Feature: Embabel Agent — Entry-Point-Driven Extraction
  The Embabel agent discovers entry points from CodebaseKnowledge, traces execution flows through the call graph, extracts business semantics (user stories, Gherkin scenarios, business rules), groups related flows into features, and produces the final specification.

  Background:
    Given CodebaseKnowledge is built with Phase 1 findings and enrichment data
    And the Embabel agent is invoked with initial blackboard state

  Rule: The agent traces execution flows from entry points and extracts functional requirements

    @US051 @E004 @F023 @must @final
    Scenario: Agent discovers entry points from CodebaseKnowledge
      Given CodebaseKnowledge contains findings with types ENDPOINT, SCHEDULED_TASK, KAFKA_LISTENER, RABBITMQ_LISTENER, and EVENT_LISTENER
      When the DiscoverEntryPoints action executes
      Then a list of EntryPoint objects is produced on the blackboard
      And trivial endpoints (actuator, health, metrics) are filtered out
      And each entry point has a priority score (0.0-1.0)
      And entry points are sorted by priority descending

    @US051 @E004 @F023 @must @final
    Scenario: Agent traces execution flow from an entry point through the call graph
      Given a prioritized list of entry points exists on the blackboard
      When the TraceFlow action executes for the highest-priority unscheduled entry point
      Then an ExecutionFlow object is produced with FlowStep entries
      And each step traces from the entry point through services to repositories
      And adaptive depth stops when max-flow-depth is exceeded or no more internal calls exist
      And unresolved calls (external services, third-party) are recorded

    @US051 @E004 @F023 @must @final
    Scenario: Agent reuses cached sub-chains for related entry points
      Given a service chain has been traced for POST /orders
      When the TraceFlow action executes for GET /orders
      Then the cached sub-chain for shared services is reused
      And only the divergent steps are newly traced

    @US051 @E004 @F023 @must @final
    Scenario: Agent extracts business semantics from a traced flow
      Given an ExecutionFlow with traced steps exists on the blackboard
      When the AnalyzeFlow action executes
      Then a FunctionalFlow object is produced with:
        - User story (As a... / I want... / so that...)
        - Gherkin scenarios (Given/When/Then)
        - Business rules with preconditions, postconditions, and error behavior
        - Edge cases with business consequences
      And enrichment context is used where available
      And raw source files are read for gaps not covered by enrichment

    @US051 @E004 @F023 @must @final
    Scenario: Agent applies progressive disclosure based on flow complexity
      Given analyzed flows of varying complexity exist on the blackboard
      When the AnalyzeFlow action evaluates each flow's complexity score
      Then flows with score < 0.3 produce MINIMAL output (1 story, 1 scenario)
      And flows with score >= 0.3 and < 0.7 produce STANDARD output (story + 2-3 scenarios + rules)
      And flows with score >= 0.7 produce FULL output (story + scenarios + rules + edge cases + Mermaid diagram)

    @US051 @E004 @F023 @must @final
    Scenario: Agent groups related flows into features using semantic clustering
      Given multiple FunctionalFlow objects exist on the blackboard
      When the GroupFlows action executes
      Then flows are clustered by semantic similarity from enrichment data
      And related flows are merged into FunctionalFeature objects (e.g., GET/POST /orders = "Order Management")
      And features are assigned descriptive names and descriptions

    @US051 @E004 @F023 @must @final
    Scenario: Agent cross-references flows for inter-flow dependencies
      Given grouped features exist on the blackboard
      When the CrossReferenceFlows action executes
      Then FlowRelationship objects are produced for inter-flow dependencies
      And floating HTTP links are matched to known endpoints
      And topic publications are matched to known consumers
      And relationship types include DELEGATES_TO, PUBLISHES_EVENT, CONSUMES_EVENT

    @US051 @E004 @F023 @must @final
    Scenario: Agent detects orphaned methods not reachable from any entry point
      Given all entry points have been traced
      When the agent analyzes reachable methods versus known methods
      Then methods that are called but NOT reachable from any entry point are flagged
      And OrphanedMethod objects are produced with file path and reason

    @US051 @E004 @F023 @must @final
    Scenario: Agent quarantines flows exceeding guardrails
      Given a candidate flow has required 6 investigation steps
      And max-flow-depth is set to 5
      When the QuarantineFlow action evaluates the flow
      Then the flow is quarantined
      And its status is set to AWAITING_HUMAN_REVIEW
      And the quarantine gap is persisted in extraction-cache.json

    @US051 @E004 @F023 @must @final
    Scenario: Agent persists extraction cache
      Given all features are grouped and cross-referenced
      When the agent persists results to extraction-cache.json
      Then the cache contains CrossReferencedResult, orphaned methods, and quarantine gaps
      And the cache is written to spec-output/extraction-cache.json
      And no spec files are written by the agent
