# Feature: Quality Audit — Post-Agent Validation
# Epic: E004 — Agentic Functional Requirement Extraction
# Feature ID: F024
# Stories: US052
# Phase 3 draft generated: 2026-06-17
# Last updated: 2026-06-17

Feature: Quality Audit
  Post-agent validation pass that samples extracted requirements against raw source files to verify correctness.

  Background:
    Given the Embabel agent has completed
    And spec-output/ contains the functional specification

  Rule: The quality audit samples a configurable percentage of requirements and validates against source files

    @US052 @E004 @F024 @should @draft
    Scenario: Audit samples 20% of extracted requirements by default
      Given 100 functional requirements have been extracted
      And semantic-validation-sample-rate is set to 0.20
      When the quality audit runs
      Then 20 requirements are randomly sampled
      And each sample is validated against the raw source file

    @US052 @E004 @F024 @should @draft
    Scenario: Audit passes when sample meets ≥92% pass rate
      Given 20 sampled requirements
      And 19 of 20 match the source file behavior
      When the quality audit evaluates results
      Then the pass rate is 95%
      And the audit passes
      And the output artifacts are finalized

    @US052 @E004 @F024 @should @draft
    Scenario: Audit fails when sample rate drops below 92%
      Given 20 sampled requirements
      And only 17 of 20 match the source file behavior
      When the quality audit evaluates results
      Then the pass rate is 85%
      And the batch is flagged for human review
      And the sample rate is increased to 100% for next run

    @US052 @E004 @F024 @should @draft
    Scenario: Audit report is written to spec-output
      Given the quality audit has completed
      When the report is generated
      Then spec-output/audit-report.md contains pass rate and sample details
