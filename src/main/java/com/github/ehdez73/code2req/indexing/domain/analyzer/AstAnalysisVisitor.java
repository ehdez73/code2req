package com.github.ehdez73.code2req.indexing.domain.analyzer;

import com.github.javaparser.ast.CompilationUnit;

@FunctionalInterface
public interface AstAnalysisVisitor {

    void analyze(CompilationUnit cu, AnalysisResultBuilder builder, AnalysisContext context);
}
