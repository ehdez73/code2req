package com.github.ehdez73.code2req.indexing.domain.analyzer.bean;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.ComponentInfo;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComponentVisitorTest {

    private final ComponentVisitor visitor = new ComponentVisitor();

    private AnalysisResult analyze(String filePath, String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        AnalysisResultBuilder builder = new AnalysisResultBuilder();
        visitor.analyze(cu, builder, new AnalysisContext(filePath));
        return builder.build(filePath);
    }

    @Test
    void detectsRestController() {
        AnalysisResult result = analyze("MyController.java", """
            package com.example;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            public class MyController {}
            """);

        assertEquals(1, result.findings(ComponentInfo.class).size());
        ComponentInfo c = result.findings(ComponentInfo.class).get(0);
        assertEquals("RestController", c.annotationType());
        assertEquals("MyController", c.className());
        assertEquals("com.example", c.packageName());
    }

    @Test
    void detectsService() {
        AnalysisResult result = analyze("MyService.java", """
            package com.example;
            import org.springframework.stereotype.Service;
            @Service
            public class MyService {}
            """);

        assertEquals(1, result.findings(ComponentInfo.class).size());
        assertEquals("Service", result.findings(ComponentInfo.class).get(0).annotationType());
    }

    @Test
    void detectsRepository() {
        AnalysisResult result = analyze("MyRepository.java", """
            package com.example;
            import org.springframework.stereotype.Repository;
            @Repository
            public class MyRepository {}
            """);

        assertEquals(1, result.findings(ComponentInfo.class).size());
        assertEquals("Repository", result.findings(ComponentInfo.class).get(0).annotationType());
    }

    @Test
    void detectsComponent() {
        AnalysisResult result = analyze("MyComponent.java", """
            package com.example;
            import org.springframework.stereotype.Component;
            @Component
            public class MyComponent {}
            """);

        assertEquals(1, result.findings(ComponentInfo.class).size());
        assertEquals("Component", result.findings(ComponentInfo.class).get(0).annotationType());
    }

    @Test
    void detectsController() {
        AnalysisResult result = analyze("MyMvcController.java", """
            package com.example;
            import org.springframework.stereotype.Controller;
            @Controller
            public class MyMvcController {}
            """);

        assertEquals(1, result.findings(ComponentInfo.class).size());
        assertEquals("Controller", result.findings(ComponentInfo.class).get(0).annotationType());
    }

    @Test
    void plainJavaClassClassifiedAsOther() {
        AnalysisResult result = analyze("PlainClass.java", """
            package com.example;
            public class PlainClass {}
            """);

        assertTrue(result.findings(ComponentInfo.class).isEmpty());
    }

    @Test
    void interfaceIsSkipped() {
        AnalysisResult result = analyze("MyInterface.java", """
            package com.example;
            public interface MyInterface {}
            """);

        assertTrue(result.findings(ComponentInfo.class).isEmpty());
    }

    @Test
    void multipleClassesInSameFile() {
        AnalysisResult result = analyze("MyService.java", """
            package com.example;
            @Service
            public class MyService {}
            class HelperClass {}
            """);

        assertEquals(1, result.findings(ComponentInfo.class).size());
        assertEquals("Service", result.findings(ComponentInfo.class).get(0).annotationType());
    }

    @Test
    void recordsFileName() {
        AnalysisResult result = analyze("/path/to/MyApi.java", """
            @RestController
            public class MyApi {}
            """);

        assertEquals("/path/to/MyApi.java", result.findings(ComponentInfo.class).get(0).filePath());
    }
}
