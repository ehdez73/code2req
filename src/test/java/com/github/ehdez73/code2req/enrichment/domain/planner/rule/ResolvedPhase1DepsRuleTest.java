package com.github.ehdez73.code2req.enrichment.domain.planner.rule;

import com.github.ehdez73.code2req.enrichment.domain.model.QualificationReason;
import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.github.ehdez73.code2req.enrichment.domain.planner.PlanningContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolvedPhase1DepsRuleTest {

    @Mock
    private PlanningContext ctx;

    private final ResolvedPhase1DepsRule rule = new ResolvedPhase1DepsRule();

    private final Task task = new Task("t1", "/src/Foo.java", TaskStatus.INDEXED, "java", "hash", "test");

    @Test
    void returnsCorrectReason() {
        assertEquals(QualificationReason.RESOLVED_PHASE1_DEPS, rule.reason());
    }

    @Test
    void qualifiesWhenCallGraphEdgeExists() {
        when(ctx.taskHasFindingType(eq("t1"), anyString())).thenReturn(false);
        when(ctx.taskHasFindingType("t1", "CALL_GRAPH_EDGE")).thenReturn(true);
        assertTrue(rule.evaluate(task, ctx));
    }

    @Test
    void qualifiesWhenEndpointExists() {
        when(ctx.taskHasFindingType(eq("t1"), anyString())).thenReturn(false);
        when(ctx.taskHasFindingType("t1", "ENDPOINT")).thenReturn(true);
        assertTrue(rule.evaluate(task, ctx));
    }

    @Test
    void qualifiesWhenComponentExists() {
        when(ctx.taskHasFindingType(eq("t1"), anyString())).thenReturn(false);
        when(ctx.taskHasFindingType("t1", "COMPONENT")).thenReturn(true);
        assertTrue(rule.evaluate(task, ctx));
    }

    @Test
    void qualifiesWhenDbAccessExists() {
        when(ctx.taskHasFindingType(eq("t1"), anyString())).thenReturn(false);
        when(ctx.taskHasFindingType("t1", "DB_ACCESS")).thenReturn(true);
        assertTrue(rule.evaluate(task, ctx));
    }

    @Test
    void doesNotQualifyWhenNoFindings() {
        when(ctx.taskHasFindingType(eq("t1"), anyString())).thenReturn(false);
        assertFalse(rule.evaluate(task, ctx));
    }
}
