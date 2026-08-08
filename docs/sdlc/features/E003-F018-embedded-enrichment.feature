# Feature: Embedded Enrichment in Extract
# Epic: E003 — Semantic Enrichment
# Feature ID: F018
# Stories: US045 (deferred — concurrency handled in EnrichFlowAction)
# Last updated: 2026-08-08

Feature: Embedded Enrichment in Extract
  EnrichFlowAction manages enrichment concurrency within the extract command. It uses a thread pool to enrich qualifying files in parallel, blocks until all complete, and caches results to avoid duplicate work across flows.

  Background:
    Given EnrichFlowAction is invoked during extract
    And qualifying files have been identified
    And the thread pool is configured (core=5, max=10)

  Rule: Enrichment runs concurrently within extract using a thread pool

    @US045 @E003 @F018 @must @deferred
    Scenario: All qualifying files in a flow are enriched concurrently
      Given 3 files in a traced flow need enrichment
      When EnrichFlowAction.enrichOneFlow() executes
      Then all 3 files are submitted to the thread pool concurrently
      And each returns a CompletableFuture

    @US045 @E003 @F018 @must @deferred
    Scenario: Enrichment barrier blocks AnalyzeFlow until all files complete
      Given 5 files are being enriched concurrently
      When 3 files have completed and 2 are still running
      Then CompletableFuture.allOf().join() blocks the calling thread
      When all 5 complete
      Then AnalyzeFlow proceeds with enriched context

    @US045 @E003 @F018 @must @deferred
    Scenario: Enrichment cache avoids duplicate work across flows
      Given File A is enriched during flow 1's analysis
      When EnrichFlowAction encounters File A again in flow 2
      Then the cached enrichment is reused
      And no new LLM call is made for File A

    @US045 @E003 @F018 @must @deferred
    Scenario: Enrichment respects execution mode
      Given execution mode is set to async
      When EnrichFlowAction fires enrichment tasks
      Then tasks run on the thread pool concurrently
      Given execution mode is set to sync
      When EnrichFlowAction fires enrichment tasks
      Then each task runs on the caller thread sequentially

    @US045 @E003 @F018 @must @deferred
    Scenario: All files proceed to AnalyzeFlow even if some enrichment fails
      Given 3 files need enrichment
      And 1 file's enrichment fails (LLM error or schema validation)
      When all enrichment futures complete
      Then AnalyzeFlow proceeds
      And the failed file is handled via source code snippets (without enrichment context)
