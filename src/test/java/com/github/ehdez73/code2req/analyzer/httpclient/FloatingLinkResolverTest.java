package com.github.ehdez73.code2req.analyzer.httpclient;

import com.github.ehdez73.code2req.analyzer.AnalysisFinding;
import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.analyzer.endpoint.EndpointInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FloatingLinkResolverTest {

    private final FloatingLinkResolver resolver = new FloatingLinkResolver();

    private static AnalysisResult resultWith(OutboundHttpCallInfo call, EndpointInfo... endpoints) {
        var findings = new ArrayList<AnalysisFinding>();
        findings.add(call);
        findings.addAll(List.of(endpoints));
        return new AnalysisResult(call.filePath(), findings);
    }

    @Test
    void exactLiteralMatchResolvedAtFullConfidence() {
        var call = new OutboundHttpCallInfo("GET", "/api/users", false,
            "REST_TEMPLATE", "getUsers", "UserService", "UserService.java");
        var endpoint = new EndpointInfo("GET", "/api/users", "UserController", List.of(), List.of(), "UserController.java", false, "");
        var links = resolver.resolve(List.of(resultWith(call, endpoint)));

        assertEquals(1, links.size());
        assertEquals("RESOLVED", links.get(0).resolvedStatus());
        assertEquals(1.0, links.get(0).confidence());
        assertEquals("/api/users", links.get(0).targetEndpoint());
    }

    @Test
    void pathVariableMatchAtEightConfidence() {
        var call = new OutboundHttpCallInfo("GET", "/api/users/42", false,
            "REST_TEMPLATE", "getUser", "UserService", "UserService.java");
        var endpoint = new EndpointInfo("GET", "/api/users/{id}", "UserController", List.of(), List.of(), "UserController.java", false, "");
        var links = resolver.resolve(List.of(resultWith(call, endpoint)));

        assertEquals(1, links.size());
        assertEquals("RESOLVED", links.get(0).resolvedStatus());
        assertTrue(links.get(0).confidence() >= 0.6);
    }

    @Test
    void noMatchLeavesPending() {
        var call = new OutboundHttpCallInfo("POST", "/api/external", false,
            "REST_TEMPLATE", "callExternal", "Service", "Service.java");
        var endpoint = new EndpointInfo("GET", "/api/internal", "InternalController", List.of(), List.of(), "IC.java", false, "");
        var links = resolver.resolve(List.of(resultWith(call, endpoint)));

        assertEquals(1, links.size());
        assertEquals("PENDING", links.get(0).resolvedStatus());
        assertEquals(0.0, links.get(0).confidence());
        assertNull(links.get(0).targetEndpoint());
    }

    @Test
    void methodMismatchPreventsMatch() {
        var call = new OutboundHttpCallInfo("DELETE", "/api/users/1", false,
            "REST_TEMPLATE", "deleteUser", "UserService", "UserService.java");
        var ep1 = new EndpointInfo("GET", "/api/users/{id}", "UserController", List.of(), List.of(), "UC.java", false, "");
        var ep2 = new EndpointInfo("DELETE", "/api/users/{id}", "UserAdminController", List.of(), List.of(), "UAC.java", false, "");
        var links = resolver.resolve(List.of(resultWith(call, ep1, ep2)));

        assertEquals(1, links.size());
        assertEquals("RESOLVED", links.get(0).resolvedStatus());
        assertEquals("DELETE", links.get(0).method());
    }

    @Test
    void bestConfidenceIsChosen() {
        var call = new OutboundHttpCallInfo("GET", "/api/items/5/details", false,
            "WEB_CLIENT", "getItemDetails", "ItemService", "ItemService.java");
        var ep1 = new EndpointInfo("GET", "/api/items/{id}/details", "ItemController", List.of(), List.of(), "IC.java", false, "");
        var ep2 = new EndpointInfo("GET", "/api/items/all", "ItemListController", List.of(), List.of(), "ILC.java", false, "");
        var links = resolver.resolve(List.of(resultWith(call, ep1, ep2)));

        assertEquals(1, links.size());
        assertEquals("RESOLVED", links.get(0).resolvedStatus());
        assertEquals("/api/items/{id}/details", links.get(0).targetEndpoint());
    }
}
