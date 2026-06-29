# Migration: Hexagonal Package Structure by Phase

**Goal:** Reorganize flat package structure into hexagonal-by-phase:
`common/`, `indexing/`, `enrichment/`, `extraction/`, `infrastructure/`

**Date:** 2026-06-29
**Status:** Completed

---

## Architecture Principles

```
┌──────────────────────┐
│   infrastructure/    │  ← Input adapters (CLI), Output adapters (JDBC, LLM), Config
├──────────────────────┤
│   indexing/          │  ← Phase 1: AST indexing (domain/ → application/ → adapter/)
│   enrichment/        │  ← Phase 2: LLM enrichment (domain/ → application/ → adapter/)
│   extraction/        │  ← Phase 3: GOAP extraction (domain/ → application/ → adapter/)
├──────────────────────┤
│   common/            │  ← Shared domain models + output port interfaces
└──────────────────────┘
```

Key rule: A class belongs to the phase whose *state* or *behavior* it knows about.

---

## Step 1: Create target directories

- [x] `common/domain/`
- [x] `common/port/`
- [x] `indexing/domain/model/`
- [x] `indexing/domain/analyzer/` (+ sub-packages: declaration, bean/java, bean/xml, callgraph, db/detector, web/endpoint/detector, web/template, httpclient/detector, event/broker/kafka, event/broker/rabbitmq, event/broker/activemq, event/link, event/listener, validator, scheduledtask)
- [x] `indexing/domain/service/`
- [x] `indexing/application/port/input/`
- [x] `indexing/application/port/output/`
- [x] `indexing/application/service/`
- [x] `indexing/adapter/output/`
- [x] `enrichment/domain/model/`
- [x] `enrichment/domain/service/`
- [x] `enrichment/domain/planner/`
- [x] `enrichment/application/port/input/`
- [x] `enrichment/application/service/`
- [x] `enrichment/adapter/llm/testmining/`
- [x] `extraction/domain/model/`
- [x] `extraction/domain/service/`
- [x] `extraction/application/port/input/`
- [x] `extraction/application/service/`
- [x] `extraction/adapter/agent/`
- [x] `extraction/adapter/output/`
- [x] `infrastructure/cli/command/`
- [x] `infrastructure/cli/support/`
- [x] `infrastructure/persistence/`
- [x] `infrastructure/config/`
- [x] `infrastructure/redaction/`
- [x] `infrastructure/file/`
- [x] `infrastructure/snapshot/`

## Step 2: Create common/port/ interfaces

- [x] `TaskRepository.java` — extracted from `TaskStore`
- [x] `ExecutionFindingRepository.java` — extracted from `ExecutionFindingStore`
- [x] `TopicLinkRepository.java` — extracted from `TopicLinkStore`
- [x] `FloatingLinkRepository.java` — extracted from `FloatingLinkStore`
- [x] `MetricsRepository.java` — extracted from `MetricsStore`

## Step 3: Create use case interfaces (application/port/input/)

- [x] `indexing/application/port/input/ScanProjectUseCase.java`
- [x] `enrichment/application/port/input/QualifyTasksUseCase.java`
- [x] `enrichment/application/port/input/EnrichProjectUseCase.java`
- [x] `enrichment/application/port/input/ResumeEnrichmentUseCase.java`
- [x] `extraction/application/port/input/ExtractRequirementsUseCase.java`

## Step 4: Create output port interfaces (application/port/output/)

- [x] `indexing/application/port/output/IndexWriter.java`

## Step 5: Move common/domain/ (from model/)

- [x] `Task.java` → `common/domain/`
- [x] `TaskStatus.java` → `common/domain/`
- [x] `Metric.java` → `common/domain/`
- [x] `ScanTarget.java` → `common/domain/`
- [x] `ProjectManifest.java` → `common/domain/`
- [x] `OutputConfig.java` → `common/domain/`
- [x] `Dependency.java` → `common/domain/`
- [x] `DependencyGraph.java` → `common/domain/`

