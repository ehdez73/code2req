package com.github.ehdez73.code2req.common.port;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;

import java.util.List;
import java.util.Map;

public interface ExecutionFindingRepository {
    void save(String taskId, String findingType, String findingJson, boolean resolved);

    void saveAllForTask(String taskId, List<AnalysisFinding> findings);

    List<Map<String, Object>> findByTaskId(String taskId);

    List<Map<String, Object>> findByTaskIdAndType(String taskId, String findingType);

    int countByTaskId(String taskId);

    int countByTaskIdAndType(String taskId, String findingType);

    void deleteByTaskId(String taskId);

    List<Map<String, Object>> findAllByType(String findingType);
}
