package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.github.ehdez73.code2req.extraction.adapter.agent.model.EntryPointDiscoveryResult;
import com.github.ehdez73.code2req.indexing.domain.analyzer.callgraph.CallGraphEdge;
import com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge;
import com.github.ehdez73.code2req.extraction.domain.model.MethodIdentifier;
import com.github.ehdez73.code2req.extraction.domain.model.StructuralGraph;
import com.github.ehdez73.code2req.extraction.domain.model.ActiveMqEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.EventListenerEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.HttpEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.KafkaEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.OrphanedMethod;
import com.github.ehdez73.code2req.extraction.domain.model.RabbitMqEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.ScheduledEntryPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scans CodebaseKnowledge for all entry points (HTTP endpoints, @Scheduled,
 * @KafkaListener, @RabbitListener, @JmsListener, @EventListener), filters
 * trivial endpoints (actuator, health, metrics), scores each by priority,
 * and detects orphaned methods not reachable from any entry point.
 */
public class DiscoverEntryPointsAction {

    private static final Logger log = LoggerFactory.getLogger(DiscoverEntryPointsAction.class);

    private static final Set<String> TRIVIAL_PATHS = Set.of(
        "/actuator", "/actuator/health", "/actuator/info", "/actuator/metrics",
        "/health", "/info", "/metrics", "/env", "/beans", "/configprops",
        "/mappings", "/trace", "/httptrace"
    );

    private final CodebaseKnowledge knowledge;

    public DiscoverEntryPointsAction(CodebaseKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public EntryPointDiscoveryResult discover() {
        StructuralGraph graph = knowledge.structuralGraph();

        List<EntryPoint> allEntryPoints = graph.getEntryPoints();

        List<EntryPoint> scored = allEntryPoints.stream()
            .map(this::scoreEntryPoint)
            .filter(ep -> !ep.trivial())
            .sorted((a, b) -> Double.compare(b.priorityScore(), a.priorityScore()))
            .collect(Collectors.toList());

        List<OrphanedMethod> orphans = detectOrphanedMethods(scored);

        log.info("Discovered {} entry points ({} trivial filtered out), {} orphaned methods",
            scored.size(), allEntryPoints.size() - scored.size(), orphans.size());

        return new EntryPointDiscoveryResult(scored, orphans);
    }

    private EntryPoint scoreEntryPoint(EntryPoint ep) {
        double score = knowledge.structuralGraph().getEntryPointPriority(
            ep, knowledge.semanticEnrichment(), knowledge.getAllTestFilePaths());

        boolean trivial = (ep instanceof HttpEntryPoint h && TRIVIAL_PATHS.contains(h.path()))
            || (ep.className() != null && ep.className().contains("Health"))
            || (ep.className() != null && ep.className().contains("Metrics"))
            || (ep.className() != null && ep.className().contains("Info"));

        return switch (ep) {
            case HttpEntryPoint h -> new HttpEntryPoint(
                h.id(), h.className(), h.methodName(), h.filePath(),
                score, trivial,
                h.httpMethod(), h.path(), h.pathVariables(), h.requestBodies(),
                h.startLine(), h.endLine()
            );
            case ScheduledEntryPoint s -> new ScheduledEntryPoint(
                s.id(), s.className(), s.methodName(), s.filePath(),
                score, trivial, s.schedule(),
                s.startLine(), s.endLine()
            );
            case KafkaEntryPoint k -> new KafkaEntryPoint(
                k.id(), k.className(), k.methodName(), k.filePath(),
                score, trivial,
                k.topics(), k.isPattern(), k.payloadType()
            );
            case RabbitMqEntryPoint r -> new RabbitMqEntryPoint(
                r.id(), r.className(), r.methodName(), r.filePath(),
                score, trivial,
                r.queues(), r.payloadType()
            );
            case ActiveMqEntryPoint a -> new ActiveMqEntryPoint(
                a.id(), a.className(), a.methodName(), a.filePath(),
                score, trivial,
                a.destination(), a.payloadType()
            );
            case EventListenerEntryPoint e -> new EventListenerEntryPoint(
                e.id(), e.className(), e.methodName(), e.filePath(),
                score, trivial,
                e.payloadType()
            );
        };
    }

    private List<OrphanedMethod> detectOrphanedMethods(List<EntryPoint> entryPoints) {
        Set<String> reachableFiles = entryPoints.stream()
            .map(EntryPoint::filePath)
            .collect(Collectors.toSet());

        List<CallGraphEdge> allEdges = knowledge.structuralGraph().callGraphEdges();

        Set<String> calledFiles = allEdges.stream()
            .filter(CallGraphEdge::isResolved)
            .map(CallGraphEdge::targetFilePath)
            .filter(f -> f != null && !f.isEmpty())
            .collect(Collectors.toSet());

        Set<String> allReachable = new HashSet<>(reachableFiles);
        allReachable.addAll(calledFiles);

        List<OrphanedMethod> orphans = new ArrayList<>();
        for (MethodIdentifier method : knowledge.getAllKnownMethods()) {
            if (!allReachable.contains(method.filePath())) {
                orphans.add(new OrphanedMethod(
                    method.className(),
                    method.methodName(),
                    method.filePath(),
                    0, 0,
                    "Not reachable from any discovered entry point"
                ));
            }
        }

        return orphans;
    }
}
