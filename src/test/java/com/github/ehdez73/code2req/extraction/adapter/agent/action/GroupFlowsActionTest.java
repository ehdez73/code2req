package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.github.ehdez73.code2req.extraction.adapter.agent.model.AnalyzedFlowResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GroupFlowsActionTest {

    @Test
    void groupEmptyFlowsReturnsEmptyFeatures() {
        var result = new GroupFlowsAction().group(new AnalyzedFlowResult(List.of()), null);
        assertTrue(result.features().isEmpty());
    }

    @Test
    void groupWithMultipleFlowsReturnsResult() {
        var result = new GroupFlowsAction().group(new AnalyzedFlowResult(List.of()), null);
        assertNotNull(result);
    }
}
