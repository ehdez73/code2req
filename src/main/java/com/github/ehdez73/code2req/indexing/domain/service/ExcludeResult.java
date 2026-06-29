package com.github.ehdez73.code2req.indexing.domain.service;

import java.nio.file.Path;
import java.util.List;

public record ExcludeResult(List<Path> included, List<Path> excluded) {
    public int excludedCount() {
        return excluded.size();
    }

    public int includedCount() {
        return included.size();
    }
}
