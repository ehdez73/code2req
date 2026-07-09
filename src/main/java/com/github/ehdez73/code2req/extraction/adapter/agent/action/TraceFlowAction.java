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
import com.github.ehdez73.code2req.extraction.domain.model.ExtractionConfig;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.ExecutionFlow;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStatus;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStep;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStepComponentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * For the highest-priority unscheduled entry point, follows call graph edges
 * through the codebase building a list of FlowSteps from entry point through
 * services to repositories/database. Uses sub-chain caching to avoid redundant
 * tracing of shared service chains and applies adaptive depth (max 5).
 */
public class TraceFlowAction {

    private static final Logger log = LoggerFactory.getLogger(TraceFlowAction.class);

    private final CodebaseKnowledge knowledge;
    private final Map<String, List<FlowStep>> subChainCache;
    private final Map<String, FlowStepComponentType> componentTypeLookup;
    private final int maxDepth;
    private final List<String> frameworkPrefixes;
    private final Map<String, FileImports> importsCache;

    public TraceFlowAction(CodebaseKnowledge knowledge, ExtractionConfig config) {
        this(knowledge, config, List.of());
    }

    public TraceFlowAction(CodebaseKnowledge knowledge, ExtractionConfig config, List<String> frameworkPrefixes) {
        this.knowledge = knowledge;
        this.subChainCache = new HashMap<>();
        this.componentTypeLookup = buildComponentTypeLookup();
        this.maxDepth = config != null ? config.resolvedMaxInvestigationStepsPerFlow() : 5;
        this.frameworkPrefixes = frameworkPrefixes != null ? frameworkPrefixes : List.of();
        this.importsCache = new HashMap<>();
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
                case "Entity", "MappedSuperclass" -> FlowStepComponentType.ENTITY;
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
                entryPoint.id(), entryPoint,
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
            entryPoint.id(), entryPoint,
            steps, steps.size(), unresolvedCalls, status
        );
    }

    private void traceFromSource(String sourceFilePath, String sourceClassName,
                                  List<FlowStep> steps, List<String> unresolvedCalls,
                                  Set<String> visited, int depth, String entryMethodName,
                                  int sourceStartLine, int sourceEndLine) {
        if (isMaxDepthReached(depth)) return;

        if (!tryVisit(sourceFilePath, sourceClassName, visited)) return;

        addEntryPointStep(depth, steps, sourceFilePath, sourceClassName, entryMethodName, sourceStartLine, sourceEndLine);

        for (CallGraphEdge edge : getOutgoingEdges(sourceFilePath, sourceClassName)) {
            if (!edge.isResolved()) {
                addUnresolvedCall(unresolvedCalls, edge, sourceFilePath);
                continue;
            }

            List<DbAccessInfo> matchingDbAccess = findDbAccessForEdge(edge);
            addComponentStep(steps, edge, matchingDbAccess);
            addDbAccessSteps(steps, edge, matchingDbAccess);

            traceFromSource(edge.targetFilePath(), edge.targetClassName(),
                steps, unresolvedCalls, visited, depth + 1, null,
                edge.targetStartLine(), edge.targetEndLine());
        }

        if (depth == 0) {
            addSelfDbAccessSteps(steps, sourceClassName);
        }
        addFloatingLinkSteps(steps, sourceFilePath, sourceClassName);
    }

    private static String visitKey(String filePath, String className) {
        return filePath + ":" + className;
    }

    private boolean isMaxDepthReached(int depth) {
        return depth >= maxDepth;
    }

    private boolean tryVisit(String filePath, String className, Set<String> visited) {
        return visited.add(visitKey(filePath, className));
    }

    private List<CallGraphEdge> getOutgoingEdges(String filePath, String className) {
        return knowledge.structuralGraph().callGraphEdges().stream()
            .filter(e -> filePath.equals(e.sourceFilePath()) || className.equals(e.sourceClassName()))
            .toList();
    }

    private void addEntryPointStep(int depth, List<FlowStep> steps, String filePath,
                                   String className, String methodName,
                                   int startLine, int endLine) {
        if (depth == 0) {
            steps.add(new FlowStep(
                    steps.size(), classifySourceComponent(filePath),
                    className, methodName, null,
                    filePath, startLine, endLine, List.of()
            ));
        }
    }

    private void addUnresolvedCall(List<String> unresolvedCalls, CallGraphEdge edge, String sourceFilePath) {
        String call = edge.targetClassName() + "." + edge.targetMethodName();
        if (!isFrameworkCall(call, sourceFilePath)) {
            unresolvedCalls.add(call);
        }
    }

    private List<DbAccessInfo> findDbAccessForEdge(CallGraphEdge edge) {
        return knowledge.structuralGraph().dbAccessPatterns().stream()
            .filter(d -> edge.targetClassName().equals(d.className()))
            .filter(d -> edge.targetStartLine() > 0
                ? (d.startLine() == edge.targetStartLine() && d.endLine() == edge.targetEndLine())
                : d.methodName().equals(edge.targetMethodName()))
            .toList();
    }

    private void addComponentStep(List<FlowStep> steps, CallGraphEdge edge,
                                   List<DbAccessInfo> matchingDbAccess) {
        if (!matchingDbAccess.isEmpty()) return;
        steps.add(new FlowStep(
            steps.size(), classifyComponent(edge),
            edge.targetClassName(), edge.targetMethodName(), null,
            edge.targetFilePath(), edge.targetStartLine(), edge.targetEndLine(), List.of()
        ));
    }

    private void addDbAccessSteps(List<FlowStep> steps, CallGraphEdge edge,
                                   List<DbAccessInfo> matchingDbAccess) {
        for (DbAccessInfo db : matchingDbAccess) {
            List<String> enrichments = new ArrayList<>();
            enrichments.add(db.sql() != null ? db.sql() : "");
            enrichments.add(classifyComponent(edge).name());
            steps.add(new FlowStep(
                steps.size(), FlowStepComponentType.DATABASE,
                db.className(), db.methodName(), null,
                db.filePath(), db.startLine(), db.endLine(), enrichments
            ));
        }
    }

    private void addSelfDbAccessSteps(List<FlowStep> steps, String sourceClassName) {
        List<DbAccessInfo> selfDbAccess = knowledge.structuralGraph().dbAccessPatterns().stream()
            .filter(d -> sourceClassName.equals(d.className()))
            .toList();
        for (DbAccessInfo db : selfDbAccess) {
            steps.add(new FlowStep(
                steps.size(), FlowStepComponentType.DATABASE,
                db.className(), db.methodName(), null,
                db.filePath(), db.startLine(), db.endLine(), List.of(db.sql() != null ? db.sql() : "")
            ));
        }
    }

    private void addFloatingLinkSteps(List<FlowStep> steps, String sourceFilePath,
                                       String sourceClassName) {
        List<FloatingLinkInfo> httpCalls = knowledge.findAllFloatingLinks().stream()
            .filter(f -> sourceFilePath.equals(f.sourceFilePath()))
            .toList();
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

    private boolean isFrameworkCall(String unresolvedCall, String sourceFilePath) {
        if (frameworkPrefixes.stream().anyMatch(unresolvedCall::startsWith)) {
            return true;
        }
        String className = extractClassName(unresolvedCall);
        if (className == null) {
            return false;
        }
        String rest = unresolvedCall.substring(className.length() + 1);
        FileImports fileImports = readFileImports(sourceFilePath);
        String fqn = fileImports.classNameToFqn().get(className);
        if (fqn != null) {
            String reconstructed = fqn + "." + rest;
            return frameworkPrefixes.stream().anyMatch(reconstructed::startsWith);
        }
        for (String pkg : fileImports.wildcardPackages()) {
            String reconstructed = pkg + "." + unresolvedCall;
            if (frameworkPrefixes.stream().anyMatch(reconstructed::startsWith)) {
                return true;
            }
        }
        return false;
    }

    private static String extractClassName(String unresolvedCall) {
        int dot = unresolvedCall.lastIndexOf('.');
        if (dot < 0) return null;
        String beforeDot = unresolvedCall.substring(0, dot);
        int lastDot = beforeDot.lastIndexOf('.');
        return lastDot >= 0 ? beforeDot.substring(lastDot + 1) : beforeDot;
    }

    record FileImports(Map<String, String> classNameToFqn, List<String> wildcardPackages) {
        static final FileImports EMPTY = new FileImports(Map.of(), List.of());
    }

    private FileImports readFileImports(String filePath) {
        if (filePath == null) return FileImports.EMPTY;
        FileImports cached = importsCache.get(filePath);
        if (cached != null) return cached;
        try (var lines = Files.lines(Path.of(filePath))) {
            Map<String, String> classNameToFqn = new HashMap<>();
            List<String> wildcardPackages = new ArrayList<>();
            lines.filter(line -> line.trim().startsWith("import "))
                .map(line -> line.trim().substring(7).replace(";", "").trim())
                .filter(imp -> !imp.startsWith("static"))
                .filter(imp -> frameworkPrefixes.stream().anyMatch(imp::startsWith))
                .forEach(imp -> {
                    if (imp.endsWith(".*")) {
                        wildcardPackages.add(imp.substring(0, imp.length() - 2));
                    } else {
                        String simpleName = imp.substring(imp.lastIndexOf('.') + 1);
                        classNameToFqn.put(simpleName, imp);
                    }
                });
            FileImports result = new FileImports(classNameToFqn, wildcardPackages);
            importsCache.put(filePath, result);
            return result;
        } catch (IOException e) {
            log.debug("Could not read {} for import analysis: {}", filePath, e.toString());
            return FileImports.EMPTY;
        }
    }
}
