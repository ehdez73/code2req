package com.github.ehdez73.code2req.analyzer;

import com.github.ehdez73.code2req.analyzer.component.ComponentInfo;
import com.github.ehdez73.code2req.analyzer.component.ComponentVisitor;
import com.github.ehdez73.code2req.analyzer.endpoint.EndpointInfo;
import com.github.ehdez73.code2req.analyzer.endpoint.EndpointVisitor;
import com.github.ehdez73.code2req.analyzer.eventlistener.EventListenerInfo;
import com.github.ehdez73.code2req.analyzer.eventlistener.EventListenerVisitor;
import com.github.ehdez73.code2req.analyzer.eventlistener.EventPublisherInfo;
import com.github.ehdez73.code2req.analyzer.scheduledtask.ScheduledTaskInfo;
import com.github.ehdez73.code2req.analyzer.scheduledtask.ScheduledTaskVisitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaAstAnalyzerTest {

    private final JavaAstAnalyzer analyzer = new JavaAstAnalyzer(
        List.of(new ComponentVisitor(), new EndpointVisitor(), new ScheduledTaskVisitor(), new EventListenerVisitor())
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

        assertEquals(1, result.findings(ComponentInfo.class).size());
        assertEquals("RestController", result.findings(ComponentInfo.class).getFirst().annotationType());
        assertEquals("OwnerController", result.findings(ComponentInfo.class).getFirst().className());

        assertEquals(2, result.findings(EndpointInfo.class).size());
        assertEquals("GET", result.findings(EndpointInfo.class).get(0).httpMethod());
        assertEquals("/api/owners/{id}", result.findings(EndpointInfo.class).get(0).path());
        assertEquals("POST", result.findings(EndpointInfo.class).get(1).httpMethod());
        assertEquals("/api/owners", result.findings(EndpointInfo.class).get(1).path());

        assertTrue(result.findings(ScheduledTaskInfo.class).isEmpty());
        assertTrue(result.findings(EventListenerInfo.class).isEmpty());
        assertTrue(result.findings(EventPublisherInfo.class).isEmpty());
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

        assertEquals(1, result.findings(ComponentInfo.class).size());
        assertEquals("Service", result.findings(ComponentInfo.class).getFirst().annotationType());
        assertTrue(result.findings(EndpointInfo.class).isEmpty());
        assertTrue(result.findings(ScheduledTaskInfo.class).isEmpty());
        assertTrue(result.findings(EventListenerInfo.class).isEmpty());
        assertTrue(result.findings(EventPublisherInfo.class).isEmpty());
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

        assertEquals(1, result.findings(ComponentInfo.class).size());
        assertEquals("other", result.findings(ComponentInfo.class).getFirst().annotationType());
        assertEquals("PlainModel", result.findings(ComponentInfo.class).getFirst().className());
        assertEquals("com.example.model", result.findings(ComponentInfo.class).getFirst().packageName());
        assertTrue(result.findings(EndpointInfo.class).isEmpty());
        assertTrue(result.findings(ScheduledTaskInfo.class).isEmpty());
        assertTrue(result.findings(EventListenerInfo.class).isEmpty());
        assertTrue(result.findings(EventPublisherInfo.class).isEmpty());
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

        assertEquals(1, result.findings(ComponentInfo.class).size());
        assertEquals("Component", result.findings(ComponentInfo.class).getFirst().annotationType());
        assertEquals(2, result.findings(ScheduledTaskInfo.class).size());
        assertEquals("cron", result.findings(ScheduledTaskInfo.class).get(0).type());
        assertEquals("fixed-rate", result.findings(ScheduledTaskInfo.class).get(1).type());
    }

    @Test
    void malformedJavaFileReturnsEmptyResult(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Broken.java");
        Files.writeString(file, "this is not valid java @@@");

        AnalysisResult result = analyzer.analyze(file);

        assertTrue(result.findings(ComponentInfo.class).isEmpty());
        assertTrue(result.findings(EndpointInfo.class).isEmpty());
        assertTrue(result.findings(ScheduledTaskInfo.class).isEmpty());
        assertTrue(result.findings(EventListenerInfo.class).isEmpty());
        assertTrue(result.findings(EventPublisherInfo.class).isEmpty());
        assertNotNull(result.filePath());
    }

    @Test
    void nonExistentFileReturnsEmptyResult() {
        Path file = Path.of("/nonexistent/File.java");
        AnalysisResult result = analyzer.analyze(file);
        assertTrue(result.findings(ComponentInfo.class).isEmpty());
    }
}
