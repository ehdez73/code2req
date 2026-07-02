# Feature: Phase 2 Planner — Task Qualification for LLM Enrichment
# Epic: E003 — Semantic Enrichment
# Feature ID: F016
# Stories: US041, US042, US064
# Phase 2 draft generated: 2026-06-17
# Last updated: 2026-06-30

Feature: Phase 2 Planner — Task Qualification for LLM Enrichment
  Reads the SQLite task store after Phase 1 and determines which tasks qualify for LLM enrichment based on configurable rules.

  Background:
    Given Phase 1 indexing completed with tasks populated in SQLite
    And the execution_findings table contains Phase 1 detection results
    And the llm-unresolved-threshold is set to 5 (default)

  Rule: A task qualifies for LLM enrichment when it exceeds the unresolved signature threshold, is a virtual declaration, contains stored procedure calls, is a custom ConstraintValidator, has a paired test file, or has a DTO/record with bean validation annotations used in a flow

    @US041 @E003 @F016 @must @draft
    Scenario: Task qualifies due to exceeding unresolved signature threshold
      Given a task with 7 unresolved signatures in execution_findings
      When the planner evaluates qualification
      Then the task is qualified with reason "UNRESOLVED_SIGNATURES_EXCEEDED"

    @US041 @E003 @F016 @must @draft
    Scenario: Task qualifies because it is a Spring Data interface
      Given a task whose file extends CrudRepository
      When the planner evaluates qualification
      Then the task is qualified with reason "SPRING_DATA_INTERFACE"

    @US041 @E003 @F016 @must @draft
    Scenario: Task qualifies due to stored procedure call
      Given a task with a DATABASE_PROCEDURE_CALL finding
      When the planner evaluates qualification
      Then the task is qualified with reason "STORED_PROCEDURE_CALL"

    @US041 @E003 @F016 @must @draft
    Scenario: Task qualifies because it is a custom ConstraintValidator
      Given a task whose file is a ConstraintValidator with isValid method
      When the planner evaluates qualification
      Then the task is qualified with reason "CUSTOM_CONSTRAINT_VALIDATOR"

    @US041 @E003 @F016 @must @draft
    Scenario: Task qualifies due to paired test file with assertions
      Given a task with a paired test file found on disk
      And the paired test file contains test assertions
      When the planner evaluates qualification
      Then the task is qualified with reason "TEST_ASSERTIONS_PRESENT"

    @US041 @E003 @F016 @must @draft
    Scenario: Task does not qualify when no qualification rule matches
      Given a task with 2 unresolved signatures
      And the task is not a Spring Data interface
      And the task has no stored procedure calls
      And the task is not a ConstraintValidator
      And the task has no paired test file
      When the planner evaluates qualification
      Then the task is not qualified
      And the decision contains the reason "NONE"

    @US041 @E003 @F016 @must @draft
    Scenario: Task qualifies with multiple simultaneous reasons
      Given a task that is a Spring Data interface
      And the task also has 8 unresolved signatures
      When the planner evaluates qualification
      Then the task is qualified
      And the decision contains reasons "SPRING_DATA_INTERFACE" and "UNRESOLVED_SIGNATURES_EXCEEDED"

    @US064 @E003 @F016 @should @draft
    Scenario: Task qualifies because its DTO/record has bean validation annotations used in a flow
      Given a task with VALIDATOR findings from a record or DTO with built-in bean validation annotations
      And the task also has a flow-relevant finding (e.g., ENDPOINT, COMPONENT, DB_ACCESS, SCHEDULED_TASK)
      When the planner evaluates qualification
      Then the task is qualified with reason "BEAN_VALIDATION"

    @US064 @E003 @F016 @should @draft
    Scenario: Task with bean validation annotations but no flow usage does NOT qualify
      Given a task with VALIDATOR findings from a DTO with built-in bean validation annotations
      And the task has no ENDPOINT, COMPONENT, DB_ACCESS, or other flow-relevant findings
      When the planner evaluates qualification
      Then the task is not qualified

  Rule: The planner returns qualification decisions without making any LLM calls

    @US042 @E003 @F016 @should @draft
    Scenario: Planner runs without network calls
      Given the planner is invoked
      When the planner evaluates all tasks
      Then zero network calls are made
      And zero LLM tokens are consumed

    @US042 @E003 @F016 @should @draft
    Scenario: Planner displays qualified tasks grouped by target
      Given a manifest with two scan targets, each having qualified tasks
      When the developer runs the plan command
      Then qualified tasks are displayed grouped by target name
      And each task shows its qualification reason(s)

    @US042 @E003 @F016 @should @draft
    Scenario: Planner respects custom llm-unresolved-threshold
      Given the llm-unresolved-threshold is set to 3
      And a task has 4 unresolved signatures
      When the planner evaluates qualification
      Then the task is qualified with reason "UNRESOLVED_SIGNATURES_EXCEEDED"
