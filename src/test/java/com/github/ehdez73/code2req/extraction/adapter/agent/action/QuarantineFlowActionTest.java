package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.github.ehdez73.code2req.extraction.adapter.agent.model.TracedFlowResult;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPointType;
import com.github.ehdez73.code2req.extraction.domain.model.ExecutionFlow;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStatus;
import com.github.ehdez73.code2req.extraction.domain.model.GapReason;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuarantineFlowActionTest {

    private final EntryPoint entryPoint = new EntryPoint("id", EntryPointType.HTTP, "GET", "/test",
        "Ctrl", "m", "/f.java", 0.5, false, List.of(), null, null);

    private ExecutionFlow flow(FlowStatus status, int stepCount, int depth, int unresolvedCount) {
        var steps = java.util.stream.IntStream.range(0, stepCount)
            .mapToObj(i -> new com.github.ehdez73.code2req.extraction.domain.model.FlowStep(
                i, com.github.ehdez73.code2req.extraction.domain.model.FlowStepComponentType.SERVICE,
                "C" + i, "m" + i, null, "/f.java", 0, 0, List.of()))
            .toList();
        var unresolved = java.util.stream.IntStream.range(0, unresolvedCount)
            .mapToObj(i -> "Unresolved" + i)
            .toList();
        return new ExecutionFlow("flow-" + stepCount + "-" + depth, entryPoint, steps, depth, unresolved, status);
    }

    @Test
    void quarantinePreservesCleanFlows() {
        var clean = flow(FlowStatus.TRACED, 3, 2, 0);
        var traced = new TracedFlowResult(List.of(clean), List.of());

        var result = new QuarantineFlowAction().quarantine(traced);

        assertEquals(1, result.flows().size());
        assertEquals(FlowStatus.TRACED, result.flows().get(0).status());
        assertTrue(result.allQuarantinedFlowIds().isEmpty());
    }

    @Test
    void quarantineQuarantinesFlowWithNoSteps() {
        var bad = flow(FlowStatus.QUARANTINED, 0, 0, 0);
        var traced = new TracedFlowResult(List.of(bad), List.of());

        var result = new QuarantineFlowAction().quarantine(traced);

        assertTrue(result.flows().isEmpty());
        assertEquals(1, result.allQuarantinedFlowIds().size());
    }

    @Test
    void quarantineQuarantinesFlowExceedingMaxSteps() {
        var bad = flow(FlowStatus.TRACED, 21, 1, 0);
        var traced = new TracedFlowResult(List.of(bad), List.of());

        var result = new QuarantineFlowAction().quarantine(traced);

        assertTrue(result.flows().isEmpty());
        assertEquals(1, result.allQuarantinedFlowIds().size());
    }

    @Test
    void quarantineQuarantinesFlowExceedingMaxDepth() {
        var bad = flow(FlowStatus.TRACED, 3, 6, 0);
        var traced = new TracedFlowResult(List.of(bad), List.of());

        var result = new QuarantineFlowAction().quarantine(traced);

        assertTrue(result.flows().isEmpty());
        assertEquals(1, result.allQuarantinedFlowIds().size());
    }

    @Test
    void quarantineQuarantinesFlowWithTooManyUnresolvedCalls() {
        var bad = flow(FlowStatus.TRACED, 3, 1, 4);
        var traced = new TracedFlowResult(List.of(bad), List.of());

        var result = new QuarantineFlowAction().quarantine(traced);

        assertTrue(result.flows().isEmpty());
        assertEquals(1, result.allQuarantinedFlowIds().size());
    }

    @Test
    void quarantineWithResultReturnsGaps() {
        var bad = flow(FlowStatus.QUARANTINED, 0, 0, 0);
        var traced = new TracedFlowResult(List.of(bad), List.of());

        var result = new QuarantineFlowAction().quarantineWithResult(traced);

        assertEquals(1, result.gaps().size());
        assertEquals(GapReason.LOW_CONFIDENCE, result.gaps().get(0).reason());
        assertFalse(result.gaps().get(0).missingContext().isBlank());
        assertTrue(result.cleanResult().flows().isEmpty());
    }

    @Test
    void quarantineMixedFlowsKeepsCleanAndQuarantinesBad() {
        var clean = flow(FlowStatus.TRACED, 3, 2, 0);
        var bad = flow(FlowStatus.TRACED, 21, 1, 0);
        var traced = new TracedFlowResult(List.of(clean, bad), List.of());

        var result = new QuarantineFlowAction().quarantine(traced);

        assertEquals(1, result.flows().size());
        assertEquals(1, result.allQuarantinedFlowIds().size());
    }

    @Test
    void getQuarantineGapsReturnsMatchingGaps() {
        var clean = flow(FlowStatus.TRACED, 3, 2, 0);
        var bad = flow(FlowStatus.QUARANTINED, 0, 0, 0);
        var original = new TracedFlowResult(List.of(clean, bad), List.of());
        var quarantined = new QuarantineFlowAction().quarantine(original);

        var gaps = new QuarantineFlowAction().getQuarantineGaps(original, quarantined);

        assertEquals(1, gaps.size());
        assertTrue(gaps.get(0).flowId().contains("0-0"));
    }

    @Test
    void getQuarantineGapsFallsBackForUnknownFlows() {
        var clean = flow(FlowStatus.TRACED, 3, 2, 0);
        var original = new TracedFlowResult(List.of(clean), List.of());
        var quarantined = new TracedFlowResult(List.of(), List.of("nonexistent"));

        var gaps = new QuarantineFlowAction().getQuarantineGaps(original, quarantined);

        assertEquals(0, gaps.size());
    }
}
