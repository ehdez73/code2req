## Why

The `robust-json-sanitization` change added JSON object extraction and quote normalization to `sanitize()`, but the ordering of steps — wrap-before-extract — causes valid JSON with trailing ````json` cruft to be incorrectly wrapped and then fail extraction. Reversing to extract-before-wrap and teaching `extractJsonSpan` to preserve property-name prefixes before the first `{` fixes the remaining parse failures observed in the `spring-petclinic` enrichment run.

## What Changes

- Swap the order of `extractJsonSpan()` and wrap logic in `sanitize()`: extract first, wrap second
- Modify `extractJsonSpan()` to preserve property-name prefixes (e.g., `'metadata":` ) before the first `{` in the returned span

## Capabilities

### New Capabilities

- `sanitize-extract-before-wrap`: The sanitization step SHALL extract the JSON span from responses and preserve leading property-name prefixes before applying brace-wrapping logic.

### Modified Capabilities

None.

## Impact

- **`ExecutionFindingParser.java`**: reorder `sanitize()` steps, modify `extractJsonSpan()` to include property-name prefix
- **`ExecutionFindingParserTest.java`**: add test for response with trailing markdown cruft after valid JSON
