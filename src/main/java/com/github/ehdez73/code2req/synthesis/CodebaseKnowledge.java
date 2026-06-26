package com.github.ehdez73.code2req.synthesis;

import com.github.ehdez73.code2req.analyzer.bean.ComponentInfo;
import com.github.ehdez73.code2req.analyzer.callgraph.CallGraphEdge;
import com.github.ehdez73.code2req.analyzer.event.link.TopicLink;
import com.github.ehdez73.code2req.analyzer.httpclient.FloatingLinkInfo;
import com.github.ehdez73.code2req.analyzer.web.endpoint.EndpointInfo;
import com.github.ehdez73.code2req.model.ExecutionFinding;
import com.github.ehdez73.code2req.synthesis.domain.EntryPoint;

import java.util.List;
import java.util.Map;

public class CodebaseKnowledge {

    private final StructuralGraph structuralGraph;
    private final SemanticEnrichment semanticEnrichment;
    private final LinkRegistry linkRegistry;

    public CodebaseKnowledge(
            StructuralGraph structuralGraph,
            SemanticEnrichment semanticEnrichment,
            LinkRegistry linkRegistry) {
        this.structuralGraph = structuralGraph;
        this.semanticEnrichment = semanticEnrichment;
        this.linkRegistry = linkRegistry;
    }

    public StructuralGraph structuralGraph() { return structuralGraph; }
    public SemanticEnrichment semanticEnrichment() { return semanticEnrichment; }
    public LinkRegistry linkRegistry() { return linkRegistry; }

    public List<String> getFlowCandidates() {
        return structuralGraph.getFlowCandidates();
    }

    public List<ComponentInfo> getComponentsByType(String annotationType) {
        return structuralGraph.getComponentsByType(annotationType);
    }

    public List<CallGraphEdge> getCallersOf(String targetFile) {
        return structuralGraph.getCallersOf(targetFile);
    }

    public List<CallGraphEdge> getCalleesOf(String sourceFile) {
        return structuralGraph.getCalleesOf(sourceFile);
    }

    public List<ExecutionFinding.HappyPath> getAllHappyPaths() {
        return semanticEnrichment.getAllHappyPaths();
    }

    public List<String> getFlowNames() {
        return semanticEnrichment.getFlowNames();
    }

    public List<FloatingLinkInfo> findUnresolvedLinks() {
        return linkRegistry.findUnresolvedFloatingLinks();
    }

    public List<TopicLink> findUnresolvedTopicLinks() {
        return linkRegistry.findUnresolvedTopicLinks();
    }

    public List<FloatingLinkInfo> findAllFloatingLinks() {
        return linkRegistry.floatingLinks();
    }

    public List<TopicLink> findAllTopicLinks() {
        return linkRegistry.topicLinks();
    }

    public List<EntryPoint> getEntryPoints() {
        return structuralGraph.getEntryPoints();
    }

    public List<MethodIdentifier> getAllKnownMethods() {
        return structuralGraph.getAllKnownMethods();
    }

    public List<ExecutionFinding.TestInsight> getAllTestInsights() {
        return semanticEnrichment.getAllTestInsights();
    }

    public Map<String, String> getAllTestFilePaths() {
        return semanticEnrichment.getAllTestFilePaths();
    }
}
