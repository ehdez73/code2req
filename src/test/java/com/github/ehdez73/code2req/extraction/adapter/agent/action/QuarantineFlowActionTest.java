package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.github.ehdez73.code2req.extraction.adapter.agent.model.TracedFlowResult;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.HttpEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.ExecutionFlow;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStatus;
import com.github.ehdez73.code2req.extraction.domain.model.GapReason;
import com.github.ehdez73.code2req.extraction.domain.model.QuarantineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuarantineFlowActionTest {

    private final EntryPoint entryPoint = new HttpEntryPoint("id", "Ctrl", "m", "/f.java",
        0.5, false, "GET", "/test", List.of(), List.of());

    @TempDir
    Path tempDir;

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

        var result = new QuarantineFlowAction(null).quarantine(traced);

        assertEquals(1, result.flows().size());
        assertEquals(FlowStatus.TRACED, result.flows().get(0).status());
        assertTrue(result.allQuarantinedFlowIds().isEmpty());
    }

    @Test
    void quarantineQuarantinesFlowWithNoSteps() {
        var bad = flow(FlowStatus.QUARANTINED, 0, 0, 0);
        var traced = new TracedFlowResult(List.of(bad), List.of());

        var result = new QuarantineFlowAction(null).quarantine(traced);

        assertTrue(result.flows().isEmpty());
        assertEquals(1, result.allQuarantinedFlowIds().size());
    }

    @Test
    void quarantineQuarantinesFlowExceedingMaxSteps() {
        var bad = flow(FlowStatus.TRACED, 21, 1, 0);
        var traced = new TracedFlowResult(List.of(bad), List.of());

        var result = new QuarantineFlowAction(null).quarantine(traced);

        assertTrue(result.flows().isEmpty());
        assertEquals(1, result.allQuarantinedFlowIds().size());
    }

    @Test
    void quarantineQuarantinesFlowExceedingMaxDepth() {
        var bad = flow(FlowStatus.TRACED, 3, 6, 0);
        var traced = new TracedFlowResult(List.of(bad), List.of());

        var result = new QuarantineFlowAction(null).quarantine(traced);

        assertTrue(result.flows().isEmpty());
        assertEquals(1, result.allQuarantinedFlowIds().size());
    }

    @Test
    void quarantineQuarantinesFlowWithTooManyUnresolvedCalls() {
        var bad = flow(FlowStatus.TRACED, 3, 1, 4);
        var traced = new TracedFlowResult(List.of(bad), List.of());

        var result = new QuarantineFlowAction(null).quarantine(traced);

        assertTrue(result.flows().isEmpty());
        assertEquals(1, result.allQuarantinedFlowIds().size());
    }

    @Test
    void quarantineQuarantinesFlowBelowConfidenceThreshold() {
        var bad = flow(FlowStatus.TRACED, 1, 1, 3);
        var traced = new TracedFlowResult(List.of(bad), List.of());

        var result = new QuarantineFlowAction(null).quarantine(traced);

        assertTrue(result.flows().isEmpty());
        assertEquals(1, result.allQuarantinedFlowIds().size());
    }

    @Test
    void quarantineKeepsFlowAboveConfidenceThreshold() {
        var good = flow(FlowStatus.TRACED, 3, 1, 1);
        var traced = new TracedFlowResult(List.of(good), List.of());

        var result = new QuarantineFlowAction(null).quarantine(traced);

        assertEquals(1, result.flows().size());
        assertEquals(FlowStatus.TRACED, result.flows().get(0).status());
        assertTrue(result.allQuarantinedFlowIds().isEmpty());
    }

    @Test
    void quarantineIgnoresFrameworkUnresolvedCalls() {
        var flow = new ExecutionFlow("flow-fw", entryPoint, List.of(), 1,
            List.of(
                "org.springframework.data.domain.PageRequest.of",
                "org.springframework.data.domain.Page.getContent",
                "org.springframework.data.domain.Page.getTotalPages",
                "org.springframework.ui.Model.addAttribute"
            ),
            FlowStatus.TRACED
        );
        var traced = new TracedFlowResult(List.of(flow), List.of());

        var result = new QuarantineFlowAction(null).quarantine(traced);

        assertEquals(1, result.flows().size());
        assertEquals(FlowStatus.TRACED, result.flows().get(0).status());
        assertTrue(result.allQuarantinedFlowIds().isEmpty());
    }

    @Test
    void quarantineStillFlagsNonFrameworkCallsAmongFrameworkOnes() {
        var flow = new ExecutionFlow("flow-mixed", entryPoint, List.of(), 1,
            List.of(
                "org.springframework.data.domain.PageRequest.of",
                "com.unknown.library.SomeClass.someMethod",
                "org.springframework.data.domain.Page.getContent",
                "com.unknown.library.OtherClass.otherMethod"
            ),
            FlowStatus.TRACED
        );
        var traced = new TracedFlowResult(List.of(flow), List.of());

        var result = new QuarantineFlowAction(null).quarantine(traced);

        assertTrue(result.flows().isEmpty());
        assertEquals(1, result.allQuarantinedFlowIds().size());
    }

    @Test
    void quarantineRecognizesFrameworkCallsViaImports() throws IOException {
        Path javaFile = tempDir.resolve("TestController.java");
        Files.writeString(javaFile, """
            package com.example;
            import org.springframework.data.domain.PageRequest;
            import org.springframework.data.domain.Page;
            import org.springframework.ui.Model;
            public class TestController {}
            """);
        var ep = new HttpEntryPoint("id", "Ctrl", "m", javaFile.toString(),
            0.5, false, "GET", "/test", List.of(), List.of());
        var flow = new ExecutionFlow("flow-imports", ep, List.of(), 1,
            List.of("PageRequest.of", "Page.getContent", "Model.addAttribute"),
            FlowStatus.TRACED);
        var traced = new TracedFlowResult(List.of(flow), List.of());

        var result = new QuarantineFlowAction(null).quarantine(traced);

        assertEquals(1, result.flows().size());
        assertEquals(FlowStatus.TRACED, result.flows().get(0).status());
        assertTrue(result.allQuarantinedFlowIds().isEmpty());
    }

    @Test
    void quarantineRecognizesFrameworkCallsViaWildcardImports() throws IOException {
        Path javaFile = tempDir.resolve("WildcardController.java");
        Files.writeString(javaFile, """
            package com.example;
            import org.springframework.data.domain.*;
            import org.springframework.ui.*;
            public class WildcardController {}
            """);
        var ep = new HttpEntryPoint("id", "Ctrl", "m", javaFile.toString(),
            0.5, false, "GET", "/test", List.of(), List.of());
        var flow = new ExecutionFlow("flow-wildcard", ep, List.of(), 1,
            List.of("PageRequest.of", "Page.getContent", "Model.addAttribute"),
            FlowStatus.TRACED);
        var traced = new TracedFlowResult(List.of(flow), List.of());

        var result = new QuarantineFlowAction(null).quarantine(traced);

        assertEquals(1, result.flows().size());
        assertEquals(FlowStatus.TRACED, result.flows().get(0).status());
        assertTrue(result.allQuarantinedFlowIds().isEmpty());
    }

    @Test
    void quarantineStillFlagsCallsNotMatchingAnyImport() throws IOException {
        Path javaFile = tempDir.resolve("OtherController.java");
        Files.writeString(javaFile, """
            package com.example;
            import com.unknown.library.SomeKnownClass;
            public class OtherController {}
            """);
        var ep = new HttpEntryPoint("id", "Ctrl", "m", javaFile.toString(),
            0.5, false, "GET", "/test", List.of(), List.of());
        var flow = new ExecutionFlow("flow-no-match", ep, List.of(), 1,
            List.of("SomeUnknownClass.doSomething", "AnotherUnknown.process"),
            FlowStatus.TRACED);
        var traced = new TracedFlowResult(List.of(flow), List.of());

        var result = new QuarantineFlowAction(null).quarantine(traced);

        assertTrue(result.flows().isEmpty());
        assertEquals(1, result.allQuarantinedFlowIds().size());
    }

    @Test
    void quarantineIgnoresStaticImports() throws IOException {
        Path javaFile = tempDir.resolve("StaticImportController.java");
        Files.writeString(javaFile, """
            package com.example;
            import static org.springframework.data.domain.PageRequest.of;
            import org.springframework.data.domain.Page;
            public class StaticImportController {}
            """);
        var ep = new HttpEntryPoint("id", "Ctrl", "m", javaFile.toString(),
            0.5, false, "GET", "/test", List.of(), List.of());
        var flow = new ExecutionFlow("flow-static", ep, List.of(), 1,
            List.of("Page.of", "Page.getContent"),
            FlowStatus.TRACED);
        var traced = new TracedFlowResult(List.of(flow), List.of());

        var result = new QuarantineFlowAction(null).quarantine(traced);

        assertEquals(1, result.flows().size());
        assertEquals(FlowStatus.TRACED, result.flows().get(0).status());
        assertTrue(result.allQuarantinedFlowIds().isEmpty());
    }

    @Test
    void quarantineWithResultReturnsGaps() {
        var bad = flow(FlowStatus.QUARANTINED, 0, 0, 0);
        var traced = new TracedFlowResult(List.of(bad), List.of());

        var result = new QuarantineFlowAction(null).quarantineWithResult(traced);

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

        var result = new QuarantineFlowAction(null).quarantine(traced);

        assertEquals(1, result.flows().size());
        assertEquals(1, result.allQuarantinedFlowIds().size());
    }

    @Test
    void getQuarantineGapsReturnsMatchingGaps() {
        var clean = flow(FlowStatus.TRACED, 3, 2, 0);
        var bad = flow(FlowStatus.QUARANTINED, 0, 0, 0);
        var original = new TracedFlowResult(List.of(clean, bad), List.of());
        var quarantined = new QuarantineFlowAction(null).quarantine(original);

        var gaps = new QuarantineFlowAction(null).getQuarantineGaps(original, quarantined);

        assertEquals(1, gaps.size());
        assertTrue(gaps.get(0).flowId().contains("0-0"));
    }

    @Test
    void getQuarantineGapsFallsBackForUnknownFlows() {
        var clean = flow(FlowStatus.TRACED, 3, 2, 0);
        var original = new TracedFlowResult(List.of(clean), List.of());
        var quarantined = new TracedFlowResult(List.of(), List.of("nonexistent"));

        var gaps = new QuarantineFlowAction(null).getQuarantineGaps(original, quarantined);

        assertEquals(0, gaps.size());
    }
}
