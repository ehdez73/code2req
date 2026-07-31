package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.github.ehdez73.code2req.extraction.adapter.llm.LlmEnrichmentService;
import com.github.ehdez73.code2req.extraction.adapter.llm.TestFileMatcher;
import com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge;
import com.github.ehdez73.code2req.extraction.domain.model.EnrichmentConfig;
import com.github.ehdez73.code2req.extraction.domain.model.ExecutionFlow;
import com.github.ehdez73.code2req.extraction.domain.model.ExecutionFinding;
import com.github.ehdez73.code2req.extraction.domain.model.ExecutionMode;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStep;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStepComponentType;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStatus;
import com.github.ehdez73.code2req.extraction.domain.model.HttpEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.LinkRegistry;
import com.github.ehdez73.code2req.extraction.domain.model.SemanticEnrichment;
import com.github.ehdez73.code2req.extraction.domain.model.StructuralGraph;
import com.github.ehdez73.code2req.extraction.domain.service.StructuralContextAssembler;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EnrichFlowActionTest {

    @TempDir
    Path tempDir;

    private static final EnrichmentConfig CONFIG = new EnrichmentConfig(2, ExecutionMode.SYNC, false, 100_000);

    @Test
    void sharedFileAcrossTwoFlowsIsEnrichedOnlyOnce() throws Exception {
        Path sharedFile = tempDir.resolve("SharedService.java");
        Path controllerA = tempDir.resolve("ControllerA.java");
        Path controllerB = tempDir.resolve("ControllerB.java");
        Files.writeString(sharedFile, "public class SharedService {}");
        Files.writeString(controllerA, "public class ControllerA {}");
        Files.writeString(controllerB, "public class ControllerB {}");

        var enrichmentService = mock(LlmEnrichmentService.class);
        when(enrichmentService.enrich(any(), any(), any(), any(), eq(false)))
            .thenReturn(CompletableFuture.completedFuture(mock(ExecutionFinding.class)));

        var taskStore = mock(TaskStore.class);
        when(taskStore.findByFilePath(anyString())).thenReturn(Optional.empty());

        var testFileMatcher = mock(TestFileMatcher.class);
        when(testFileMatcher.findTestFilePath(anyString())).thenReturn(Optional.of("TestFile.java"));

        var structuralContextAssembler = mock(StructuralContextAssembler.class);
        when(structuralContextAssembler.assemble(anyString())).thenReturn("{}");

        var executionFindingStore = mock(ExecutionFindingStore.class);
        when(executionFindingStore.findAllByType(any())).thenReturn(List.of());

        var knowledge = new CodebaseKnowledge(new StructuralGraph(), new SemanticEnrichment(), new LinkRegistry());

        var action = new EnrichFlowAction(
            enrichmentService, CONFIG, executionFindingStore, testFileMatcher,
            new LinkRegistry(), taskStore, structuralContextAssembler, new ObjectMapper());

        var entryA = new HttpEntryPoint("ep-a", "ControllerA", "handle", controllerA.toString(),
            0.5, false, "GET", "/a", List.of(), List.of(), 1, 2);
        var entryB = new HttpEntryPoint("ep-b", "ControllerB", "handle", controllerB.toString(),
            0.5, false, "GET", "/b", List.of(), List.of(), 1, 2);

        var sharedStep = new FlowStep(1, FlowStepComponentType.SERVICE, "SharedService", "process",
            null, sharedFile.toString(), 0, 0, List.of());

        var flow1 = new ExecutionFlow("flow-1", entryA, List.of(sharedStep), 1, List.of(), FlowStatus.TRACED);
        var flow2 = new ExecutionFlow("flow-2", entryB, List.of(sharedStep), 1, List.of(), FlowStatus.TRACED);

        action.enrich(List.of(flow1, flow2), knowledge);

        verify(enrichmentService, times(1)).enrich(
            argThat(t -> t.filePath().equals(sharedFile.toString())),
            any(), any(), any(), eq(false));
    }

    @Test
    void knowledgeCacheSkipsFileBeforeInlineCache() throws Exception {
        Path cachedFile = tempDir.resolve("CachedService.java");
        Path controller = tempDir.resolve("Controller.java");
        Files.writeString(cachedFile, "public class CachedService {}");
        Files.writeString(controller, "public class Controller {}");

        var enrichmentService = mock(LlmEnrichmentService.class);

        var taskStore = mock(TaskStore.class);
        when(taskStore.findByFilePath(anyString())).thenReturn(Optional.empty());

        var testFileMatcher = mock(TestFileMatcher.class);
        when(testFileMatcher.findTestFilePath(anyString())).thenReturn(Optional.empty());

        var structuralContextAssembler = mock(StructuralContextAssembler.class);
        var executionFindingStore = mock(ExecutionFindingStore.class);
        when(executionFindingStore.findAllByType(any())).thenReturn(List.of());

        var existingFinding = new ExecutionFinding(
            new ExecutionFinding.Metadata("task-1", "cached", cachedFile.toString(), "java", "module", "2024-01-01"),
            new ExecutionFinding.BusinessRulesAndGuardrails(List.of(), List.of()),
            List.of(),
            new ExecutionFinding.ArchitecturalConnections(
                new ExecutionFinding.Inbound(List.of(), List.of(), List.of()),
                new ExecutionFinding.Outbound(List.of(), List.of())),
            List.of());
        var semanticEnrichment = new SemanticEnrichment(
            java.util.Map.of(cachedFile.toString(), existingFinding));
        var knowledge = new CodebaseKnowledge(null, semanticEnrichment, new LinkRegistry());

        var action = new EnrichFlowAction(
            enrichmentService, CONFIG, executionFindingStore, testFileMatcher,
            new LinkRegistry(), taskStore, structuralContextAssembler, new ObjectMapper());

        var entry = new HttpEntryPoint("ep-1", "Controller", "handle", controller.toString(),
            0.5, false, "GET", "/", List.of(), List.of(), 1, 2);
        var step = new FlowStep(1, FlowStepComponentType.SERVICE, "CachedService", "process",
            null, cachedFile.toString(), 0, 0, List.of());

        var flow = new ExecutionFlow("flow-1", entry, List.of(step), 1, List.of(), FlowStatus.TRACED);

        action.enrich(List.of(flow), knowledge);

        verify(enrichmentService, never()).enrich(any(), any(), any(), any(), eq(false));
    }

    @Test
    void inlineCacheIsClearedBetweenEnrichCalls() throws Exception {
        Path sharedFile = tempDir.resolve("SharedService.java");
        Path controllerA = tempDir.resolve("ControllerA.java");
        Path controllerB = tempDir.resolve("ControllerB.java");
        Files.writeString(sharedFile, "public class SharedService {}");
        Files.writeString(controllerA, "public class ControllerA {}");
        Files.writeString(controllerB, "public class ControllerB {}");

        var enrichmentService = mock(LlmEnrichmentService.class);
        when(enrichmentService.enrich(any(), any(), any(), any(), eq(false)))
            .thenReturn(CompletableFuture.completedFuture(mock(ExecutionFinding.class)));

        var taskStore = mock(TaskStore.class);
        when(taskStore.findByFilePath(anyString())).thenReturn(Optional.empty());

        var testFileMatcher = mock(TestFileMatcher.class);
        when(testFileMatcher.findTestFilePath(anyString())).thenReturn(Optional.of("TestFile.java"));

        var structuralContextAssembler = mock(StructuralContextAssembler.class);
        when(structuralContextAssembler.assemble(anyString())).thenReturn("{}");

        var executionFindingStore = mock(ExecutionFindingStore.class);
        when(executionFindingStore.findAllByType(any())).thenReturn(List.of());

        var knowledge = new CodebaseKnowledge(new StructuralGraph(), new SemanticEnrichment(), new LinkRegistry());

        var action = new EnrichFlowAction(
            enrichmentService, CONFIG, executionFindingStore, testFileMatcher,
            new LinkRegistry(), taskStore, structuralContextAssembler, new ObjectMapper());

        var entryA = new HttpEntryPoint("ep-a", "ControllerA", "handle", controllerA.toString(),
            0.5, false, "GET", "/a", List.of(), List.of(), 1, 2);
        var entryB = new HttpEntryPoint("ep-b", "ControllerB", "handle", controllerB.toString(),
            0.5, false, "GET", "/b", List.of(), List.of(), 1, 2);

        var sharedStep = new FlowStep(1, FlowStepComponentType.SERVICE, "SharedService", "process",
            null, sharedFile.toString(), 0, 0, List.of());

        var flow1 = new ExecutionFlow("flow-1", entryA, List.of(sharedStep), 1, List.of(), FlowStatus.TRACED);
        var flow2 = new ExecutionFlow("flow-2", entryB, List.of(sharedStep), 1, List.of(), FlowStatus.TRACED);

        action.enrich(List.of(flow1, flow2), knowledge);

        verify(enrichmentService, times(1)).enrich(
            argThat(t -> t.filePath().equals(sharedFile.toString())),
            any(), any(), any(), eq(false));

        action.enrich(List.of(flow1, flow2), knowledge);

        verify(enrichmentService, times(2)).enrich(
            argThat(t -> t.filePath().equals(sharedFile.toString())),
            any(), any(), any(), eq(false));
    }
}
