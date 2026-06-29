package com.github.ehdez73.code2req.common.domain;

public enum TaskStatus {
    PENDING,
    ENRICH_PENDING,
    ENRICHING,
    INDEXED,
    ENRICHED,
    FAILED,
    ENRICH_FAILED,
    AWAITING_HUMAN_REVIEW
}
