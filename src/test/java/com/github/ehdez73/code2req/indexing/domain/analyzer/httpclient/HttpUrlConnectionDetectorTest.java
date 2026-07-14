package com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient;

import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.detector.HttpUrlConnectionDetector;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HttpUrlConnectionDetectorTest {

    private final HttpUrlConnectionDetector detector = new HttpUrlConnectionDetector();

    private List<OutboundHttpCallInfo> detect(String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        List<OutboundHttpCallInfo> result = new ArrayList<>();
        cu.findAll(MethodDeclaration.class).forEach(md ->
            detector.detect(result, md, "MyService", "MyService.java"));
        return result;
    }

    @Test
    void detectsHttpUrlConnection() {
        var result = detect("""
            import java.net.*;
            class MyService {
                void call() throws Exception {
                    URL url = new URL("http://api.example.com/users");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.connect();
                }
            }
            """);
        assertEquals(1, result.size());
        assertEquals("GET", result.get(0).method());
        assertEquals("http://api.example.com/users", result.get(0).urlPattern());
        assertEquals("HTTP_URL_CONNECTION", result.get(0).clientType());
    }

    @Test
    void detectsPostConnection() {
        var result = detect("""
            import java.net.*;
            class MyService {
                void call() throws Exception {
                    URL url = new URL("http://api.example.com/orders");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.connect();
                }
            }
            """);
        assertEquals(1, result.size());
        assertEquals("POST", result.get(0).method());
    }
}
