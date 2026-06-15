# Feature: Inter-File Call Graph Resolution
# Epic: E001 — Deterministic Multi-Language Indexing
# Feature ID: F010
# Stories: US030
# Phase 1 draft generated: 2026-06-15
# Last updated: 2026-06-15

Feature: Inter-File Call Graph Resolution
  Two-pass deterministic linker resolves method calls across files within the same scan target.

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