## Step 6: Move indexing/domain/ (from analyzer/ + pipeline/ + resolver/)

### model/
- [x] `AnalysisResult.java` → `indexing/domain/model/`
- [x] `AnalysisFinding.java` → `indexing/domain/model/`
- [x] `AnalysisContext.java` → `indexing/domain/model/`
- [x] `AnalysisResultBuilder.java` → `indexing/domain/model/`
- [x] `ComponentInfo.java` → `indexing/domain/model/`
- [x] `BeanMethodInfo.java` → `indexing/domain/model/`
- [x] `XmlBeanInfo.java` → `indexing/domain/model/`
- [x] `XmlComponentScanInfo.java` → `indexing/domain/model/`
- [x] `XmlNamespaceBeanInfo.java` → `indexing/domain/model/`
- [x] `XmlAopConfigInfo.java` → `indexing/domain/model/`
- [x] `CallGraphEdge.java` → `indexing/domain/model/`
- [x] `DbAccessInfo.java` → `indexing/domain/model/`
- [x] `DbAccessType.java` → `indexing/domain/model/`
- [x] `EndpointInfo.java` → `indexing/domain/model/`
- [x] `TemplateFormInfo.java` → `indexing/domain/model/`
- [x] `TemplateLinkInfo.java` → `indexing/domain/model/`
- [x] `OutboundHttpCallInfo.java` → `indexing/domain/model/`
- [x] `OutboundHttpClientType.java` → `indexing/domain/model/`
- [x] `FloatingLinkInfo.java` → `indexing/domain/model/`
- [x] `TopicLink.java` → `indexing/domain/model/`
- [x] `KafkaInfo.java` → `indexing/domain/model/`
- [x] `KafkaPublisherInfo.java` → `indexing/domain/model/`
- [x] `RabbitMqInfo.java` → `indexing/domain/model/`
- [x] `RabbitMqPublisherInfo.java` → `indexing/domain/model/`
- [x] `ActiveMqInfo.java` → `indexing/domain/model/`
- [x] `ActiveMqPublisherInfo.java` → `indexing/domain/model/`
- [x] `EventListenerInfo.java` → `indexing/domain/model/`
- [x] `EventPublisherInfo.java` → `indexing/domain/model/`
- [x] `MethodCallInfo.java` → `indexing/domain/model/`
- [x] `ValidatorInfo.java` → `indexing/domain/model/`
- [x] `ScheduledTaskInfo.java` → `indexing/domain/model/`
- [x] `DeclarationInfo.java` → `indexing/domain/model/`
- [x] `ExcludeResult.java` → `indexing/domain/model/`
- [x] `RedactionResult.java` → `indexing/domain/model/`
- [x] `ScanPipelineResult.java` → `indexing/domain/model/`
- [x] `DbAccessHelper.java` → `indexing/domain/model/`

### service/ (renamed)
- [x] `JavaAstAnalyzer.java` → `indexing/domain/service/AstAnalysisEngine.java` (rename)
- [x] `MavenDependencyResolver.java` → `indexing/domain/service/`
- [x] `ExcludeFilter.java` → `indexing/domain/service/`
- [x] `GlobalDeclarationRegistry.java` → `indexing/domain/service/`
- [x] `JavaVersionMapper.java` → `indexing/domain/service/`
- [x] `SecretRedactor.java` → `indexing/domain/service/`

### analyzer/ (move as-is)
- [x] All AST visitors (ComponentVisitor, BeanMethodVisitor, XmlBeanAnalyzer, CallGraphVisitor, DbAccessVisitor, EndpointVisitor, TemplateAnalyzer, TemplateLinkResolver, OutboundHttpVisitor, FloatingLinkResolver, TopicLinkResolver, KafkaVisitor, RabbitMqVisitor, ActiveMqVisitor, EventListenerVisitor, ValidatorVisitor, ScheduledTaskVisitor) → `indexing/domain/analyzer/`
- [x] All SPI interfaces (AstAnalysisVisitor, EndpointDetector, DbAccessDetector, HttpClientDetector, TemplateParser, TopicLinkResolverStrategy) → `indexing/domain/analyzer/`
- [x] All detector implementations → `indexing/domain/analyzer/{sub-package}/`
- [x] `WebXmlAnalyzer.java` → `indexing/domain/analyzer/web/endpoint/`
- [x] `JspTemplateParser.java`, `ThymeleafTemplateParser.java` → `indexing/domain/analyzer/web/template/`
- [x] `SpringXmlNamespaceRegistry.java` → `indexing/domain/analyzer/bean/xml/`

