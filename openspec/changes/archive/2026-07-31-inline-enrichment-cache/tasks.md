## 1. Test

- [x] 1.1 Create `EnrichFlowActionTest` with a test verifying that a file appearing in two flows is enriched only once via `LlmEnrichmentService`
- [x] 1.2 Add test verifying that the knowledge cache (SQLite-backed `SemanticEnrichment`) is still checked before the inline cache
- [x] 1.3 Add test verifying that the inline cache is cleared between `enrich()` calls
- [x] 1.4 Run `mvn clean test -pl .` to verify new tests fail before implementation

## 2. Implementation

- [x] 2.1 Add a `private final Map<String, CompletableFuture<Void>> enrichmentCache` field to `EnrichFlowAction` (initialized as `new ConcurrentHashMap<>()`)
- [x] 2.2 In `enrichOneFlow()`, wrap the `CompletableFuture.runAsync()` call with `enrichmentCache.computeIfAbsent(filePath, ...)` so files already in the cache reuse the existing future
- [x] 2.3 Add `enrichmentCache.clear()` at the end of `enrich()` (after the flow loop, before returning)
- [x] 2.4 Run `mvn clean compile` to verify compilation

## 3. Verification

- [x] 3.1 Run `mvn clean test` and confirm all tests pass (new and existing)
- [x] 3.2 Run `mvn spring-boot:run` and execute a full `extract` on `spring-petclinic` to verify no regressions in enrichment behavior
