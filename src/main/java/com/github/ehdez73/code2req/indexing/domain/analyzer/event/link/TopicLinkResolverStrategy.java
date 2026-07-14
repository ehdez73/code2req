package com.github.ehdez73.code2req.indexing.domain.analyzer.event.link;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResult;

import java.util.List;

public interface TopicLinkResolverStrategy {
    String brokerType();
    List<TopicLink> resolve(List<AnalysisResult> allResults);
}
