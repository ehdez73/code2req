package com.github.ehdez73.code2req.analyzer;

import com.github.javaparser.ast.CompilationUnit;

@FunctionalInterface
public interface AstAnalysisVisitor {

    void analyze(CompilationUnit cu, AnalysisResultBuilder builder, String filePath);
}
