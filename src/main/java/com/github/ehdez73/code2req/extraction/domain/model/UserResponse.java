package com.github.ehdez73.code2req.extraction.domain.model;

public record UserResponse(
    String sessionId,
    String question,
    String answer,
    String createdAt
) {}
