package com.github.ehdez73.code2req.pipeline;

import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.analyzer.declaration.GlobalDeclarationRegistry;
import com.github.ehdez73.code2req.analyzer.event.link.TopicLink;
import com.github.ehdez73.code2req.analyzer.httpclient.FloatingLinkInfo;

import java.util.Collections;
import java.util.List;

public record ScanPipelineResult(
    List<AnalysisResult> results,
    GlobalDeclarationRegistry declarationRegistry,
    int analyzedCount,
    int failedCount,
    List<TopicLink> topicLinks,
    List<FloatingLinkInfo> floatingLinks
) {
    public ScanPipelineResult(
            List<AnalysisResult> results,
            GlobalDeclarationRegistry declarationRegistry,
            int analyzedCount,
            int failedCount) {
        this(results, declarationRegistry, analyzedCount, failedCount,
             Collections.emptyList(), Collections.emptyList());
    }

    public ScanPipelineResult(
            List<AnalysisResult> results,
            GlobalDeclarationRegistry declarationRegistry,
            int analyzedCount,
            int failedCount,
            List<TopicLink> topicLinks) {
        this(results, declarationRegistry, analyzedCount, failedCount,
             topicLinks, Collections.emptyList());
    }
}
