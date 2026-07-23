# Implementation Plan: Interactive Mode (US057 / F028)

**Status:** Draft
**PRD:** §2.2.2 Interactive Mode (v5.10)
**Redline:** `#242` in docs/PRD.md

---

## Scope

Add interactive user prompts to `QuarantineFlowAction.evaluateQuarantine()`. When the agent encounters ambiguity below the `ambiguity-confidence-threshold` (0.7), it asks the user to resolve it rather than silently creating an `AmbiguityGap`. A "Write your own answer" option lets the user provide context the LLM cannot infer.

**Deferred (not in this plan):**
- `AnalyzeFlowAction` quarantined-flow force-analysis prompt
- CrossReferenceFlows interaction

---

## Architecture

```
ExtractionOrchestrator (Spring @Component)
  │  chooses NoOp or Interactive impl based on headless flag
  │  └─ places on blackboard: "userInteractionService"
  │
  ▼
FunctionalRequirementAgent (reads from OperationContext)
  │  passes to QuarantineFlowAction constructor
  │
  ▼
QuarantineFlowAction
  │─ evaluateQuarantine(flow)
  │    ├─ if UIS.isInteractive()  → prompt with select()
  │    │    ├─ "Accept"       → gap with userProvided=false
  │    │    ├─ "Provide context" → ask() → gap with userProvided=true, userAnswer
  │    │    └─ "Dismiss"      → return null (flow is clean, no gap)
  │    │
  │    └─ if NoOp            → create gap as before (existing behavior unchanged)
```

---

## Files to Create (10)

### 1. `extraction/domain/spi/UserInteractionService.java`

```java
public interface UserInteractionService {
    String ask(String prompt, String context);
    boolean confirm(String message);
    String select(List<String> options, String prompt);
    default boolean isInteractive() { return false; }
}
```

### 2. `extraction/domain/model/UserResponse.java`

```java
public record UserResponse(
    String sessionId,
    String question,
    String answer,
    String createdAt
) {}
```

### 3. `extraction/adapter/cli/NoOpUserInteractionService.java`

| Method | Return | When used |
|--------|--------|-----------|
| `ask(prompt, context)` | `null` | Headless mode |
| `confirm(message)` | `false` | Headless mode (never force-analyze) |
| `select(options, prompt)` | `null` | Headless mode (accept default gap) |
| `isInteractive()` | `false` | |

Plain Java class (not a Spring bean). Constructor-injected where needed.

### 4. `extraction/adapter/cli/InteractiveUserInteractionService.java`

Reads from `System.console()`. `select()` always appends "None of these — write my own answer" as the last option. Two-phase prompt when chosen:

```
  1. StripePaymentGateway
  2. PaypalPaymentGateway
  3. None of these — write my own answer

> 3

Provide additional context the LLM should consider:
> The runtime bean is injected via a FactoryBean in AcmeConfig.java.
  Look for @Qualifier("acme") or check application-acme.yml.
```

Plain Java class (not a Spring bean). Constructor.

### 5. `infrastructure/persistence/UserResponseStore.java`

`@Repository` wrapping `JdbcTemplate`. Methods:

- `void save(UserResponse)`
- `List<UserResponse> findBySessionId(String sessionId)`
- `Optional<UserResponse> findByQuestion(String question)` — for auto-cache

### 6. `extraction/domain/spi/UserInteractionServiceTest.java`

Compile-time reflection check: interface declares `ask`, `confirm`, `select`, `isInteractive` with correct signatures.

### 7. `extraction/adapter/cli/NoOpUserInteractionServiceTest.java`

Assert:
- `ask() → null`
- `confirm() → false`
- `select() → null`
- `isInteractive() → false`

### 8. `extraction/adapter/cli/InteractiveUserInteractionServiceTest.java`

Mock `System.console()` with `ByteArrayInputStream`/`ByteArrayOutputStream`. Test:

- `select()` lists options with write-your-own as last option
- Choosing write-your-own prompts for free text and returns it
- Choosing a concrete option returns that option string
- `confirm("y") → true`
- `confirm("n") → false`
- `ask()` returns read line

### 9. `extraction/adapter/agent/action/QuarantineFlowActionWithInteractionTest.java`

Mock `UserInteractionService` with `isInteractive()=true`. Test three paths:

| Scenario | User action | Expected result |
|----------|-------------|-----------------|
| Accept quarantine | select() → "Accept" | Gap created, `userProvided=false` |
| Provide context | select() → "Provide context" → ask() → "text" | Gap created, `userProvided=true`, `userAnswer="text"` |
| Dismiss | select() → "Dismiss" | No gap created, flow is clean |

Also verify that NoOp path preserves existing behavior (gap created, no interaction).

### 10. `infrastructure/persistence/UserResponseStoreTest.java`

`@TempDir` + manual `SQLiteDataSource` + `TaskStoreSchema`. Test:

- `save()` inserts row
- `findBySessionId()` returns matching rows
- `findByQuestion()` returns cached answer or empty

---

## Files to Modify (12)

### 1. `extraction/domain/model/GapReason.java`

Add 6 new enum values:

