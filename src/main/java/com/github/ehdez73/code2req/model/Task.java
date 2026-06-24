package com.github.ehdez73.code2req.model;

import java.time.LocalDateTime;

public record Task(
    String taskId,
    String filePath,
    TaskStatus status,
    String contentType,
    String contentHash,
    String targetName,
    String pairedTestPath,
    String createdAt,
    String updatedAt
) {
    public Task(String taskId, String filePath, TaskStatus status, String contentType, String contentHash, String targetName) {
        this(taskId, filePath, status, contentType, contentHash, targetName, null, LocalDateTime.now().toString(), LocalDateTime.now().toString());
    }

    public Task(String taskId, String filePath, TaskStatus status, String contentType, String contentHash, String targetName, String pairedTestPath) {
        this(taskId, filePath, status, contentType, contentHash, targetName, pairedTestPath, LocalDateTime.now().toString(), LocalDateTime.now().toString());
    }
}
