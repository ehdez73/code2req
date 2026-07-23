package com.github.ehdez73.code2req.extraction.domain.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QuarantineUserActionTest {

    @Test
    void optionsReturnsAllActions() {
        var opts = QuarantineUserAction.options();
        assertEquals(3, opts.size());
        assertTrue(opts.contains(QuarantineUserAction.ACCEPT.label()));
        assertTrue(opts.contains(QuarantineUserAction.PROVIDE_CONTEXT.label()));
        assertTrue(opts.contains(QuarantineUserAction.DISMISS.label()));
    }

    @Test
    void fromLabelReturnsCorrectEnum() {
        assertEquals(QuarantineUserAction.ACCEPT, QuarantineUserAction.fromLabel("Accept and quarantine"));
        assertEquals(QuarantineUserAction.PROVIDE_CONTEXT, QuarantineUserAction.fromLabel("Provide additional context for the LLM"));
        assertEquals(QuarantineUserAction.DISMISS, QuarantineUserAction.fromLabel("Dismiss — flow is correct as-is"));
    }

    @Test
    void fromLabelReturnsNullForUnknown() {
        assertNull(QuarantineUserAction.fromLabel("nonexistent"));
    }

    @Test
    void labelReturnsCorrectString() {
        assertEquals("Accept and quarantine", QuarantineUserAction.ACCEPT.label());
        assertEquals("Provide additional context for the LLM", QuarantineUserAction.PROVIDE_CONTEXT.label());
        assertEquals("Dismiss — flow is correct as-is", QuarantineUserAction.DISMISS.label());
    }
}
