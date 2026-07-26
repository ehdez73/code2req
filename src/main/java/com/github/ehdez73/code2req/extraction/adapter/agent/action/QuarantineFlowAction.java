package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.github.ehdez73.code2req.extraction.adapter.agent.model.TracedFlowResult;
import com.github.ehdez73.code2req.extraction.domain.model.ActiveMqEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.AmbiguityGap;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.EventListenerEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.ExecutionFlow;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStatus;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStep;
import com.github.ehdez73.code2req.extraction.domain.model.GapReason;
import com.github.ehdez73.code2req.extraction.domain.model.HttpEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.QuarantineConfig;
import com.github.ehdez73.code2req.extraction.domain.model.KafkaEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.RabbitMqEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.ScheduledEntryPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class QuarantineFlowAction {

    private static final Logger log = LoggerFactory.getLogger(QuarantineFlowAction.class);

    private final double lowConfidenceThreshold;
    private final int maxUnresolvedCalls;

    public QuarantineFlowAction(QuarantineConfig config) {
        this.lowConfidenceThreshold = config != null ? config.resolvedAmbiguityConfidenceThreshold() : QuarantineConfig.DEFAULT_AMBIGUITY_CONFIDENCE_THRESHOLD;
        this.maxUnresolvedCalls = config != null ? config.resolvedMaxUnresolvedCalls() : QuarantineConfig.DEFAULT_MAX_UNRESOLVED_CALLS;
    }

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
                    flowLabel(flow.entryPoint()),
                    flow.entryPoint().filePath(),
                    reason.description,
                    reason.suggestedApproach,
                    reason.confidence,
                    reason.gapReason
                ));
            } else {
                clean.add(resetIfDismissed(flow));
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
                        f.flowId(), flowLabel(f.entryPoint()), f.entryPoint().filePath(),
                        reason.description, reason.suggestedApproach,
                        reason.confidence, reason.gapReason
                    );
                }
                return new AmbiguityGap(
                    f.flowId(), flowLabel(f.entryPoint()), f.entryPoint().filePath(),
                    "Unknown quarantine reason", "Manual review required",
                    0.0, GapReason.LOW_CONFIDENCE
                );
            })
            .collect(Collectors.toList());
    }

    private static ExecutionFlow resetIfDismissed(ExecutionFlow flow) {
        if (flow.status() != FlowStatus.TRACED) {
            return new ExecutionFlow(flow.flowId(), flow.entryPoint(), flow.steps(),
                flow.depth(), flow.unresolvedCalls(), FlowStatus.TRACED);
        }
        return flow;
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

        List<String> unresolvedCalls = flow.unresolvedCalls();
        if (unresolvedCalls.isEmpty()) {
            return null;
        }

        if (unresolvedCalls.size() > maxUnresolvedCalls) {
            return new QuarantineReason(
                "Too many unresolved calls (" + unresolvedCalls.size() + ")",
                "Resolve external dependencies or add LLM enrichment context",
                0.4, GapReason.LOW_CONFIDENCE
            );
        }

        int totalReferences = flow.steps().size() + unresolvedCalls.size();
        if (totalReferences > 0) {
            double confidence = 1.0 - (double) unresolvedCalls.size() / totalReferences;
            if (confidence < lowConfidenceThreshold) {
                return new QuarantineReason(
                    "Flow confidence below threshold (" + String.format("%.2f", confidence)
                        + " < " + lowConfidenceThreshold + ")",
                    "Improve call graph resolution or add manual annotations for unresolved calls",
                    confidence, GapReason.LOW_CONFIDENCE
                );
            }
        }

        return null;
    }

    private static String flowLabel(EntryPoint ep) {
        return switch (ep) {
            case HttpEntryPoint h -> h.httpMethod() + " " + h.path();
            case ScheduledEntryPoint s -> s.className() + "." + s.methodName();
            case KafkaEntryPoint k -> k.className() + "." + k.methodName();
            case RabbitMqEntryPoint r -> r.className() + "." + r.methodName();
            case ActiveMqEntryPoint a -> a.className() + "." + a.methodName();
            case EventListenerEntryPoint e -> e.className() + "." + e.methodName();
        };
    }

    record QuarantineReason(
        String description,
        String suggestedApproach,
        double confidence,
        GapReason gapReason
    ) {}
}
