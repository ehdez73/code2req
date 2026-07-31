## Purpose

Ensures LLM enrichment responses containing structurally malformed JSON — missing opening braces, mixed quote styles in property names, or JSON embedded in surrounding prose — are parsed successfully without triggering costly error-feedback retries.

## Requirements

### Requirement: Responses missing the opening brace are wrapped

The sanitization step SHALL detect LLM responses that lack an opening `{` but contain JSON object content (identifiable by the presence of property-name patterns like `"key":` or `'key':`) and SHALL prepend `{` before the first property assignment to produce valid JSON.

#### Scenario: Response starts with a property name but no opening brace

- **WHEN** the LLM returns `"metadata": {"task_id": "abc", "file_path": "/src/Foo.java"}` without a leading `{`
- **THEN** `sanitize()` SHALL prepend `{` to produce `{"metadata": {...}}`
- **AND** the result SHALL parse successfully

#### Scenario: Response starts with a single-quoted property name

- **WHEN** the LLM returns `'metadata": {"task_id": "abc"}` without a leading `{`
- **THEN** `sanitize()` SHALL convert the property name to double quotes and prepend `{` to produce `{"metadata": {...}}`
- **AND** the result SHALL parse successfully

### Requirement: Single-quoted property names are normalized to double-quoted

The sanitization step SHALL convert single-quoted JSON property names to double-quoted form. It SHALL handle both pure single-quote (`'field':`) and mixed single/double quote (`'field":`) patterns.

#### Scenario: Pure single-quoted property names

- **WHEN** the response contains `'metadata': {'task_id': 'abc'}`
- **THEN** `sanitize()` SHALL convert to `"metadata": {"task_id": "abc"}`
- **AND** the result SHALL parse successfully

#### Scenario: Mixed single/double-quoted property names

- **WHEN** the response contains `'metadata": {"task_id": "abc"}`
- **THEN** `sanitize()` SHALL convert the opening single quote to double, producing `"metadata": {"task_id": "abc"}`
- **AND** the result SHALL parse successfully

### Requirement: JSON embedded in surrounding text is extracted

The sanitization step SHALL locate the outermost `{...}` span in the response by finding the first opening brace and its matching closing brace, tracking string state to avoid false matches inside string values. Content before the first `{` and after the last matching `}` SHALL be discarded.

#### Scenario: JSON followed by LLM afterthoughts

- **WHEN** the response is `{"metadata": {...}} ```json {` (valid JSON followed by stray markdown characters)
- **THEN** `sanitize()` SHALL extract only `{"metadata": {...}}`
- **AND** the trailing ` ```json {` SHALL be discarded

#### Scenario: LLM reasoning text before JSON

- **WHEN** the response is `Here is the result: {"metadata": {...}}`
- **THEN** `sanitize()` SHALL extract only `{"metadata": {...}}`
- **AND** the leading prose SHALL be discarded

### Requirement: Valid JSON responses pass through unchanged

The sanitization step SHALL NOT alter responses that are already valid JSON or that require only the existing fixes (trailing commas, brace balance, etc.).

#### Scenario: Well-formed JSON with markdown fences

- **WHEN** the response is ``` ```json\n{"metadata": {...}}\n``` ```
- **THEN** `sanitize()` SHALL strip the markdown fences and retain the valid JSON unchanged
- **AND** the result SHALL parse successfully
