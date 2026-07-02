# Feature: LLM Executor Framework — Per-file Semantic Enrichment
# Epic: E003 — Semantic Enrichment
# Feature ID: F017
# Stories: US043, US044, US063
# Phase 2 draft generated: 2026-06-17
# Last updated: 2026-06-17

Feature: LLM Executor Framework
  Individual file enrichment workers that receive pre-resolved structural context and source code, then produce validated ExecutionFinding JSON.

  Background:
    Given the planner has qualified a task for enrichment
    And the task has pre-resolved structural context from Phase 1 (call graph edges, endpoint info, database access, link registrations)
    And the raw source file content is available

  Rule: The executor calls the LLM with the structural context and source file, producing a validated ExecutionFinding

    @US043 @E003 @F017 @must @draft
    Scenario: Executor enriches a single file successfully
      Given a qualified task with full structural context
      When the executor processes the file
      Then the LLM receives a prompt containing structural context and source code
      And the prompt instructs the model not to resolve structural dependencies
      And the response is parsed as ExecutionFinding JSON
      And the JSON validates against the §4 schema
      And the finding is persisted to execution_findings with type SEMANTIC_ENRICHMENT
      And the task transitions to SUCCESS

    @US043 @E003 @F017 @must @draft
    Scenario: Executor detects a discovered dependency
      Given a qualified task with a runtime dependency not in the index
      When the executor processes the file
      Then the ExecutionFinding contains a discovered_dependencies array
      And the new dependency is registered in SQLite as PENDING

    @US043 @E003 @F017 @must @draft
    Scenario: Executor fails on invalid JSON Schema output
      Given the LLM returns a response missing the required "business_abstraction" field
      When the executor validates the output
      Then validation fails with a structural error
      And the task transitions to FAILED
      And the error is logged with schema violation details

    @US043 @E003 @F017 @must @draft
    Scenario: Executor retries on HTTP 429 rate limit
      Given the first LLM call returns HTTP 429
      When the executor applies exponential backoff
      Then the call is retried after initial delay of 2s
      And the delay doubles on each subsequent retry
      And after 3 retries, if still 429, the task transitions to FAILED

    @US043 @E003 @F017 @should @draft
    Scenario: Executor triggers pre-summarization when context exceeds 80% window
      Given the combined token weight (source + test + validators) exceeds 80% of model context window
      When the executor applies context budgeting
      Then companion logic slices are pre-summarized by the LLM
      And the summary is attached under "COMPANION CUSTOM VALIDATORS" section
      And the final prompt is within the model's context window

  Rule: Dry-run mode returns deterministic static JSON without calling any LLM

    @US044 @E003 @F017 @should @draft
    Scenario: Executor in dry-run mode returns deterministic output
      Given dry-run mode is active
      When the executor processes the file
      Then no LLM call is made
      And the output matches the expected static ExecutionFinding JSON
      And the finding is persisted to execution_findings with type SEMANTIC_ENRICHMENT
      And the task transitions to SUCCESS

  Rule: Invalid LLM responses trigger error-feedback retry

    @US063 @E003 @F017 @should @draft
    Scenario: Invalid JSON response triggers retry with error-feedback
      Given the LLM returns a response that fails JSON parsing
      When the executor processes the file
      Then the executor retries with an error-feedback prompt containing the failed JSON and parse error

    @US063 @E003 @F017 @should @draft
    Scenario: Retry exhausted transitions task to FAILED
      Given the LLM consistently returns invalid JSON
      When all 3 retry attempts are exhausted
      Then the task transitions to FAILED
      And the failure is recorded in execution_findings
