# Feature: Project Configuration & Manifest Parsing
# Epic: E001 — Deterministic Multi-Language Indexing
# Feature ID: F001
# Stories: US001, US002, US003
# Phase 1 draft generated: 2026-06-12
# Last updated: 2026-06-12

Feature: Project Configuration & Manifest Parsing
  Parse project-manifest.yaml, resolve scan targets against the filesystem, validate configuration structure and required fields.

  Background:
    Given a valid project-manifest.yaml file exists in the working directory with at least one scan target defined
    And the project-manifest.yaml path is resolved from: user-provided argument, or fallback to current working directory

  Rule: All scan target paths must be accessible on the local filesystem — the CLI validates existence before processing

    # ---------------------------------------------------------------------------
    # Story US001: Developer defines scan targets in project manifest
    # ---------------------------------------------------------------------------

    @US001 @E001 @F001 @must @final
    Scenario: Developer configures a manifest with valid scan targets
      Given a project-manifest.yaml with a single scan target pointing to an existing directory
      When the CLI loads the manifest
      Then the manifest is accepted without errors
      And the scan target is registered for processing

    @US001 @E001 @F001 @must @final
    Scenario: Developer configures a manifest with multiple scan targets
      Given a project-manifest.yaml with three scan targets pointing to existing directories
      When the CLI loads the manifest
      Then all three targets are registered for processing

    @US001 @E001 @F001 @must @final
    Scenario: Developer provides a manifest with zero scan targets
      Given a project-manifest.yaml with an empty scan targets list
      When the CLI loads the manifest
      Then the CLI rejects the manifest with a message indicating at least one target is required

  Rule: The manifest schema must conform to the expected YAML structure; unrecognized fields produce warnings but don't block execution

    # ---------------------------------------------------------------------------
    # Story US002: Developer validates manifest before scanning
    # ---------------------------------------------------------------------------

    @US002 @E001 @F001 @should @final
    Scenario: Developer validates a well-formed manifest
      Given a project-manifest.yaml with valid scan targets and no structural issues
      When the developer runs manifest validation
      Then validation passes with no errors

    @US002 @E001 @F001 @should @final
    Scenario: Developer validates a manifest with invalid YAML syntax
      Given a project-manifest.yaml with malformed YAML content
      When the developer runs manifest validation
      Then validation fails with an error message containing the line number and parse detail

    @US002 @E001 @F001 @should @final
    Scenario: Developer validates a manifest with unrecognized fields
      Given a project-manifest.yaml with an unknown field "custom-option"
      When the developer runs manifest validation
      Then validation passes with a warning about the unrecognized field

    @US002 @E001 @F001 @should @final
    Scenario Outline: Developer validates a manifest with missing required fields
      Given a project-manifest.yaml missing the required field "<field>"
      When the developer runs manifest validation
      Then validation fails with an error identifying "<field>" as missing

      Examples:
        | field        |
        | targets |

  Rule: If a scan target directory lacks Java source files, the CLI logs a warning and skips it rather than failing

    # ---------------------------------------------------------------------------
    # Story US003: Developer recovers gracefully from manifest errors
    # ---------------------------------------------------------------------------

    @US003 @E001 @F001 @should @final
    Scenario: Developer runs scan with a mix of valid and invalid targets
      Given a project-manifest.yaml with one valid target and one target path that does not exist
      When the CLI runs the scan
      Then the valid target is processed
      And the CLI logs a warning for the invalid target including the path and a suggested fix
      And the final summary reports targets succeeded, warned, and failed

    @US003 @E001 @F001 @should @final
    Scenario: Developer runs scan with a target that has no Java files
      Given a project-manifest.yaml with a target directory containing only non-Java files
      When the CLI runs the scan
      Then the target is skipped with a logged warning
      And the scan continues with other targets

    @US003 @E001 @F001 @should @final
    Scenario: Developer runs scan with all invalid targets
      Given a project-manifest.yaml where all target paths are invalid
      When the CLI runs the scan
      Then the CLI reports all targets failed
      And the scan exits without producing an index

  # No error path — manifest existence is a prerequisite, not validated here
