# Feature: Quality Audit — Post-Agent Validation (Simplified)
# Epic: E004 — Agentic Functional Requirement Extraction
# Feature ID: F024
# Stories: US052
# Phase 3 simplified: 2026-06-26

Feature: Quality Audit
  Post-agent validation pass that flags resolved and quarantined flows in the output, verifying that all output artifacts conform to the defined JSON schema.

  Background:
    Given the Embabel agent has completed
    And spec-output/ contains the functional specification

  Rule: The quality audit validates structural conformance and flags quarantined flows

    @US052 @E004 @F024 @should @draft
    Scenario: Audit validates JSON schema conformance
      Given the semantic_manifest.json has been written
      When the quality audit runs
      Then the manifest is validated against the PRD §6.2 JSON schema
      And any schema violations are logged as errors
      And the Phase 3 marker is set to FAILED if validation fails

    @US052 @E004 @F024 @should @draft
    Scenario: Audit ensures quarantined flows appear in spec output
      Given there are flows marked as AWAITING_HUMAN_REVIEW
      When the quality audit runs
      Then each quarantined flow appears in spec Section 5 (Unresolved Dependencies)
      And each quarantined flow has review_required: true in the manifest
