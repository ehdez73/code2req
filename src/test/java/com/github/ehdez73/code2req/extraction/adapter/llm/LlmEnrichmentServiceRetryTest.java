package com.github.ehdez73.code2req.extraction.adapter.llm;

import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.github.ehdez73.code2req.extraction.domain.model.EnrichmentConfig;
import com.github.ehdez73.code2req.extraction.domain.model.ExecutionFinding;
import com.github.ehdez73.code2req.extraction.domain.model.ExecutionMode;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStoreSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;

class LlmEnrichmentServiceRetryTest {

    @TempDir
    Path tempDir;

    private TaskStore taskStore;
    private ExecutionFindingStore findingStore;
    private AtomicInteger invokeCount;

    @BeforeEach
    void setUp() {
        var dbPath = tempDir.resolve("retry-test.db");
        var ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        var jdbc = new JdbcTemplate(ds);
        var schema = new TaskStoreSchema(jdbc);
        schema.createSchemaIfNotExists();

        taskStore = new TaskStore(jdbc);
        findingStore = new ExecutionFindingStore(jdbc);
        invokeCount = new AtomicInteger();
    }

    private TestableService createService(List<String> responses) {
        var budgetCalculator = new ContextBudgetCalculator();
        var enrichmentConfig = new EnrichmentConfig(null, ExecutionMode.SYNC, false, null);
        var txManager = new DataSourceTransactionManager(
            (org.sqlite.SQLiteDataSource) ((JdbcTemplate) new JdbcTemplate(
                new org.sqlite.SQLiteDataSource())).getDataSource());
        var txTemplate = new TransactionTemplate(txManager);
        return new TestableService(null, findingStore, taskStore,
            budgetCalculator, new SimulationStub(), null,
            new ExecutionFindingParser(), enrichmentConfig, null, txTemplate,
            responses, invokeCount);
    }

    private Task createTask() {
        var task = new Task("task-1", "/src/Foo.java",
            TaskStatus.PENDING, "java", "abc123", "test");
        taskStore.save(task);
        return task;
    }

    private static final String VALID_JSON = """
        {"metadata":{"task_id":"task-1","target_name":"test","file_path":"/src/Foo.java","tech_profile":"spring","module_tag":"java","timestamp":"2026-01-01T00:00:00"},\
        "business_rules_and_guardrails":{"validations":[],"edge_cases":[]},\
        "test_insights":[],\
        "architectural_connections":{"inbound":{"http_endpoints":[],"event_subscriptions":[],"scheduled_triggers":[]},"outbound":{"http_calls":[],"event_publications":[]}},\
        "discovered_dependencies":[]}""";

    private static final String BROKEN_JSON = "'metadata";

    @Test
    void parseFailureRetriesWithSamePrompt() {
        var service = createService(List.of(BROKEN_JSON, VALID_JSON));
        var task = createTask();

        var finding = service.callLlm(task, "class Foo {}", null, null);

        assertNotNull(finding);
        assertEquals("task-1", finding.metadata().taskId());
        assertEquals(2, invokeCount.get(), "should retry once with same prompt");
    }

    @Test
    void threeParseFailuresReturnsMinimalStub() {
        var service = createService(List.of(BROKEN_JSON, BROKEN_JSON, BROKEN_JSON));
        var task = createTask();

        var finding = service.callLlm(task, "class Foo {}", null, null);

        assertNotNull(finding);
        assertEquals("task-1", finding.metadata().taskId());
        assertEquals("test", finding.metadata().targetName());
        assertEquals("/src/Foo.java", finding.metadata().filePath());
        assertTrue(finding.businessRulesAndGuardrails().validations().isEmpty());
        assertTrue(finding.businessRulesAndGuardrails().edgeCases().isEmpty());
        assertTrue(finding.testInsights().isEmpty());
        assertTrue(finding.discoveredDependencies().isEmpty());
        assertEquals(3, invokeCount.get());
    }

    @Test
    void validJsonOnRetry2ReturnsNormally() {
        var service = createService(List.of(BROKEN_JSON, VALID_JSON));
        var task = createTask();

        var finding = service.callLlm(task, "class Foo {}", null, null);

        assertNotNull(finding);
        assertEquals("task-1", finding.metadata().taskId());
        assertEquals(2, invokeCount.get());
    }

    @Test
    void rateLimitStillThrows() {
        var service = createService(List.of("429 Too Many Requests", "429 Too Many Requests", "429 Too Many Requests"));
        service.throwOnInvoke = true;
        var task = createTask();

        assertThrows(RuntimeException.class,
            () -> service.callLlm(task, "class Foo {}", null, null));
    }

    static class TestableService extends LlmEnrichmentService {
        private final List<String> responses;
        private final AtomicInteger callCounter;
        boolean throwOnInvoke = false;

        TestableService(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                        ExecutionFindingStore findingStore, TaskStore taskStore,
                        ContextBudgetCalculator budgetCalculator,
                        SimulationStub simulationStub,
                        ExecutionFindingValidator validator,
                        ExecutionFindingParser parser,
                        EnrichmentConfig enrichmentConfig,
                        Executor taskExecutor,
                        TransactionTemplate transactionTemplate,
                        List<String> responses,
                        AtomicInteger callCounter) {
            super(chatClientBuilderProvider, findingStore, taskStore, budgetCalculator,
                simulationStub, validator, parser, enrichmentConfig, taskExecutor, transactionTemplate);
            this.responses = responses;
            this.callCounter = callCounter;
        }

        @Override
        String invokeLlm(String systemPrompt, String userPrompt, OpenAiChatOptions options) {
            int idx = callCounter.getAndIncrement();
            if (throwOnInvoke) {
                throw new RuntimeException("429");
            }
            if (idx < responses.size()) {
                return responses.get(idx);
            }
            return responses.get(responses.size() - 1);
        }
    }
}
