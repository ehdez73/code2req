package com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient;

import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.detector.RestTemplateDetector;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RestTemplateDetectorTest {

    private final RestTemplateDetector detector = new RestTemplateDetector();

    private List<OutboundHttpCallInfo> detect(String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        List<OutboundHttpCallInfo> result = new ArrayList<>();
        cu.findAll(MethodDeclaration.class).forEach(md ->
            detector.detect(result, md, "MyService", "MyService.java"));
        return result;
    }

    @Test
    void detectsGetForObject() {
        var result = detect("""
            class MyService {
                void call() {
                    restTemplate.getForObject("http://api.example.com/users", List.class);
                }
            }
            """);
        assertEquals(1, result.size());
        assertEquals("GET", result.get(0).method());
        assertEquals("http://api.example.com/users", result.get(0).urlPattern());
        assertFalse(result.get(0).isExpression());
        assertEquals("REST_TEMPLATE", result.get(0).clientType());
    }

    @Test
    void detectsPostForObject() {
        var result = detect("""
            class MyService {
                void call() {
                    restTemplate.postForObject("http://api.example.com/orders", request, Response.class);
                }
            }
            """);
        assertEquals(1, result.size());
        assertEquals("POST", result.get(0).method());
        assertEquals("http://api.example.com/orders", result.get(0).urlPattern());
    }

    @Test
    void detectsExchangeWithHttpMethod() {
        var result = detect("""
            class MyService {
                void call() {
                    restTemplate.exchange("http://api.example.com/items", HttpMethod.PUT, entity, Response.class);
                }
            }
            """);
        assertEquals(1, result.size());
        assertEquals("PUT", result.get(0).method());
        assertEquals("http://api.example.com/items", result.get(0).urlPattern());
    }

    @Test
    void detectsExpressionUrl() {
        var result = detect("""
            class MyService {
                void call() {
                    restTemplate.getForObject("${api.base.url}/users", List.class);
                }
            }
            """);
        assertEquals(1, result.size());
        assertTrue(result.get(0).isExpression());
    }

    @Test
    void skipsNonRestTemplateCalls() {
        var result = detect("""
            class MyService {
                void call() {
                    someOtherClient.getForObject("http://example.com", List.class);
                }
            }
            """);
        assertTrue(result.isEmpty());
    }
}
