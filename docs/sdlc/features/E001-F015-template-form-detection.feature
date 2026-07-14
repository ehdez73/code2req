# Feature: JSP/Thymeleaf Form & View Detection
# Epic: E001 — Deterministic Multi-Language Indexing
# Feature ID: F015
# Stories: US037, US038, US039, US040, US062
# Phase 1 draft generated: 2026-06-16
# Last updated: 2026-06-16

Feature: JSP/Thymeleaf Form & View Detection
  Detect view-returning controller methods, parse JSP/Thymeleaf templates for
  forms and links, and match them to controller endpoints for end-to-end tracing.

  Background:
    Given the Phase 1 scan pipeline is executing

  Rule: Controller methods returning HTML views are tagged

    @US037 @E001 @F015 @should @draft
    Scenario: Controller with String return type is view-serving
      Given a @Controller class with @GetMapping("/hello")
      And a method returns String
      When EndpointVisitor analyses the method
      Then an endpoint is recorded with servesView = true
      And the viewName is extracted from the return statement

    @US037 @E001 @F015 @should @draft
    Scenario: Controller with ModelAndView return type
      Given a @Controller class with @GetMapping("/show")
      And a method returns ModelAndView
      When EndpointVisitor analyses the method
      Then an endpoint is recorded with servesView = true
      And the viewName is extracted from the ModelAndView constructor

    @US037 @E001 @F015 @should @draft
    Scenario: RestController String return is NOT a view
      Given a @RestController class with @GetMapping("/api/hello")
      And a method returns String
      When EndpointVisitor analyses the method
      Then the endpoint is recorded with servesView = false

    @US037 @E001 @F015 @should @draft
    Scenario: @ResponseBody overrides view detection
      Given a @Controller class with @GetMapping("/data")
      And a method annotated with @ResponseBody returns String
      When EndpointVisitor analyses the method
      Then the endpoint is recorded with servesView = false

  Rule: JSP template forms and links are extracted

    @US038 @E001 @F015 @should @draft
    Scenario: JSP form with action and method
      Given a .jsp file with <form action="/owners" method="post">
      When TemplateAnalyzer parses the file
      Then a template_form entry is created with method POST
      And the URL pattern is "/owners"

    @US038 @E001 @F015 @should @draft
    Scenario: JSP form without method defaults to GET
      Given a .jsp file with <form action="/search">
      When TemplateAnalyzer parses the file
      Then a template_form entry is created with method GET
      And the URL pattern is "/search"

    @US038 @E001 @F015 @should @draft
    Scenario: JSP anchor link is extracted
      Given a .jsp file with <a href="/owners/1">
      When TemplateAnalyzer parses the file
      Then a template_anchor_link entry is created with method GET
      And the URL pattern is "/owners/1"

    @US038 @E001 @F015 @should @draft
    Scenario: JSP form fields are collected
      Given a .jsp file with <form action="/owners" method="post">
      And <input name="firstName"> and <input name="lastName">
      When TemplateAnalyzer parses the file
      Then the template_form entry includes field names ["firstName", "lastName"]

  Rule: Thymeleaf template forms and links are extracted

    @US039 @E001 @F015 @should @draft
    Scenario: Thymeleaf form with th:action and th:method
      Given a .html file with <form th:action="@{/owners}" th:method="post">
      When TemplateAnalyzer parses the file
      Then a template_form entry is created with method POST
      And the URL pattern is "/owners"

    @US039 @E001 @F015 @should @draft
    Scenario: Thymeleaf form with expression URL
      Given a .html file with <form th:action="@{/owners/{id}(id=${owner.id})}" th:method="post">
      When TemplateAnalyzer parses the file
      Then a template_form entry is created with isExpression = true

    @US039 @E001 @F015 @should @draft
    Scenario: Thymeleaf anchor link is extracted
      Given a .html file with <a th:href="@{/owners}">
      When TemplateAnalyzer parses the file
      Then a template_anchor_link entry is created with method GET
      And the URL pattern is "/owners"

    @US039 @E001 @F015 @should @draft
    Scenario: Thymeleaf input fields are collected
      Given a .html file with <form th:action="@{/owners}" th:method="post">
      And <input th:field="*{name}">
      When TemplateAnalyzer parses the file
      Then the template_form entry includes field name "name"

  Rule: Template form actions are matched to controller endpoints

    @US040 @E001 @F015 @could @draft
    Scenario: Exact path match with same HTTP method
      Given a template_form with action "/owners" and method GET
      And a controller endpoint with path "/owners" and method GET
      When TemplateLinkResolver resolves the pair
      Then a template_endpoint_link is created with confidence 1.0
      And the matched endpoint path is "/owners"

    @US040 @E001 @F015 @could @draft
    Scenario: Path-parameterized match
      Given a template_form with action "/owners/5" and method GET
      And a controller endpoint with path "/owners/{id}" and method GET
      When TemplateLinkResolver resolves the pair
      Then a template_endpoint_link is created with confidence 0.8

    @US040 @E001 @F015 @could @draft
    Scenario: HTTP method mismatch prevents match
      Given a template_form with action "/owners" and method POST
      And a controller endpoint with path "/owners" and method GET
      When TemplateLinkResolver resolves the pair
      Then no template_endpoint_link is created

  Rule: @Controller methods returning void are detected as view-serving endpoints

    @US062 @E001 @F015 @should @draft
    Scenario: @Controller void method serves implicit view
      Given a @Controller class with a void method annotated @GetMapping("/orders")
      When SpringEndpointDetector analyses the method
      Then the endpoint is marked as serving a view
      And the view name is inferred from the request path

    @US062 @E001 @F015 @should @draft
    Scenario: @RestController void method does not serve a view
      Given a @RestController class with a void method annotated @GetMapping("/api/orders")
      When SpringEndpointDetector analyses the method
      Then the endpoint is not marked as serving a view
