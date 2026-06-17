# Feature: Embabel Agent Framework Setup
# Epic: E004 — Agentic Functional Requirement Extraction
# Feature ID: F021
# Stories: US049
# Phase 3 draft generated: 2026-06-17
# Last updated: 2026-06-17

Feature: Embabel Agent Framework Setup
  Uncomment and configure the Embabel dependency in pom.xml. Embabel provides the GOAP engine for Phase 3's agentic functional requirement extraction.

  Background:
    Given the project is configured with Java 21 and Spring Boot 3.4.x
    And the Embabel repository is accessible

  Rule: The Embabel dependency must be resolvable and the framework must initialize at startup

    @US049 @E004 @F021 @must @draft
    Scenario: Embabel dependency resolves from Maven repositories
      Given the embabel-agent-starter dependency is uncommented in pom.xml
      When the project is compiled with mvn compile
      Then compilation succeeds
      And Embabel classes are on the classpath

    @US049 @E004 @F021 @must @draft
    Scenario: Embabel initializes on application startup
      Given the Embabel dependency is on the classpath
      When the application starts
      Then Embabel initializes without errors
      And the AgentPlatform is available for Phase 3

    @US049 @E004 @F021 @must @draft
    Scenario: Embabel repo is unreachable — build fails clearly
      Given the Embabel repository is unreachable
      When the project is compiled
      Then compilation fails with a clear Maven error
      And Phase 3 cannot proceed
