package com.github.ehdez73.code2req.executor;

import org.springframework.stereotype.Component;

@Component
public class ContextBudgetCalculator {

    static final double TOKEN_ESTIMATE_RATIO = 4.0;
    static final double CONTEXT_THRESHOLD = 0.80;
    static final int DEFAULT_CONTEXT_WINDOW = 128_000;

    private final int contextWindow;

    public ContextBudgetCalculator() {
        this(DEFAULT_CONTEXT_WINDOW);
    }

    ContextBudgetCalculator(int contextWindow) {
        this.contextWindow = contextWindow;
    }

    public int estimateTokenCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / TOKEN_ESTIMATE_RATIO);
    }

    public boolean needsSummarization(String sourceContent, String testContent, String validatorSlices) {
        int totalTokens = estimateTokenCount(sourceContent)
                        + estimateTokenCount(testContent)
                        + estimateTokenCount(validatorSlices);
        return totalTokens > (int) (contextWindow * CONTEXT_THRESHOLD);
    }

    public int getDefaultContextWindow() {
        return contextWindow;
    }
}
