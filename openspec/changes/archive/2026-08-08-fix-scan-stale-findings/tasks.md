## 1. ExecutionFindingStore — deleteByFilePath

- [x] 1.1 Add `deleteByFilePath(String filePath)` method to `ExecutionFindingStore` that deletes findings by joining on `tasks.file_path`
- [x] 1.2 Add unit test in `ExecutionFindingStoreTest` verifying deletion removes only the target file's findings, not other files'

## 2. IndexingOrchestrator — call deleteByFilePath before persistence

- [x] 2.1 Add `executionFindingStore.deleteByFilePath(fp)` call in `analyzeSingleFile()` before the existing `deleteByTaskId(taskId)` line
- [x] 2.2 Add integration test in `IndexingOrchestratorTest` simulating two scan passes over the same file (different content) and asserting old findings are purged

## 3. Verification

- [x] 3.1 Run `mvn clean test` to confirm all existing and new tests pass
