package com.github.ehdez73.code2req.synthesis;

import com.github.ehdez73.code2req.analyzer.bean.ComponentInfo;
import com.github.ehdez73.code2req.analyzer.callgraph.CallGraphEdge;
import com.github.ehdez73.code2req.analyzer.db.DbAccessInfo;
import com.github.ehdez73.code2req.analyzer.web.endpoint.EndpointInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class StructuralGraph {

    private final List<CallGraphEdge> callGraphEdges;
    private final List<EndpointInfo> endpoints;
    private final List<DbAccessInfo> dbAccessPatterns;
    private final List<ComponentInfo> components;

    public StructuralGraph() {
        this.callGraphEdges = new ArrayList<>();
        this.endpoints = new ArrayList<>();
        this.dbAccessPatterns = new ArrayList<>();
        this.components = new ArrayList<>();
    }

    public StructuralGraph(
            List<CallGraphEdge> callGraphEdges,
            List<EndpointInfo> endpoints,
            List<DbAccessInfo> dbAccessPatterns,
            List<ComponentInfo> components) {
        this.callGraphEdges = Collections.unmodifiableList(callGraphEdges);
        this.endpoints = Collections.unmodifiableList(endpoints);
        this.dbAccessPatterns = Collections.unmodifiableList(dbAccessPatterns);
        this.components = Collections.unmodifiableList(components);
    }

    public List<CallGraphEdge> callGraphEdges() { return callGraphEdges; }
    public List<EndpointInfo> endpoints() { return endpoints; }
    public List<DbAccessInfo> dbAccessPatterns() { return dbAccessPatterns; }
    public List<ComponentInfo> components() { return components; }

    public List<String> getFlowCandidates() {
        return callGraphEdges.stream()
            .filter(CallGraphEdge::isResolved)
            .map(CallGraphEdge::sourceFilePath)
            .distinct()
            .collect(Collectors.toList());
    }

    public List<EndpointInfo> getEndpointsByHttpMethod(String method) {
        return endpoints.stream()
            .filter(e -> e.httpMethod().equalsIgnoreCase(method))
            .collect(Collectors.toList());
    }

    public List<CallGraphEdge> getCallersOf(String targetFile) {
        return callGraphEdges.stream()
            .filter(e -> targetFile.equals(e.targetFilePath()))
            .collect(Collectors.toList());
    }

    public List<CallGraphEdge> getCalleesOf(String sourceFile) {
        return callGraphEdges.stream()
            .filter(e -> sourceFile.equals(e.sourceFilePath()))
            .collect(Collectors.toList());
    }

    public List<ComponentInfo> getComponentsByType(String annotationType) {
        return components.stream()
            .filter(c -> annotationType.equals(c.annotationType()))
            .collect(Collectors.toList());
    }
}
