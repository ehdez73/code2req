package com.github.ehdez73.code2req.synthesis.agent;

import com.embabel.agent.api.common.OperationContext;
import com.github.ehdez73.code2req.synthesis.CodebaseKnowledge;
import com.github.ehdez73.code2req.synthesis.domain.AmbiguityGap;
import com.github.ehdez73.code2req.synthesis.domain.OrphanedMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FunctionalRequirementExtractor {

    private static final Logger log = LoggerFactory.getLogger(FunctionalRequirementExtractor.class);

    private final CodebaseKnowledge knowledge;
    private final Path outputDir;

    private final DiscoverEntryPointsAction discoverAction;
    private final TraceFlowAction traceAction;
    private final AnalyzeFlowAction analyzeAction;
    private final GroupFlowsAction groupAction;
    private final CrossReferenceFlowsAction crossRefAction;
    private final QuarantineFlowAction quarantineAction;
    private final SynthesizeSpecAction synthesizeAction;

    public FunctionalRequirementExtractor(CodebaseKnowledge knowledge) {
        this(knowledge, Path.of("spec-output"));
    }

    public FunctionalRequirementExtractor(CodebaseKnowledge knowledge, Path outputDir) {
        this.knowledge = knowledge;
        this.outputDir = outputDir;

        this.discoverAction = new DiscoverEntryPointsAction(knowledge);
        this.traceAction = new TraceFlowAction(knowledge);
        this.analyzeAction = new AnalyzeFlowAction(knowledge);
        this.groupAction = new GroupFlowsAction();
        this.crossRefAction = new CrossReferenceFlowsAction(knowledge);
        this.quarantineAction = new QuarantineFlowAction();
        this.synthesizeAction = new SynthesizeSpecAction(outputDir);
    }

    public EntryPointDiscoveryResult discoverEntryPoints() {
        log.info("GOAP Action: DiscoverEntryPoints");
        return discoverAction.discover();
    }

    public TracedFlowResult traceFlows(EntryPointDiscoveryResult discoveryResult) {
        log.info("GOAP Action: TraceFlow ({} entry points)", discoveryResult.entryPoints().size());
        TracedFlowResult traced = traceAction.traceAll(discoveryResult);
        return quarantineAction.quarantine(traced);
    }

    public AnalyzedFlowResult analyzeFlows(TracedFlowResult tracedResult, OperationContext context) {
        log.info("GOAP Action: AnalyzeFlow ({} traced flows)", tracedResult.flows().size());
        if (context == null || tracedResult.flows().isEmpty()) {
            return new AnalyzedFlowResult(List.of());
        }
        return analyzeAction.analyze(tracedResult, context);
    }

    public GroupedFlowsResult groupFlows(AnalyzedFlowResult analyzedResult, OperationContext context) {
        log.info("GOAP Action: GroupFlows ({} analyzed flows)", analyzedResult.flows().size());
        if (context == null || analyzedResult.flows().isEmpty()) {
            return new GroupedFlowsResult(List.of());
        }
        return groupAction.group(analyzedResult, context);
    }

    public CrossReferencedResult crossReferenceFlows(GroupedFlowsResult groupedResult) {
        log.info("GOAP Action: CrossReferenceFlows ({} features)", groupedResult.features().size());
        return crossRefAction.crossReference(groupedResult);
    }

    public SpecResult synthesizeSpec(
            CrossReferencedResult crossRefResult,
            EntryPointDiscoveryResult discoveryResult,
            TracedFlowResult tracedResult,
            OperationContext context) throws IOException {

        log.info("GOAP Action: SynthesizeSpec ({} features)", crossRefResult.features().size());

        List<OrphanedMethod> orphanedMethods = discoveryResult.orphanedMethods();

        List<AmbiguityGap> quarantineGaps = new ArrayList<>();
        for (var flow : tracedResult.flows()) {
            if (flow.status() == com.github.ehdez73.code2req.synthesis.domain.FlowStatus.QUARANTINED) {
                quarantineGaps.add(new AmbiguityGap(
                    flow.flowId(),
                    "Flow quarantined during tracing",
                    "Review entry point and call graph resolution",
                    0.3,
                    com.github.ehdez73.code2req.synthesis.domain.GapReason.LOW_CONFIDENCE
                ));
            }
        }

        return synthesizeAction.synthesize(crossRefResult, orphanedMethods, quarantineGaps, context);
    }
}