```java
public enum GapReason {
    LOW_CONFIDENCE,
    AMBIGUOUS_CALL_TARGET,
    UNCLEAR_BUSINESS_RULE,
    UNRESOLVED_EXTERNAL_SERVICE,
    AWAITING_USER_INPUT,
    USER_CLARIFIED,
    USER_DISMISSED
}
```

### 2. `extraction/domain/model/AmbiguityGap.java`

Add 3 new fields:

```java
public record AmbiguityGap(
    String flowId,
    String flowName,
    String filePath,
    String missingContext,
    String suggestedApproach,
    double confidence,
    GapReason reason,
    boolean userProvided,
    String selectedOption,
    String userAnswer
) {
    public AmbiguityGap {
        userProvided = false;
        selectedOption = null;
        userAnswer = null;
    }
}
```

Wait — Java records don't allow default values in compact constructors for fields that are already in the canonical constructor. The caller must provide them. Better approach: use a builder pattern or provide a secondary constructor.

Actually, Java records require all fields in the constructor. I can use a compact constructor to set defaults:

```java
public record AmbiguityGap(
    String flowId, String flowName, String filePath,
    String missingContext, String suggestedApproach,
    double confidence, GapReason reason,
    boolean userProvided, String selectedOption, String userAnswer
) {
    public AmbiguityGap(String flowId, String flowName, String filePath,
                        String missingContext, String suggestedApproach,
                        double confidence, GapReason reason) {
        this(flowId, flowName, filePath, missingContext, suggestedApproach,
             confidence, reason, false, null, null);
    }
}
```

This preserves backward compatibility: all existing callers that use the 7-arg constructor continue to compile unchanged.

### 3. `extraction/adapter/agent/action/QuarantineFlowAction.java`

**Constructor change:**

```java
public QuarantineFlowAction(QuarantineConfig config, UserInteractionService uis) {
    this.lowConfidenceThreshold = config != null ? config.resolvedAmbiguityConfidenceThreshold() : ...;
    this.maxUnresolvedCalls = config != null ? config.resolvedMaxUnresolvedCalls() : ...;
    this.uis = uis;
}
```

**evaluateQuarantine() change:**

Replace simple `return new QuarantineReason(...)` for checks 2-4 with:

```java
private QuarantineReason evaluateQuarantine(ExecutionFlow flow) {
    // Check 1: already quarantined + zero steps — no interaction possible
    if (flow.status() == FlowStatus.QUARANTINED && flow.steps().isEmpty()) {
        return new QuarantineReason("No traceable steps found...", "...", 0.1, GapReason.LOW_CONFIDENCE);
    }

    // Compute reason for checks 2-4
    QuarantineReason reason = computeQuarantineReason(flow);
    if (reason == null) return null; // clean flow

    // Interactive path
    if (uis.isInteractive()) {
        List<String> options = List.of(
            "Accept and quarantine",
            "Provide additional context for the LLM",
            "Dismiss — flow is correct as-is"
        );
        String choice = uis.select(options, reason.description());

        return switch (choice) {
            case "Dismiss — flow is correct as-is" -> null; // no gap
            case "Provide additional context for the LLM" -> {
                String answer = uis.ask(
                    "Describe what the LLM should know about this ambiguity:",
                    buildContext(flow, reason)
                );
                yield new QuarantineReason(
                    reason.description, reason.suggestedApproach,
                    reason.confidence, GapReason.USER_CLARIFIED,
                    true, answer
                );
            }
            default -> reason; // "Accept" → gap as computed, userProvided=false
        };
    }

    // NoOp path: existing behavior unchanged
    return reason;
}
```

Note: `QuarantineReason` needs new fields `userProvided, userAnswer`. Or — simpler — the interaction result is passed back to `quarantineWithResult()` which creates the `AmbiguityGap`. This way the gap creation logic stays in `quarantineWithResult()` and only the quarantine decision is moved.

Simplest approach: leave `evaluateQuarantine()` returning `QuarantineReason` as before, but make it take `UserInteractionService` as a parameter. The interaction happens inside `evaluateQuarantine()` before returning:

```java
private QuarantineReason evaluateQuarantine(ExecutionFlow flow) {
    check 1 → same
    
    QuarantineReason reason = computeQuarantineReason(flow);
    if (reason == null) return null;
    
    if (uis.isInteractive()) {
        String choice = uis.select(options, reason.description());
        if ("Dismiss".equals(choice)) return null;
        if ("Provide context".equals(choice)) {
            String answer = uis.ask("Your answer:", "");
            return new QuarantineReason(reason.description, reason.suggestedApproach,
                reason.confidence, GapReason.USER_CLARIFIED);
        }
    }
    return reason;
}
```

And update `QuarantineReason` record to include userProvided/userAnswer or just handle it in `quarantineWithResult()`. Actually, the simplest: add `userProvided` and `userAnswer` to `QuarantineReason`:

```java
record QuarantineReason(
    String description, String suggestedApproach,
    double confidence, GapReason gapReason,
    boolean userProvided, String userAnswer
) {
    QuarantineReason(String description, String suggestedApproach,
                     double confidence, GapReason gapReason) {
        this(description, suggestedApproach, confidence, gapReason, false, null);
    }
}
```

