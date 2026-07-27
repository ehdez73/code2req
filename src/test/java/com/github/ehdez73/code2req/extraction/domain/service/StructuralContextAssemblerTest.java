package com.github.ehdez73.code2req.extraction.domain.service;

import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StructuralContextAssemblerTest {

    @Mock
    private ExecutionFindingStore executionFindingStore;

    @InjectMocks
    private StructuralContextAssembler assembler;

    @Test
    void returnsNullWhenNoFindings() {
        when(executionFindingStore.findByTaskId("t1")).thenReturn(List.of());
        assertNull(assembler.assemble("t1"));
    }

    @Test
    void assemblesContextWithSingleType() {
        when(executionFindingStore.findByTaskId("t1"))
            .thenReturn(List.of(
                Map.of("finding_type", "ENDPOINT", "finding_json", "{\"path\":\"/api/test\",\"method\":\"GET\"}")
            ));

        String json = assembler.assemble("t1");
        assertNotNull(json);
        assertTrue(json.contains("endpoint"));
        assertTrue(json.contains("/api/test"));
    }

    @Test
    void groupsFindingsByType() {
        when(executionFindingStore.findByTaskId("t1"))
            .thenReturn(List.of(
                Map.of("finding_type", "ENDPOINT", "finding_json", "{\"path\":\"/api/a\"}"),
                Map.of("finding_type", "ENDPOINT", "finding_json", "{\"path\":\"/api/b\"}"),
                Map.of("finding_type", "COMPONENT", "finding_json", "{\"className\":\"MyService\"}")
            ));

        String json = assembler.assemble("t1");
        assertNotNull(json);
        assertTrue(json.contains("/api/a"));
        assertTrue(json.contains("/api/b"));
        assertTrue(json.contains("MyService"));
    }

    @Test
    void excludesSemanticEnrichmentType() {
        when(executionFindingStore.findByTaskId("t1"))
            .thenReturn(List.of(
                Map.of("finding_type", "SEMANTIC_ENRICHMENT", "finding_json", "{\"ignored\":true}")
            ));

        assertNull(assembler.assemble("t1"));
    }

    @Test
    void excludesFlowTypes() {
        when(executionFindingStore.findByTaskId("t1"))
            .thenReturn(List.of(
                Map.of("finding_type", "FLOW_ANALYSIS", "finding_json", "{}"),
                Map.of("finding_type", "FLOW_GROUPING", "finding_json", "{}")
            ));

        assertNull(assembler.assemble("t1"));
    }

    @Test
    void structuralTypesPassThroughExclusion() {
        when(executionFindingStore.findByTaskId("t1"))
            .thenReturn(List.of(
                Map.of("finding_type", "COMPONENT", "finding_json", "{\"className\":\"x\"}"),
                Map.of("finding_type", "SEMANTIC_ENRICHMENT", "finding_json", "{\"ignored\":true}")
            ));

        String json = assembler.assemble("t1");
        assertNotNull(json);
        assertTrue(json.contains("component"));
    }

    @Test
    void skipsMalformedFindingJson() {
        when(executionFindingStore.findByTaskId("t1"))
            .thenReturn(List.of(
                Map.of("finding_type", "ENDPOINT", "finding_json", "not-valid-json")
            ));

        assertNull(assembler.assemble("t1"));
    }

    @Test
    void skipsNullFindingJson() {
        var row = new java.util.HashMap<String, Object>();
        row.put("finding_type", "ENDPOINT");
        row.put("finding_json", null);

        when(executionFindingStore.findByTaskId("t1"))
            .thenReturn(List.of(row));

        assertNull(assembler.assemble("t1"));
    }

    @Test
    void keysAreLowercase() {
        when(executionFindingStore.findByTaskId("t1"))
            .thenReturn(List.of(
                Map.of("finding_type", "CALL_GRAPH_EDGE", "finding_json", "{\"source\":\"A\"}")
            ));

        String json = assembler.assemble("t1");
        assertNotNull(json);
        assertTrue(json.contains("call_graph_edge"));
    }
}
