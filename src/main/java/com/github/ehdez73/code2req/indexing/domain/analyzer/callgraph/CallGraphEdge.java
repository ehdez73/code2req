package com.github.ehdez73.code2req.indexing.domain.analyzer.callgraph;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;

import java.util.List;

public record CallGraphEdge(
    String sourceClassName,
    String sourceMethodName,
    String sourceFilePath,
    String targetClassName,
    String targetMethodName,
    String targetFilePath,
    int argCount,
    String resolvedStatus,
    List<String> ambiguousCandidates,
    int targetStartLine,
    int targetEndLine
) implements AnalysisFinding {

    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_UNRESOLVED = "UNRESOLVED";
    public static final String STATUS_AMBIGUOUS = "AMBIGUOUS";

    @Override
    public boolean isResolved() {
        return STATUS_RESOLVED.equals(resolvedStatus());
    }

    @Override
    public String className() {
        return sourceClassName;
    }

    @Override
    public String filePath() {
        return sourceFilePath;
    }

    public static CallGraphEdge resolved(String sourceClass, String sourceMethod, String sourceFile,
                                          String targetClass, String targetMethod, String targetFile,
                                          int argCount) {
        return resolved(sourceClass, sourceMethod, sourceFile,
                        targetClass, targetMethod, targetFile,
                        argCount, 0, 0);
    }

    public static CallGraphEdge resolved(String sourceClass, String sourceMethod, String sourceFile,
                                          String targetClass, String targetMethod, String targetFile,
                                          int argCount, int targetStartLine, int targetEndLine) {
        return new CallGraphEdge(sourceClass, sourceMethod, sourceFile,
                                 targetClass, targetMethod, targetFile,
                                 argCount, STATUS_RESOLVED, List.of(), targetStartLine, targetEndLine);
    }

    public static CallGraphEdge unresolved(String sourceClass, String sourceMethod, String sourceFile,
                                            String targetClass, String targetMethod,
                                            int argCount) {
        return unresolved(sourceClass, sourceMethod, sourceFile,
                          targetClass, targetMethod, argCount, 0, 0);
    }

    public static CallGraphEdge unresolved(String sourceClass, String sourceMethod, String sourceFile,
                                            String targetClass, String targetMethod,
                                            int argCount, int targetStartLine, int targetEndLine) {
        return new CallGraphEdge(sourceClass, sourceMethod, sourceFile,
                                 targetClass, targetMethod, "",
                                 argCount, STATUS_UNRESOLVED, List.of(), targetStartLine, targetEndLine);
    }

    public static CallGraphEdge ambiguous(String sourceClass, String sourceMethod, String sourceFile,
                                           String targetClass, String targetMethod,
                                           int argCount, List<String> candidates) {
        return ambiguous(sourceClass, sourceMethod, sourceFile,
                         targetClass, targetMethod, argCount, candidates, 0, 0);
    }

    public static CallGraphEdge ambiguous(String sourceClass, String sourceMethod, String sourceFile,
                                           String targetClass, String targetMethod,
                                           int argCount, List<String> candidates,
                                           int targetStartLine, int targetEndLine) {
        return new CallGraphEdge(sourceClass, sourceMethod, sourceFile,
                                 targetClass, targetMethod, "",
                                 argCount, STATUS_AMBIGUOUS, candidates, targetStartLine, targetEndLine);
    }
}
