# Feature: Dependency Graph Resolution
# Epic: E001 — Deterministic Multi-Language Indexing
# Feature ID: F002
# Stories: US004, US005
# Phase 1 draft generated: 2026-06-12
# Last updated: 2026-06-12

Feature: Dependency Graph Resolution
  Optional Maven dependency resolution with graceful fallback to standalone heuristic mode when Maven is unavailable.

  Background:
    Given the manifest has been parsed and scan targets resolved

  Rule: Dependency resolution is optional — the pipeline never blocks or fails on Maven failure

    # ---------------------------------------------------------------------------
    # Story US004: Developer resolves project dependencies automatically
    # ---------------------------------------------------------------------------

    @US004 @E001 @F002 @should @draft
    Scenario: Developer scans a project with Maven available
      Given Maven is installed and pom.xml is present in the scan target
      When the CLI resolves project dependencies
      Then dependency metadata including group, artifact, and version coordinates is collected
      And resolution completes within 30 seconds

    @US004 @E001 @F002 @should @draft
    Scenario: Developer scans a project with partial dependency resolution failures
      Given Maven is available but one dependency fails to resolve
      When the CLI resolves project dependencies
      Then a warning is logged for the failed dependency
      And the remaining dependencies are still resolved
      And the pipeline continues without interruption

  Rule: If Maven is unavailable or pom.xml is missing, CLI proceeds with standalone heuristic mode and logs a clear warning

    # ---------------------------------------------------------------------------
    # Story US005: Developer operates without Maven available
    # ---------------------------------------------------------------------------

    @US005 @E001 @F002 @should @draft
    Scenario: Developer scans a project without Maven installed
      Given Maven is not installed on the system
      When the CLI attempts dependency resolution
      Then a clear warning is logged indicating Maven is unavailable
      And heuristic mode activates automatically
      And AST analysis proceeds with best-effort type resolution

    @US005 @E001 @F002 @should @draft
    Scenario: Developer scans a project with Maven present but resolution failure
      Given Maven is installed but dependency resolution fails entirely
      When the CLI attempts dependency resolution
      Then a warning is logged with the failure reason
      And heuristic mode activates automatically
      And the final summary notes that dependency metadata was unavailable
