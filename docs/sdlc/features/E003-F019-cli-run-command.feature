# Feature: CLI Commands — plan and run
# Epic: E003 — Semantic Enrichment
# Feature ID: F019
# Stories: US046, US047
# Phase 2 draft generated: 2026-06-17
# Last updated: 2026-06-17

Feature: CLI Commands — plan and run
  The plan command displays the execution DAG without running. The run command orchestrates Phase 2 then Phase 3 with configurable thresholds and dry-run mode.

  Background:
    Given Phase 1 indexing completed successfully
    And the SQLite store contains tasks with Phase 1 findings

  Rule: The plan command shows the execution DAG with zero LLM calls

    @US046 @E003 @F019 @must @draft
    Scenario: Developer runs plan command and sees qualified tasks
      Given 8 qualified tasks and 12 non-qualified tasks exist in SQLite
      When the developer runs plan --manifest project-manifest.yaml
      Then the 8 qualified tasks are displayed
      And each task shows its qualification reason
      And the 12 non-qualified tasks are listed with "NONE" reason
      And zero LLM calls are made

    @US046 @E003 @F019 @must @draft
    Scenario: Developer runs plan with zero qualified tasks
      Given no tasks qualify for LLM enrichment
      When the developer runs plan --manifest project-manifest.yaml
      Then the output indicates no tasks qualified
      And suggests running with lower llm-threshold if needed

  Rule: The run command orchestrates Phase 2 then Phase 3 end-to-end

    @US047 @E003 @F019 @must @draft
    Scenario: Developer runs full pipeline end-to-end
      Given qualified tasks exist in SQLite
      When the developer runs run --manifest project-manifest.yaml
      Then Phase 2 runs: Planner → Executors → Orchestrator with barrier
      And Phase 3 runs after the barrier is released
      And spec-output/ contains specification documents

    @US047 @E003 @F019 @must @draft
    Scenario: Developer runs with --llm-threshold 0 to skip Phase 2
      Given qualified tasks exist in SQLite
      When the developer runs run --llm-threshold 0
      Then Phase 2 is skipped entirely
      And Phase 3 runs on structural data only

    @US047 @E003 @F019 @must @draft
    Scenario: Developer runs with --dry-run for simulation
      Given no LLM credentials are configured
      When the developer runs run --dry-run --manifest project-manifest.yaml
      Then Phase 2 executors use stubs returning deterministic JSON
      And no API calls are made
      And spec-output/ contains simulated output

    @US047 @E003 @F019 @must @draft
    Scenario: Developer checks status with Phase 2 counters
      Given Phase 2 has completed some tasks
      When the developer runs status
      Then the output shows enriched task count
      And shows tokens consumed and estimated cost
      And shows pending and completed counts

    @US047 @E003 @F019 @must @draft
    Scenario: Developer recovers AWAITING_HUMAN_REVIEW tasks with --resume
      Given 2 tasks are AWAITING_HUMAN_REVIEW
      And the Phase 3 marker is stuck on ENRICHING
      When the developer runs run --resume
      Then recoverOrphanedTasks resets AWAITING_HUMAN_REVIEW tasks to INDEXED
      And recoverPhase3Marker resets the Phase 3 marker from ENRICHING to PENDING
      And stale .tmp.* output files are cleaned
      And Phase 3 re-runs from scratch

    @US047 @E003 @F019 @must @draft
    Scenario: Developer sees Phase 3 skip guard when already complete
      Given the Phase 3 marker is ENRICHED
      When the developer runs run without --force-phase3
      Then Phase 3 is skipped
      And the output shows "Phase 3 already completed (use --force-phase3 to re-run)"

    @US047 @E003 @F019 @must @draft
    Scenario: Developer forces Phase 3 re-run with --force-phase3
      Given the Phase 3 marker is ENRICHED
      When the developer runs run --force-phase3
      Then the marker is reset to PENDING
      And Phase 3 executes from scratch
      And existing spec-output/ files are overwritten
