# Feature: Flow-Driven Enrichment Qualification
# Epic: E003 — Semantic Enrichment
# Feature ID: F016
# Stories: US041, US042, US064 (deferred — implemented inline in EnrichFlowAction)
# Last updated: 2026-08-08

Feature: Flow-Driven Enrichment Qualification
  EnrichFlowAction.shouldEnrich() evaluates whether files in a traced flow need LLM enrichment using inline qualification checks. Qualification runs inside extract, not as a standalone Planner.

  Background:
    Given Phase 1 indexing completed with tasks and findings in SQLite
    And the EnrichFlowAction is invoked during extract
    And enrichment is not already cached for the file

  Rule: A file qualifies for enrichment based on structural findings

    @US041 @E003 @F016 @must @deferred
    Scenario: File qualifies due to stored procedure call
      Given a file with a DATABASE_PROCEDURE_CALL finding
      When EnrichFlowAction.shouldEnrich() evaluates the file
      Then the file is qualified for LLM enrichment

    @US041 @E003 @F016 @must @deferred
    Scenario: File qualifies due to custom constraint validator
      Given a file with a CONSTRAINT_VALIDATOR finding
      When EnrichFlowAction.shouldEnrich() evaluates the file
      Then the file is qualified for LLM enrichment

    @US041 @E003 @F016 @must @deferred
    Scenario: File qualifies due to complex SQL
      Given a file with a NATIVE_SQL_QUERY or JPQL_HQL_QUERY finding
      When EnrichFlowAction.shouldEnrich() evaluates the file
      Then the file is qualified for LLM enrichment

    @US041 @E003 @F016 @must @deferred
    Scenario: File qualifies due to unresolved floating link
      Given a file with floating links in PENDING status
      When EnrichFlowAction.shouldEnrich() evaluates the file
      Then the file is qualified for LLM enrichment

    @US041 @E003 @F016 @must @deferred
    Scenario: File qualifies due to paired test file
      Given a file with a paired test file on disk
      When EnrichFlowAction.shouldEnrich() evaluates the file
      Then the file is qualified for LLM enrichment

    @US041 @E003 @F016 @must @deferred
    Scenario: File qualifies because it is a complex entry point
      Given a file is an entry point (endpoint, scheduled task, event listener)
      And the entry point has ComplexityLevel >= STANDARD
      When EnrichFlowAction.shouldEnrich() evaluates the file
      Then the file is qualified for LLM enrichment

    @US041 @E003 @F016 @must @deferred
    Scenario: File does not qualify when no rule matches
      Given a file with no qualifying findings (no stored procedures, custom validators, complex SQL, unresolved links, test files, or complex entry points)
      When EnrichFlowAction.shouldEnrich() evaluates the file
      Then the file is not qualified
      And enrichment is skipped for this file

  Rule: Enrichment results are cached and reused across flows

    Scenario: Already-enriched file is skipped
      Given a file has been enriched in a previous flow within the same extract session
      When EnrichFlowAction encounters the file again
      Then the cached enrichment result is reused
      And no new LLM call is made
