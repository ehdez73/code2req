## Purpose

Ensures sanitization correctly handles LLM responses where valid JSON is followed by trailing markdown or prose characters by extracting the JSON span before applying brace-wrapping logic.

## ADDED Requirements

### Requirement: Extraction preserves leading property-name prefixes

The `extractJsonSpan` step SHALL include any property-name pattern (e.g., `'metadata":` or `"metadata":`) found immediately before the first `{` in the returned span, so that subsequent wrap and quote-normalization steps can correctly transform the full response.

#### Scenario: Response with property name prefix before inner JSON

- **WHEN** the LLM returns `'metadata": {"task_id": "abc"}`
- **THEN** `extractJsonSpan()` SHALL return `'metadata": {"task_id": "abc"}` (not `{"task_id": "abc"}`)
- **AND** subsequent wrap SHALL produce `{'metadata": {"task_id": "abc"}}`

### Requirement: Extraction removes trailing text before wrap

The extraction step SHALL run before the wrapping step so that trailing characters after the last balanced `}` (e.g., ` ```json {` ) are discarded before any `{...}` wrapping is applied.

#### Scenario: Valid JSON with trailing markdown cruft

- **WHEN** the LLM returns `{"metadata": {...}} ```json {`
- **THEN** `sanitize()` SHALL extract `{"metadata": {...}}` first
- **AND** the trailing ` ```json {` SHALL be discarded before wrap logic runs
