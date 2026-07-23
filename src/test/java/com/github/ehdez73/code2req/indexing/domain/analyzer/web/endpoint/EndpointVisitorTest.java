package com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.ComponentInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.detector.SpringEndpointDetector;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EndpointVisitorTest {

    private final EndpointVisitor visitor = new EndpointVisitor(List.of(new SpringEndpointDetector()));

    private AnalysisResult analyze(String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        AnalysisResultBuilder builder = new AnalysisResultBuilder();
        // pre-populate with a controller component so EndpointVisitor doesn't short-circuit
        builder.addFinding(new ComponentInfo("RestController", "MyController", "com.example", "test.java", false));
        visitor.analyze(cu, builder, new AnalysisContext("test.java"));
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

        assertEquals(1, result.findings(EndpointInfo.class).size());
        EndpointInfo e = result.findings(EndpointInfo.class).getFirst();
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

        assertEquals(1, result.findings(EndpointInfo.class).size());
        assertEquals("POST", result.findings(EndpointInfo.class).getFirst().httpMethod());
        assertEquals("/create", result.findings(EndpointInfo.class).getFirst().path());
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

        assertEquals(1, result.findings(EndpointInfo.class).size());
        assertEquals("/api/users", result.findings(EndpointInfo.class).getFirst().path());
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

        assertEquals(1, result.findings(EndpointInfo.class).size());
        assertEquals("/api", result.findings(EndpointInfo.class).getFirst().path());
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

        assertEquals(1, result.findings(EndpointInfo.class).size());
        assertEquals(List.of("id"), result.findings(EndpointInfo.class).getFirst().pathVariables());
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

        assertEquals(1, result.findings(EndpointInfo.class).size());
        assertEquals(List.of("q"), result.findings(EndpointInfo.class).getFirst().queryParameters());
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

        assertTrue(result.findings(EndpointInfo.class).isEmpty());
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

        assertEquals(2, result.findings(EndpointInfo.class).size());
        assertEquals("PUT", result.findings(EndpointInfo.class).get(0).httpMethod());
        assertEquals("/update", result.findings(EndpointInfo.class).get(0).path());
        assertEquals("DELETE", result.findings(EndpointInfo.class).get(1).httpMethod());
        assertEquals("/remove", result.findings(EndpointInfo.class).get(1).path());
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
        visitor.analyze(cu, builder, new AnalysisContext("test.java"));

        assertTrue(builder.build("test.java").findings(EndpointInfo.class).isEmpty());
    }

    @Test
    void combinePathsHandlesVariousFormats() {
        assertEquals("/api/users", SpringEndpointDetector.combinePaths("/api", "/users"));
        assertEquals("/api/users", SpringEndpointDetector.combinePaths("/api/", "users"));
        assertEquals("/api", SpringEndpointDetector.combinePaths("/api", ""));
        assertEquals("/users", SpringEndpointDetector.combinePaths("", "/users"));
        assertEquals("", SpringEndpointDetector.combinePaths("", ""));
    }

    // --- View return detection tests ---

    private AnalysisResult analyzeWithController(String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        AnalysisResultBuilder builder = new AnalysisResultBuilder();
        builder.addFinding(new ComponentInfo("Controller", "MyController", "com.example", "test.java", false));
        visitor.analyze(cu, builder, new AnalysisContext("test.java"));
        return builder.build("test.java");
    }

    @Test
    void stringReturnInControllerMarksAsView() {
        AnalysisResult result = analyzeWithController("""
            import org.springframework.stereotype.Controller;
            import org.springframework.web.bind.annotation.GetMapping;
            @Controller
            public class MyController {
                @GetMapping("/hello")
                public String hello() { return "hello-view"; }
            }
            """);

        EndpointInfo e = result.findings(EndpointInfo.class).getFirst();
        assertTrue(e.servesView());
        assertEquals("hello-view", e.viewName());
    }

    @Test
    void modelAndViewReturnExtractsViewName() {
        AnalysisResult result = analyzeWithController("""
            import org.springframework.stereotype.Controller;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.servlet.ModelAndView;
            @Controller
            public class MyController {
                @GetMapping("/show")
                public ModelAndView show() { return new ModelAndView("show-view"); }
            }
            """);

        EndpointInfo e = result.findings(EndpointInfo.class).getFirst();
        assertTrue(e.servesView());
        assertEquals("show-view", e.viewName());
    }

    @Test
    void voidReturnInControllerMarksAsView() {
        AnalysisResult result = analyzeWithController("""
            import org.springframework.stereotype.Controller;
            import org.springframework.web.bind.annotation.GetMapping;
            @Controller
            public class MyController {
                @GetMapping("/action")
                public void doAction() {}
            }
            """);

        EndpointInfo e = result.findings(EndpointInfo.class).getFirst();
        assertTrue(e.servesView());
        assertEquals("", e.viewName());
    }

    @Test
    void restControllerStringReturnNotView() {
        AnalysisResult result = analyze("""
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            public class MyController {
                @GetMapping("/api/hello")
                public String hello() { return "hello"; }
            }
            """);

        EndpointInfo e = result.findings(EndpointInfo.class).getFirst();
        assertFalse(e.servesView());
        assertEquals("", e.viewName());
    }

    @Test
    void responseBodyOverridesViewDetection() {
        CompilationUnit cu = StaticJavaParser.parse("""
            import org.springframework.stereotype.Controller;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.ResponseBody;
            @Controller
            public class MyController {
                @GetMapping("/data")
                @ResponseBody
                public String data() { return "raw-data"; }
            }
            """);
        AnalysisResultBuilder builder = new AnalysisResultBuilder();
        builder.addFinding(new ComponentInfo("Controller", "MyController", "com.example", "test.java", false));
        visitor.analyze(cu, builder, new AnalysisContext("test.java"));
        AnalysisResult result = builder.build("test.java");

        EndpointInfo e = result.findings(EndpointInfo.class).getFirst();
        assertFalse(e.servesView());
        assertEquals("", e.viewName());
    }

    @Test
    void extractsRequestBody() {
        AnalysisResult result = analyze("""
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RequestBody;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            public class MyController {
                @PostMapping("/users")
                public String create(@RequestBody CreateUserRequest req) { return ""; }
            }
            """);

        assertEquals(1, result.findings(EndpointInfo.class).size());
        assertEquals(List.of("CreateUserRequest"), result.findings(EndpointInfo.class).getFirst().requestBodies());
    }

    @Test
    void extractsMultipleRequestBodies_BodyAndEmptyPath() {
        AnalysisResult result = analyze("""
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.PutMapping;
            import org.springframework.web.bind.annotation.RequestBody;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            public class MyController {
                @PostMapping("/users")
                public String create(@RequestBody CreateUserRequest req,
                                     @RequestBody UpdateUserRequest upd) { return ""; }
                @PutMapping("/users/{id}")
                public String update(@PathVariable Long id, @RequestBody UserDto dto) { return ""; }
            }
            """);

        List<EndpointInfo> eps = result.findings(EndpointInfo.class);
        assertEquals(2, eps.size());
        assertEquals(List.of("CreateUserRequest", "UpdateUserRequest"), eps.get(0).requestBodies());
        assertEquals(List.of("UserDto"), eps.get(1).requestBodies());
    }

    @Test
    void nonControllerEndpointNotMarkedAsView() {
        AnalysisResult result = analyze("""
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            public class MyController {
                @GetMapping("/api/data")
                public String data() { return "data"; }
            }
            """);

        EndpointInfo e = result.findings(EndpointInfo.class).getFirst();
        assertFalse(e.servesView());
    }

    @Test
    void viewReturnMarkedAsView() {
        AnalysisResult result = analyzeWithController("""
            import org.springframework.stereotype.Controller;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.servlet.View;
            @Controller
            public class MyController {
                @GetMapping("/report")
                public View report() { return null; }
            }
            """);

        EndpointInfo e = result.findings(EndpointInfo.class).getFirst();
        assertTrue(e.servesView());
    }
}
