package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.github.ehdez73.code2req.extraction.adapter.agent.model.EntryPointDiscoveryResult;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.TracedFlowResult;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.ComponentInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.callgraph.CallGraphEdge;
import com.github.ehdez73.code2req.indexing.domain.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.FloatingLinkInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.scheduledtask.ScheduledTaskInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo;
import com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.ExecutionFlow;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStatus;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStep;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStepComponentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * For the highest-priority unscheduled entry point, follows call graph edges
 * through the codebase building a list of FlowSteps from entry point through
 * services to repositories/database. Uses sub-chain caching to avoid redundant
 * tracing of shared service chains and applies adaptive depth (max 5).
 */
public class TraceFlowAction {

    private static final Logger log = LoggerFactory.getLogger(TraceFlowAction.class);
    private static final int MAX_DEPTH = 5;

    private final CodebaseKnowledge knowledge;
    private final Map<String, List<FlowStep>> subChainCache;
    private final Map<String, FlowStepComponentType> componentTypeLookup;

    public TraceFlowAction(CodebaseKnowledge knowledge) {
        this.knowledge = knowledge;
        this.subChainCache = new HashMap<>();
        this.componentTypeLookup = buildComponentTypeLookup();
    }

    private Map<String, FlowStepComponentType> buildComponentTypeLookup() {
        Map<String, FlowStepComponentType> lookup = new HashMap<>();

        for (EndpointInfo ep : knowledge.structuralGraph().endpoints()) {
            lookup.putIfAbsent(ep.filePath(), FlowStepComponentType.REST_ENDPOINT);
            lookup.putIfAbsent(ep.className(), FlowStepComponentType.REST_ENDPOINT);
        }

        for (ScheduledTaskInfo st : knowledge.structuralGraph().scheduledTasks()) {
            lookup.putIfAbsent(st.filePath(), FlowStepComponentType.SCHEDULED_TASK);
            lookup.putIfAbsent(st.className(), FlowStepComponentType.SCHEDULED_TASK);
        }

        for (ComponentInfo ci : knowledge.structuralGraph().components()) {
            FlowStepComponentType type = switch (ci.annotationType()) {
                case "Controller", "RestController" -> FlowStepComponentType.REST_ENDPOINT;
                case "Service" -> FlowStepComponentType.SERVICE;
                case "Repository" -> FlowStepComponentType.REPOSITORY;
                default -> null;
            };
            if (type != null) {
                lookup.putIfAbsent(ci.filePath(), type);
                lookup.putIfAbsent(ci.className(), type);
            }
        }

        return lookup;
    }

    public TracedFlowResult traceAll(EntryPointDiscoveryResult discoveryResult) {
        List<ExecutionFlow> flows = new ArrayList<>();
        List<String> quarantinedIds = new ArrayList<>();

        for (EntryPoint entryPoint : discoveryResult.entryPoints()) {
            ExecutionFlow flow = traceFlow(entryPoint);
            if (flow.status() == FlowStatus.QUARANTINED) {
                quarantinedIds.add(flow.flowId());
            }
            flows.add(flow);
        }

        log.info("Traced {} flows ({} quarantined)", flows.size(), quarantinedIds.size());
        return new TracedFlowResult(flows, quarantinedIds);
    }

    private ExecutionFlow traceFlow(EntryPoint entryPoint) {
        String cacheKey = entryPoint.filePath() + ":" + entryPoint.className();

        if (subChainCache.containsKey(cacheKey)) {
            log.debug("Reusing cached trace for {}", cacheKey);
            List<FlowStep> cachedSteps = subChainCache.get(cacheKey);
            return new ExecutionFlow(
                UUID.randomUUID().toString(), entryPoint,
                cachedSteps, cachedSteps.size(), List.of(),
                FlowStatus.TRACED
            );
        }

        List<FlowStep> steps = new ArrayList<>();
        List<String> unresolvedCalls = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        traceFromSource(entryPoint.filePath(), entryPoint.className(),
            steps, unresolvedCalls, visited, 0, entryPoint.methodName(),
            entryPoint.startLine(), entryPoint.endLine());

        subChainCache.put(cacheKey, steps);

        FlowStatus status = steps.isEmpty() ? FlowStatus.QUARANTINED : FlowStatus.TRACED;

        return new ExecutionFlow(
            UUID.randomUUID().toString(), entryPoint,
            steps, steps.size(), unresolvedCalls, status
        );
    }

