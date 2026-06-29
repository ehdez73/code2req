package com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient;

import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.detector.WebClientDetector;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WebClientDetectorTest {

    private final WebClientDetector detector = new WebClientDetector();

    private List<OutboundHttpCallInfo> detect(String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        List<OutboundHttpCallInfo> result = new ArrayList<>();
        cu.findAll(MethodDeclaration.class).forEach(md ->
            detector.detect(result, md, "MyService", "MyService.java"));
        return result;
    }

    @Test
    void detectsWebClientGet() {
        var result = detect("""
            class MyService {
                void call() {
                    webClient.get().uri("http://api.example.com/users").retrieve();
                }
            }
            """);
        assertEquals(1, result.size());
        assertEquals("GET", result.get(0).method());
        assertEquals("http://api.example.com/users", result.get(0).urlPattern());
        assertEquals("WEB_CLIENT", result.get(0).clientType());
    }

    @Test
    void detectsWebClientPost() {
        var result = detect("""
            class MyService {
                void call() {
                    webClient.post().uri("http://api.example.com/orders").retrieve();
                }
            }
            """);
        assertEquals(1, result.size());
        assertEquals("POST", result.get(0).method());
    }

    @Test
    void detectsWebClientMethodChain() {
        var result = detect("""
            class MyService {
                void call() {
                    webClient.method(HttpMethod.DELETE).uri("http://api.example.com/items/1").retrieve();
                }
            }
            """);
        assertEquals(1, result.size());
        assertEquals("DELETE", result.get(0).method());
    }

    @Test
    void detectsExpressionUrl() {
        var result = detect("""
            class MyService {
                void call() {
                    webClient.get().uri("${service.url}/users").retrieve();
                }
            }
            """);
        assertEquals(1, result.size());
        assertTrue(result.get(0).isExpression());
    }
}
