package com.github.ehdez73.code2req.analyzer.httpclient;

import com.github.ehdez73.code2req.analyzer.httpclient.detector.FeignClientDetector;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FeignClientDetectorTest {

    private final FeignClientDetector detector = new FeignClientDetector();

    private List<OutboundHttpCallInfo> detect(String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        List<OutboundHttpCallInfo> result = new ArrayList<>();
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cid ->
            detector.detectClass(result, cid, cid.getNameAsString(), "PaymentClient.java"));
        return result;
    }

    @Test
    void detectsFeignClientWithUrlAndMapping() {
        var result = detect("""
            import org.springframework.cloud.openfeign.FeignClient;
            import org.springframework.web.bind.annotation.PostMapping;
            @FeignClient(name = "payment", url = "${payment.url}")
            interface PaymentClient {
                @PostMapping("/charges")
                Object charge(Object request);
            }
            """);
        assertEquals(1, result.size());
        assertEquals("POST", result.get(0).method());
        assertEquals("${payment.url}/charges", result.get(0).urlPattern());
        assertTrue(result.get(0).isExpression());
        assertEquals("FEIGN_CLIENT", result.get(0).clientType());
    }

    @Test
    void detectsFeignClientWithGetMapping() {
        var result = detect("""
            import org.springframework.cloud.openfeign.FeignClient;
            import org.springframework.web.bind.annotation.GetMapping;
            @FeignClient(name = "users", url = "http://users-api:8080")
            interface UserClient {
                @GetMapping("/users/{id}")
                Object getUser(String id);
            }
            """);
        assertEquals(1, result.size());
        assertEquals("GET", result.get(0).method());
        assertEquals("http://users-api:8080/users/{id}", result.get(0).urlPattern());
    }

    @Test
    void skipsNonFeignClient() {
        var result = detect("""
            interface PlainInterface {
                void doSomething();
            }
            """);
        assertTrue(result.isEmpty());
    }
}
