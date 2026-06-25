# Feature: Review CLI Command — AWAITING_HUMAN_REVIEW lifecycle
# Epic: E004 — Agentic Functional Requirement Extraction
# Feature ID: F026
# Stories: US055
# Phase 3 draft generated: 2026-06-25
# Last updated: 2026-06-25

Feature: Review CLI Command
  The review command lets developers inspect and resolve tasks and flows flagged as AWAITING_HUMAN_REVIEW, providing a single workflow for diagnosis, acceptance, and reset.

  Background:
    Given the pipeline has completed with AWAITING_HUMAN_REVIEW tasks in SQLite
    And HUMAN_REVIEW_REASON findings exist with reason_type, detail, and confidence

  Rule: The review command groups and displays quarantined tasks by reason type

    @US055 @E004 @F026 @should @draft
    Scenario: review list shows all quarantined tasks grouped by reason
      Given 3 HOP_DEPTH tasks and 2 STEPS_EXCEEDED tasks exist
      When the developer runs "review list"
      Then output shows "HOP_DEPTH" section with 3 tasks
      And output shows "STEPS_EXCEEDED" section with 2 tasks
      And each entry shows flow name, detail, confidence, and source file

    @US055 @E004 @F026 @should @draft
    Scenario: review show displays full quarantine context
      Given a task is AWAITING_HUMAN_REVIEW with a HUMAN_REVIEW_REASON finding
      When the developer runs "review show --task <id>"
      Then the output includes the reason_type, detail JSON, and confidence score
      And the output includes the task file path and source trace chain
      And the output includes related execution_findings for the task

  Rule: The developer can accept or reset quarantined tasks

    @US055 @E004 @F026 @should @draft
    Scenario: review accept documents gap and resets to INDEXED
      Given a task is AWAITING_HUMAN_REVIEW
      When the developer runs "review accept --task <id>"
      Then the task status is changed to INDEXED
      And the HUMAN_REVIEW_REASON finding is preserved
      And the flow appears in spec Section 5 as explicitly unresolved

    @US055 @E004 @F026 @should @draft
    Scenario: review reset deletes findings and resets to INDEXED
      Given a task is AWAITING_HUMAN_REVIEW with HUMAN_REVIEW_REASON findings
      When the developer runs "review reset --task <id>"
      Then the HUMAN_REVIEW_REASON findings are deleted
      And the task status is changed to INDEXED
      And the next run re-qualifies the task via planner

    @US055 @E004 @F026 @should @draft
    Scenario: batch accept-all accepts all quarantined flows
      Given 5 tasks are AWAITING_HUMAN_REVIEW
      When the developer runs "review accept-all"
      Then all 5 tasks are changed to INDEXED
      And all HUMAN_REVIEW_REASON findings are preserved for spec output

    @US055 @E004 @F026 @should @draft
    Scenario: batch reset-all resets all for re-processing
      Given 5 tasks are AWAITING_HUMAN_REVIEW
      When the developer runs "review reset-all"
      Then all 5 tasks are changed to INDEXED
      And all HUMAN_REVIEW_REASON findings are deleted
