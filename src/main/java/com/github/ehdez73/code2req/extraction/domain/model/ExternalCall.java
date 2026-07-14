package com.github.ehdez73.code2req.extraction.domain.model;

public record ExternalCall(
    String httpMethod,
    String url,
    Integer timeoutMs,
    String retryStrategy,
    String fallbackBehavior
) {}
