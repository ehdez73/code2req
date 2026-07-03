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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

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
        assertTrue(prompt.contains("TestController.java lines 9-11"),
            "LLM prompt should reference source file and lines");
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
}
