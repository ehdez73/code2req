package com.github.ehdez73.code2req.analyzer.httpclient;

import com.github.ehdez73.code2req.analyzer.httpclient.detector.JavaNetHttpClientDetector;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaNetHttpClientDetectorTest {

    private final JavaNetHttpClientDetector detector = new JavaNetHttpClientDetector();

    private List<OutboundHttpCallInfo> detect(String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        List<OutboundHttpCallInfo> result = new ArrayList<>();
        cu.findAll(MethodDeclaration.class).forEach(md ->
            detector.detect(result, md, "MyService", "MyService.java"));
        return result;
    }

    @Test
    void detectsHttpClientSend() {
        var result = detect("""
            import java.net.http.*;
            class MyService {
                void call() throws Exception {
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://api.example.com/users")).build();
                    client.send(request, HttpResponse.BodyHandlers.ofString());
                }
            }
            """);
        assertFalse(result.isEmpty());
        assertEquals("JAVA_NET_HTTP", result.get(0).clientType());
    }

    @Test
    void detectsHttpClientSendAsync() {
        var result = detect("""
            import java.net.http.*;
            class MyService {
                void call() {
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://api.example.com/orders")).build();
                    client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
                }
            }
            """);
        assertFalse(result.isEmpty());
        assertEquals("JAVA_NET_HTTP", result.get(0).clientType());
    }
}
