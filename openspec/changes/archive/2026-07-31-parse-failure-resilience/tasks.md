## 1. Test

- [x] 1.1 Add test verifying that a JSON parse failure on attempt 1 retries with the original prompt (not error feedback)
- [x] 1.2 Add test verifying that three consecutive parse failures return a minimal stub `ExecutionFinding` with metadata filled and empty collections
- [x] 1.3 Add test verifying that a successful parse on retry 2 returns the parsed result normally
- [x] 1.4 Add test verifying that rate-limit (429) behavior is unchanged
- [x] 1.5 Run `mvn test -Dtest=LlmEnrichmentServiceTest -DfailIfNoTests=false` to verify new tests fail before implementation

## 2. Implementation

- [x] 2.1 Modify the retry loop in `callLlm()` to use original `userPrompt` on non-rate-limit parse failures instead of `buildErrorFeedbackPrompt()`
- [x] 2.2 Add a `buildMinimalFinding(Task)` private method that constructs a stub `ExecutionFinding` from task metadata
- [x] 2.3 On exhaustion (attempt > maxRetries), return the minimal finding instead of throwing
- [x] 2.4 Run `mvn clean compile` to verify compilation

## 3. Verification

- [x] 3.1 Run `mvn clean test` and confirm all tests pass (new and existing)
