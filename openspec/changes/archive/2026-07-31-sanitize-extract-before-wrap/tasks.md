## 1. Test

- [x] 1.1 Add test for response with property-name prefix and trailing markdown cruft (e.g., `'metadata": {"a": 1} \`\`\`json {`)
- [x] 1.2 Add test verifying that response with trailing ` ```json {` after valid JSON is correctly extracted and parsed
- [x] 1.3 Run `mvn test -Dtest=ExecutionFindingParserTest` to verify new tests fail before implementation

## 2. Implementation

- [x] 2.1 Modify `extractJsonSpan()` to include the property-name prefix before `firstBrace` when present
- [x] 2.2 Swap the order of extract and wrap in `sanitize()` so extraction runs first
- [x] 2.3 Run `mvn clean compile` to verify compilation

## 3. Verification

- [x] 3.1 Run `mvn clean test` and confirm all tests pass (new and existing)
