## Why

LLM responses during per-file enrichment sometimes produce JSON that lacks an opening `{` or mixes single and double quotes in property names (e.g., `'metadata": {...}`). The current `sanitize()` method in `ExecutionFindingParser` handles trailing commas, markdown fences, and brace imbalance but does not extract the JSON object from surrounding text or normalize mixed-quote property names. When `sanitize()` fails to produce parseable JSON, both the strict (`BeanOutputConverter`) and lenient (`parseLenient`) code paths fail, triggering up to 3 error-feedback retries that re-send the entire context to the LLM — doubling token cost per retry with no mechanism to correct the underlying structural issue.

In a recent `extract` run against `spring-petclinic` (18 flows, 8 enrichment files), 5 of 8 files required parse-failure retries, wasting approximately 3.5 minutes of LLM time. Two files (`VetController`, `CrashController`) exhausted all 3 retries and failed permanently.

## What Changes

- Add JSON object extraction to `sanitize()`: find the first `{` and last balanced `}` in the response and extract that span, discarding surrounding prose, markdown, or LLM afterthoughts
- Add property-name quote normalization to `sanitize()`: convert single-quoted and mixed-quoted property names (e.g., `'field':` and `'field":`) to standard double-quoted form
- Wrap response content in `{...}` if no opening brace is found but the content resembles the interior of a JSON object

## Capabilities

### New Capabilities

- `json-response-sanitization`: The LLM response parser SHALL recover from structural JSON malformations including missing opening braces, mixed quote styles in property names, and JSON embedded within prose, without triggering error-feedback retries.

### Modified Capabilities

None.

## Impact

- **`ExecutionFindingParser.java`**: modify `sanitize()` to add extraction and quote-normalization steps before existing fixes; add private helper methods for extraction and quote fixing
- **`ExecutionFindingParserTest.java`**: add test cases for missing opening brace, mixed quotes, embedded JSON, and prose-wrapped responses
- **No changes** to `LlmEnrichmentService`, `ExecutionFindingValidator`, or any other class
