package com.github.ehdez73.code2req.analyzer.eventlink;

import com.github.ehdez73.code2req.analyzer.AnalysisResult;

import java.util.List;

public interface TopicLinkResolverStrategy {
    String brokerType();
    List<TopicLink> resolve(List<AnalysisResult> allResults);
}
