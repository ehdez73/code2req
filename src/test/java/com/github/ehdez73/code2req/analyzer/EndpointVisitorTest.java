package com.github.ehdez73.code2req.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EndpointVisitorTest {

    private final EndpointVisitor visitor = new EndpointVisitor();

    private AnalysisResult analyze(String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        AnalysisResultBuilder builder = new AnalysisResultBuilder();
        // pre-populate with a controller component so EndpointVisitor doesn't short-circuit
        builder.addComponent(new ComponentInfo("RestController", "MyController", "com.example", "test.java"));
        visitor.analyze(cu, builder, "test.java");
        return builder.build("test.java");
    }

    @Test
    void extractsGetMapping() {
        AnalysisResult result = analyze("""
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            public class MyController {
                @GetMapping("/hello")
                public String hello() { return "hi"; }
            }
            """);

        assertEquals(1, result.endpoints().size());
        EndpointInfo e = result.endpoints().getFirst();
        assertEquals("GET", e.httpMethod());
        assertEquals("/hello", e.path());
        assertEquals("MyController", e.controllerName());
    }

    @Test
    void extractsPostMapping() {
        AnalysisResult result = analyze("""
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            public class MyController {
                @PostMapping("/create")
                public void create() {}
            }
            """);

        assertEquals(1, result.endpoints().size());
        assertEquals("POST", result.endpoints().getFirst().httpMethod());
        assertEquals("/create", result.endpoints().getFirst().path());
    }

    @Test
    void combinesClassAndMethodPaths() {
        AnalysisResult result = analyze("""
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            @RequestMapping("/api")
            public class MyController {
                @GetMapping("/users")
                public String users() { return ""; }
            }
            """);

        assertEquals(1, result.endpoints().size());
        assertEquals("/api/users", result.endpoints().getFirst().path());
    }

    @Test
    void handlesEmptyMethodPath() {
        AnalysisResult result = analyze("""
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            @RequestMapping("/api")
            public class MyController {
                @GetMapping
                public String list() { return ""; }
            }
            """);

        assertEquals(1, result.endpoints().size());
        assertEquals("/api", result.endpoints().getFirst().path());
    }

    @Test
    void extractsPathVariables() {
        AnalysisResult result = analyze("""
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.PathVariable;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            public class MyController {
                @GetMapping("/users/{id}")
                public String getUser(@PathVariable Long id) { return ""; }
            }
            """);

        assertEquals(1, result.endpoints().size());
        assertEquals(List.of("id"), result.endpoints().getFirst().pathVariables());
    }

    @Test
    void extractsQueryParameters() {
        AnalysisResult result = analyze("""
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestParam;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            public class MyController {
                @GetMapping("/search")
                public String search(@RequestParam String q) { return ""; }
            }
            """);

        assertEquals(1, result.endpoints().size());
        assertEquals(List.of("q"), result.endpoints().getFirst().queryParameters());
    }

    @Test
    void controllerWithNoEndpointMethods_producesEmptyEndpoints() {
        AnalysisResult result = analyze("""
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            public class MyController {
                public String notAnEndpoint() { return ""; }
            }
            """);

        assertTrue(result.endpoints().isEmpty());
    }

    @Test
    void extractsPutAndDeleteMappings() {
        AnalysisResult result = analyze("""
            import org.springframework.web.bind.annotation.PutMapping;
            import org.springframework.web.bind.annotation.DeleteMapping;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            public class MyController {
                @PutMapping("/update")
                public void update() {}
                @DeleteMapping("/remove")
                public void remove() {}
            }
            """);

        assertEquals(2, result.endpoints().size());
        assertEquals("PUT", result.endpoints().get(0).httpMethod());
        assertEquals("/update", result.endpoints().get(0).path());
        assertEquals("DELETE", result.endpoints().get(1).httpMethod());
        assertEquals("/remove", result.endpoints().get(1).path());
    }

    @Test
    void skipsWhenNoControllerComponentInBuilder() {
        CompilationUnit cu = StaticJavaParser.parse("""
            import org.springframework.web.bind.annotation.GetMapping;
            public class NotAController {
                @GetMapping("/hello")
                public String hello() { return "hi"; }
            }
            """);
        AnalysisResultBuilder builder = new AnalysisResultBuilder();
        visitor.analyze(cu, builder, "test.java");

        assertTrue(builder.build("test.java").endpoints().isEmpty());
    }

    @Test
    void combinePathsHandlesVariousFormats() {
        assertEquals("/api/users", EndpointVisitor.EndpointAstAdapter.combinePaths("/api", "/users"));
        assertEquals("/api/users", EndpointVisitor.EndpointAstAdapter.combinePaths("/api/", "users"));
        assertEquals("/api", EndpointVisitor.EndpointAstAdapter.combinePaths("/api", ""));
        assertEquals("/users", EndpointVisitor.EndpointAstAdapter.combinePaths("", "/users"));
        assertEquals("", EndpointVisitor.EndpointAstAdapter.combinePaths("", ""));
    }
}
