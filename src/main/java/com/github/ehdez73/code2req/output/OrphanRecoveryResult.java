package com.github.ehdez73.code2req.output;

public record OrphanRecoveryResult(
    int orphanedCount,
    int revertedCount
) {
    public boolean recovered() {
        return revertedCount > 0;
    }
}
