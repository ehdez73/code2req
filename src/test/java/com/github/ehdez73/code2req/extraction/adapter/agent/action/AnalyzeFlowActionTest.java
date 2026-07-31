package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.embabel.agent.test.unit.FakeOperationContext;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.TracedFlowResult;
import com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPointType;
import com.github.ehdez73.code2req.extraction.domain.model.EventListenerEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.HttpEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.ExecutionFlow;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStatus;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStep;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStepComponentType;
import com.github.ehdez73.code2req.extraction.domain.model.LinkRegistry;
import com.github.ehdez73.code2req.extraction.domain.model.SemanticEnrichment;
import com.github.ehdez73.code2req.extraction.domain.model.StructuralGraph;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyzeFlowActionTest {

    private static final String SAMPLE_CONTROLLER = Path.of("src/test/resources/sample/TestController.java")
        .toAbsolutePath().normalize().toString();

    private final EntryPoint httpEntry = new HttpEntryPoint("GET /test", "TestController", "handle", SAMPLE_CONTROLLER,
        0.5, false, "GET", "/test", List.of(), List.of(), 9, 11);

    private AnalyzeFlowAction action() {
        return new AnalyzeFlowAction(
            new CodebaseKnowledge(new StructuralGraph(), new SemanticEnrichment(), new LinkRegistry()),
            mock(ExecutionFindingStore.class), new ObjectMapper(), false);
    }

    private ExecutionFlow flow(FlowStatus status, List<FlowStep> steps, EntryPoint entryPoint) {
        return new ExecutionFlow("flow-1", entryPoint, steps, steps.size(), List.of(), status);
    }

    @Test
    void analyzeSkipsQuarantinedFlows() {
        var traced = new TracedFlowResult(
            List.of(flow(FlowStatus.QUARANTINED, List.of(), httpEntry)), List.of());
        var result = action().analyze(traced, null);
        assertTrue(result.flows().isEmpty());
    }

    @Test
    void analyzeEmptyTracedResultReturnsEmpty() {
        var traced = new TracedFlowResult(List.of(), List.of());
        var result = action().analyze(traced, null);
        assertTrue(result.flows().isEmpty());
    }

    @Test
    void analyzeEventListenerFlowIncludesEventTypeInPrompt() {
        var entryPoint = new EventListenerEntryPoint("CronMessage", "Listener", "handleMessage", "/src/Listener.java",
            0.5, false, "CronMessage");

        var steps = List.of(new FlowStep(0, FlowStepComponentType.SERVICE, "Listener", "handleMessage",
            null, "/src/Listener.java", 0, 0, List.of()));
        var flow = flow(FlowStatus.TRACED, steps, entryPoint);

        var ctx = FakeOperationContext.create();
        ctx.expectResponse(new AnalyzeFlowAction.FlowAnalysisResponse(
            "As a listener, I consume CronMessage events from the scheduler and log them.",
            List.of(), List.of(), List.of(), List.of()
        ));

        var traced = new TracedFlowResult(List.of(flow), List.of());
        var result = action().analyze(traced, ctx);

        assertEquals(1, result.flows().size());
        var analyzed = result.flows().get(0);
        assertEquals("Listener.handleMessage", analyzed.name());
        assertEquals("As a listener, I consume CronMessage events from the scheduler and log them.",
            analyzed.userStory());

        var invocations = ctx.getLlmInvocations();
        assertEquals(1, invocations.size());
        var prompt = invocations.get(0).getPrompt();
        assertNotNull(prompt);
        assertTrue(prompt.contains("CronMessage"),
            "LLM prompt should contain event type 'CronMessage' but got: " + prompt);
        assertTrue(prompt.contains("EVENT_LISTENER"),
            "LLM prompt should contain entry point type 'EVENT_LISTENER'");
    }

    @Test
    void analyzeEventListenerWithCustomMessage() {
        var entryPoint = new EventListenerEntryPoint("CustomMessage", "Listener", "handleMessage", "/src/Listener.java",
            0.5, false, "CustomMessage");

        var steps = List.of(new FlowStep(0, FlowStepComponentType.SERVICE, "Listener", "handleMessage",
            null, "/src/Listener.java", 0, 0, List.of()));
        var flow = flow(FlowStatus.TRACED, steps, entryPoint);

        var ctx = FakeOperationContext.create();
        ctx.expectResponse(new AnalyzeFlowAction.FlowAnalysisResponse(
            "As an operator, I want the listener to consume CustomMessage events...",
            List.of(), List.of(), List.of(), List.of()
        ));

        var traced = new TracedFlowResult(List.of(flow), List.of());
        var result = action().analyze(traced, ctx);

        assertEquals(1, result.flows().size());

        var invocations = ctx.getLlmInvocations();
        assertEquals(1, invocations.size());
        var prompt = invocations.get(0).getPrompt();
        assertTrue(prompt.contains("CustomMessage"),
            "LLM prompt should contain event type 'CustomMessage' but got: " + prompt);
    }

    @Test
    void analyzeHttpFlowStillWorks() {
        var steps = List.of(new FlowStep(0, FlowStepComponentType.REST_ENDPOINT, "TestController", "handle",
            null, SAMPLE_CONTROLLER, 9, 11, List.of()));
        var flow = flow(FlowStatus.TRACED, steps, httpEntry);

        var ctx = FakeOperationContext.create();
        ctx.expectResponse(new AnalyzeFlowAction.FlowAnalysisResponse(
            "As a user, I want to GET /test so that I can test the endpoint.",
            List.of(), List.of(), List.of(), List.of()
        ));

        var traced = new TracedFlowResult(List.of(flow), List.of());
        var result = action().analyze(traced, ctx);

        assertEquals(1, result.flows().size());
        var analyzed = result.flows().get(0);
        assertEquals("GET /test", analyzed.name());
        assertEquals("As a user, I want to GET /test so that I can test the endpoint.",
            analyzed.userStory());

        var prompt = ctx.getLlmInvocations().get(0).getPrompt();
        assertTrue(prompt.contains("Source Code (traced steps):"),
            "LLM prompt should contain source code section");
        assertTrue(prompt.contains("TestController.java:"),
            "LLM prompt should reference source file");
        assertTrue(prompt.contains("// lines 9-11 (handle)"),
            "LLM prompt should reference line numbers and method name");
    }

    @Test
    void analyzeFlowPopulatesBusinessRulesAndEdgeCasesFromResponse() {
        var entryPoint = new EventListenerEntryPoint("CronMessage", "Listener", "handleMessage", "/src/Listener.java",
            0.5, false, "CronMessage");

        var steps = List.of(new FlowStep(0, FlowStepComponentType.SERVICE, "Listener", "handleMessage",
            null, "/src/Listener.java", 0, 0, List.of()));
        var flow = flow(FlowStatus.TRACED, steps, entryPoint);

        var gherkinScenarios = List.of(
            new AnalyzeFlowAction.GherkinScenarioDto(
                "GS-001", "Successful message processing",
                List.of("A valid CronMessage is received"),
                List.of("Listener.handleMessage is invoked"),
                List.of("The message is processed", "The result is logged")
            )
        );

        var businessRules = List.of(
            new AnalyzeFlowAction.BusinessRuleDto(
                "BR-001",
                "Incoming message must be validated before processing",
                "Message is received",
                "Message is validated or rejected",
                "Log error and discard message",
                null, null
            )
        );

        var edgeCases = List.of(
            new AnalyzeFlowAction.EdgeCaseDto(
                "Null message payload",
                "Causes NullPointerException if not checked",
                "HIGH"
            )
        );

        var ctx = FakeOperationContext.create();
        ctx.expectResponse(new AnalyzeFlowAction.FlowAnalysisResponse(
            "As a listener, I consume CronMessage events.",
            gherkinScenarios,
            businessRules,
            edgeCases,
            List.of()
        ));

        var traced = new TracedFlowResult(List.of(flow), List.of());
        var result = action().analyze(traced, ctx);

        assertEquals(1, result.flows().size());
        var analyzed = result.flows().get(0);

        assertEquals(1, analyzed.acceptanceCriteria().size());
        assertEquals("GS-001", analyzed.acceptanceCriteria().get(0).scenarioId());
        assertEquals("Successful message processing", analyzed.acceptanceCriteria().get(0).name());
        assertEquals(List.of("A valid CronMessage is received"),
            analyzed.acceptanceCriteria().get(0).givenSteps());

        assertEquals(1, analyzed.businessRules().size());
        assertEquals("BR-001", analyzed.businessRules().get(0).ruleId());
        assertNull(analyzed.businessRules().get(0).externalCall(),
            "externalCall should be null when not provided");
        assertEquals("/src/Listener.java", analyzed.businessRules().get(0).sourceFile(),
            "sourceFile should fall back to entry point file path when LLM provides null");

        assertEquals(1, analyzed.edgeCases().size());
        assertEquals("Null message payload", analyzed.edgeCases().get(0).scenario());
        assertEquals("HIGH", analyzed.edgeCases().get(0).severity(),
            "severity should be mapped from EdgeCaseDto");

        assertEquals(0, analyzed.nonFunctionalRequirements().size(),
            "nonFunctionalRequirements should be empty when response provides none");

        assertEquals("flow-1", analyzed.flowId());
        assertNotNull(analyzed.complexity());
    }

    @Test
    void parallelSingleThreadProducesIdenticalResultsToSequential() {
        var executor = Executors.newSingleThreadExecutor();
        var sequentialAction = new AnalyzeFlowAction(
            new CodebaseKnowledge(new StructuralGraph(), new SemanticEnrichment(), new LinkRegistry()),
            mock(ExecutionFindingStore.class), new ObjectMapper(), false);
        var parallelAction = new AnalyzeFlowAction(
            new CodebaseKnowledge(new StructuralGraph(), new SemanticEnrichment(), new LinkRegistry()),
            mock(ExecutionFindingStore.class), new ObjectMapper(), false, executor);

        var entryPoint = new HttpEntryPoint("GET /test", "TestController", "handle", SAMPLE_CONTROLLER,
            0.5, false, "GET", "/test", List.of(), List.of(), 9, 11);
        var steps = List.of(new FlowStep(0, FlowStepComponentType.REST_ENDPOINT, "TestController", "handle",
            null, SAMPLE_CONTROLLER, 9, 11, List.of()));
        var flow = flow(FlowStatus.TRACED, steps, entryPoint);

        var ctxSeq = FakeOperationContext.create();
        ctxSeq.expectResponse(new AnalyzeFlowAction.FlowAnalysisResponse(
            "As a user, I want to test the endpoint.", List.of(), List.of(), List.of(), List.of()));

        var ctxPar = FakeOperationContext.create();
        ctxPar.expectResponse(new AnalyzeFlowAction.FlowAnalysisResponse(
            "As a user, I want to test the endpoint.", List.of(), List.of(), List.of(), List.of()));

        var traced = new TracedFlowResult(List.of(flow), List.of());
        var sequentialResult = sequentialAction.analyze(traced, ctxSeq);
        var parallelResult = parallelAction.analyze(traced, ctxPar);

        assertEquals(sequentialResult.flows().size(), parallelResult.flows().size());
        assertEquals(sequentialResult.flows().get(0).name(), parallelResult.flows().get(0).name());
        assertEquals(sequentialResult.flows().get(0).userStory(), parallelResult.flows().get(0).userStory());
        executor.shutdown();
    }

    @Test
    void concurrentExecutorProcessesAllFlowsWithCorrectCount() throws Exception {
        var executor = Executors.newFixedThreadPool(3);
        var latch = new CountDownLatch(3);
        var executions = new AtomicInteger(0);

        var entryPoint = new HttpEntryPoint("GET /test", "TestController", "handle", SAMPLE_CONTROLLER,
            0.5, false, "GET", "/test", List.of(), List.of(), 9, 11);
        var steps = List.of(new FlowStep(0, FlowStepComponentType.REST_ENDPOINT, "TestController", "handle",
            null, SAMPLE_CONTROLLER, 9, 11, List.of()));

        var flows = List.of(
            flow(FlowStatus.TRACED, steps, entryPoint),
            flow(FlowStatus.TRACED, steps, entryPoint),
            flow(FlowStatus.TRACED, steps, entryPoint)
        );

        var action = new AnalyzeFlowAction(
            new CodebaseKnowledge(new StructuralGraph(), new SemanticEnrichment(), new LinkRegistry()),
            mock(ExecutionFindingStore.class), new ObjectMapper(), false, executor);

        var ctx = FakeOperationContext.create();
        ctx.expectResponse(new AnalyzeFlowAction.FlowAnalysisResponse(
            "User story", List.of(), List.of(), List.of(), List.of()));
        ctx.expectResponse(new AnalyzeFlowAction.FlowAnalysisResponse(
            "User story", List.of(), List.of(), List.of(), List.of()));
        ctx.expectResponse(new AnalyzeFlowAction.FlowAnalysisResponse(
            "User story", List.of(), List.of(), List.of(), List.of()));

        var traced = new TracedFlowResult(flows, List.of());
        var result = action.analyze(traced, ctx);

        assertEquals(3, result.flows().size(), "All flows should be analyzed");
        executor.shutdown();
    }

    @Test
    void singleFlowFailureDoesNotAbortRemainingFlows() {
        var executor = Executors.newFixedThreadPool(2);

        var entryPoint = new HttpEntryPoint("GET /test", "TestController", "handle", SAMPLE_CONTROLLER,
            0.5, false, "GET", "/test", List.of(), List.of(), 9, 11);
        var steps = List.of(new FlowStep(0, FlowStepComponentType.REST_ENDPOINT, "TestController", "handle",
            null, SAMPLE_CONTROLLER, 9, 11, List.of()));

        var flows = List.of(
            flow(FlowStatus.TRACED, steps, entryPoint),
            flow(FlowStatus.TRACED, steps, entryPoint)
        );

        var action = new AnalyzeFlowAction(
            new CodebaseKnowledge(new StructuralGraph(), new SemanticEnrichment(), new LinkRegistry()),
            mock(ExecutionFindingStore.class), new ObjectMapper(), false, executor);

        var ctx = FakeOperationContext.create();
        ctx.expectResponse(new AnalyzeFlowAction.FlowAnalysisResponse(
            "Successful flow", List.of(), List.of(), List.of(), List.of()));

        var traced = new TracedFlowResult(flows, List.of());
        var result = action.analyze(traced, ctx);

        assertEquals(1, result.flows().size(), "Should have exactly one successful flow");
        assertEquals("Successful flow", result.flows().get(0).userStory());
        executor.shutdown();
    }

    @Test
    void cachedFlowAnalysesAreReusedUnderConcurrentExecution() {
        var executor = Executors.newFixedThreadPool(2);
        var findingStore = mock(ExecutionFindingStore.class);
        var objectMapper = new ObjectMapper();

        var cachedResponse = new AnalyzeFlowAction.FlowAnalysisResponse(
            "Cached user story", List.of(), List.of(), List.of(), List.of());
        var cachedJson = "{\"userStory\":\"Cached user story\",\"gherkinScenarios\":[],\"businessRules\":[],\"edgeCases\":[],\"nonFunctionalRequirements\":[]}";
        when(findingStore.findByTaskIdAndType(anyString(), eq(com.github.ehdez73.code2req.infrastructure.persistence.FindingType.FLOW_ANALYSIS)))
            .thenReturn(List.of(Map.of("finding_json", (Object) cachedJson)));

        var entryPoint = new HttpEntryPoint("GET /test", "TestController", "handle", SAMPLE_CONTROLLER,
            0.5, false, "GET", "/test", List.of(), List.of(), 9, 11);
        var steps = List.of(new FlowStep(0, FlowStepComponentType.REST_ENDPOINT, "TestController", "handle",
            null, SAMPLE_CONTROLLER, 9, 11, List.of()));
        var flow = flow(FlowStatus.TRACED, steps, entryPoint);

        var action = new AnalyzeFlowAction(
            new CodebaseKnowledge(new StructuralGraph(), new SemanticEnrichment(), new LinkRegistry()),
            findingStore, objectMapper, true, executor);

        var ctx = FakeOperationContext.create();

        var traced = new TracedFlowResult(List.of(flow), List.of());
        var result = action.analyze(traced, ctx);

        assertEquals(1, result.flows().size());
        assertEquals("Cached user story", result.flows().get(0).userStory(),
            "Should reuse cached analysis instead of calling LLM");
        executor.shutdown();
    }

    @Test
    void nullExecutorFallsBackToSequentialLoop() {
        var entryPoint = new HttpEntryPoint("GET /test", "TestController", "handle", SAMPLE_CONTROLLER,
            0.5, false, "GET", "/test", List.of(), List.of(), 9, 11);
        var steps = List.of(new FlowStep(0, FlowStepComponentType.REST_ENDPOINT, "TestController", "handle",
            null, SAMPLE_CONTROLLER, 9, 11, List.of()));
        var flow = flow(FlowStatus.TRACED, steps, entryPoint);

        var action = new AnalyzeFlowAction(
            new CodebaseKnowledge(new StructuralGraph(), new SemanticEnrichment(), new LinkRegistry()),
            mock(ExecutionFindingStore.class), new ObjectMapper(), false);

        var ctx = FakeOperationContext.create();
        ctx.expectResponse(new AnalyzeFlowAction.FlowAnalysisResponse(
            "Sequential result", List.of(), List.of(), List.of(), List.of()));

        var traced = new TracedFlowResult(List.of(flow), List.of());
        var result = action.analyze(traced, ctx);

        assertEquals(1, result.flows().size());
        assertEquals("Sequential result", result.flows().get(0).userStory());
    }
}
