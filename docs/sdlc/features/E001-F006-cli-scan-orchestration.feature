# Feature: CLI Scan Orchestration
# Epic: E001 — Deterministic Multi-Language Indexing
# Feature ID: F006
# Stories: US015, US016, US017
# Phase 1 draft generated: 2026-06-12
# Last updated: 2026-06-12 14:00

Feature: CLI Scan Orchestration
  Phase 1 scan command that orchestrates the full pipeline end-to-end.

  Background:
    Given all configuration has been validated

  Rule: The scan command must never make network calls or LLM interactions (Phase 1 only)

    # ---------------------------------------------------------------------------
    # Story US015: Developer runs a full Phase 1 scan
    # ---------------------------------------------------------------------------

    @US015 @E001 @F006 @must @draft
    Scenario: Developer runs a complete Phase 1 scan successfully
      Given a valid project-manifest.yaml with existing scan targets
      When the developer runs the scan command
      Then the pipeline executes in order: manifest parsing, dependency resolution, AST analysis, redaction, and persistence
      And progress is reported per stage with file counts and elapsed time
      And the scan produces a JSON index and a populated SQLite database
      And the CLI exits with a zero exit code

    @US015 @E001 @F006 @must @draft
    Scenario: Developer runs a scan where a stage fails
      Given a project-manifest.yaml that passes validation
      When the scan encounters a failure in the AST analysis stage
      Then the CLI reports which stage failed
      And the CLI exits with a non-zero exit code
      And no partial output is generated

    @US015 @E001 @F006 @must @draft
    Scenario: Developer runs a scan with zero valid targets
      Given a project-manifest.yaml where all targets are invalid or have no Java files
      When the developer runs the scan command
      Then the CLI exits early with a descriptive message
      And no index or database is produced

    @US015 @E001 @F006 @must @draft
    Scenario: Developer resumes a scan after interruption
      Given a previously interrupted scan with orphaned tasks in the SQLite store
      When the developer runs the resume command
      Then orphaned RUNNING tasks are reconciled
      And the scan resumes from the recovered state
      And no already-completed files are re-processed

  # ---------------------------------------------------------------------------
  # Story US016: Tech Lead confirms offline operation
  # ---------------------------------------------------------------------------

  Rule: Phase 1 operation is entirely offline and uses only local resources

    @US016 @E001 @F006 @should @draft
    Scenario: Tech Lead verifies Phase 1 completes without network access
      Given a valid project-manifest.yaml with existing scan targets
      And the machine has no outbound internet access
      When the developer runs the scan command
      Then the full pipeline completes successfully
      And zero outbound HTTP connections are made

    @US016 @E001 @F006 @should @draft
    Scenario: Tech Lead verifies Phase 1 requires no LLM credentials
      Given a valid project-manifest.yaml with existing scan targets
      And no LLM provider credentials are configured
      When the developer runs the scan command
      Then the full pipeline completes successfully
      And no LLM credentials are checked or required

    @US016 @E001 @F006 @should @draft
    Scenario: Tech Lead verifies Phase 1 produces deterministic output
      Given a valid project-manifest.yaml with existing scan targets
      When the developer runs the scan command twice on the same workspace
      Then both runs produce identical output

  # ---------------------------------------------------------------------------
  # Story US028: Developer cleans all local data
  # ---------------------------------------------------------------------------

  Rule: The clean command removes all local cached state

    @US028 @E001 @F006 @should @draft
    Scenario: Developer cleans a populated store
      Given a workspace with a populated task store and an existing index output file
      When the developer runs the clean command
      Then the tasks table is empty
      And the index output file is removed
      And the spec output directory is removed if empty

    @US028 @E001 @F006 @should @draft
    Scenario: Developer cleans an empty store
      Given a workspace with no tasks and no output files
      When the developer runs the clean command
      Then the CLI reports a graceful message
      And no errors occur

    @US028 @E001 @F006 @should @draft
    Scenario: Developer cleans with a custom manifest path
      Given a project-manifest.yaml with custom output directory paths
      When the developer runs the clean --manifest project-manifest.yaml command
      Then the custom output files are removed
      And the tasks table is cleaned
