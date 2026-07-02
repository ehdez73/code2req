package com.github.ehdez73.code2req.common.port;

import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.common.domain.TaskStatus;

import java.util.List;
import java.util.Optional;

public interface TaskRepository {
    void save(Task task);

    Optional<Task> findById(String taskId);

    List<Task> findAll();

    List<Task> findByStatus(TaskStatus status);

    List<Task> findByStatusWithoutFinding(TaskStatus status, String findingType);

    List<Task> findByStatusesWithoutFinding(List<TaskStatus> statuses, String findingType);

    void updateStatus(String taskId, TaskStatus status);

    int updateStatusByOldStatus(TaskStatus oldStatus, TaskStatus newStatus);

    void deleteAll();

    int deleteByStatus(TaskStatus status);

    int count();

    int countByStatus(TaskStatus status);

    Task findByIdOrPrefix(String input);

    List<Task> findByPrefix(String prefix);

    List<Task> findByTargetPrefix(String prefix);

    List<Task> findByStatusAndTargetPrefix(TaskStatus status, String prefix);
}
