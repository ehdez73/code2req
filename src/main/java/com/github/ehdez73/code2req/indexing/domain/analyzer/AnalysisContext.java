package com.github.ehdez73.code2req.indexing.domain.analyzer;

import com.github.ehdez73.code2req.indexing.domain.analyzer.declaration.GlobalDeclarationRegistry;

public record AnalysisContext(
    String filePath,
    String sourceRoot,
    GlobalDeclarationRegistry declarationRegistry
) {

    public AnalysisContext(String filePath) {
        this(filePath, "", null);
    }

    public AnalysisContext(String filePath, String sourceRoot) {
        this(filePath, sourceRoot, null);
    }
}
