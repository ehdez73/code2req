# Feature: Domain Model + Output Writers
# Epic: E004 — Agentic Functional Requirement Extraction
# Feature ID: F027
# Stories: US056
# Phase 3 draft generated: 2026-06-26
# Last updated: 2026-07-01 (added ExternalCall, NonFunctionalRequirement, NFR section, severity, externalCall)

Feature: Domain Model + Output Writers
  Pure-Java domain classes for Phase 3 synthesis output and writers that produce structured Markdown
  specification and semantic JSON manifest from the extraction cache.

  Background:
    Given extraction-cache.json exists with CrossReferencedResult, OrphanedMethod, and AmbiguityGap data
    And the generate command reads the cache
    And the extraction cache path is configurable via code2req.output.spec-dir and code2req.output.extraction-cache-file

  Rule: Domain records are defined for all Phase 3 synthesis output types

    @US056 @E004 @F027 @must @draft
    Scenario: Domain model defines all Phase 3 output records
      Given a Phase 3 synthesis codebase package exists
      When the domain model is inspected
      Then records exist for EntryPoint, ExecutionFlow, FlowStep, FunctionalFlow, GherkinScenario, BusinessRule, EdgeCase, ExternalCall, NonFunctionalRequirement, FunctionalFeature, FlowRelationship, AmbiguityGap, and OrphanedMethod
      And all records use Java record types
      And each record has deterministic equals/hashCode based on business keys
      And BusinessRule has an optional externalCall field with httpMethod, url, timeoutMs, retryStrategy, fallbackBehavior
      And EdgeCase has a severity field (LOW/MEDIUM/HIGH)
      And FunctionalFlow has a nonFunctionalRequirements list

  Rule: MarkdownSpecWriter produces a structured specification document

    @US056 @E004 @F027 @must @draft
    Scenario: Writer produces spec.md with full feature breakdown
      Given a list of FunctionalFeature objects with analyzed flows, business rules, edge cases, and relationships
      When MarkdownSpecWriter.write() is called
      Then spec-output/spec.md is created with a table of contents
      And each feature has a User Story section with "As a... / I want... / so that..." format
      And each feature has an Execution Flow subsection
      And complex flows (ComplexityLevel.FULL) include a Mermaid graph TD diagram
      And each feature has Business Rules table (with per-rule sourceFile reference and inline external call details) and Edge Cases table (with Severity column)
      And each feature has a Non-Functional Requirements section (Category, Requirement, Source)
      And each feature has Gherkin acceptance criteria under an Acceptance Criteria subsection
      And a Cross-Flow Relationships section lists inter-flow dependencies
      And an Unresolved Dependencies section lists quarantined flows
      And an Orphaned Methods section lists dead code candidates

  Rule: SemanticManifestWriter produces a valid JSON manifest

    @US056 @E004 @F027 @must @draft
    Scenario: Writer produces valid semantic_manifest.json
      Given a list of FunctionalFeature objects with full analysis data
      When SemanticManifestWriter.write() is called
      Then spec-output/semantic_manifest.json is created
      And the JSON manifest contains manifest_version "3.0.0"
      And the manifest contains system_name, generated_at, and features array
      And each feature contains feature_id, name, description, and flows array
      And each flow contains flow_id, entry_point, steps, user_story, acceptance_criteria, complexity, and non_functional_requirements
      And each flow entry includes business_rules (with external_call sub-object) and edge_cases (with severity field)
      And each quarantined flow has review_required: true and unresolved_reason object
      And orphaned_methods and cross_flow_relationships arrays are present at the root level

  Rule: Output validation enforces schema conformance

    @US056 @E004 @F027 @must @draft
    Scenario: SemanticManifestWriter validates output before persisting
      Given SemanticManifestWriter is invoked from generate command
      When the writer generates the manifest JSON
      Then the JSON is validated against the PLAN-Phase3 §4.2 schema
      And if validation fails, the Phase 3 marker is set to FAILED
      And the validation error is logged
