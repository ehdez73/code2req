package com.github.ehdez73.code2req.common.port;

import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.FloatingLinkInfo;

import java.util.List;

public interface FloatingLinkRepository {
    void save(FloatingLinkInfo link);

    List<FloatingLinkInfo> findAll();

    void deleteByTaskId(String taskId);

    int countByTaskId(String taskId);

    List<String> findSourceFilePathsByResolvedStatus(String status);
}
