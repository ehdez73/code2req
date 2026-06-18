package com.github.ehdez73.code2req.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ehdez73.code2req.model.ExecutionFinding;
import com.github.ehdez73.code2req.model.PlannerDecision;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.store.ExecutionFindingStore;
import com.github.ehdez73.code2req.store.FindingType;
import com.github.ehdez73.code2req.store.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class SemanticExecutor {

    private static final Logger log = LoggerFactory.getLogger(SemanticExecutor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatClient chatClient;
    private final ExecutionFindingStore findingStore;
    private final TaskStore taskStore;
    private final ExecutionFindingValidator validator;
    private final ContextBudgetCalculator budgetCalculator;
    private final SimulationStub simulationStub;

    public SemanticExecutor(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                            ExecutionFindingStore findingStore,
                            TaskStore taskStore,
                            ExecutionFindingValidator validator,
                            ContextBudgetCalculator budgetCalculator,
                            SimulationStub simulationStub) {
        ChatClient.Builder builder = chatClientBuilderProvider != null ? chatClientBuilderProvider.getIfAvailable() : null;
        this.chatClient = builder != null ? builder.build() : null;
        this.findingStore = findingStore;
        this.taskStore = taskStore;
        this.validator = validator;
        this.budgetCalculator = budgetCalculator;
        this.simulationStub = simulationStub;
    }

    @Async("orchestratorTaskExecutor")
    public CompletableFuture<ExecutionFinding> enrich(Task task, PlannerDecision decision,
                                                       String sourceContent, String testContent,
                                                       String structuralContextJson, boolean dryRun) {
        try {
            String resultJson;
            if (dryRun) {
                resultJson = simulationStub.generateEnrichment(task.taskId(), task.filePath(), task.contentType());
            } else {
                if (chatClient == null) {
                    throw new IllegalStateException(
                        "ChatClient not available: configure spring.ai.openai.* properties or use --dry-run");
                }
                resultJson = callLlm(task, sourceContent, testContent, structuralContextJson);
            }

            var validationErrors = validator.validate(resultJson);
            if (!validationErrors.isEmpty()) {
                log.warn("Schema validation failed for task {}: {}", task.taskId(), validationErrors);
                taskStore.updateStatus(task.taskId(), TaskStatus.FAILED);
                return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Output failed §4 schema validation: " + validationErrors));
            }

            ExecutionFinding finding;
            try {
                finding = MAPPER.readValue(resultJson, ExecutionFinding.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to parse enriched JSON for task {}: {}", task.taskId(), e.getMessage());
                taskStore.updateStatus(task.taskId(), TaskStatus.FAILED);
                return CompletableFuture.failedFuture(e);
            }

            findingStore.save(task.taskId(), FindingType.SEMANTIC_ENRICHMENT, resultJson, true);
            taskStore.updateStatus(task.taskId(), TaskStatus.SUCCESS);

            if (finding.discoveredDependencies() != null && !finding.discoveredDependencies().isEmpty()) {
                log.info("Task {} discovered {} new dependencies", task.taskId(), finding.discoveredDependencies().size());
            }

            log.info("Successfully enriched task {} ({})", task.taskId(), task.filePath());
            return CompletableFuture.completedFuture(finding);

        } catch (Exception e) {
            log.error("Failed to enrich task {}: {}", task.taskId(), e.getMessage());
            taskStore.updateStatus(task.taskId(), TaskStatus.FAILED);
            return CompletableFuture.failedFuture(e);
        }
    }

    private String callLlm(Task task, String sourceContent, String testContent, String structuralContextJson) {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(task, sourceContent, testContent, structuralContextJson);

        boolean needsSummarization = budgetCalculator.needsSummarization(
            sourceContent, testContent, structuralContextJson != null ? structuralContextJson : "");
        if (needsSummarization) {
            log.info("Context budget exceeded 80% for task {}, triggering pre-summarization", task.taskId());
            userPrompt = summarizeContext(userPrompt);
        }

        int maxRetries = 3;
        long delayMs = 2000;
        double multiplier = 2.0;
        long capMs = 60000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

                if (response == null || response.isBlank()) {
                    throw new RuntimeException("LLM returned empty response");
                }
                return response;

            } catch (Exception e) {
                boolean isRateLimit = e.getMessage() != null && e.getMessage().contains("429");
                if (isRateLimit && attempt < maxRetries) {
                    log.warn("Rate limited (attempt {}/{}), backing off {}ms for task {}",
                        attempt, maxRetries, delayMs, task.taskId());
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                    delayMs = (long) Math.min(delayMs * (long) multiplier, capMs);
                } else {
                    throw new RuntimeException("LLM call failed after " + attempt + " attempt(s)", e);
                }
            }
        }
        throw new RuntimeException("LLM call failed after " + maxRetries + " retries");
    }

    private String buildSystemPrompt() {
        return """
            You are a code analyst specializing in extracting business semantics from source code.
            The structural dependencies (call graph, database access, endpoint mappings, event links)
            have ALREADY been resolved. Do NOT attempt to resolve structural dependencies.
            Focus exclusively on:
            1. Business purpose of each method (1-2 sentences)
            2. Implicit validation rules not captured by annotations
            3. Inferred SQL for Spring Data derived query methods
            4. Business logic interpretation of stored procedures
            5. Edge cases extracted from test file assertions
            Output must conform to the ExecutionFinding JSON Schema exactly.
            """.stripIndent();
    }

    private String buildUserPrompt(Task task, String sourceContent, String testContent, String structuralContextJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("### TASK\n");
        sb.append("File: ").append(task.filePath()).append("\n");
        sb.append("Module: ").append(task.contentType()).append("\n\n");

        if (structuralContextJson != null && !structuralContextJson.isBlank()) {
            sb.append("### STRUCTURAL CONTEXT (pre-resolved)\n");
            sb.append(structuralContextJson).append("\n\n");
        }

        sb.append("### SOURCE CODE\n");
        sb.append(sourceContent).append("\n");

        if (testContent != null && !testContent.isBlank()) {
            sb.append("\n### TEST FILE CONTENT\n");
            sb.append(testContent).append("\n");
        }

        return sb.toString();
    }

    private String summarizeContext(String userPrompt) {
        try {
            String summary = chatClient.prompt()
                .system("Summarize the following code context concisely, preserving all business-relevant details.")
                .user(userPrompt)
                .call()
                .content();
            return summary != null ? summary : userPrompt;
        } catch (Exception e) {
            log.warn("Pre-summarization failed, using original context: {}", e.getMessage());
            return userPrompt;
        }
    }
}
