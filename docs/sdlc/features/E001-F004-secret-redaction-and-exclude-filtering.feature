# Feature: Secret Redaction & Exclude Filtering
# Epic: E001 — Deterministic Multi-Language Indexing
# Feature ID: F004
# Stories: US011, US012
# Phase 1 draft generated: 2026-06-12
# Last updated: 2026-06-12

Feature: Secret Redaction & Exclude Filtering
  In-memory redaction of passwords/keys/credentials before any LLM processing + exclude pattern filtering for generated/vendor code.

  Background:
    Given source files have been parsed and content is prepared for output

  Rule: Redaction operates in-memory only — source files on disk are never touched

    # ---------------------------------------------------------------------------
    # Story US011: Developer prevents secret leakage
    # ---------------------------------------------------------------------------

    @US011 @E001 @F004 @must @final
    Scenario: Developer scans a project containing hardcoded passwords
      Given a Java source file containing a hardcoded password assignment
      When the CLI redacts secrets before output
      Then the password value is replaced with [REDACTED:password]
      And the original source file on disk remains unchanged

    @US011 @E001 @F004 @must @final
    Scenario: Developer scans a project containing API keys and tokens
      Given a Java source file containing an API key and an authentication token
      When the CLI redacts secrets before output
      Then the API key is replaced with [REDACTED:api_key]
      And the token is replaced with [REDACTED:token]

    @US011 @E001 @F004 @must @final
    Scenario: Developer scans a project with mixed secret and non-secret content
      Given a Java source file containing both hardcoded credentials and regular configuration values
      When the CLI redacts secrets before output
      Then the credentials are replaced with [REDACTED:type] placeholders
      And the non-secret values are preserved unchanged

  Rule: Exclude patterns follow .gitignore-style glob matching against file paths; generated code and vendor packages are excluded by default

    # ---------------------------------------------------------------------------
    # Story US012: Developer filters out generated and vendor code
    # ---------------------------------------------------------------------------

    @US012 @E001 @F004 @should @final
    Scenario: Developer scans a project with generated source directories
      Given a scan target containing a target/ directory with generated sources
      When the CLI applies exclude filters
      Then the generated files under target/ are excluded from analysis
      And the exclusion is reported in the scan summary

    @US012 @E001 @F004 @should @final
    Scenario: Developer overrides default exclude patterns via manifest
      Given a project-manifest.yaml that overrides the default exclude patterns
      When the CLI applies exclude filters
      Then the custom exclude patterns are used instead of the defaults

    @US012 @E001 @F004 @should @final
    Scenario: Developer scans a project with no generated or vendor code
      Given a scan target with only hand-written Java source files
      When the CLI applies exclude filters
      Then no files are excluded
      And the scan summary reports zero exclusions
