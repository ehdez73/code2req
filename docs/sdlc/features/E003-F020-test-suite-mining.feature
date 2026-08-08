# Feature: Test Suite Mining — Assertion Extraction
# Epic: E003 — Semantic Enrichment
# Feature ID: F020
# Stories: US048
# Last updated: 2026-08-08

Feature: Test Suite Mining (Embedded in EnrichFlowAction)
  Test file pairing and assertion extraction are embedded in EnrichFlowAction. When a production file has a paired test file (by naming convention), the test content is sent alongside the source code in the enrichment prompt. The LLM extracts test-encoded business rules and edge cases from the assertion content. No standalone mining step or planner-qualified pipeline exists.

  Background:
    Given Phase 1 indexing completed with file discovery
    And EnrichFlowAction is enriching a flow's files

  Rule: Test files are paired with production files by file name convention

    @US048 @E003 @F020 @should @implemented
    Scenario: Test file is paired with production file by suffix convention
      Given a production file named OrderService.java
      And a test file named OrderServiceTest.java exists in the test directory
      When EnrichFlowAction.enrichFile() processes the production file
      Then OrderServiceTest.java content is included in the LLM prompt
      And the LLM extracts test-encoded business rules as part of enrichment

    @US048 @E003 @F020 @should @implemented
    Scenario: Production file with no matching test file has no test context
      Given a production file named InventoryService.java
      And no InventoryServiceTest.java exists anywhere under the test source roots
      When EnrichFlowAction.enrichFile() processes the file
      Then no test file content is included in the LLM prompt
