# Feature: Embabel Agent — Goals, Actions, Output
# Epic: E004 — Agentic Functional Requirement Extraction
# Feature ID: F023
# Stories: US051
# Phase 3 draft generated: 2026-06-17
# Last updated: 2026-06-25

Feature: Embabel Agent — Goals, Actions, Output
  The Embabel agent extracts functional requirements using GOAP dynamic planning. It receives CodebaseKnowledge, pursues goals through actions, and produces the final specification.

  Background:
    Given CodebaseKnowledge is built with Phase 1 and Phase 2 data
    And the Embabel agent is invoked with initial blackboard state

  Rule: The agent dynamically investigates ambiguity through GOAP planning

    @US051 @E004 @F023 @must @draft
    Scenario: Agent analyzes findings and produces candidate functional flows
      Given CodebaseKnowledge contains enriched records and structural graph
      When the AnalyzeFindings action executes
      Then candidate FunctionalFlow objects are produced
      And each flow has trigger, steps, and outcomes
      And flows with complete data are marked ready for synthesis

    @US051 @E004 @F023 @must @draft
    Scenario: Agent resolves ambiguity by searching CodebaseKnowledge
      Given a candidate flow has an ambiguity gap (missing endpoint for a service method)
      When the ResolveAmbiguity action executes
      Then it queries getCallersOf(target) on CodebaseKnowledge
      If the caller is found, the flow gap is resolved
      If not found, it falls back to reading the raw source file

    @US051 @E004 @F023 @must @draft
    Scenario: Agent cross-references floating links against known endpoints
      Given floating links with PENDING resolution status exist
      When the CrossReferenceFloatingLinks action executes
      Then each floating link's URL pattern is matched against known endpoint paths
      And matched links are resolved with confidence score
      And unmatched links remain PENDING for documentation

    @US051 @E004 @F023 @must @draft
    Scenario: Agent quarantines flows exceeding guardrails
      Given a candidate flow has required 6 investigation steps
      And max-investigation-steps-per-flow is set to 5
      When the QuarantineUnresolvable action evaluates the flow
      Then the flow is quarantined
      And its status is set to AWAITING_HUMAN_REVIEW
      And it appears in spec Section 5 (unresolved dependencies)

    @US051 @E004 @F023 @must @draft
    Scenario: Agent synthesizes final functional specification
      Given all goals are achieved or unresolvable flows are quarantined
      When the SynthesizeFunctionalSpec action executes
      Then MarkdownSpecWriter produces spec-output/*.md per PRD §6.1
      And SemanticManifestWriter produces spec-output/semantic_manifest.json
      And the manifest includes full traceability (file paths, AST signatures, line numbers)

    @US051 @E004 @F023 @must @draft
    Scenario: Phase3Orchestrator persists quarantined flows after agent completes
      Given the agent has completed with AmbiguityGap objects on the blackboard
      When Phase3Orchestrator collects and persists results
      Then HUMAN_REVIEW_REASON findings are saved to execution_finding_store
      And associated task statuses are updated to AWAITING_HUMAN_REVIEW
      And Phase3Result includes awaitingReviewReasons with flow name and detail

    @US051 @E004 @F023 @must @draft
    Scenario: Phase 3 crash marker prevents redundant re-execution
      Given Phase 3 has completed successfully with marker set to ENRICHED
      When the run command starts Phase 3 without --force-phase3
      Then Phase 3 is skipped
      And the output shows "use --force-phase3 to re-run"
      When --force-phase3 is set
      Then the marker is reset to PENDING
      And Phase 3 executes from scratch
