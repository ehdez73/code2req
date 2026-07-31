## Why

When `sanitize()` cannot salvage a structurally broken LLM response (e.g., bare strings like `'metadata'`, LLM soliloquy with no parseable JSON), the `callLlm()` retry loop feeds the broken response back to the LLM via `buildErrorFeedbackPrompt()` — doubling token cost on every attempt while the LLM has no structural mechanism to self-correct. After 3 failed retries, the task fails permanently (`ENRICH_FAILED`), losing the file from the enrichment phase entirely. Two such files failed in the most recent `spring-petclinic` run.

## What Changes

- On JSON parse failures, retry with the original prompt instead of the error-feedback prompt (the LLM is non-deterministic and may produce valid JSON on a second independent attempt)
- On the third exhaustion, instead of throwing an exception, return a minimal `ExecutionFinding` containing only the known metadata (`task_id`, `target_name`, `file_path`, etc.) with empty defaults for all other fields
- Rate-limit (429) retry behavior remains unchanged

## Capabilities

### New Capabilities

- `parse-failure-resilience`: The enrichment LLM call SHALL retry parse failures with fresh prompts rather than error feedback, and SHALL return a minimal metadata-only result on third exhaustion instead of failing permanently.

### Modified Capabilities

None.

## Impact

- **`LlmEnrichmentService.java`**: modify retry logic in `callLlm()` — on non-rate-limit failures, use original prompt for retries; on final exhaustion, construct and return a minimal `ExecutionFinding`
- **`LlmEnrichmentServiceTest.java`**: add test cases for fresh-prompt retry and minimal-stub fallback
- **No changes** to `ExecutionFindingParser`, `EnrichFlowAction`, or any other class
