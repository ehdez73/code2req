# Feature: Outbound HTTP Client Detection
# Epic: E001 — Deterministic Multi-Language Indexing
# Feature ID: F012
# Stories: US032
# Phase 1 draft generated: 2026-06-15
# Last updated: 2026-06-16

Feature: Outbound HTTP Client Detection
  Detect outbound HTTP calls from RestTemplate, WebClient, FeignClient, RestClient (Spring 6.1),
  @HttpExchange (Spring 6), java.net.http.HttpClient, HttpURLConnection, Apache HttpClient,
  and OkHttp, registering them as floating links with deterministic endpoint matching.

  Background:
    Given Java source files are being analysed in Pass 2

  Rule: RestTemplate calls are captured with HTTP method and URL

    @US032 @E001 @F012 @must @draft
    Scenario: Service calls external API via RestTemplate
      Given a @Service using RestTemplate
      And a method calls restTemplate.postForObject(url, request, Response.class)
      When OutboundHttpVisitor analyses the method
      Then an outbound HTTP call entry is recorded with method POST
      And the URL pattern is captured (literal or expression)
      And the call is registered as a floating_link in the SQLite store

    @US032 @E001 @F012 @must @draft
    Scenario: RestTemplate.exchange() with HttpMethod
      Given a method calling restTemplate.exchange(url, HttpMethod.GET, entity, Response.class)
      When OutboundHttpVisitor analyses the method
      Then an outbound HTTP call entry is recorded with method GET

  Rule: WebClient builder chains are followed

    @US032 @E001 @F012 @should @draft
    Scenario: WebClient fluent chain
      Given a method using webClient.method(HttpMethod.POST).uri(url).retrieve().bodyToMono(Response.class)
      When OutboundHttpVisitor analyses the method
      Then an outbound HTTP call entry is recorded with method POST
      And the URL pattern is extracted from .uri()

  Rule: FeignClient interfaces are detected

    @US032 @E001 @F012 @should @draft
    Scenario: FeignClient interface declares external API
      Given a @FeignClient(name = "payment", url = "${payment.url}")
      And a method annotated with @PostMapping("/charges")
      When OutboundHttpVisitor analyses the interface
      Then a floating_link is registered with method POST and URL pattern "${payment.url}/charges"
      And the link is marked is_expression = true

  Rule: SpEL expressions in URLs are flagged

    @US032 @E001 @F012 @should @draft
    Scenario: RestTemplate URL contains environment variable
      Given a method calling restTemplate.exchange("${api.base.url}/orders", ...)
      When OutboundHttpVisitor analyses the call
      Then the URL pattern "${api.base.url}/orders" is captured as-is
      And is_expression is set to true

    @US032 @E001 @F012 @should @draft
    Scenario: Multiple RestTemplate calls in the same method
      Given a method calling restTemplate.getForObject(url1, A.class) and restTemplate.postForObject(url2, body, B.class)
      When OutboundHttpVisitor analyses the method
      Then two floating_link entries are created
      And each has its own method and URL pattern

  Rule: Spring 6.1 RestClient calls are detected

    @US032 @E001 @F012 @should @draft
    Scenario: RestClient fluent chain
      Given a method using restClient.get().uri(url).retrieve()
      When OutboundHttpVisitor analyses the method
      Then an outbound HTTP call entry is recorded with method GET
      And the client type is REST_CLIENT

  Rule: Spring 6 @HttpExchange interfaces are detected

    @US032 @E001 @F012 @should @draft
    Scenario: @HttpExchange interface declares API
      Given an interface annotated with @HttpExchange(url = "${service.url}")
      And a method annotated with @PostExchange("/charges")
      When OutboundHttpVisitor analyses the interface
      Then a floating_link is registered with method POST and URL pattern "${service.url}/charges"

  Rule: Java 11+ java.net.http.HttpClient calls are detected

    @US032 @E001 @F012 @should @draft
    Scenario: HttpClient.send() with HttpRequest
      Given a method using HttpClient.newHttpClient().send(request, handler)
      When OutboundHttpVisitor analyses the method
      Then an outbound HTTP call entry is recorded with method GET
      And the client type is JAVA_NET_HTTP

  Rule: Legacy HttpURLConnection calls are detected

    @US032 @E001 @F012 @should @draft
    Scenario: HttpURLConnection with openConnection
      Given a method using new URL("http://example.com").openConnection()
      When OutboundHttpVisitor analyses the method
      Then an outbound HTTP call entry is recorded with method GET
      And the URL pattern is "http://example.com"

  Rule: Apache HttpClient calls are detected

    @US032 @E001 @F012 @should @draft
    Scenario: Apache HttpClient using HttpGet
      Given a method using new HttpGet("http://example.com/users")
      When OutboundHttpVisitor analyses the method
      Then an outbound HTTP call entry is recorded with method GET
      And the client type is APACHE_HTTP

  Rule: OkHttp calls are detected

    @US032 @E001 @F012 @should @draft
    Scenario: OkHttp client.newCall()
      Given a method using OkHttpClient.newCall(request)
      When OutboundHttpVisitor analyses the method
      Then an outbound HTTP call entry is recorded with method GET
      And the client type is OK_HTTP

  Rule: Floating links are resolved against known endpoints

    @US032 @E001 @F012 @must @draft
    Scenario: Literal URL matches known endpoint
      Given an OutboundHttpCallInfo with method GET and URL "/api/users"
      And an EndpointInfo with method GET and path "/api/users"
      When FloatingLinkResolver resolves the link
      Then the link is RESOLVED with confidence 1.0

    @US032 @E001 @F012 @should @draft
    Scenario: Path-variable URL matches known endpoint
      Given an OutboundHttpCallInfo with method GET and URL "/api/users/42"
      And an EndpointInfo with method GET and path "/api/users/{id}"
      When FloatingLinkResolver resolves the link
      Then the link is RESOLVED with confidence >= 0.6

    @US032 @E001 @F012 @should @draft
    Scenario: Unmatched URL remains PENDING
      Given an OutboundHttpCallInfo with method POST and URL "/api/external"
      And no matching endpoint exists
      When FloatingLinkResolver resolves the link
      Then the link is PENDING with confidence 0.0
