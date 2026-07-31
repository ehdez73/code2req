## 1. Test

- [x] 1.1 Create `AnalyzeFlowActionTest` with a test verifying that a single-thread executor produces identical results to sequential execution for the same inputs
- [x] 1.2 Add test verifying that concurrent executor processes all flows, producing the correct count of `FunctionalFlow` entries in `AnalyzedFlowResult`
- [x] 1.3 Add test verifying that a single flow failure (simulated LLM exception) does not abort remaining flows, and the failing flow is omitted from results
- [x] 1.4 Add test verifying that cached flow analyses (resume mode) are reused under concurrent execution without duplicate LLM calls
- [x] 1.5 Add test verifying that null executor falls back to sequential loop (backward compatibility)
- [x] 1.6 Run `mvn clean test -pl .` to verify new tests fail before implementation

## 2. Implementation

- [x] 2.1 Add `private final Executor analysisExecutor` field to `AnalyzeFlowAction` (nullable, passes null for sequential fallback)
- [x] 2.2 Modify `AnalyzeFlowAction.analyze()` to use `CompletableFuture.supplyAsync(..., analysisExecutor)` when executor is non-null, with per-flow try-catch wrapping to handle failures gracefully
- [x] 2.3 Inject `@Qualifier("orchestratorTaskExecutor") Executor orchestratorTaskExecutor` into `FunctionalRequirementAgent` constructor
- [x] 2.4 Pass `orchestratorTaskExecutor` to `AnalyzeFlowAction` constructor in `FunctionalRequirementAgent.analyzeFlows()`
- [x] 2.5 Run `mvn clean compile` to verify compilation

## 3. Verification

- [x] 3.1 Run `mvn clean test` and confirm all new and existing tests pass
- [ ] 3.2 Run `mvn spring-boot:run` and execute `extract` on `spring-petclinic` to verify reduced Phase 3 wall-clock time and identical output
