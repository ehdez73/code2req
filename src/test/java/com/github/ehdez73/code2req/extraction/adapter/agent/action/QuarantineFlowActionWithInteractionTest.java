package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.github.ehdez73.code2req.extraction.adapter.agent.model.TracedFlowResult;
import com.github.ehdez73.code2req.extraction.domain.model.*;
import com.github.ehdez73.code2req.extraction.domain.spi.UserInteractionService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QuarantineFlowActionWithInteractionTest {

    private final EntryPoint entryPoint = new HttpEntryPoint("id", "Ctrl", "m", "/f.java",
        0.5, false, "GET", "/test", List.of(), List.of());

    private ExecutionFlow flow(FlowStatus status, int stepCount, int depth, int unresolvedCount) {
        var steps = java.util.stream.IntStream.range(0, stepCount)
            .mapToObj(i -> new FlowStep(i, FlowStepComponentType.SERVICE,
                "C" + i, "m" + i, null, "/f.java", 0, 0, List.of()))
            .toList();
        var unresolved = java.util.stream.IntStream.range(0, unresolvedCount)
            .mapToObj(i -> "Unresolved" + i)
            .toList();
        return new ExecutionFlow("flow-" + stepCount + "-" + depth, entryPoint, steps, depth, unresolved, status);
    }

    @Test
    void acceptOptionCreatesGapWithSelectedOption() {
        var uis = new UserInteractionService() {
            @Override public String ask(String p, String c) { return null; }
            @Override public boolean confirm(String m) { return false; }
            @Override public String select(List<String> o, String p) { return QuarantineUserAction.ACCEPT.label(); }
            @Override public boolean isInteractive() { return true; }
        };

        var bad = flow(FlowStatus.QUARANTINED, 2, 1, 2);
        var traced = new TracedFlowResult(List.of(bad), List.of());
        var action = new QuarantineFlowAction(null, uis);

        var result = action.quarantineWithResult(traced);

        assertEquals(1, result.gaps().size());
        assertFalse(result.gaps().get(0).userProvided());
        assertNull(result.gaps().get(0).userAnswer());
        assertEquals(QuarantineUserAction.ACCEPT.label(), result.gaps().get(0).selectedOption());
        assertEquals(GapReason.LOW_CONFIDENCE, result.gaps().get(0).reason());
    }

    @Test
    void dismissOptionSkipsGap() {
        var uis = new UserInteractionService() {
            @Override public String ask(String p, String c) { return null; }
            @Override public boolean confirm(String m) { return false; }
            @Override public String select(List<String> o, String p) { return QuarantineUserAction.DISMISS.label(); }
            @Override public boolean isInteractive() { return true; }
        };

        var bad = flow(FlowStatus.QUARANTINED, 2, 1, 2);
        var traced = new TracedFlowResult(List.of(bad), List.of());
        var action = new QuarantineFlowAction(null, uis);

        var result = action.quarantineWithResult(traced);

        assertTrue(result.gaps().isEmpty());
        assertEquals(1, result.cleanResult().flows().size());
        assertEquals(FlowStatus.TRACED, result.cleanResult().flows().get(0).status());
    }

    @Test
    void provideContextOptionCreatesGapWithUserAnswer() {
        var uis = new UserInteractionService() {
            @Override public String ask(String p, String c) { return "This call is external SDK"; }
            @Override public boolean confirm(String m) { return false; }
            @Override public String select(List<String> o, String p) { return QuarantineUserAction.PROVIDE_CONTEXT.label(); }
            @Override public boolean isInteractive() { return true; }
        };

        var bad = flow(FlowStatus.QUARANTINED, 2, 1, 2);
        var traced = new TracedFlowResult(List.of(bad), List.of());
        var action = new QuarantineFlowAction(null, uis);

        var result = action.quarantineWithResult(traced);

        assertEquals(1, result.gaps().size());
        assertTrue(result.gaps().get(0).userProvided());
        assertEquals("This call is external SDK", result.gaps().get(0).userAnswer());
        assertEquals(GapReason.USER_CLARIFIED, result.gaps().get(0).reason());
    }

    @Test
    void noOpInteractiveBehavesSameAsBefore() {
        var bad = flow(FlowStatus.QUARANTINED, 0, 0, 0);
        var traced = new TracedFlowResult(List.of(bad), List.of());
        var action = new QuarantineFlowAction(null);

        var result = action.quarantineWithResult(traced);

        assertEquals(1, result.gaps().size());
        assertFalse(result.gaps().get(0).userProvided());
        assertNull(result.gaps().get(0).userAnswer());
    }
}
