package com.github.ehdez73.code2req.extraction.adapter.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContextBudgetCalculatorTest {

    private final ContextBudgetCalculator calculator = new ContextBudgetCalculator(128_000);

    @Test
    void estimateTokenCountForEmptyString() {
        assertEquals(0, calculator.estimateTokenCount(null));
        assertEquals(0, calculator.estimateTokenCount(""));
        assertEquals(0, calculator.estimateTokenCount("  "));
    }

    @Test
    void estimateTokenCountUsesCharDiv4Ratio() {
        assertEquals(1, calculator.estimateTokenCount("abcd"));
        assertEquals(2, calculator.estimateTokenCount("abcdefgh"));
        assertEquals(1, calculator.estimateTokenCount("abc"));
    }

    @Test
    void noSummarizationNeededWhenUnderThreshold() {
        String smallContent = "a".repeat(10_000);
        assertFalse(calculator.needsSummarization(smallContent, "", ""));
    }

    @Test
    void needsSummarizationWhenOverThreshold() {
        String largeContent = "a".repeat(500_000);
        assertTrue(calculator.needsSummarization(largeContent, "", ""));
    }

    @Test
    void testContentContributesToBudget() {
        String source = "a".repeat(250_000);
        String test = "b".repeat(250_000);
        assertTrue(calculator.needsSummarization(source, test, ""));
    }

    @Test
    void validatorSlicesContributeToBudget() {
        String source = "a".repeat(200_000);
        String test = "b".repeat(120_000);
        String validator = "c".repeat(120_000);
        assertTrue(calculator.needsSummarization(source, test, validator));
    }

    @Test
    void getDefaultContextWindow() {
        assertEquals(128_000, calculator.getDefaultContextWindow());
    }

    @Test
    void customContextWindow() {
        var custom = new ContextBudgetCalculator(8_000);
        assertEquals(8_000, custom.getDefaultContextWindow());
        String content = "a".repeat(30_000);
        assertTrue(custom.needsSummarization(content, "", ""));
    }
}
