# Feature: Java Source AST Analysis
# Epic: E001 — Deterministic Multi-Language Indexing
# Feature ID: F003
# Stories: US006, US007, US008, US009, US010
# Phase 1 draft generated: 2026-06-12
# Last updated: 2026-06-12

Feature: Java Source AST Analysis
  JavaParser-based scanning: component types, HTTP endpoints, event listeners, method calls, custom validators, and scheduled tasks.

  Background:
    Given scan target directories are resolved and accessible
    And only .java files under scan targets are processed

  Rule: JavaParser processes each file independently — a single parse failure does not stop the full scan

    # ---------------------------------------------------------------------------
    # Story US006: Developer discovers application components
    # ---------------------------------------------------------------------------

    @US006 @E001 @F003 @must @draft
    Scenario: Developer scans a project with Spring stereotyped classes
      Given a scan target containing @Controller, @Service, @Repository, and @Component classes
      When the CLI analyzes the Java source files
      Then each Spring stereotype is classified with its annotation type
      And the component inventory includes all identified classes

    @US006 @E001 @F003 @must @draft
    Scenario: Developer scans a file that fails to parse
      Given a scan target containing a Java file with syntax errors
      When the CLI analyzes the Java source files
      Then the failed file is logged with its path and parse error
      And other files in the target continue processing

    @US006 @E001 @F003 @must @draft
    Scenario: Developer scans a project with plain Java classes
      Given a scan target containing classes without Spring stereotypes
      When the CLI analyzes the Java source files
      Then each class is classified as "other" with its package and name

  Rule: Component classification uses annotation presence as the primary heuristic, with structural fallback when type resolution fails

    # ---------------------------------------------------------------------------
    # Story US007: Developer maps HTTP endpoints
    # ---------------------------------------------------------------------------

    @US007 @E001 @F003 @must @draft
    Scenario: Developer scans a controller with multiple endpoint mappings
      Given a @RestController with @GetMapping, @PostMapping, and @RequestMapping methods
      When the CLI extracts HTTP endpoints
      Then each endpoint is captured with its HTTP method and full path
      And class-level and method-level path segments are combined
      And each endpoint is linked to its controller component

    @US007 @E001 @F003 @must @draft
    Scenario: Developer scans a controller with path variables and query parameters
      Given a @RestController method containing @PathVariable and @RequestParam annotations
      When the CLI extracts HTTP endpoints
      Then the path variables and query parameters are captured with the endpoint
      And the endpoint path includes the path variable segment

    @US007 @E001 @F003 @must @draft
    Scenario: Developer scans a controller with no request mappings
      Given a @Controller class with no request mapping annotations on its methods
      When the CLI extracts HTTP endpoints
      Then the class is noted as a controller with zero endpoints
      And no error is raised

    # ---------------------------------------------------------------------------
    # Story US008: Developer traces event listeners
    # ---------------------------------------------------------------------------

    @US008 @E001 @F003 @should @draft
    Scenario: Developer scans a project with @EventListener methods
      Given a component with @EventListener-annotated methods
      When the CLI extracts event listeners
      Then each listener is identified with its event type
      And the listener is linked to its owning component

    @US008 @E001 @F003 @should @draft
    Scenario: Developer scans event handlers with method call chains
      Given an event listener method that calls other service methods
      When the CLI extracts call chains from event handlers
      Then method calls within the handler are captured up to 3 levels deep
      And each call is recorded with target type and method name

    @US008 @E001 @F003 @should @draft
    Scenario: Developer scans a project with ApplicationEventPublisher usage
      Given a component that uses ApplicationEventPublisher to publish events
      When the CLI identifies event publishers
      Then the publisher is linked to the events it publishes

    # ---------------------------------------------------------------------------
    # Story US009: Developer extracts custom validation logic
    # ---------------------------------------------------------------------------

    @US009 @E001 @F003 @should @draft
    Scenario: Developer scans a project with custom @Constraint validators
      Given a field annotated with a custom @Constraint validator
      When the CLI extracts validation logic
      Then the validator class is captured and linked to the field
      And the isValid method body is extracted as a text slice

    @US009 @E001 @F003 @should @draft
    Scenario: Developer scans a project with built-in Jakarta validators
      Given a field annotated with @NotNull or @Size
      When the CLI extracts validation logic
      Then the built-in annotation is noted
      And no isValid body extraction is attempted

    # ---------------------------------------------------------------------------
    # Story US010: Developer discovers scheduled tasks
    # ---------------------------------------------------------------------------

    @US010 @E001 @F003 @should @draft
    Scenario: Developer scans a component with cron-based @Scheduled methods
      Given a component with a @Scheduled method using a cron expression
      When the CLI extracts scheduled tasks
      Then the method is captured with its cron expression
      And the task is categorized as cron-based
      And the method is linked to its owning component

    @US010 @E001 @F003 @should @draft
    Scenario: Developer scans a component with fixed-rate and fixed-delay tasks
      Given a component with @Scheduled methods using fixedRate and fixedDelay
      When the CLI extracts scheduled tasks
      Then each method is captured with its rate or delay value
      And the tasks are categorized as fixed-rate and fixed-delay respectively

  # No error path — parse failures are handled at the file level by US006
