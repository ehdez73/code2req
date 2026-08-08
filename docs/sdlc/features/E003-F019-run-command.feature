# Feature: CLI Commands — run
# Epic: E003 — Semantic Enrichment
# Feature ID: F019
# Stories: US046, US047
# Last updated: 2026-08-08

Feature: CLI Commands — run
  The run command orchestrates the full pipeline: scan → extract → generate. No standalone plan/enrich commands exist — enrichment runs embedded in extract via EnrichFlowAction.

  Background:
    Given Phase 1 indexing completed successfully
    And the SQLite store contains tasks with Phase 1 findings

  Rule: The run command orchestrates scan → extract → generate end-to-end

    @US047 @E003 @F019 @must @modified
    Scenario: Developer runs full pipeline
      Given scan targets exist in project-manifest.yaml
      When the developer runs run --manifest project-manifest.yaml
      Then scan indexes all source files
      And extract traces flows, enriches qualifying files, and analyzes requirements
      And generate produces spec-output/ specification documents

    @US047 @E003 @F019 @must @modified
    Scenario: Developer runs with --dry-run for simulation
      Given no LLM credentials are configured
      When the developer runs run --dry-run --manifest project-manifest.yaml
      Then scan runs normally (Phase 1 is offline)
      And extract uses stubs for LLM calls — zero API spend
      And generate produces spec-output/ from simulated results

    @US047 @E003 @F019 @must @modified
    Scenario: Developer runs with --force to overwrite existing cache
      Given extraction-cache.json already exists from a previous extract
      When the developer runs run --force
      Then extract deletes cached FLOW_ANALYSIS findings and re-processes all entry points
      And generate overwrites existing spec-output files

    @US047 @E003 @F019 @must @modified
    Scenario: Developer checks status with flow extraction metrics
      Given extract has completed
      When the developer runs status
      Then the output shows task counts by status
      And shows flow count from the extraction cache

  Rule: Per-flow extraction is available via extract --flow

    @US047 @E003 @F019 @must @modified
    Scenario: Developer re-extracts a single flow
      Given a flow exists in the extraction cache
      When the developer runs extract --flow <short-id>
      Then only that flow is re-traced and re-analyzed
      And the result is merged into the existing extraction cache
      And other flows and features are preserved
