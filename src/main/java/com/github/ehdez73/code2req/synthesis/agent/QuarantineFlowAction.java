package com.github.ehdez73.code2req.synthesis.agent;

import com.github.ehdez73.code2req.synthesis.domain.AmbiguityGap;
import com.github.ehdez73.code2req.synthesis.domain.ExecutionFlow;
import com.github.ehdez73.code2req.synthesis.domain.FlowStatus;
import com.github.ehdez73.code2req.synthesis.domain.GapReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class QuarantineFlowAction {

    private static final Logger log = LoggerFactory.getLogger(QuarantineFlowAction.class);

    private static final int MAX_STEPS = 20;
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.3;

    public TracedFlowResult quarantine(TracedFlowResult tracedResult) {
        QuarantineFlowResult result = quarantineWithResult(tracedResult);
        return result.cleanResult();
    }

    public QuarantineFlowResult quarantineWithResult(TracedFlowResult tracedResult) {
        List<ExecutionFlow> quarantined = new ArrayList<>();
        List<ExecutionFlow> clean = new ArrayList<>();
        List<AmbiguityGap> gaps = new ArrayList<>();

        for (ExecutionFlow flow : tracedResult.flows()) {
            QuarantineReason reason = evaluateQuarantine(flow);

            if (reason != null) {
                quarantined.add(new ExecutionFlow(
                    flow.flowId(), flow.entryPoint(), flow.steps(),
                    flow.depth(), flow.unresolvedCalls(),
                    FlowStatus.QUARANTINED
                ));

                gaps.add(new AmbiguityGap(
                    flow.flowId(),
                    reason.description,
                    reason.suggestedApproach,
                    reason.confidence,
                    reason.gapReason
                ));
            } else {
                clean.add(flow);
            }
        }

        if (!quarantined.isEmpty()) {
            log.info("Quarantined {} flows: {}", quarantined.size(),
                gaps.stream().map(g -> g.flowId() + " (" + g.reason() + ")").collect(Collectors.joining(", ")));
        }

        List<String> allQuarantinedIds = new ArrayList<>(tracedResult.allQuarantinedFlowIds());
        allQuarantinedIds.addAll(quarantined.stream().map(ExecutionFlow::flowId).toList());

        return new QuarantineFlowResult(new TracedFlowResult(clean, allQuarantinedIds), gaps);
    }

    public record QuarantineFlowResult(TracedFlowResult cleanResult, List<AmbiguityGap> gaps) {}

    public List<AmbiguityGap> getQuarantineGaps(TracedFlowResult originalResult, TracedFlowResult quarantinedResult) {
        List<String> quarantinedIds = quarantinedResult.allQuarantinedFlowIds();

        return originalResult.flows().stream()
            .filter(f -> quarantinedIds.contains(f.flowId()))
            .map(f -> {
                QuarantineReason reason = evaluateQuarantine(f);
                if (reason != null) {
                    return new AmbiguityGap(
                        f.flowId(), reason.description, reason.suggestedApproach,
                        reason.confidence, reason.gapReason
                    );
                }
                return new AmbiguityGap(
                    f.flowId(), "Unknown quarantine reason", "Manual review required",
                    0.0, GapReason.LOW_CONFIDENCE
                );
            })
            .collect(Collectors.toList());
    }

    private QuarantineReason evaluateQuarantine(ExecutionFlow flow) {
        if (flow.status() == FlowStatus.QUARANTINED) {
            if (flow.steps().isEmpty()) {
                return new QuarantineReason(
                    "No traceable steps found from entry point",
                    "Verify entry point class exists in codebase and has call graph edges",
                    0.1, GapReason.LOW_CONFIDENCE
                );
            }
            return new QuarantineReason(
                "Flow marked as quarantined during tracing",
                "Review entry point configuration and call graph resolution",
                0.2, GapReason.LOW_CONFIDENCE
            );
        }

        if (flow.steps().size() > MAX_STEPS) {
            return new QuarantineReason(
                "Flow exceeds maximum step limit (" + flow.steps().size() + " > " + MAX_STEPS + ")",
                "Split into sub-flows or increase investigation budget",
                0.4, GapReason.STEPS_EXCEEDED
            );
        }

        if (flow.depth() > 5) {
            return new QuarantineReason(
                "Call chain depth exceeds maximum (" + flow.depth() + " > 5)",
                "Review deep call chains for potential abstraction layers",
                0.3, GapReason.HOP_DEPTH
            );
        }

        long unresolvedCount = flow.unresolvedCalls().size();
        if (unresolvedCount > 3) {
            return new QuarantineReason(
                "Too many unresolved calls (" + unresolvedCount + ")",
                "Resolve external dependencies or add LLM enrichment context",
                0.4, GapReason.LOW_CONFIDENCE
            );
        }

        return null;
    }

    private record QuarantineReason(
        String description,
        String suggestedApproach,
        double confidence,
        GapReason gapReason
    ) {}
}
