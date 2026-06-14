package com.github.ehdez73.code2req.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScheduledTaskVisitorTest {

    private final ScheduledTaskVisitor visitor = new ScheduledTaskVisitor();

    private AnalysisResult analyze(String filePath, String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        AnalysisResultBuilder builder = new AnalysisResultBuilder();
        visitor.analyze(cu, builder, filePath);
        return builder.build(filePath);
    }

    @Test
    void extractsCronExpression() {
        AnalysisResult result = analyze("MyTask.java", """
            import org.springframework.scheduling.annotation.Scheduled;
            import org.springframework.stereotype.Component;
            @Component
            public class MyTask {
                @Scheduled(cron = "0 * * * * ?")
                public void runMe() {}
            }
            """);

        assertEquals(1, result.scheduledTasks().size());
        ScheduledTaskInfo t = result.scheduledTasks().getFirst();
        assertEquals("runMe", t.methodName());
        assertEquals("MyTask", t.className());
        assertEquals("0 * * * * ?", t.cron());
        assertEquals("cron", t.type());
        assertNull(t.fixedRate());
        assertNull(t.fixedDelay());
    }

    @Test
    void extractsFixedRate() {
        AnalysisResult result = analyze("MyTask.java", """
            import org.springframework.scheduling.annotation.Scheduled;
            import org.springframework.stereotype.Component;
            @Component
            public class MyTask {
                @Scheduled(fixedRate = 5000)
                public void runMe() {}
            }
            """);

        assertEquals(1, result.scheduledTasks().size());
        ScheduledTaskInfo t = result.scheduledTasks().getFirst();
        assertEquals("fixed-rate", t.type());
        assertEquals(Long.valueOf(5000L), t.fixedRate());
        assertNull(t.cron());
        assertNull(t.fixedDelay());
    }

    @Test
    void extractsFixedDelay() {
        AnalysisResult result = analyze("MyTask.java", """
            import org.springframework.scheduling.annotation.Scheduled;
            import org.springframework.stereotype.Component;
            @Component
            public class MyTask {
                @Scheduled(fixedDelay = 3000)
                public void runMe() {}
            }
            """);

        assertEquals(1, result.scheduledTasks().size());
        assertEquals("fixed-delay", result.scheduledTasks().getFirst().type());
        assertEquals(Long.valueOf(3000L), result.scheduledTasks().getFirst().fixedDelay());
    }

    @Test
    void noScheduledAnnotation_producesEmpty() {
        AnalysisResult result = analyze("MyTask.java", """
            @Component
            public class MyTask {
                public void runMe() {}
            }
            """);

        assertTrue(result.scheduledTasks().isEmpty());
    }
}
