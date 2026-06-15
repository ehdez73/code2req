package com.github.ehdez73.code2req.pipeline;

import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.analyzer.declaration.GlobalDeclarationRegistry;

import java.util.List;

public record ScanPipelineResult(
    List<AnalysisResult> results,
    GlobalDeclarationRegistry declarationRegistry,
    int analyzedCount,
    int failedCount
) {}