Then in `quarantineWithResult()`, when creating the AmbiguityGap:

```java
gaps.add(new AmbiguityGap(
    flow.flowId(), deriveFlowName(flow.entryPoint()),
    flow.entryPoint().filePath(),
    reason.description, reason.suggestedApproach,
    reason.confidence, reason.gapReason,
    reason.userProvided, null, reason.userAnswer
));
```

### 4. `extraction/adapter/agent/FunctionalRequirementAgent.java`

In `traceFlows()` (line 80-96), read `UserInteractionService` from context:

```java
@Action
public TracedFlowResult traceFlows(EntryPointDiscoveryResult discoveryResult, OperationContext context) {
    // ... existing code ...
    UserInteractionService uis = (UserInteractionService) context.get("userInteractionService");
    if (uis == null) { uis = new NoOpUserInteractionService(); }
    QuarantineFlowAction quarantineAction = new QuarantineFlowAction(quarantineConfig, uis);
    // ... rest ...
}
```

### 5. `extraction/ExtractionOrchestrator.java`

**New constructor overload and execute() overload:**

```java
public ExtractionResult execute(boolean dryRun, boolean force, boolean resume, boolean headless) {
    // ... existing execute() logic ...
    UserInteractionService uis = headless
        ? new NoOpUserInteractionService()
        : new InteractiveUserInteractionService();
    initialBlackboard.put("userInteractionService", uis);
    // ... rest ...
}
```

**Keep backward-compatible execute signatures:**

```java
public ExtractionResult execute(boolean dryRun, boolean force) {
    return execute(dryRun, force, false, false);
}
public ExtractionResult execute(boolean dryRun, boolean force, boolean resume) {
    return execute(dryRun, force, resume, false);
}
```

### 6. `infrastructure/cli/command/ExtractCommand.java`

Add `headless` param:

```java
@ShellOption(value = "--headless", defaultValue = "false",
             help = "Headless mode: suppress interactive prompts") boolean headless
```

Display mode in output when `true`. Pass to orchestrator:

```java
ExtractionResult result = extractionOrchestrator.execute(dryRun, force, resume, headless);
```

### 7. `infrastructure/cli/command/RunCommand.java`

Add `headless` param and forward to `extractCommand.extract()`:

```java
@ShellOption(value = "--headless", defaultValue = "false",
             help = "Headless mode: suppress interactive prompts") boolean headless
```

```java
sb.append(stripSuggestions(extractCommand.extract(manifestPath, dryRun, force, resume, headless))).append("\n");
```

### 8. `infrastructure/persistence/TaskStoreSchema.java`

Add `user_responses` table in `createSchemaIfNotExists()`:

```java
jdbc.execute("""
    CREATE TABLE IF NOT EXISTS user_responses (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        session_id TEXT NOT NULL,
        question TEXT NOT NULL,
        answer TEXT,
        created_at TEXT NOT NULL DEFAULT (datetime('now'))
    )
""");
```

Update log: `"Task store schema initialized with 6 tables"`.
Add to `dropAllTables()`: `jdbc.execute("DROP TABLE IF EXISTS user_responses")`.

### 9. `TaskStoreSchemaTest.java`

Update table count assertion from `5` to `6`. Add `PRAGMA table_info('user_responses')` assertion for column verification.

### 10. `QuarantineFlowActionTest.java`

Update constructor calls to pass `new NoOpUserInteractionService()`. All existing assertions must still pass (NoOp = same behavior as before).

### 11. `RunCommandTest.java`

Add test: `--headless true` flag is forwarded to `extractCommand.extract()`.

### 12. `ExtractCommandTest.java`

Add mock for `UserInteractionService` to constructor. Add test: `--headless true` prints "HEADLESS" in output.

---

## Implementation Order

| # | Step | Files | Tests after |
|---|------|-------|-------------|
| 1 | Domain model + SPI | `GapReason.java`, `AmbiguityGap.java`, `UserInteractionService.java`, `UserResponse.java` | `mvn test` — existing tests pass (backward-compatible overloaded constructor) |
| 2 | Implementations | `NoOpUserInteractionService.java`, `InteractiveUserInteractionService.java` | Manual verification + new unit tests |
| 3 | Action injection | `QuarantineFlowAction.java`, `FunctionalRequirementAgent.java`, `ExtractionOrchestrator.java` | `mvn test` — existing tests pass (NoOp preserves behavior) |
| 4 | CLI flags | `ExtractCommand.java`, `RunCommand.java` | `mvn test` — CLI command tests pass |
| 5 | SQLite persistence | `TaskStoreSchema.java`, `UserResponseStore.java` | `mvn test` — schema test updated to 6 tables |
| 6 | New tests | All 10 new test files | `mvn test` — 753 + 10+ new tests pass |

---

## Verification

- `mvn test` — 753 baseline + ~15 new tests, all pass
- `mvn spring-boot:run` → `extract` pauses for user input on ambiguous flows
- `extract --headless` prints "HEADLESS" and runs without interaction
- `extract --headless` produces same output as current `extract` (no behavioral regression)
