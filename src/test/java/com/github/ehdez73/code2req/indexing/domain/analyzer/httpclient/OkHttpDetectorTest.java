package com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient;

import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.detector.OkHttpDetector;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OkHttpDetectorTest {

    private final OkHttpDetector detector = new OkHttpDetector();

    private List<OutboundHttpCallInfo> detect(String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        List<OutboundHttpCallInfo> result = new ArrayList<>();
        cu.findAll(MethodDeclaration.class).forEach(md ->
            detector.detect(result, md, "MyService", "MyService.java"));
        return result;
    }

    @Test
    void detectsOkHttpGet() {
        var result = detect("""
            import okhttp3.*;
            class MyService {
                void call() throws Exception {
                    OkHttpClient client = new OkHttpClient();
                    Request request = new Request.Builder().url("http://api.example.com/users").get().build();
                    client.newCall(request).execute();
                }
            }
            """);
        assertEquals(1, result.size());
        assertEquals("GET", result.get(0).method());
        assertEquals("http://api.example.com/users", result.get(0).urlPattern());
        assertEquals("OK_HTTP", result.get(0).clientType());
    }

    @Test
    void detectsOkHttpPost() {
        var result = detect("""
            import okhttp3.*;
            class MyService {
                void call() throws Exception {
                    OkHttpClient client = new OkHttpClient();
                    RequestBody body = RequestBody.create("{}", MediaType.parse("application/json"));
                    Request request = new Request.Builder().url("http://api.example.com/orders").post(body).build();
                    client.newCall(request).execute();
                }
            }
            """);
        assertEquals(1, result.size());
        assertEquals("POST", result.get(0).method());
    }
}
