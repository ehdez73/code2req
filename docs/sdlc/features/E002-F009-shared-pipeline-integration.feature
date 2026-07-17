# Feature: Shared Pipeline Integration
# Epic: E002 — Language Extension Framework
# Feature ID: F009
# Status: POSTPONED — E002 is formally de-scoped.
# Stories: US022
# Phase 1 draft generated: 2026-06-12
# Last updated: 2026-07-17

Feature: Shared Pipeline Integration
  Wire the parser SPI into the existing pipeline so that redaction, exclude filtering, output generation, and orchestration work with any parser's output without modification.

  Background:
    Given the parser SPI, discovery, and at least one parser implementation exist

  Rule: Adding a new language parser must not require changes to the core pipeline

    # ---------------------------------------------------------------------------
    # Story US022: Developer adds a parser without changing the pipeline
    # ---------------------------------------------------------------------------

    @US022 @E002 @F009 @should @draft
    Scenario: Developer adds a Python parser and runs a scan
      Given a Python parser implementing the LanguageParser interface
      And no changes have been made to the core pipeline code
      When the developer runs the scan command against a Python codebase
      Then the pipeline processes the parser output through redaction
      And the pipeline writes the output to the JSON index
      And the pipeline stores results in SQLite
      And no pipeline code was modified to support the new language

    @US022 @E002 @F009 @should @draft
    Scenario: Pipeline handles parser output with language-specific metadata
      Given a parser that returns language-specific metadata in optional fields
      When the pipeline processes the output
      Then the shared output schema accommodates the language-specific fields
      And the JSON index includes both standard and language-specific fields
      And standard pipeline features (redaction, exclude, persistence) are unaffected

    @US022 @E002 @F009 @should @draft
    Scenario: Pipeline handles a parser that returns empty results
      Given a parser that found no components, endpoints, or other artifacts
      When the pipeline processes the empty output
      Then the pipeline does not crash or produce corrupt output
      And the JSON index contains an empty results section for that parser
      And the scan summary reports zero findings for that language
