package com.github.ehdez73.code2req.model;

import java.time.LocalDateTime;

public record Task(
    String taskId,
    String filePath,
    TaskStatus status,
    String contentType,
    String contentHash,
    String createdAt,
    String updatedAt
) {
    public Task(String taskId, String filePath, TaskStatus status, String contentType, String contentHash) {
        this(taskId, filePath, status, contentType, contentHash, LocalDateTime.now().toString(), LocalDateTime.now().toString());
    }
}
