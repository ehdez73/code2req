# Feature: Outbound HTTP Client Detection
# Epic: E001 — Deterministic Multi-Language Indexing
# Feature ID: F012
# Stories: US032
# Phase 1 draft generated: 2026-06-15
# Last updated: 2026-06-15

Feature: Outbound HTTP Client Detection
  Detect RestTemplate, WebClient, and FeignClient outbound calls and register them as floating links.

  Background:
    Given Java source files are being analysed in Pass 2

  Rule: RestTemplate calls are captured with HTTP method and URL

    @US032 @E001 @F012 @must @draft
    Scenario: Service calls external API via RestTemplate
      Given a @Service using RestTemplate
      And a method calls restTemplate.postForObject(url, request, Response.class)
      When RestClientVisitor analyses the method
      Then an outbound HTTP call entry is recorded with method POST
      And the URL pattern is captured (literal or expression)
      And the call is registered as a floating_link in the SQLite store

    @US032 @E001 @F012 @must @draft
    Scenario: RestTemplate.exchange() with HttpMethod
      Given a method calling restTemplate.exchange(url, HttpMethod.GET, entity, Response.class)
      When RestClientVisitor analyses the method
      Then an outbound HTTP call entry is recorded with method GET

  Rule: WebClient builder chains are followed

    @US032 @E001 @F012 @should @draft
    Scenario: WebClient fluent chain
      Given a method using webClient.method(HttpMethod.POST).uri(url).retrieve().bodyToMono(Response.class)
      When RestClientVisitor analyses the method
      Then an outbound HTTP call entry is recorded with method POST
      And the URL pattern is extracted from .uri()

  Rule: FeignClient interfaces are detected

    @US032 @E001 @F012 @should @draft
    Scenario: FeignClient interface declares external API
      Given a @FeignClient(name = "payment", url = "${payment.url}")
      And a method annotated with @PostMapping("/charges")
      When RestClientVisitor analyses the interface
      Then a floating_link is registered with method POST and URL pattern "${payment.url}/charges"
      And the link is marked is_expression = true

  Rule: SpEL expressions in URLs are flagged

    @US032 @E001 @F012 @should @draft
    Scenario: RestTemplate URL contains environment variable
      Given a method calling restTemplate.exchange("${api.base.url}/orders", ...)
      When RestClientVisitor analyses the call
      Then the URL pattern "${api.base.url}/orders" is captured as-is
      And is_expression is set to true

    @US032 @E001 @F012 @should @draft
    Scenario: Multiple RestTemplate calls in the same method
      Given a method calling restTemplate.getForObject(url1, A.class) and restTemplate.postForObject(url2, body, B.class)
      When RestClientVisitor analyses the method
      Then two floating_link entries are created
      And each has its own method and URL pattern
