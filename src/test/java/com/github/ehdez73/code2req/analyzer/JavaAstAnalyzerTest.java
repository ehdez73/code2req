package com.github.ehdez73.code2req.analyzer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaAstAnalyzerTest {

    private final JavaAstAnalyzer analyzer = new JavaAstAnalyzer(
        List.of(new ComponentVisitor(), new EndpointVisitor(), new ScheduledTaskVisitor())
    );

    @Test
    void analyzesRestControllerWithEndpoints(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("OwnerController.java");
        Files.writeString(file, """
            package com.example;
            import org.springframework.web.bind.annotation.*;
            @RestController
            @RequestMapping("/api/owners")
            public class OwnerController {
                @GetMapping("/{id}")
                public String getOwner(@PathVariable Long id) {
                    return "owner";
                }
                @PostMapping
                public void createOwner() {}
            }
            """);

        AnalysisResult result = analyzer.analyze(file);

        assertEquals(1, result.components().size());
        assertEquals("RestController", result.components().getFirst().annotationType());
        assertEquals("OwnerController", result.components().getFirst().className());

        assertEquals(2, result.endpoints().size());
        assertEquals("GET", result.endpoints().get(0).httpMethod());
        assertEquals("/api/owners/{id}", result.endpoints().get(0).path());
        assertEquals("POST", result.endpoints().get(1).httpMethod());
        assertEquals("/api/owners", result.endpoints().get(1).path());

        assertTrue(result.scheduledTasks().isEmpty());
    }

    @Test
    void analyzesServiceWithoutEndpoints(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("ClinicService.java");
        Files.writeString(file, """
            package com.example;
            import org.springframework.stereotype.Service;
            @Service
            public class ClinicService {
                public void doSomething() {}
            }
            """);

        AnalysisResult result = analyzer.analyze(file);

        assertEquals(1, result.components().size());
        assertEquals("Service", result.components().getFirst().annotationType());
        assertTrue(result.endpoints().isEmpty());
        assertTrue(result.scheduledTasks().isEmpty());
    }

    @Test
    void analyzesPlainJavaClass(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("PlainModel.java");
        Files.writeString(file, """
            package com.example.model;
            public class PlainModel {
                private String name;
                public String getName() { return name; }
            }
            """);

        AnalysisResult result = analyzer.analyze(file);

        assertEquals(1, result.components().size());
        assertEquals("other", result.components().getFirst().annotationType());
        assertEquals("PlainModel", result.components().getFirst().className());
        assertEquals("com.example.model", result.components().getFirst().packageName());
        assertTrue(result.endpoints().isEmpty());
        assertTrue(result.scheduledTasks().isEmpty());
    }

    @Test
    void analyzesFileWithScheduledTasks(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("ScheduledTasks.java");
        Files.writeString(file, """
            package com.example;
            import org.springframework.scheduling.annotation.Scheduled;
            import org.springframework.stereotype.Component;
            @Component
            public class ScheduledTasks {
                @Scheduled(cron = "0 0 * * * ?")
                public void hourlyReport() {}
                @Scheduled(fixedRate = 60000)
                public void everyMinute() {}
            }
            """);

        AnalysisResult result = analyzer.analyze(file);

        assertEquals(1, result.components().size());
        assertEquals("Component", result.components().getFirst().annotationType());
        assertEquals(2, result.scheduledTasks().size());
        assertEquals("cron", result.scheduledTasks().get(0).type());
        assertEquals("fixed-rate", result.scheduledTasks().get(1).type());
    }

    @Test
    void malformedJavaFileReturnsEmptyResult(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Broken.java");
        Files.writeString(file, "this is not valid java @@@");

        AnalysisResult result = analyzer.analyze(file);

        assertTrue(result.components().isEmpty());
        assertTrue(result.endpoints().isEmpty());
        assertTrue(result.scheduledTasks().isEmpty());
        assertNotNull(result.filePath());
    }

    @Test
    void nonExistentFileReturnsEmptyResult() {
        Path file = Path.of("/nonexistent/File.java");
        AnalysisResult result = analyzer.analyze(file);
        assertTrue(result.components().isEmpty());
    }
}
