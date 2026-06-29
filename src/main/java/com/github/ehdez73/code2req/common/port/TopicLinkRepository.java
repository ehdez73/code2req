package com.github.ehdez73.code2req.common.port;

import com.github.ehdez73.code2req.indexing.domain.analyzer.event.link.TopicLink;

import java.util.List;

public interface TopicLinkRepository {
    void save(TopicLink link);

    List<TopicLink> findAll();

    void deleteByTaskId(String taskId);

    int countByTaskId(String taskId);
}
