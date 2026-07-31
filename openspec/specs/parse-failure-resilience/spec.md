## Purpose

Prevents permanent enrichment failure when the LLM produces structurally uncorrectable responses — including bare strings, monologues, or broken JSON fragments — by retrying with fresh prompts and falling back to a minimal metadata-only result on exhaustion.

## Requirements

### Requirement: Parse failures retry with fresh prompts

On a JSON parse failure (not a rate-limit error), the system SHALL retry the LLM call with the original user prompt rather than an error-feedback prompt. Rate-limit (HTTP 429) errors SHALL continue using the existing exponential-backoff behavior without prompt modification.

#### Scenario: Parse failure on attempt 1 retries with original prompt

- **WHEN** the first LLM response fails JSON parsing but is not an HTTP 429 error
- **THEN** retry attempt 2 SHALL use the original user prompt and system prompt
- **AND** the broken response SHALL NOT be included in the retry prompt

#### Scenario: Rate limit retries unchanged

- **WHEN** the LLM returns HTTP 429
- **THEN** the system SHALL apply exponential backoff with the existing delay and multiplier
- **AND** the original prompt SHALL be retried unchanged (current behavior)

### Requirement: Exhaustion returns minimal result

After the maximum retries (3) are exhausted on parse failures, the system SHALL return a minimally valid `ExecutionFinding` containing only the known task metadata (`task_id`, `target_name`, `file_path`, `tech_profile`, `module_tag`) with empty collections for all business rules, test insights, and architectural connections. The task SHALL transition to `ENRICHED` rather than `ENRICH_FAILED`.

#### Scenario: Three parse failures yield a stub result

- **WHEN** all 3 LLM attempts produce unparseable JSON
- **THEN** the enrichment SHALL return an `ExecutionFinding` with metadata filled from the task context
- **AND** `business_rules_and_guardrails.validations` SHALL be an empty list
- **AND** `business_rules_and_guardrails.edge_cases` SHALL be an empty list
- **AND** `test_insights` SHALL be an empty list
- **AND** `architectural_connections.inbound.http_endpoints` SHALL be an empty list
- **AND** `discovered_dependencies` SHALL be an empty list

#### Scenario: Rate limit exhaustion still fails

- **WHEN** all 3 LLM attempts return HTTP 429
- **THEN** the system SHALL throw an exception marking the task as failed (current behavior, no change)

### Requirement: Successful parse on retry continues normally

If a retry produces valid JSON that parses successfully, the system SHALL return the parsed `ExecutionFinding` as normal, with no difference in behavior from a first-attempt success.

#### Scenario: Retry succeeds with valid JSON

- **WHEN** attempt 1 fails with unparseable JSON
- **AND** attempt 2 returns valid, parseable JSON
- **THEN** the enrichment SHALL return the parsed `ExecutionFinding`
- **AND** the task SHALL transition to `ENRICHED`
