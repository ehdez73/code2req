package com.github.ehdez73.code2req.analyzer.httpclient;

import com.github.ehdez73.code2req.analyzer.httpclient.detector.RestClientDetector;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RestClientDetectorTest {

    private final RestClientDetector detector = new RestClientDetector();

    private List<OutboundHttpCallInfo> detect(String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        List<OutboundHttpCallInfo> result = new ArrayList<>();
        cu.findAll(MethodDeclaration.class).forEach(md ->
            detector.detect(result, md, "MyService", "MyService.java"));
        return result;
    }

    @Test
    void detectsRestClientGet() {
        var result = detect("""
            class MyService {
                void call() {
                    restClient.get().uri("http://api.example.com/users").retrieve();
                }
            }
            """);
        assertEquals(1, result.size());
        assertEquals("GET", result.get(0).method());
        assertEquals("http://api.example.com/users", result.get(0).urlPattern());
        assertEquals("REST_CLIENT", result.get(0).clientType());
    }

    @Test
    void detectsRestClientPost() {
        var result = detect("""
            class MyService {
                void call() {
                    restClient.post().uri("http://api.example.com/orders").retrieve();
                }
            }
            """);
        assertEquals(1, result.size());
        assertEquals("POST", result.get(0).method());
    }

    @Test
    void detectsRestClientExpressionUrl() {
        var result = detect("""
            class MyService {
                void call() {
                    restClient.get().uri("${base.url}/items").retrieve();
                }
            }
            """);
        assertEquals(1, result.size());
        assertTrue(result.get(0).isExpression());
    }
}
