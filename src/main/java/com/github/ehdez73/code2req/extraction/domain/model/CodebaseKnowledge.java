package com.github.ehdez73.code2req.extraction.domain.model;

import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.ComponentInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.callgraph.CallGraphEdge;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.link.TopicLink;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.FloatingLinkInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo;
import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionFinding;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPoint;

import java.util.List;
import java.util.Map;

public record CodebaseKnowledge(
        StructuralGraph structuralGraph,
        SemanticEnrichment semanticEnrichment,
        LinkRegistry linkRegistry
) {

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
