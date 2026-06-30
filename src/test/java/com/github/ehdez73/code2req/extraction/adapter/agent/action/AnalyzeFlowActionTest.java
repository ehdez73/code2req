package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.github.ehdez73.code2req.extraction.adapter.agent.model.TracedFlowResult;
import com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPointType;
import com.github.ehdez73.code2req.extraction.domain.model.ExecutionFlow;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStatus;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStep;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStepComponentType;
import com.github.ehdez73.code2req.extraction.domain.model.LinkRegistry;
import com.github.ehdez73.code2req.extraction.domain.model.SemanticEnrichment;
import com.github.ehdez73.code2req.extraction.domain.model.StructuralGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzeFlowActionTest {

    private final EntryPoint entryPoint = new EntryPoint("GET /test", EntryPointType.HTTP, "GET", "/test",
        "TestController", "handle", "/src/TestController.java",
        0.5, false, List.of(), null, null);

    private AnalyzeFlowAction action() {
        return new AnalyzeFlowAction(new CodebaseKnowledge(new StructuralGraph(), new SemanticEnrichment(), new LinkRegistry()));
    }

    private ExecutionFlow flow(FlowStatus status, List<FlowStep> steps) {
        return new ExecutionFlow("flow-1", entryPoint, steps, steps.size(), List.of(), status);
    }

    @Test
    void analyzeSkipsQuarantinedFlows() {
        var traced = new TracedFlowResult(List.of(flow(FlowStatus.QUARANTINED, List.of())), List.of());
        var result = action().analyze(traced, null);
        assertTrue(result.flows().isEmpty());
    }

    @Test
    void analyzeEmptyTracedResultReturnsEmpty() {
        var traced = new TracedFlowResult(List.of(), List.of());
        var result = action().analyze(traced, null);
        assertTrue(result.flows().isEmpty());
    }
}
