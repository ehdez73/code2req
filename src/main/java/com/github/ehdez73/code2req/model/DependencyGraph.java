package com.github.ehdez73.code2req.model;

import java.util.List;

public record DependencyGraph(
    boolean resolved,
    boolean heuristicMode,
    List<Dependency> dependencies,
    String resolutionMessage
) {
    public static DependencyGraph heuristic(String message) {
        return new DependencyGraph(false, true, List.of(), message);
    }
}
