# Feature: Interactive Mode — Agent-User Clarification
# Epic: E004 — Agentic Functional Requirement Extraction
# Feature ID: F028
# Stories: US057
# Phase 3 draft generated: 2026-06-26

Feature: Interactive Mode
  When the Embabel agent encounters ambiguity it cannot resolve from code analysis,
  it defers to the user via terminal prompts. A UserInteractionService SPI decouples
  the agent from the I/O implementation, enabling both headless and interactive modes.

  Background:
    Given the Embabel agent is executing a GOAP action (TraceFlow, AnalyzeFlow, or CrossReferenceFlows)
    And the action's internal confidence score is below ambiguity-confidence-threshold (0.7)

  Rule: UserInteractionService SPI decouples agent from I/O

    @US057 @E004 @F028 @should @draft
    Scenario: UserInteractionService SPI defines three interaction methods
      Given the Phase 3 synthesis package is inspected
      When UserInteractionService interface is examined
      Then it defines ask(prompt, context) returning String
      And it defines confirm(message) returning boolean
      And it defines select(options, prompt) returning String

    @US057 @E004 @F028 @should @draft
    Scenario: NoOpUserInteractionService is the default headless implementation
      Given the pipeline is invoked without --interactive flag
      When the agent encounters ambiguity
      Then NoOpUserInteractionService is used
      And ask() returns null
      And confirm() returns true
      And select() returns null
      And AmbiguityGap records are created for each unresolved ambiguity

    @US057 @E004 @F028 @should @draft
    Scenario: InteractiveUserInteractionService prompts user via terminal
      Given the pipeline is invoked with --interactive flag
      When the agent encounters ambiguity
      Then InteractiveUserInteractionService reads from stdin
      And prompts are displayed on stdout
      And the user's response is returned to the agent

  Rule: Agent defers to user on ambiguous decisions

    @US057 @E004 @F028 @should @draft
    Scenario: TraceFlow calls UserInteractionService for ambiguous call targets
      Given a MethodCallExpr has multiple candidate targets (AMBIGUOUS resolution)
      When TraceFlow attempts to resolve the call
      And confidence is below threshold
      Then UserInteractionService.select() is called with the candidates
      And the selected target is used for the flow step
      And an AmbiguityGap is recorded with the user's decision

    @US057 @E004 @F028 @should @draft
    Scenario: AnalyzeFlow calls UserInteractionService for unclear business rules
      Given a method body has logic that cannot be mapped to a known business rule
      When AnalyzeFlow extracts business semantics
      And confidence is below threshold
      Then UserInteractionService.ask() is called with the ambiguous context
      And the user's answer is incorporated into the business rule extraction

  Rule: User responses are cached in SQLite

    @US057 @E004 @F028 @should @draft
    Scenario: User answers persist in user_responses table
      Given the user has provided answers during an interactive session
      When the session completes
      Then each Q&A pair is stored in the user_responses SQLite table
      And the table contains session_id, question, answer, and created_at columns
