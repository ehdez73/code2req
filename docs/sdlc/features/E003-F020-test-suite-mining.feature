# Feature: Test Suite Mining — Assertion Extraction
# Epic: E003 — Semantic Enrichment
# Feature ID: F020
# Stories: US048
# Phase 2 draft generated: 2026-06-17
# Last updated: 2026-06-17

Feature: Test Suite Mining
  Match production files with paired test files, extract assertions, and translate them into functional edge cases and validation requirements.

  Background:
    Given Phase 1 indexing completed with file discovery
    And the planner has qualified tasks for enrichment

  Rule: Test files are paired with production files by file name convention

    @US048 @E003 @F020 @should @draft
    Scenario: Test file is paired with production file by suffix convention
      Given a production file named OrderService.java
      And a test file named OrderServiceTest.java exists in the test directory
      When the test matcher discovers pairs
      Then OrderServiceTest.java is paired with OrderService.java

    @US048 @E003 @F020 @should @draft
    Scenario: Production file with no matching test file has no pair
      Given a production file named InventoryService.java
      And no InventoryServiceTest.java exists anywhere under the test source roots
      When the test matcher discovers pairs
      Then InventoryService.java has no paired test file

  Rule: Assertions in test files are extracted and translated to edge cases

    @US048 @E003 @F020 @should @draft
    Scenario: assertEquals assertions are extracted as validation rules
      Given a paired test file contains assertEquals(expected, actual) calls
      When the executor processes both files
      Then each assertEquals call is converted to a validation rule
      And the rule appears in business_rules_and_guardrails.validations

    @US048 @E003 @F020 @should @draft
    Scenario: assertThrows assertions are extracted as edge cases
      Given a paired test file contains assertThrows(Exception.class, () -> ...) calls
      When the executor processes both files
      Then each assertThrows call is converted to an edge case
      And the edge case includes the exception type and triggering condition

    @US048 @E003 @F020 @should @draft
    Scenario: Extracted insights are merged into the test_insights array
      Given a paired test file with both assertEquals and assertThrows assertions
      When the executor completes
      Then the ExecutionFinding contains a test_insights array
      And each entry includes test_file_path, scenario_verified, and hidden_rule_uncovered
