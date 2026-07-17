# Feature: Phase 2 Orchestrator — Enrichment DAG Management
# Epic: E003 — Semantic Enrichment
# Feature ID: F018
# Stories: US045
# Phase 2 draft generated: 2026-06-17
# Last updated: 2026-06-17

Feature: Phase 2 Orchestrator
  Manages the enrichment DAG, submits tasks asynchronously via CompletableFuture, tracks progress via CompletableFuture, and handles dynamic re-planning.

  Background:
    Given the planner has produced qualification decisions for all tasks
    And the thread pool is configured with core=5, max=10

  Rule: The orchestrator submits qualified tasks to the async pool and blocks Phase 3 until all complete

    @US045 @E003 @F018 @must @final
    Scenario: Orchestrator submits all qualified tasks
      Given 10 qualified tasks from the planner
      When the orchestrator starts Phase 2
      Then all 10 tasks are submitted to the async pool
      And each submission returns a CompletableFuture<ExecutionFinding>

    @US045 @E003 @F018 @must @final
    Scenario: Orchestrator barrier blocks Phase 3 until all tasks complete
      Given 10 qualified tasks submitted to the async pool
      When 5 tasks are still running
      Then the Phase 2→3 barrier prevents Phase 3 from starting
      When all 10 tasks complete
      Then the barrier is released
      And Phase 3 proceeds

    @US045 @E003 @F018 @must @final
    Scenario: Orchestrator respects max hop depth
      Given a discovery sequence reaches 4 hops from the root entry point
      And max-discovery-depth is set to 3
      When the orchestrator evaluates the branch
      Then the branch is frozen
      And the task status is set to AWAITING_HUMAN_REVIEW
      And an alert is logged

    @US045 @E003 @F018 @must @final
    Scenario: Orchestrator handles dynamic re-planning for discovered dependency
      Given a running executor discovers a new dependency
      When the orchestrator processes the discovered_dependency
      Then only the affected branch is paused
      And the new dependency is registered in SQLite as PENDING
      And the new task is submitted to the async pool
      When the new task completes
      Then the original branch resumes

    @US045 @E003 @F018 @should @final
    Scenario: Orchestrator prevents redundant evaluation via visited registry
      Given a task hash already marked as RUNNING
      When a redundant evaluation request arrives for the same hash
      Then the request is discarded
      And a warning is logged

    @US045 @E003 @F018 @should @final
    Scenario: Orchestrator writes Phase 2 metrics after completion
      Given all Phase 2 tasks have completed
      When the orchestrator finalizes Phase 2
      Then a metrics record is written with tokens consumed and cost estimate

    @US045 @E003 @F018 @should @final
    Scenario: run --resume recovers AWAITING_HUMAN_REVIEW tasks
      Given 2 tasks are in AWAITING_HUMAN_REVIEW state
      When the run --resume recovery executes
      Then both tasks are changed to INDEXED
      And their findings are cleaned
      And the planner re-qualifies them on next run
