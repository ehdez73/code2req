package com.github.ehdez73.code2req.extraction.adapter.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionConfig;
import com.github.ehdez73.code2req.extraction.ExtractionCache;
import com.github.ehdez73.code2req.extraction.adapter.agent.action.*;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.*;
import com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Agent(
    name = "functional-requirement-extractor",
    description = "Extracts functional requirements by tracing execution flows from entry points"
)
public class FunctionalRequirementAgent {

    private static final Logger log = LoggerFactory.getLogger(FunctionalRequirementAgent.class);

    public KnowledgeLoaded loadKnowledge(OperationContext context) {
        CodebaseKnowledge knowledge = (CodebaseKnowledge) context.get("codebaseKnowledge");
        if (knowledge == null) {
            throw new IllegalStateException("codebaseKnowledge not set on blackboard");
        }
        Path outputDir = (Path) context.get("outputDir");
        if (outputDir == null) {
            outputDir = Path.of("spec-output");
        }
        context.set("knowledge", knowledge);
        context.set("outputDir", outputDir);
        WorldState ws = new WorldState();
        context.set("worldState", ws);
        ws.reset();
        ws.setKnowledgeLoaded(true);
        return new KnowledgeLoaded(knowledge);
    }

    @Action
    public EntryPointDiscoveryResult discoverEntryPoints(OperationContext context) {

        KnowledgeLoaded knowledgeLoaded = loadKnowledge(context);

        log.info("GOAP Action: DiscoverEntryPoints (ENTRY_POINTS_DISCOVERED)");
        CodebaseKnowledge knowledge = (CodebaseKnowledge) context.get("knowledge");
        WorldState ws = (WorldState) context.get("worldState");
        DiscoverEntryPointsAction action = new DiscoverEntryPointsAction(knowledge);
        EntryPointDiscoveryResult result = action.discover();
        ws.setEntryPointsDiscovered(true);
        return result;
    }

    @Action
    public TracedFlowResult traceFlows(EntryPointDiscoveryResult discoveryResult, OperationContext context) {
        log.info("GOAP Action: TraceFlow ({} entry points)", discoveryResult.entryPoints().size());
        CodebaseKnowledge knowledge = (CodebaseKnowledge) context.get("knowledge");
        WorldState ws = (WorldState) context.get("worldState");
        ExecutionConfig config = (ExecutionConfig) context.get("executionConfig");
        TraceFlowAction traceAction = new TraceFlowAction(knowledge, config);
        QuarantineFlowAction quarantineAction = new QuarantineFlowAction(config);
        TracedFlowResult traced = traceAction.traceAll(discoveryResult);
        QuarantineFlowAction.QuarantineFlowResult quarantineResult = quarantineAction.quarantineWithResult(traced);
        if (!quarantineResult.gaps().isEmpty()) {
            ws.setFlowQuarantined(true);
        }
        ws.setQuarantineGaps(quarantineResult.gaps());
        ws.setFlowTraced(true);
        return quarantineResult.cleanResult();
    }

    @Action
    public AnalyzedFlowResult analyzeFlows(TracedFlowResult tracedResult, OperationContext context) {
        log.info("GOAP Action: AnalyzeFlow ({} traced flows)", tracedResult.flows().size());
        CodebaseKnowledge knowledge = (CodebaseKnowledge) context.get("knowledge");
        WorldState ws = (WorldState) context.get("worldState");
        if (tracedResult.flows().isEmpty()) {
            ws.setFlowAnalyzed(true);
            return new AnalyzedFlowResult(List.of());
        }
        AnalyzeFlowAction action = new AnalyzeFlowAction(knowledge);
        AnalyzedFlowResult result = action.analyze(tracedResult, context);
        ws.setFlowAnalyzed(true);
        return result;
    }

    @Action
    public AllFlowsAnalyzed allFlowsAnalyzed(AnalyzedFlowResult analyzedResult, OperationContext context) {
        log.info("GOAP Action: allFlowsAnalyzed (ALL_FLOWS_TRACED)");
        WorldState ws = (WorldState) context.get("worldState");
        ws.setAllFlowsTraced(true);
        return new AllFlowsAnalyzed(analyzedResult.flows());
    }

    @Action
    public GroupedFlowsResult groupFlows(AllFlowsAnalyzed allAnalyzed, OperationContext context) {
        log.info("GOAP Action: GroupFlows ({} analyzed flows)", allAnalyzed.flows().size());
        WorldState ws = (WorldState) context.get("worldState");
        if (allAnalyzed.flows().isEmpty()) {
            ws.setFlowsGrouped(true);
            return new GroupedFlowsResult(List.of());
        }
        AnalyzedFlowResult analyzedResult = new AnalyzedFlowResult(allAnalyzed.flows());
        GroupFlowsAction action = new GroupFlowsAction();
        GroupedFlowsResult result = action.group(analyzedResult, context);
        ws.setFlowsGrouped(true);
        return result;
    }

    @Action
    public CrossReferencedResult crossReferenceFlows(GroupedFlowsResult groupedResult, OperationContext context) {
        log.info("GOAP Action: CrossReferenceFlows ({} features)", groupedResult.features().size());
        CodebaseKnowledge knowledge = (CodebaseKnowledge) context.get("knowledge");
        WorldState ws = (WorldState) context.get("worldState");
        CrossReferenceFlowsAction action = new CrossReferenceFlowsAction(knowledge);
        CrossReferencedResult result = action.crossReference(groupedResult);
        ws.setCrossRefsResolved(true);
        return result;
    }

    @AchievesGoal(description = "Extraction cache persisted with features, relationships, and orphaned methods")
    @Action
    public ExtractionCache persistCache(CrossReferencedResult crossRefResult,
                                         EntryPointDiscoveryResult discoveryResult,
                                         TracedFlowResult tracedResult,
                                         OperationContext context) throws IOException {
        log.info("GOAP Action: PersistCache ({} features)", crossRefResult.features().size());
        Path outputDir = (Path) context.get("outputDir");
        if (outputDir == null) {
            outputDir = Path.of("spec-output");
        }
        WorldState ws = (WorldState) context.get("worldState");
        Files.createDirectories(outputDir);

        ExtractionCache cache = new ExtractionCache(
            crossRefResult, discoveryResult.orphanedMethods(), ws.getQuarantineGaps());

        ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);
        Path cachePath = outputDir.resolve("extraction-cache.json");
        mapper.writeValue(cachePath.toFile(), cache);

        log.info("Persisted extraction cache: {} ({} features, {} orphaned methods, {} quarantine gaps)",
            cachePath, crossRefResult.features().size(),
            discoveryResult.orphanedMethods().size(), ws.getQuarantineGaps().size());
        ws.setSpecSynthesized(true);
        return cache;
    }

    public record KnowledgeLoaded(CodebaseKnowledge knowledge) {}

    public record AllFlowsAnalyzed(List<FunctionalFlow> flows) {}
}
