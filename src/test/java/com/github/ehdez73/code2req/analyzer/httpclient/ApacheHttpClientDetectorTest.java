package com.github.ehdez73.code2req.analyzer.httpclient;

import com.github.ehdez73.code2req.analyzer.httpclient.detector.ApacheHttpClientDetector;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApacheHttpClientDetectorTest {

    private final ApacheHttpClientDetector detector = new ApacheHttpClientDetector();

    private List<OutboundHttpCallInfo> detect(String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        List<OutboundHttpCallInfo> result = new ArrayList<>();
        cu.findAll(MethodDeclaration.class).forEach(md ->
            detector.detect(result, md, "MyService", "MyService.java"));
        return result;
    }

    @Test
    void detectsHttpGet() {
        var result = detect("""
            import org.apache.hc.client5.http.classic.HttpClient;
            import org.apache.hc.client5.http.classic.methods.HttpGet;
            class MyService {
                void call() throws Exception {
                    HttpClient client = HttpClients.createDefault();
                    HttpGet request = new HttpGet("http://api.example.com/users");
                    client.execute(request);
                }
            }
            """);
        assertEquals(1, result.size());
        assertEquals("GET", result.get(0).method());
        assertEquals("http://api.example.com/users", result.get(0).urlPattern());
        assertEquals("APACHE_HTTP", result.get(0).clientType());
    }

    @Test
    void detectsHttpPost() {
        var result = detect("""
            import org.apache.hc.client5.http.classic.methods.HttpPost;
            class MyService {
                void call() throws Exception {
                    HttpPost request = new HttpPost("http://api.example.com/orders");
                    httpClient.execute(request);
                }
            }
            """);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> "APACHE_HTTP".equals(r.clientType())));
        assertEquals("http://api.example.com/orders", result.get(1).urlPattern());
    }

    @Test
    void detectsHttpDelete() {
        var result = detect("""
            import org.apache.hc.client5.http.classic.methods.HttpDelete;
            class MyService {
                void call() throws Exception {
                    HttpDelete request = new HttpDelete("http://api.example.com/items/1");
                }
            }
            """);
        assertEquals(1, result.size());
        assertEquals("DELETE", result.get(0).method());
    }
}
