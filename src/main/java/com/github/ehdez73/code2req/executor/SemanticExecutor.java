package com.github.ehdez73.code2req.executor;

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
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
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
    private final ContextBudgetCalculator budgetCalculator;
    private final SimulationStub simulationStub;

    public SemanticExecutor(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                            ExecutionFindingStore findingStore,
                            TaskStore taskStore,
                            ContextBudgetCalculator budgetCalculator,
                            SimulationStub simulationStub) {
        ChatClient.Builder builder = chatClientBuilderProvider != null ? chatClientBuilderProvider.getIfAvailable() : null;
        this.chatClient = builder != null
            ? builder.defaultAdvisors(new SimpleLoggerAdvisor()).build()
            : null;
        this.findingStore = findingStore;
        this.taskStore = taskStore;
        this.budgetCalculator = budgetCalculator;
        this.simulationStub = simulationStub;
    }

    @Async("orchestratorTaskExecutor")
    public CompletableFuture<ExecutionFinding> enrich(Task task, PlannerDecision decision,
                                                       String sourceContent, String testContent,
                                                       String structuralContextJson, boolean dryRun) {
        try {
            ExecutionFinding finding;
            String resultJson;

            if (dryRun) {
                resultJson = simulationStub.generateEnrichment(task.taskId(), task.filePath(), task.contentType());
                finding = MAPPER.readValue(resultJson, ExecutionFinding.class);
            } else {
                if (chatClient == null) {
                    throw new IllegalStateException(
                        "ChatClient not available: configure spring.ai.openai.* properties or use --dry-run");
                }
                finding = callLlm(task, sourceContent, testContent, structuralContextJson);
                resultJson = MAPPER.writeValueAsString(finding);
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

    private ExecutionFinding callLlm(Task task, String sourceContent, String testContent, String structuralContextJson) {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(task, sourceContent, testContent, structuralContextJson);

        boolean needsSummarization = budgetCalculator.needsSummarization(
            sourceContent, testContent, structuralContextJson != null ? structuralContextJson : "");
        if (needsSummarization) {
            log.info("Context budget exceeded 80% for task {}, triggering pre-summarization", task.taskId());
            userPrompt = summarizeContext(userPrompt);
        }

        var outputConverter = new BeanOutputConverter<>(ExecutionFinding.class);
        var options = OpenAiChatOptions.builder()
            .responseFormat(ResponseFormat.builder()
                .type(ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(outputConverter.getJsonSchema())
                .build())
            .build();

        int maxRetries = 3;
        long delayMs = 2000;
        double multiplier = 2.0;
        long capMs = 60000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(options)
                    .call()
                    .content();

                //   log.debug(response);
                if (response == null || response.isBlank()) {
                    throw new RuntimeException("LLM returned empty response");
                }
                return outputConverter.convert(response);

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

            Populate each section of the output JSON as follows:

            metadata
              Copy task_id, target_name, file_path, module_tag and tech_profile from the task context.
              Set timestamp to the current date/time.

            business_abstraction.purpose
              1-2 sentence high-level summary of this file's business responsibility.

            business_abstraction.happy_paths[]
              High-level describe each normal (success) execution flow the code supports.

            business_rules_and_guardrails.validations[]
              List input constraints and business rejection rules found in the code.
              For each: field_or_context = the validated input/context; rule = the constraint;
              error_behavior = what happens on failure.

            business_rules_and_guardrails.edge_cases[]
              Boundary conditions and error states the code handles explicitly.

            test_insights[]
              If a test file is provided, extract: scenario_verified (what the test checks)
              and hidden_rule_uncovered (assertions that reveal non-obvious rules).

            architectural_connections
              List HTTP endpoints, event subscriptions, scheduled triggers (inbound) and
              HTTP calls, event publications (outbound) found in the code.
              Use empty arrays/objects if none exist — do not omit the section.

            discovered_dependencies[]
              List any references to files not already in the indexed call graph.
              Use an empty array if no new dependencies are found.
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
