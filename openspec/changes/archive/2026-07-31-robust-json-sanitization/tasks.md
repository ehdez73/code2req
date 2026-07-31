## 1. Test

- [x] 1.1 Add test in `ExecutionFindingParserTest` for response missing opening brace with pure single-quoted property names (e.g., `'metadata': {'task_id': 'abc'}`)
- [x] 1.2 Add test for response missing opening brace with mixed quotes (e.g., `'metadata": {"task_id": "abc"}`)
- [x] 1.3 Add test for JSON embedded in surrounding text — trailing characters after valid JSON (e.g., `{"metadata": {}} ```json {`)
- [x] 1.4 Add test for JSON embedded in surrounding text — leading prose before valid JSON (e.g., `Here is the result: {"metadata": {}}`)
- [x] 1.5 Add test verifying valid JSON passes through unchanged (regression)
- [x] 1.6 Run `mvn test -Dtest=ExecutionFindingParserTest` to verify new tests fail before implementation

## 2. Implementation

- [x] 2.1 Add private `extractJsonSpan(String)` method to `ExecutionFindingParser` that finds the first `{`, tracks balanced braces with string awareness, and returns the substring
- [x] 2.2 Add private `normalizePropertyQuotes(String)` method that converts `'(\w+)'(\s*:)` → `"$1"$2` and `'(\w+)"(\s*:)` → `"$1"$2`
- [x] 2.3 Modify `sanitize()` to call `extractJsonSpan()` first, then `normalizePropertyQuotes()`, before applying existing fixes
- [x] 2.4 If `extractJsonSpan()` finds no `{`, detect property-name pattern at start and wrap content in `{...}`
- [x] 2.5 Run `mvn clean compile` to verify compilation

## 3. Verification

- [x] 3.1 Run `mvn clean test` and confirm all tests pass (new and existing)