## Step 7: Move indexing/application/ + indexing/adapter/

### application/service/
- [x] Create `ScanProjectService.java` (extracted from `ScanPipeline`, implements `ScanProjectUseCase`)

### adapter/output/
- [x] Create `JsonIndexWriter.java` (rename from `IndexWriter`, implements `IndexWriter` port)

## Step 8: Move enrichment/domain/ (from planner/ + orchestrator/ + model/)

- [x] `PlannerDecision.java` → `enrichment/domain/model/`
- [x] `QualificationReason.java` → `enrichment/domain/model/`
- [x] `ExecutionFinding.java` → `enrichment/domain/model/`
- [x] `ExecutionConfig.java` → `enrichment/domain/model/`
- [x] `ExecutionMode.java` → `enrichment/domain/model/`
- [x] `CompletionStatus.java` → `enrichment/domain/model/`
- [x] `OrphanRecoveryResult.java` → `enrichment/domain/model/`
- [x] `OrphanRecovery.java` → `enrichment/domain/service/`
- [x] `EnrichmentDag.java` → `enrichment/domain/service/`
- [x] `BranchState.java` → `enrichment/domain/service/`
- [x] `PlanningContext.java` → `enrichment/domain/planner/`
- [x] `QualificationRule.java` → `enrichment/domain/planner/`

## Step 9: Move enrichment/application/ + enrichment/adapter/

### application/service/
- [x] Create `QualifyTasksService.java` (extracted from `Phase2Planner`, implements `QualifyTasksUseCase`)
- [x] Create `EnrichProjectService.java` (extracted from `Phase2Orchestrator`, implements `EnrichProjectUseCase`)
- [x] Create `ResumeEnrichmentService.java` (implements `ResumeEnrichmentUseCase`)

### adapter/llm/
- [x] Create `LlmEnrichmentService.java` (rename from `SemanticExecutor`)
- [x] `SimulationStub.java` → `enrichment/adapter/llm/`
- [x] `ContextBudgetCalculator.java` → `enrichment/adapter/llm/`
- [x] `ExecutionFindingParser.java` → `enrichment/adapter/llm/`
- [x] `ExecutionFindingValidator.java` → `enrichment/adapter/llm/`
- [x] `TestFileMatcher.java` → `enrichment/adapter/llm/testmining/`
- [x] `TestAssertionExtractor.java` → `enrichment/adapter/llm/testmining/`
- [x] `PairedExecutionResolver.java` → `enrichment/adapter/llm/testmining/`

## Step 10: Move extraction/ (from synthesis/ + agent/)

### domain/model/
- [x] `CodebaseKnowledge.java` → `extraction/domain/model/`
- [x] `StructuralGraph.java` → `extraction/domain/model/`
- [x] `SemanticEnrichment.java` → `extraction/domain/model/`
- [x] `LinkRegistry.java` → `extraction/domain/model/`
- [x] `MethodIdentifier.java` → `extraction/domain/model/`
- [x] Create `ExtractionResult.java` (rename from `Phase3Result`)

### domain/service/
- [x] Create `CodebaseKnowledgeBuilder.java` (if extracted)

### application/service/
- [x] Create `ExtractRequirementsService.java` (rename from `Phase3Orchestrator`, implements `ExtractRequirementsUseCase`)

