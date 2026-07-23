package com.github.ehdez73.code2req.indexing.domain.analyzer.bean;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResultBuilder;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QualifierVisitorTest {

    private final QualifierVisitor visitor = new QualifierVisitor();

    private AnalysisResult analyze(String filePath, String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        AnalysisResultBuilder builder = new AnalysisResultBuilder();
        visitor.analyze(cu, builder, new AnalysisContext(filePath));
        return builder.build(filePath);
    }

    @Test
    void detectsQualifierOnField() {
        AnalysisResult result = analyze("OrderService.java", """
            package com.example;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.beans.factory.annotation.Qualifier;
            public class OrderService {
                @Autowired @Qualifier("stripe")
                private PaymentGateway gateway;
            }
            """);

        assertEquals(1, result.findings(QualifierInfo.class).size());
        QualifierInfo q = result.findings(QualifierInfo.class).get(0);
        assertEquals("OrderService", q.className());
        assertEquals("gateway", q.fieldName());
        assertEquals("stripe", q.qualifierValue());
    }

    @Test
    void detectsQualifierOnConstructorParameter() {
        AnalysisResult result = analyze("OrderService.java", """
            package com.example;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.beans.factory.annotation.Qualifier;
            public class OrderService {
                public OrderService(@Autowired @Qualifier("externalNameService") PaymentGateway gateway) {}
            }
            """);

        assertEquals(1, result.findings(QualifierInfo.class).size());
        QualifierInfo q = result.findings(QualifierInfo.class).get(0);
        assertEquals("OrderService", q.className());
        assertEquals("gateway", q.fieldName());
        assertEquals("externalNameService", q.qualifierValue());
    }

    @Test
    void detectsQualifierWithExplicitValue() {
        AnalysisResult result = analyze("Config.java", """
            package com.example;
            import org.springframework.beans.factory.annotation.Qualifier;
            public class Config {
                @Qualifier(value = "primary")
                private PaymentGateway gateway;
            }
            """);

        assertEquals(1, result.findings(QualifierInfo.class).size());
        assertEquals("primary", result.findings(QualifierInfo.class).get(0).qualifierValue());
    }

    @Test
    void ignoresClassesWithoutQualifier() {
        AnalysisResult result = analyze("Simple.java", """
            package com.example;
            public class Simple {
                private String name;
            }
            """);

        assertTrue(result.findings(QualifierInfo.class).isEmpty());
    }

    @Test
    void recordsFilePath() {
        AnalysisResult result = analyze("/src/PaymentService.java", """
            package com.example;
            import org.springframework.beans.factory.annotation.Qualifier;
            public class PaymentService {
                @Qualifier("fast")
                private PaymentGateway gateway;
            }
            """);

        assertEquals("/src/PaymentService.java", result.findings(QualifierInfo.class).get(0).filePath());
    }
}