    private void traceFromSource(String sourceFilePath, String sourceClassName,
                                  List<FlowStep> steps, List<String> unresolvedCalls,
                                  Set<String> visited, int depth, String entryMethodName,
                                  int sourceStartLine, int sourceEndLine) {
        if (depth >= MAX_DEPTH) return;

        String visitKey = sourceFilePath + ":" + sourceClassName;
        if (visited.contains(visitKey)) return;
        visited.add(visitKey);

        List<CallGraphEdge> outgoing = knowledge.structuralGraph().callGraphEdges().stream()
            .filter(e -> sourceFilePath.equals(e.sourceFilePath()) || sourceClassName.equals(e.sourceClassName()))
            .collect(Collectors.toList());

        if (depth == 0) {
            steps.add(new FlowStep(
                steps.size(), classifySourceComponent(sourceFilePath),
                sourceClassName, entryMethodName, null,
                sourceFilePath, sourceStartLine, sourceEndLine, List.of()
            ));
        }

        for (CallGraphEdge edge : outgoing) {
            if (!edge.isResolved()) {
                unresolvedCalls.add(edge.targetClassName() + "." + edge.targetMethodName());
                continue;
            }

            String targetKey = edge.targetFilePath() + ":" + edge.targetClassName();
            if (visited.contains(targetKey)) continue;

            FlowStepComponentType componentType = classifyComponent(edge);
            steps.add(new FlowStep(
                steps.size(), componentType,
                edge.targetClassName(), edge.targetMethodName(), null,
                edge.targetFilePath(), edge.targetStartLine(), edge.targetEndLine(), List.of()
            ));

            traceFromSource(edge.targetFilePath(), edge.targetClassName(),
                steps, unresolvedCalls, visited, depth + 1, null,
                edge.targetStartLine(), edge.targetEndLine());
        }

        List<DbAccessInfo> dbAccesses = knowledge.structuralGraph().dbAccessPatterns().stream()
            .filter(d -> sourceClassName.equals(d.className()))
            .collect(Collectors.toList());

        for (DbAccessInfo db : dbAccesses) {
            steps.add(new FlowStep(
                steps.size(), FlowStepComponentType.DATABASE,
                db.className(), db.methodName(), null,
                db.filePath(), db.startLine(), db.endLine(), List.of(db.sql() != null ? db.sql() : "")
            ));
        }

        List<FloatingLinkInfo> httpCalls = knowledge.findAllFloatingLinks().stream()
            .filter(f -> sourceFilePath.equals(f.sourceFilePath()))
            .collect(Collectors.toList());

        for (FloatingLinkInfo http : httpCalls) {
            steps.add(new FlowStep(
                steps.size(), FlowStepComponentType.EXTERNAL_CALL,
                sourceClassName, null, null,
                sourceFilePath, http.startLine(), http.endLine(),
                List.of(http.method() + " " + http.urlPattern())
            ));
        }
    }

    private FlowStepComponentType classifySourceComponent(String filePath) {
        FlowStepComponentType type = componentTypeLookup.get(filePath);
        if (type != null) return type;
        return fallbackSourceComponentType(filePath);
    }

    private FlowStepComponentType classifyComponent(CallGraphEdge edge) {
        FlowStepComponentType type = componentTypeLookup.get(edge.targetFilePath());
        if (type == null) type = componentTypeLookup.get(edge.targetClassName());
        if (type != null) return type;
        return fallbackTargetComponentType(edge);
    }

    private static FlowStepComponentType fallbackSourceComponentType(String filePath) {
        if (filePath.contains("Controller")) return FlowStepComponentType.REST_ENDPOINT;
        if (filePath.contains("Service")) return FlowStepComponentType.SERVICE;
        if (filePath.contains("Repository") || filePath.contains("Repo")) return FlowStepComponentType.REPOSITORY;
        if (filePath.contains("Scheduler") || filePath.contains("Job")) return FlowStepComponentType.SCHEDULED_TASK;
        if (filePath.contains("Listener") || filePath.contains("Consumer")) return FlowStepComponentType.EVENT_PUBLISHER;
        return FlowStepComponentType.SERVICE;
    }

    private static FlowStepComponentType fallbackTargetComponentType(CallGraphEdge edge) {
        String targetFile = edge.targetFilePath();
        if (targetFile.contains("Controller")) return FlowStepComponentType.REST_ENDPOINT;
        if (targetFile.contains("Service")) return FlowStepComponentType.SERVICE;
        if (targetFile.contains("Repository") || targetFile.contains("Repo")) return FlowStepComponentType.REPOSITORY;
        if (targetFile.contains("Client") || targetFile.contains("Feign")) return FlowStepComponentType.EXTERNAL_CALL;
        if (targetFile.contains("Listener") || targetFile.contains("Consumer")) return FlowStepComponentType.EVENT_PUBLISHER;
        if (targetFile.contains("Scheduler") || targetFile.contains("Job")) return FlowStepComponentType.SCHEDULED_TASK;
        return FlowStepComponentType.SERVICE;
    }
}