### adapter/agent/
- [x] `HelloAgent.java` → `extraction/adapter/agent/`
- [x] `HelloResponse.java` → `extraction/adapter/agent/`
- [x] `SuggestedName.java` → `extraction/adapter/agent/`
- [x] `SuggestedAge.java` → `extraction/adapter/agent/`
- [x] `SuggestedSurname.java` → `extraction/adapter/agent/`

## Step 11: Move infrastructure/

### cli/command/
- [x] `ScanCommand.java` → `infrastructure/cli/command/`
- [x] `RunCommand.java` → `infrastructure/cli/command/`
- [x] `PlanCommand.java` → `infrastructure/cli/command/`
- [x] `StatusCommand.java` → `infrastructure/cli/command/`
- [x] `TaskCommands.java` → `infrastructure/cli/command/`
- [x] `CleanCommand.java` → `infrastructure/cli/command/`
- [x] `SnapshotCommand.java` → `infrastructure/cli/command/`
- [x] `ValidateCommand.java` → `infrastructure/cli/command/`
- [x] `HelloCommand.java` → `infrastructure/cli/command/`
- [x] `Application.java` (entrypoint) → `infrastructure/cli/command/`

### cli/support/
- [x] `SuggestionService.java` → `infrastructure/cli/support/`
- [x] `CommandSuggestionAspect.java` → `infrastructure/cli/support/`

### persistence/
- [x] `TaskStore.java` → `infrastructure/persistence/` (implements `TaskRepository`)
- [x] `ExecutionFindingStore.java` → `infrastructure/persistence/` (implements `ExecutionFindingRepository`)
- [x] `TopicLinkStore.java` → `infrastructure/persistence/` (implements `TopicLinkRepository`)
- [x] `FloatingLinkStore.java` → `infrastructure/persistence/` (implements `FloatingLinkRepository`)
- [x] `MetricsStore.java` → `infrastructure/persistence/` (implements `MetricsRepository`)
- [x] `TaskStoreSchema.java` → `infrastructure/persistence/`
- [x] `FindingType.java` → `infrastructure/persistence/`
- [x] `TaskIdHasher.java` → `infrastructure/persistence/`

### config/
- [x] `AppConfig.java` → `infrastructure/config/`
- [x] `ManifestLoader.java` → `infrastructure/config/`
- [x] `ManifestValidator.java` → `infrastructure/config/`
- [x] `ManifestValidationResult.java` → `infrastructure/config/`

### file/
- [x] `FilePathResolver.java` → `infrastructure/file/`

### snapshot/
- [x] `RefreshableDataSource.java` → `infrastructure/snapshot/`
- [x] `SnapshotService.java` → `infrastructure/snapshot/`

## Step 12: Update all imports across ~188 files

- [x] Run sed/regex to update package prefixes
- [x] Handle special cases (split packages, renamed classes)

## Step 13: Create package-info.java files

- [x] `common/domain/package-info.java`
- [x] `common/port/package-info.java`
- [x] `indexing/domain/package-info.java`
- [x] `indexing/application/package-info.java`
- [x] `enrichment/domain/package-info.java`
- [x] `enrichment/application/package-info.java`
- [x] `extraction/domain/package-info.java`
- [x] `extraction/application/package-info.java`
- [x] `infrastructure/cli/package-info.java`
- [x] `infrastructure/persistence/package-info.java`

## Step 14: Update documentation

- [x] `docs/PLAN.md` — Update class/package references
- [x] `docs/PLAN2.md` — Update class/package references
- [x] `docs/PLAN-Phase3.md` — Update `synthesis/` → `extraction/` references
- [x] `docs/PLAN-Phase3-steps.md` — Update package references
- [x] `docs/sdlc/tech-stack.md` — Add Package Architecture section
- [x] `docs/sdlc/adrs/ADR-006-hexagonal-package-structure.md` — New ADR
- [x] `.agents/skills/spring/SKILL.md` — Update package references

## Step 15: Verify

- [x] `mvn clean compile` — zero errors
- [x] `mvn clean test` — all tests pass
