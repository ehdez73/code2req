package com.github.ehdez73.code2req.analyzer.bean.java;

import com.github.ehdez73.code2req.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.analyzer.AnalysisResultBuilder;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BeanMethodVisitorTest {

    private final BeanMethodVisitor visitor = new BeanMethodVisitor();

    private AnalysisResult analyze(String filePath, String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        AnalysisResultBuilder builder = new AnalysisResultBuilder();
        visitor.analyze(cu, builder, new AnalysisContext(filePath));
        return builder.build(filePath);
    }

    @Test
    void detectsBeanMethodInConfiguration() {
        AnalysisResult result = analyze("AppConfig.java", """
            package com.example;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            @Configuration
            public class AppConfig {
                @Bean
                public MyService myService() { return new MyService(); }
            }
            """);

        assertEquals(1, result.findings(BeanMethodInfo.class).size());
        BeanMethodInfo bean = result.findings(BeanMethodInfo.class).getFirst();
        assertEquals("myService", bean.beanName());
        assertEquals("MyService", bean.returnType());
        assertEquals("AppConfig", bean.configurationClass());
    }

    @Test
    void usesExplicitBeanNameFromValue() {
        AnalysisResult result = analyze("AppConfig.java", """
            package com.example;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            @Configuration
            public class AppConfig {
                @Bean("explicitName")
                public MyService myService() { return new MyService(); }
            }
            """);

        assertEquals("explicitName", result.findings(BeanMethodInfo.class).getFirst().beanName());
    }

    @Test
    void usesExplicitBeanNameFromNameAttr() {
        AnalysisResult result = analyze("AppConfig.java", """
            package com.example;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            @Configuration
            public class AppConfig {
                @Bean(name = "namedBean")
                public MyService myService() { return new MyService(); }
            }
            """);

        assertEquals("namedBean", result.findings(BeanMethodInfo.class).getFirst().beanName());
    }

    @Test
    void usesFirstNameWhenArrayProvided() {
        AnalysisResult result = analyze("AppConfig.java", """
            package com.example;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            @Configuration
            public class AppConfig {
                @Bean(name = {"primaryName", "alias"})
                public MyService myService() { return new MyService(); }
            }
            """);

        assertEquals("primaryName", result.findings(BeanMethodInfo.class).getFirst().beanName());
    }

    @Test
    void detectsMultipleBeanMethods() {
        AnalysisResult result = analyze("AppConfig.java", """
            package com.example;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            @Configuration
            public class AppConfig {
                @Bean public Foo foo() { return new Foo(); }
                @Bean public Bar bar() { return new Bar(); }
                @Bean public Baz baz() { return new Baz(); }
            }
            """);

        assertEquals(3, result.findings(BeanMethodInfo.class).size());
    }

    @Test
    void ignoresBeanMethodInNonConfigurationClass() {
        AnalysisResult result = analyze("PlainService.java", """
            package com.example;
            import org.springframework.context.annotation.Bean;
            public class PlainService {
                @Bean
                public MyService myService() { return new MyService(); }
            }
            """);

        assertTrue(result.findings(BeanMethodInfo.class).isEmpty());
    }

    @Test
    void worksWithSpringBootApplication() {
        AnalysisResult result = analyze("Application.java", """
            package com.example;
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            import org.springframework.context.annotation.Bean;
            @SpringBootApplication
            public class Application {
                @Bean
                public MyService myService() { return new MyService(); }
            }
            """);

        assertEquals(1, result.findings(BeanMethodInfo.class).size());
    }

    @Test
    void recordsFilePath() {
        AnalysisResult result = analyze("/project/AppConfig.java", """
            package com.example;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            @Configuration
            public class AppConfig {
                @Bean public Foo foo() { return new Foo(); }
            }
            """);

        assertEquals("/project/AppConfig.java", result.findings(BeanMethodInfo.class).getFirst().filePath());
    }

    @Test
    void interfaceIsSkipped() {
        AnalysisResult result = analyze("ConfigInterface.java", """
            package com.example;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.context.annotation.Bean;
            @Configuration
            public interface ConfigInterface {
                @Bean default Foo foo() { return null; }
            }
            """);

        assertTrue(result.findings(BeanMethodInfo.class).isEmpty());
    }
}
