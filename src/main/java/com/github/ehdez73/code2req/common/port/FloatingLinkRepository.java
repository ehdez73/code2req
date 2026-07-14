package com.github.ehdez73.code2req.common.port;

import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.FloatingLinkInfo;

import java.util.List;

public interface FloatingLinkRepository {
    void save(FloatingLinkInfo link);

    void saveAll(List<FloatingLinkInfo> links);

    List<FloatingLinkInfo> findAll();

    void deleteByTaskId(String taskId);

    void deleteAll();

    int countByTaskId(String taskId);

    int count();

    List<String> findSourceFilePathsByResolvedStatus(String status);
}
