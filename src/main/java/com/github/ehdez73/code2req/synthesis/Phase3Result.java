package com.github.ehdez73.code2req.synthesis;

import java.util.List;

public record Phase3Result(
    int flowsExtracted,
    int ambiguityGaps,
    int awaitingReview,
    List<String> flowNames
) {
    public static Phase3Result empty() {
        return new Phase3Result(0, 0, 0, List.of());
    }
}
