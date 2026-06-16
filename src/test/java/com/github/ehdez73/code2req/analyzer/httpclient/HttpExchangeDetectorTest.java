package com.github.ehdez73.code2req.analyzer.httpclient;

import com.github.ehdez73.code2req.analyzer.httpclient.detector.HttpExchangeDetector;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HttpExchangeDetectorTest {

    private final HttpExchangeDetector detector = new HttpExchangeDetector();

    private List<OutboundHttpCallInfo> detect(String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        List<OutboundHttpCallInfo> result = new ArrayList<>();
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cid ->
            detector.detectClass(result, cid, cid.getNameAsString(), "UserClient.java"));
        return result;
    }

    @Test
    void detectsHttpExchangeInterface() {
        var result = detect("""
            import org.springframework.web.service.annotation.HttpExchange;
            import org.springframework.web.service.annotation.PostExchange;
            @HttpExchange(url = "${payment.service.url}")
            interface PaymentClient {
                @PostExchange("/charges")
                Object charge(Object request);
            }
            """);
        assertEquals(1, result.size());
        assertEquals("POST", result.get(0).method());
        assertEquals("${payment.service.url}/charges", result.get(0).urlPattern());
        assertTrue(result.get(0).isExpression());
        assertEquals("HTTP_EXCHANGE", result.get(0).clientType());
    }

    @Test
    void detectsGetExchange() {
        var result = detect("""
            import org.springframework.web.service.annotation.GetExchange;
            interface UserClient {
                @GetExchange("/users/{id}")
                Object getUser(String id);
            }
            """);
        assertEquals(1, result.size());
        assertEquals("GET", result.get(0).method());
        assertEquals("/users/{id}", result.get(0).urlPattern());
    }
}
