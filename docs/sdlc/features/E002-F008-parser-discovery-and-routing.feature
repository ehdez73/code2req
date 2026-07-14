# Feature: Parser Discovery & Routing
# Epic: E002 — Language Extension Framework
# Feature ID: F008
# Stories: US020, US021
# Phase 1 draft generated: 2026-06-12
# Last updated: 2026-06-12 14:00

Feature: Parser Discovery & Routing
  A registry that maps file extensions to parsers. Discovers available parsers at startup and routes each source file to the correct parser.

  Background:
    Given the LanguageParser SPI is defined and at least one parser implementation exists

  Rule: Parser registration maps one or more file extensions to a single parser

    # ---------------------------------------------------------------------------
    # Story US020: Developer registers a new parser for a language
    # ---------------------------------------------------------------------------

    @US020 @E002 @F008 @should @draft
    Scenario: Developer registers a parser for .js files
      Given a JavaScript parser implementation exists
      When the developer registers it for the ".js" and ".mjs" extensions
      Then the parser appears in the registry for both extensions
      And the registry contains the mapping

    @US020 @E002 @F008 @should @draft
    Scenario: Developer registers a parser with an already-claimed extension
      Given a parser is already registered for ".js" files
      When a second parser attempts to register for ".js"
      Then the first parser remains registered
      And a warning is logged about the duplicate extension

  Rule: If no parser is registered for a file extension, the file is logged as unsupported and skipped

    # ---------------------------------------------------------------------------
    # Story US021: CLI routes files to the correct parser by extension
    # ---------------------------------------------------------------------------

    @US021 @E002 @F008 @should @draft
    Scenario: CLI finds a parser for a known file extension
      Given a scan target containing a ".js" file
      And a parser is registered for ".js" extension
      When the CLI routes the file
      Then the file is sent to the JavaScript parser for analysis

    @US021 @E002 @F008 @should @draft
    Scenario: CLI encounters a file with no registered parser
      Given a scan target containing a ".py" file
      And no parser is registered for ".py"
      When the CLI routes the file
      Then the file is logged as unsupported and skipped
      And the scan continues with other files

    @US021 @E002 @F008 @should @draft
    Scenario: CLI scans a mixed-language project
      Given a scan target containing ".java" and ".js" files
      And parsers are registered for both extensions
      When the CLI routes each file
      Then ".java" files go to the Java parser
      And ".js" files go to the JavaScript parser
