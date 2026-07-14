package com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.detector.*;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OutboundHttpVisitorTest {

    private final OutboundHttpVisitor visitor = new OutboundHttpVisitor(List.of(
        new RestTemplateDetector(),
        new WebClientDetector(),
        new FeignClientDetector(),
        new RestClientDetector(),
        new HttpExchangeDetector(),
        new JavaNetHttpClientDetector(),
        new HttpUrlConnectionDetector(),
        new ApacheHttpClientDetector(),
        new OkHttpDetector()
    ));

    private AnalysisResult analyze(String filePath, String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        AnalysisResultBuilder builder = new AnalysisResultBuilder();
        visitor.analyze(cu, builder, new AnalysisContext(filePath));
        return builder.build(filePath);
    }

    @Test
    void allDetectorsWiredViaVisitor() {
        var result = analyze("App.java", """
            class App {
                void getUsers() {
                    restTemplate.getForObject("/api/users", String.class);
                }
            }
            """);
        assertEquals(1, result.findings(OutboundHttpCallInfo.class).size());
    }

    @Test
    void multipleDetectorsInSameFile() {
        var result = analyze("Service.java", """
            import org.springframework.web.client.RestTemplate;
            class Service {
                void callA() {
                    restTemplate.getForObject("/api/a", String.class);
                }
                void callB() {
                    restTemplate.postForObject("/api/b", "body", String.class);
                }
            }
            """);
        assertEquals(2, result.findings(OutboundHttpCallInfo.class).size());
    }

    @Test
    void nonHttpCodeProducesNoFindings() {
        var result = analyze("MathService.java", """
            class MathService {
                int add(int a, int b) { return a + b; }
            }
            """);
        assertTrue(result.findings(OutboundHttpCallInfo.class).isEmpty());
    }
}
