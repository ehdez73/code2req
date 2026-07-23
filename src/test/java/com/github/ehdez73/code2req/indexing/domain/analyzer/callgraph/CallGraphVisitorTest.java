package com.github.ehdez73.code2req.indexing.domain.analyzer.callgraph;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResultBuilder;
import com.github.ehdez73.code2req.indexing.domain.analyzer.declaration.DeclarationInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.declaration.GlobalDeclarationRegistry;
import com.github.ehdez73.code2req.indexing.domain.model.AllowedLibrariesConfig;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CallGraphVisitorTest {

    private final CallGraphVisitor visitor = new CallGraphVisitor(new AllowedLibrariesConfig(null, null));
    private GlobalDeclarationRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new GlobalDeclarationRegistry();
        registry.register(new DeclarationInfo("OrderService", "createOrder", List.of("OrderDto"), "/app/OrderService.java"));
        registry.register(new DeclarationInfo("OrderService", "find", List.of("String"), "/app/OrderService.java"));
        registry.register(new DeclarationInfo("OrderService", "find", List.of("String", "String"), "/app/OrderService.java"));
        registry.register(new DeclarationInfo("OrderService", "process", List.of("OrderDto"), "/app/OrderService.java"));
        registry.register(new DeclarationInfo("OrderService", "process", List.of("InvoiceDto"), "/app/OrderService.java"));
        registry.register(new DeclarationInfo("OrderRepository", "save", List.of("Order"), "/app/OrderRepository.java"));
        registry.freeze();
    }

    private AnalysisResult analyze(String filePath, String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        AnalysisResultBuilder builder = new AnalysisResultBuilder();
        AnalysisContext context = new AnalysisContext(filePath, "", registry);
        visitor.analyze(cu, builder, context);
        return builder.build(filePath);
    }

    @Test
    void controllerCallsServiceViaInjectedField() {
        AnalysisResult result = analyze("OrderController.java", """
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            public class OrderController {
                @Autowired
                private OrderService orderService;
                public void create() {
                    orderService.createOrder(dto);
                }
            }
            """);

        assertEquals(1, result.findings(CallGraphEdge.class).size());
        CallGraphEdge edge = result.findings(CallGraphEdge.class).getFirst();
        assertEquals("OrderController", edge.sourceClassName());
        assertEquals("create", edge.sourceMethodName());
        assertEquals("OrderService", edge.targetClassName());
        assertEquals("createOrder", edge.targetMethodName());
        assertEquals("/app/OrderService.java", edge.targetFilePath());
        assertEquals(1, edge.argCount());
        assertEquals(CallGraphEdge.STATUS_RESOLVED, edge.resolvedStatus());
    }

    @Test
    void serviceCallsRepositoryViaInjectedField() {
        AnalysisResult result = analyze("OrderService.java", """
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Service;
            @Service
            public class OrderService {
                @Autowired
                private OrderRepository orderRepository;
                public void placeOrder() {
                    orderRepository.save(order);
                }
            }
            """);

        assertEquals(1, result.findings(CallGraphEdge.class).size());
        CallGraphEdge edge = result.findings(CallGraphEdge.class).getFirst();
        assertEquals("OrderService", edge.sourceClassName());
        assertEquals("placeOrder", edge.sourceMethodName());
        assertEquals("OrderRepository", edge.targetClassName());
        assertEquals("save", edge.targetMethodName());
        assertEquals("/app/OrderRepository.java", edge.targetFilePath());
        assertEquals(1, edge.argCount());
        assertEquals(CallGraphEdge.STATUS_RESOLVED, edge.resolvedStatus());
    }

    @Test
    void jdkMethodCallsAreIgnored() {
        AnalysisResult result = analyze("MyService.java", """
            import org.springframework.stereotype.Service;
            import java.util.List;
            @Service
            public class MyService {
                private final List<String> items;
                public MyService(List<String> items) { this.items = items; }
                public void doStuff() {
                    String.format("hello %s", "world");
                    items.add("x");
                }
            }
            """);

        assertTrue(result.findings(CallGraphEdge.class).isEmpty());
    }

    @Test
    void thirdPartyCallIsUnresolved() {
        AnalysisResult result = analyze("MyService.java", """
            import org.springframework.stereotype.Service;
            @Service
            public class MyService {
                private ThirdPartySdk thirdparty;
                public void doStuff() {
                    thirdparty.calculateScore(data);
                }
            }
            """);

        assertEquals(1, result.findings(CallGraphEdge.class).size());
        CallGraphEdge edge = result.findings(CallGraphEdge.class).getFirst();
        assertEquals("ThirdPartySdk", edge.targetClassName());
        assertEquals("calculateScore", edge.targetMethodName());
        assertEquals(1, edge.argCount());
        assertEquals(CallGraphEdge.STATUS_UNRESOLVED, edge.resolvedStatus());
        assertTrue(edge.targetFilePath().isEmpty());
    }

    @Test
    void overloadedMethodWithDifferentArgCountResolvedCorrectly() {
        AnalysisResult result = analyze("OrderController.java", """
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            public class OrderController {
                @Autowired
                private OrderService orderService;
                public void lookup() {
                    orderService.find("abc");
                }
            }
            """);

        assertEquals(1, result.findings(CallGraphEdge.class).size());
        CallGraphEdge edge = result.findings(CallGraphEdge.class).getFirst();
        assertEquals("OrderService", edge.targetClassName());
        assertEquals("find", edge.targetMethodName());
        assertEquals(1, edge.argCount());
        assertEquals(CallGraphEdge.STATUS_RESOLVED, edge.resolvedStatus());
    }

    @Test
    void ambiguousOverloadWithSameArgCount() {
        AnalysisResult result = analyze("OrderController.java", """
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            public class OrderController {
                @Autowired
                private OrderService orderService;
                public void run() {
                    orderService.process(dto);
                }
            }
            """);

        assertEquals(1, result.findings(CallGraphEdge.class).size());
        CallGraphEdge edge = result.findings(CallGraphEdge.class).getFirst();
        assertEquals("OrderService", edge.targetClassName());
        assertEquals("process", edge.targetMethodName());
        assertEquals(1, edge.argCount());
        assertEquals(CallGraphEdge.STATUS_AMBIGUOUS, edge.resolvedStatus());
        assertEquals(2, edge.ambiguousCandidates().size());
    }

    @Test
    void emptyFileProducesNoEdges() {
        AnalysisResult result = analyze("Empty.java", """
            package com.app;
            public class Empty {
                public void doNothing() {}
            }
            """);

        assertTrue(result.findings(CallGraphEdge.class).isEmpty());
    }

    @Test
    void selfCallWithoutScopeIsCaptured() {
        registry = new GlobalDeclarationRegistry();
        registry.register(new DeclarationInfo("MyService", "helper", List.of(), "/app/MyService.java"));
        registry.freeze();

        AnalysisResult result = analyze("MyService.java", """
            import org.springframework.stereotype.Service;
            @Service
            public class MyService {
                public void doStuff() {
                    helper();
                }
                private void helper() {}
            }
            """);

        List<CallGraphEdge> edges = result.findings(CallGraphEdge.class);
        assertEquals(1, edges.size());
        assertEquals("MyService", edges.get(0).sourceClassName());
        assertEquals("MyService", edges.get(0).targetClassName());
        assertEquals("helper", edges.get(0).targetMethodName());
    }

    @Test
    void multipleCallsFromSameMethodAllCaptured() {
        registry = new GlobalDeclarationRegistry();
        registry.register(new DeclarationInfo("ServiceA", "doA", List.of(), "/app/ServiceA.java"));
        registry.register(new DeclarationInfo("ServiceB", "doB", List.of(), "/app/ServiceB.java"));
        registry.freeze();

        AnalysisResult result = analyze("Orchestrator.java", """
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Service;
            @Service
            public class Orchestrator {
                @Autowired
                private ServiceA serviceA;
                @Autowired
                private ServiceB serviceB;
                public void orchestrate() {
                    serviceA.doA();
                    serviceB.doB();
                }
            }
            """);

        assertEquals(2, result.findings(CallGraphEdge.class).size());
        assertTrue(result.findings(CallGraphEdge.class).stream()
            .allMatch(e -> CallGraphEdge.STATUS_RESOLVED.equals(e.resolvedStatus())));
    }

    @Test
    void fieldTypeNotInRegistryIsUnresolved() {
        AnalysisResult result = analyze("MyController.java", """
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            public class MyController {
                @Autowired
                private ExternalSdk external;
                public void callIt() {
                    external.execute();
                }
            }
            """);

        assertEquals(1, result.findings(CallGraphEdge.class).size());
        CallGraphEdge edge = result.findings(CallGraphEdge.class).getFirst();
        assertEquals("ExternalSdk", edge.targetClassName());
        assertEquals("execute", edge.targetMethodName());
        assertEquals(CallGraphEdge.STATUS_UNRESOLVED, edge.resolvedStatus());
    }

    @Test
    void interfaceWithMultipleImplementationsIsAmbiguous() {
        registry = new GlobalDeclarationRegistry();
        registry.register(new DeclarationInfo("NameService", "getName", List.of(), "/src/NameService.java"));
        registry.register(new DeclarationInfo("FixedNameService", "getName", List.of(), "/src/FixedNameService.java"));
        registry.register(new DeclarationInfo("RandomNameService", "getName", List.of(), "/src/RandomNameService.java"));
        registry.registerSuperType("FixedNameService", "NameService", "com.example.NameService");
        registry.registerSuperType("RandomNameService", "NameService", "com.example.NameService");
        registry.register(new DeclarationInfo("FixedNameService", "<clinit>", List.of(), "/src/FixedNameService.java"));
        registry.register(new DeclarationInfo("RandomNameService", "<clinit>", List.of(), "/src/RandomNameService.java"));
        registry.freeze();

        AnalysisResult result = analyze("Controller.java", """
            public class Controller {
                private NameService nameService;
                public void execute() {
                    nameService.getName();
                }
            }
            """);

        assertEquals(1, result.findings(CallGraphEdge.class).size());
        CallGraphEdge edge = result.findings(CallGraphEdge.class).getFirst();
        assertEquals("Controller", edge.sourceClassName());
        assertEquals("NameService", edge.targetClassName());
        assertEquals("getName", edge.targetMethodName());
        assertEquals(CallGraphEdge.STATUS_AMBIGUOUS, edge.resolvedStatus());
        assertEquals(2, edge.ambiguousCandidates().size());
        assertTrue(edge.ambiguousCandidates().stream().anyMatch(c -> c.startsWith("FixedNameService")));
        assertTrue(edge.ambiguousCandidates().stream().anyMatch(c -> c.startsWith("RandomNameService")));
    }

    @Test
    void interfaceWithSingleImplementationIsResolved() {
        registry = new GlobalDeclarationRegistry();
        registry.register(new DeclarationInfo("NameService", "getName", List.of(), "/src/NameService.java"));
        registry.register(new DeclarationInfo("FixedNameService", "getName", List.of(), "/src/FixedNameService.java"));
        registry.registerSuperType("FixedNameService", "NameService", "com.example.NameService");
        registry.register(new DeclarationInfo("FixedNameService", "<clinit>", List.of(), "/src/FixedNameService.java"));
        registry.freeze();

        AnalysisResult result = analyze("Controller.java", """
            public class Controller {
                private NameService nameService;
                public void execute() {
                    nameService.getName();
                }
            }
            """);

        assertEquals(1, result.findings(CallGraphEdge.class).size());
        CallGraphEdge edge = result.findings(CallGraphEdge.class).getFirst();
        assertEquals(CallGraphEdge.STATUS_RESOLVED, edge.resolvedStatus());
        assertTrue(edge.targetFilePath().contains("FixedNameService"),
            "Expected edge to resolve to FixedNameService.java");
    }
}
