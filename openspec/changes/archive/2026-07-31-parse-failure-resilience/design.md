## Context

`LlmEnrichmentService.callLlm()` (lines 152-259) handles the LLM call, response parsing, and retry logic. The retry loop (lines 190-258) currently:
1. Calls the LLM with the user prompt
2. Sanitizes and parses the response
3. On failure, if non-rate-limit and response was received, calls `buildErrorFeedbackPrompt()` to create a new prompt containing the original context + broken JSON + error message
4. Retries up to 3 times
5. On exhaustion, throws `RuntimeException`

The `buildErrorFeedbackPrompt()` approach (line 235) was designed for LLMs that can self-correct JSON errors when shown the malformed output. In practice, the free-tier model produces structurally uncorrectable output (bare strings, monologues) and the error feedback only adds noise.

Rate-limit (429) handling is separate: it uses exponential backoff and re-sends the original prompt.

See `proposal.md` for motivation and live-run data.

## Goals / Non-Goals

**Goals:**
- Stop feeding broken JSON back to the LLM (eliminate `buildErrorFeedbackPrompt` for parse failures)
- Prevent permanent enrichment failure by returning a minimal stub on exhaustion
- Preserve existing rate-limit handling unchanged

**Non-Goals:**
- Changing `buildErrorFeedbackPrompt()` itself (keep it for potential future use)
- Modifying `sanitize()`, `ExecutionFindingParser`, or `EnrichFlowAction`
- Changing the max retry count (stays at 3)
- Affecting the handling of empty responses, network timeouts, or other non-parse exceptions

## Decisions

### Decision 1: Fresh-prompt retry instead of error feedback

**Rationale:** The LLM is non-deterministic. A fresh call with the same prompt has a chance of producing valid JSON where the first attempt didn't. The error-feedback approach has near-zero success rate on structural failures (bare strings, monologues) and costs 2x tokens.

**Alternatives considered:**
- **Keep error feedback**: wastes tokens with no evidence of working. Rejected.
- **Skip retries entirely on parse failure**: too aggressive — the LLM sometimes produces valid JSON on a second independent attempt.

### Decision 2: Minimal stub on third exhaustion

**Rationale:** A file with metadata-only enrichment is better than a completely lost file. Downstream consumers (`AnalyzeFlowAction`, `GenerateOrchestrator`) can still use the metadata (file path, target name, task ID) for traceability and structural analysis. They already handle missing business rules gracefully.

The stub is constructed from the task context already available in `callLlm()` — no additional database lookups needed.

```java
new ExecutionFinding(
    new Metadata(task.taskId(), task.targetName(), task.filePath(),
        "business_semantics", task.contentType(), nowTimestamp()),
    new BusinessRulesAndGuardrails(List.of(), List.of()),
    List.of(),
    new ArchitecturalConnections(
        new Inbound(List.of(), List.of(), List.of()),
        new Outbound(List.of(), List.of())),
    List.of()
);
```

**Alternatives considered:**
- **Throw exception (current behavior)**: loses the file entirely. Rejected.
- **Return null and skip downstream**: breaks the pipeline contract.
- **Retry with a different model**: out of scope for a single change, requires configuration redesign.

### Decision 3: Rate-limit behavior unchanged

**Rationale:** Rate limits are transient and recoverable. Re-sending the same prompt after backoff is the correct strategy. Only parse failures need the retry-strategy change.

## Risks / Trade-offs

- **[Risk] Stub results may produce lower-quality spec output** since downstream actions rely on enrichment data for business rule extraction. → **Mitigation:** A metadata-only stub is still better than a missing file. Stubs are logged at WARN level for visibility. Files with stubs will still appear in flow traces and call graphs.

- **[Risk] Fresh-prompt retry may not fix the root cause** if the model consistently produces broken JSON for a specific file. → **Mitigation:** Three independent attempts with the same prompt have a higher probability of at least one success than one attempt with error feedback. The stub is the safety net.
