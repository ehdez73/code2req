# Feature: Index Output & SQLite Persistence
# Epic: E001 — Deterministic Multi-Language Indexing
# Feature ID: F005
# Stories: US013, US014
# Phase 1 draft generated: 2026-06-12
# Last updated: 2026-06-12

Feature: Index Output & SQLite Persistence
  Generate code-graph-index.json + SQLite task store with WAL mode and idempotent task IDs.

  Background:
    Given AST analysis and redaction have completed for all scan targets

  Rule: If the output directory is unwritable, CLI fails with a clear message rather than partial output

    # ---------------------------------------------------------------------------
    # Story US013: Developer produces structured analysis output
    # ---------------------------------------------------------------------------

    @US013 @E001 @F005 @must @draft
    Scenario: Developer scans a project and obtains the structured index
      Given a completed scan with findings from AST analysis and redaction
      When the CLI generates the output index
      Then a single structured JSON file is produced at the configured output path
      And the index contains all components, endpoints, listeners, validators, and scheduled tasks
      And the index uses a standard format

    @US013 @E001 @F005 @must @draft
    Scenario: Developer configures a custom output path
      Given a project-manifest.yaml specifying a custom output path
      When the CLI generates the output index
      Then the index is written to the custom path

    @US013 @E001 @F005 @must @draft
    Scenario: Developer scans with an unwritable output directory
      Given an output directory that does not exist or is not writable
      When the CLI attempts to write the index
      Then the CLI exits with a clear error message indicating the output path is unwritable

  Rule: Task IDs are deterministic SHA-256 hashes — never random

    # ---------------------------------------------------------------------------
    # Story US014: Developer persists scan state locally
    # ---------------------------------------------------------------------------

    @US014 @E001 @F005 @should @draft
    Scenario: Developer runs a scan and state is persisted to SQLite
      Given a completed scan with AST analysis results
      When the CLI persists results to the local database
      Then a SQLite database is created at the configured path
      And WAL journal mode and 5000ms busy timeout are configured on the connection
      And each processing task has a deterministic ID derived from file path, content hash, and configuration

    @US014 @E001 @F005 @should @draft
    Scenario: Developer re-runs scan against an unchanged workspace
      Given a previously scanned workspace with no file modifications
      When the CLI re-runs the scan
      Then the scan completes within 60 seconds
      And all task records are retrieved from the cache with no re-processing
