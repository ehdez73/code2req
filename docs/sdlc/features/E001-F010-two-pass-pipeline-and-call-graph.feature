# Feature: Two-Pass Pipeline Orchestration & Call Graph Resolution
# Epic: E001 — Deterministic Multi-Language Indexing
# Feature ID: F010
# Stories: US030, US036
# Phase 1 draft generated: 2026-06-15
# Last updated: 2026-06-15

Feature: Two-Pass Pipeline Orchestration & Call Graph Resolution
  The ScanCommand pipeline is refactored to two-pass orchestration:
  Pass 1 collects declarations into GlobalDeclarationRegistry,
  Pass 2 resolves against the registry, Post-Pass runs link resolvers.

  Rule: Pass 1 collects all declarations before any resolution runs

    @US036 @E001 @F010 @must @draft
    Scenario: All files are parsed in Pass 1 before Pass 2 begins
      Given N source files in the scan target
      When Pass 1 executes across all files
      Then every file is visited by Pass1DeclarationCollector
      And the GlobalDeclarationRegistry contains declarations from all N files
      And no resolution or analysis is performed during Pass 1

    @US036 @E001 @F010 @must @draft
    Scenario: Pass 2 runs full visitor suite against populated registry
      Given the GlobalDeclarationRegistry is populated from Pass 1
      When Pass 2 executes across all files
      Then ComponentVisitor, EndpointVisitor, and all companion visitors run
      And each visitor has access to the complete registry
      And resolution visitors resolve method calls against the registry

  Rule: Existing tests pass unchanged after refactoring

    @US036 @E001 @F010 @should @draft
    Scenario: All existing F003 and F006 tests pass after refactoring
      Given the codebase has been refactored to two-pass orchestration
      When the full test suite is executed
      Then all existing F003 tests pass
      And all existing F006 tests pass
      And no existing test assertions are modified

  Rule: Post-pass runs after all files are processed

    @US036 @E001 @F010 @should @draft
    Scenario: Post-pass resolvers run after all files analyzed
      Given Pass 2 has completed for all files
      When the post-pass phase executes
      Then TopicLinkResolver matches producers to consumers
      And FloatingLinkResolver registers unmatched HTTP calls
      And results are written to the scan output

  Rule: Graceful degradation on parse errors

    @US036 @E001 @F010 @should @draft
    Scenario: Parse error in one file does not block other files
      Given one source file has a syntax error
      When Pass 1 runs
      Then the file with the error is skipped with a warning
      And all other files are successfully collected into the registry
      And Pass 2 proceeds for all successfully parsed files

  Rule: Empty codebase produces empty output

    @US036 @E001 @F010 @should @draft
    Scenario: Empty scan target produces empty registry
      Given a scan target with zero source files
      When Pass 1 runs
      Then the GlobalDeclarationRegistry is empty
      And Pass 2 produces no findings
      And IndexWriter outputs valid JSON with no entries

  Background:
    Given Pass 1 has collected all method declarations into the GlobalDeclarationRegistry
    And Pass 2 visitors have access to the populated registry

  Rule: Direct method calls on known field types are resolved against the registry

    @US030 @E001 @F010 @must @draft
    Scenario: Controller calls service method via injected field
      Given a @RestController with an @Autowired OrderService field
      And OrderService declares createOrder(OrderDto)
      When the controller method calls service.createOrder(dto)
      Then a call graph edge is recorded from the controller to OrderService.createOrder
      And the edge includes source file, target file, and method signatures
      And the edge is marked resolved = true

    @US030 @E001 @F010 @must @draft
    Scenario: Service calls repository method
      Given a @Service with an @Autowired OrderRepository field
      And OrderRepository declares save(Order)
      When the service method calls repository.save(order)
      Then a call graph edge is recorded to OrderRepository.save
      And the edge is marked resolved = true

  Rule: Method calls to JDK and Spring framework classes are not recorded

    @US030 @E001 @F010 @should @draft
    Scenario: Method calls to JDK classes are ignored
      Given a service method that calls order.getTotal(), String.format(), and List.add()
      When the method calls are processed by CallGraphVisitor
      Then no call graph edges are recorded for JDK methods

  Rule: Unresolved calls are logged as unresolved_signatures

    @US030 @E001 @F010 @should @draft
    Scenario: Method call to third-party library is unresolved
      Given a service that calls thirdparty-sdk.calculateScore(data)
      And thirdparty-sdk is not in the scanned codebase
      When the method call is processed
      Then the call is recorded as an unresolved_signature
      And no call graph edge is created

    @US030 @E001 @F010 @should @draft
    Scenario: Overloaded method with same argument count
      Given OrderService declares find(String id) and find(String name, String status)
      When a controller calls service.find("abc")
      Then the call is resolved to OrderService.find(String)
      And the edge is marked resolved = true

    @US030 @E001 @F010 @should @draft
    Scenario: Overloaded method with ambiguous argument count
      Given OrderService declares process(OrderDto) and process(InvoiceDto)
      When a controller calls service.process(dto) and dto type cannot be resolved
      Then the call is marked AMBIGUOUS
      And both overloads are listed in the edge metadata

  Rule: Event listener call chains are resolved against the registry

    @US030 @E001 @F010 @should @draft
    Scenario: Event listener calls service method
      Given a component with @EventListener handling OrderCreated event
      And the listener method calls notificationService.sendEmail(event)
      When CallGraphVisitor processes the listener
      Then a call graph edge is recorded to notificationService.sendEmail
      And the edge depth is captured in the listener call chain
