package com.github.ehdez73.code2req.indexing.domain.service;

public record OrphanRecoveryResult(
    int orphanedCount,
    int revertedCount
) {
    public boolean recovered() {
        return revertedCount > 0;
    }
}
