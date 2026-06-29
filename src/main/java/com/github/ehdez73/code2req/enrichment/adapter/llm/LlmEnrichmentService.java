package com.github.ehdez73.code2req.enrichment.adapter.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.ehdez73.code2req.enrichment.adapter.llm.testmining.TestAssertionExtractor;
import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionFinding;
import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionConfig;
import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionMode;
import com.github.ehdez73.code2req.enrichment.domain.model.PlannerDecision;
import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FindingType;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
public class LlmEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(LlmEnrichmentService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatClient chatClient;
    private final ExecutionFindingStore findingStore;
    private final TaskStore taskStore;
    private final ContextBudgetCalculator budgetCalculator;
    private final SimulationStub simulationStub;
    private final ExecutionFindingValidator validator;
    private final ExecutionFindingParser parser;
    private final TestAssertionExtractor assertionExtractor;
    private final ExecutionConfig executionConfig;
    private final Executor taskExecutor;
    private final TransactionTemplate transactionTemplate;

    public LlmEnrichmentService(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                                ExecutionFindingStore findingStore,
                                TaskStore taskStore,
                                ContextBudgetCalculator budgetCalculator,
                                SimulationStub simulationStub,
                                ExecutionFindingValidator validator,
                                ExecutionFindingParser parser,
                                TestAssertionExtractor assertionExtractor,
                                ExecutionConfig executionConfig,
                                @Qualifier("orchestratorTaskExecutor") Executor taskExecutor,
                                TransactionTemplate transactionTemplate) {
        this.findingStore = findingStore;
        this.taskStore = taskStore;
        this.budgetCalculator = budgetCalculator;
        this.simulationStub = simulationStub;
        this.validator = validator;
        this.parser = parser;
        this.assertionExtractor = assertionExtractor;
        this.executionConfig = executionConfig;
        this.taskExecutor = taskExecutor;
        this.transactionTemplate = transactionTemplate;
        this.chatClient = chatClientBuilderProvider != null
            ? chatClientBuilderProvider.getObject().defaultAdvisors(new SimpleLoggerAdvisor()).build()
            : null;
    }

    public CompletableFuture<ExecutionFinding> enrich(Task task, PlannerDecision decision,
                                                       String sourceContent, String testContent,
                                                       String structuralContextJson, boolean dryRun) {
        log.info("Enriching task {}, {}", task.taskId(), task.filePath());

        if (executionConfig.resolvedExecutionMode() == ExecutionMode.SYNC) {
            return executeSync(task, decision, sourceContent, testContent, structuralContextJson, dryRun);
        }
        return executeAsync(task, decision, sourceContent, testContent, structuralContextJson, dryRun);
    }

    private CompletableFuture<ExecutionFinding> executeSync(Task task, PlannerDecision decision,
                                                             String sourceContent, String testContent,
                                                             String structuralContextJson, boolean dryRun) {
        try {
            ExecutionFinding finding = doEnrich(task, decision, sourceContent, testContent, structuralContextJson, dryRun);
            return CompletableFuture.completedFuture(finding);
        } catch (Exception e) {
            log.error("Failed to enrich task {}: {}", task.taskId(), e.getMessage());
            taskStore.updateStatus(task.taskId(), TaskStatus.ENRICH_FAILED);
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<ExecutionFinding> executeAsync(Task task, PlannerDecision decision,
                                                              String sourceContent, String testContent,
                                                              String structuralContextJson, boolean dryRun) {
        var future = new CompletableFuture<ExecutionFinding>();
        Runnable work = () -> {
            try {
                future.complete(doEnrich(task, decision, sourceContent, testContent, structuralContextJson, dryRun));
            } catch (Exception e) {
                log.error("Failed to enrich task {}: {}", task.taskId(), e.getMessage());
                taskStore.updateStatus(task.taskId(), TaskStatus.ENRICH_FAILED);
                future.completeExceptionally(e);
            }
        };
        if (taskExecutor != null) {
            taskExecutor.execute(work);
        } else {
            work.run();
        }
        return future;
    }

    private ExecutionFinding doEnrich(Task task, PlannerDecision decision,
                                       String sourceContent, String testContent,
                                       String structuralContextJson, boolean dryRun) throws Exception {
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

        transactionTemplate.executeWithoutResult(txStatus -> {
            findingStore.save(task.taskId(), FindingType.SEMANTIC_ENRICHMENT, resultJson, true);
            taskStore.updateStatus(task.taskId(), TaskStatus.ENRICHED);
        });

        if (finding.discoveredDependencies() != null && !finding.discoveredDependencies().isEmpty()) {
            var depDetails = finding.discoveredDependencies().stream()
                .map(d -> d.filePath() + " (" + d.reason() + ")")
                .collect(java.util.stream.Collectors.joining(", "));
            log.info("Task {} discovered {} new dependencies: [{}]", task.taskId(), finding.discoveredDependencies().size(), depDetails);
        }

        log.info("Successfully enriched task {} ({})", task.taskId(), task.filePath());
        return finding;
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
        String rawSchema = outputConverter.getJsonSchema();
        String strictSchema = buildStrictSchema(rawSchema);

        var responseFormat = executionConfig.resolvedStrictResponseFormat()
            ? ResponseFormat.builder()
                .type(ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(strictSchema)
                .build()
            : ResponseFormat.builder()
                .type(ResponseFormat.Type.JSON_OBJECT)
                .build();

        var options = OpenAiChatOptions.builder()
            .httpHeaders(Map.of(
                    "X-OpenRouter-Plugins", "[{\"id\":\"response-healing\"}]"
            ))
            .responseFormat(responseFormat)
            .build();

        int maxRetries = 3;
        long delayMs = 2000;
        double multiplier = 2.0;
        long capMs = 60000;

        String response = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(options)
                    .call()
                    .content();

                if (response == null || response.isBlank()) {
                    throw new RuntimeException("LLM returned empty response");
                }

                String sanitized = parser.sanitize(response);
                String normalized = parser.normalize(sanitized);

                ExecutionFinding finding;
                try {
                    finding = outputConverter.convert(normalized);
                } catch (Exception strictError) {
                    log.warn("Strict JSON parsing failed for task {}, trying lenient fallback: {}",
                        task.taskId(), strictError.getMessage());
                    finding = parser.parseLenient(sanitized);
                }

                if (validator != null) {
                    try {
                        Set<ValidationMessage> violations = validator.validate(
                            MAPPER.writeValueAsString(finding));
                        if (!violations.isEmpty()) {
                            log.warn("Schema validation found {} issue(s) for task {}: {}",
                                violations.size(), task.taskId(), violations);
                        }
                    } catch (Exception valError) {
                        log.warn("Schema validation failed for task {}: {}", task.taskId(), valError.getMessage());
                    }
                }

                return finding;

            } catch (Exception e) {
                boolean isRateLimit = e.getMessage() != null && e.getMessage().contains("429");
                long backoffMs = isRateLimit ? delayMs : 1000L;
                if (attempt < maxRetries) {
                    if (!isRateLimit && response != null) {
                        userPrompt = buildErrorFeedbackPrompt(
                            task, sourceContent, testContent, structuralContextJson,
                            response, e.getMessage());
                        log.warn("Recoverable error (attempt {}/{}), feeding back to LLM for task {}: {}",
                            attempt, maxRetries, task.taskId(), e.getMessage());
                    } else {
                        log.warn("Recoverable error (attempt {}/{}), retrying in {}ms for task {}: {}",
                            attempt, maxRetries, backoffMs, task.taskId(), e.getMessage());
                    }
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                    if (isRateLimit) {
                        delayMs = (long) Math.min(delayMs * (long) multiplier, capMs);
                    }
                } else {
                    throw new RuntimeException("LLM call failed after " + attempt + " attempt(s)", e);
                }
            }
        }
        throw new RuntimeException("LLM call failed after " + maxRetries + " retries");
    }

private String buildSystemPrompt() {
    return """
        You are a code analyst. Your ONLY output is a single JSON object — no markdown fences, \
        no prose, no explanation. Any non-JSON output will break the pipeline.

        STRICT OUTPUT CONTRACT
        ─────────────────────
        • Output MUST start with `{` and end with `}`.
        • All string values must be valid JSON strings (escape quotes, newlines, backslashes).
        • Arrays that have no entries MUST be `[]` — never omit them.
        • Objects that have no entries MUST be `{}` — never omit them.
        • Do NOT add fields not listed in the schema below.
        • Do NOT include comments or trailing commas.

        SCHEMA (fill every field)
        ─────────────────────────
        {
          "metadata": {
            "task_id":      "<copy from task context>",
            "target_name":  "<copy from task context>",
            "file_path":    "<copy from task context>",
            "tech_profile": "<copy from task context>",
            "module_tag":   "<copy from task context>",
            "timestamp":    "<ISO-8601, e.g. 2025-06-01T12:00:00>"
          },
          "business_abstraction": {
            "purpose": "<1-2 sentence business responsibility of this file>",
            "happy_paths": [
              { "flow_name": "<short name>", "description": "<normal success flow>" }
            ]
          },
          "business_rules_and_guardrails": {
            "validations": [
              {
                "field_or_context": "<validated input or context>",
                "rule":             "<constraint>",
                "error_behavior":   "<what happens on failure>"
              }
            ],
            "edge_cases": [
              { "scenario": "<boundary or error state>", "business_consequence": "<impact>" }
            ]
          },
          "test_insights": [
            {
              "test_file_path":       "<path to test file>",
              "scenario_verified":    "<what the test checks>",
              "hidden_rule_uncovered": "<non-obvious rule revealed by assertions>"
            }
          ],
          "architectural_connections": {
            "inbound": {
              "http_endpoints":    [ { "method": "<GET|POST|…>", "path_pattern": "<path>", "description": "<purpose>" } ],
              "event_subscriptions": [ { "broker": "<kafka|rabbitmq|jms|...>", "topic_or_queue": "<topic or queue>", "payload_structure": "<expected payload type>" } ],
              "scheduled_triggers":  [ { "schedule_expression": "<cron or interval>", "description": "<purpose>" } ]
            },
            "outbound": {
              "http_calls": [
                {
                  "method":                "<GET|POST|…>",
                  "url_or_path":           "<url>",
                  "encapsulated_in":       "<method call>",
                  "is_external":           true,
                  "external_contract_hint": "<REST/SOAP/etc: expected response shape>"
                }
              ],
              "event_publications": [ { "broker": "<kafka|rabbitmq|jms|...>", "topic_or_queue": "<topic>", "routing_key": "<routing key>", "business_trigger": "<what triggers publication>" } ]
            }
          },
          "discovered_dependencies": [
            { "file_path": "<path not in structural context>", "reason": "<why discovered>", "discovery_depth": 0 }
          ]
        }

        FIELD RULES
        ───────────
        metadata          — copy all five fields verbatim from the task context block.
        purpose           — one or two sentences, business language, no code terms.
        happy_paths       — one entry per distinct success flow; omit error flows here.
        validations       — every guard clause, null check, or business rejection rule.
        edge_cases        — boundary states and explicit error handling only.
        test_insights     — only if a TEST FILE section is present; otherwise `[]`.
        architectural_connections — scan for @RequestMapping, RestTemplate, @KafkaListener,
                            @Scheduled, JmsTemplate, WebClient, and similar. Empty arrays
                            for sections with no matches — never omit the section.
        discovered_dependencies — objects with file_path, reason, and discovery_depth for files referenced
                            but absent from STRUCTURAL CONTEXT; `[]` if none.
        """.stripIndent();
}

    private String buildUserPrompt(Task task, String sourceContent, String testContent,
                                   String structuralContextJson) {
        StringBuilder sb = new StringBuilder();

        // ── TASK CONTEXT (metadata source for the LLM) ──────────────────────────
        sb.append("TASK CONTEXT\n");
        sb.append("task_id:     ").append(task.taskId()).append("\n");
        sb.append("target_name: ").append(task.filePath()
                .substring(task.filePath().lastIndexOf('/') + 1)
                .replaceAll("\\.java$", "")).append("\n");
        sb.append("file_path:   ").append(task.filePath()).append("\n");
        sb.append("tech_profile: business_semantics\n");
        sb.append("module_tag:  ").append(task.contentType()).append("\n\n");

        // ── STRUCTURAL CONTEXT ───────────────────────────────────────────────────
        if (structuralContextJson != null && !structuralContextJson.isBlank()) {
            sb.append("STRUCTURAL CONTEXT (already resolved — do not re-derive these)\n");
            sb.append(structuralContextJson).append("\n\n");
        }

        // ── SOURCE CODE ──────────────────────────────────────────────────────────
        sb.append("SOURCE CODE\n");
        sb.append(sourceContent).append("\n");

        // ── TEST FILE ────────────────────────────────────────────────────────────
        if (testContent != null && !testContent.isBlank()) {
            sb.append("\nTEST FILE\n");
            sb.append(testContent).append("\n");

            if (assertionExtractor != null) {
                var assertions = assertionExtractor.extract(testContent);
                if (!assertions.isEmpty()) {
                    sb.append("\nEXTRACTED TEST ASSERTIONS\n");
                    for (var a : assertions) {
                        sb.append("  [").append(a.type()).append("] ");
                        sb.append(a.detail()).append("\n");
                        sb.append("    -> ").append(a.description()).append("\n");
                    }
                    sb.append("\n");
                }
            }
        }

        // ── OUTPUT REMINDER (keeps contract top-of-mind at the end too) ─────────
        sb.append("\nRemember: output ONLY the JSON object. Start with `{`, end with `}`. No other text.\n");

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

    private String buildStrictSchema(String rawSchema) {
        try {
            JsonNode schemaNode = MAPPER.readTree(rawSchema);
            if (schemaNode instanceof ObjectNode root) {
                addAdditionalPropertiesFalse(root);
                ObjectNode wrapper = MAPPER.createObjectNode();
                wrapper.put("name", "ExecutionFinding");
                wrapper.put("strict", true);
                wrapper.set("schema", root);
                return MAPPER.writeValueAsString(wrapper);
            }
        } catch (Exception e) {
            log.warn("Failed to build strict schema, using raw schema: {}", e.getMessage());
        }
        return rawSchema;
    }

    private void addAdditionalPropertiesFalse(ObjectNode node) {
        if (!node.has("type") || !"object".equals(node.get("type").asText())) return;
        node.put("additionalProperties", false);
        JsonNode properties = node.get("properties");
        if (properties instanceof ObjectNode propsObj) {
            propsObj.fieldNames().forEachRemaining(fieldName -> {
                JsonNode fieldSchema = propsObj.get(fieldName);
                if (fieldSchema instanceof ObjectNode fieldObj) {
                    addAdditionalPropertiesFalse(fieldObj);
                }
            });
        }
        JsonNode items = node.get("items");
        if (items instanceof ObjectNode itemsObj) {
            addAdditionalPropertiesFalse(itemsObj);
        }
    }

    private String buildErrorFeedbackPrompt(Task task, String sourceContent, String testContent,
                                             String structuralContextJson, String failedJson, String error) {
        StringBuilder sb = new StringBuilder();
        sb.append("YOUR PREVIOUS RESPONSE FAILED JSON PARSING.\n\n");
        sb.append("FAILED JSON:\n").append(failedJson).append("\n\n");
        sb.append("PARSE ERROR:\n").append(error).append("\n\n");
        sb.append("Fix the JSON and output ONLY the corrected version. ");
        sb.append("Follow the schema exactly.\n");
        sb.append("─────────────────────────────────────\n\n");
        sb.append(buildUserPrompt(task, sourceContent, testContent, structuralContextJson));
        return sb.toString();
    }
}
