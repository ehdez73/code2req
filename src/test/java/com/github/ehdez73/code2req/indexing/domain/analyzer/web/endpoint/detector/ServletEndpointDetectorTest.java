package com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.detector;

import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServletEndpointDetectorTest {

    private final ServletEndpointDetector detector = new ServletEndpointDetector();

    @Test
    void nonHttpServletSubclassIsIgnored() {
        List<EndpointInfo> result = new ArrayList<>();
        detectMethod(result, """
            public class NotAServlet {
                public void doGet(HttpServletRequest req, HttpServletResponse resp) {}
            }
            """, "NotAServlet");
        assertTrue(result.isEmpty());
    }

    @Test
    void doGetDetectedWithWebServletAnnotation() {
        List<EndpointInfo> result = new ArrayList<>();
        detectMethod(result, """
            import javax.servlet.http.HttpServlet;
            import javax.servlet.http.HttpServletRequest;
            import javax.servlet.http.HttpServletResponse;
            import javax.servlet.annotation.WebServlet;
            @WebServlet("/orders")
            public class OrderServlet extends HttpServlet {
                public void doGet(HttpServletRequest req, HttpServletResponse resp) {}
            }
            """, "OrderServlet");
        assertEquals(1, result.size());
        EndpointInfo ep = result.get(0);
        assertEquals("GET", ep.httpMethod());
        assertEquals("/orders", ep.path());
        assertEquals("OrderServlet", ep.controllerName());
    }

    @Test
    void doPostDetected() {
        List<EndpointInfo> result = new ArrayList<>();
        detectMethod(result, """
            import javax.servlet.http.HttpServlet;
            import javax.servlet.http.HttpServletRequest;
            import javax.servlet.http.HttpServletResponse;
            import javax.servlet.annotation.WebServlet;
            @WebServlet("/orders")
            public class OrderServlet extends HttpServlet {
                public void doPost(HttpServletRequest req, HttpServletResponse resp) {}
            }
            """, "OrderServlet");
        assertEquals(1, result.size());
        assertEquals("POST", result.get(0).httpMethod());
    }

    @Test
    void allHttpMethodsDetected() {
        var methodNames = List.of("doGet", "doPost", "doPut", "doDelete",
            "doPatch", "doHead", "doTrace", "doOptions");
        var expectedHttp = List.of("GET", "POST", "PUT", "DELETE",
            "PATCH", "HEAD", "TRACE", "OPTIONS");

        String imports = """
            import javax.servlet.http.HttpServlet;
            import javax.servlet.http.HttpServletRequest;
            import javax.servlet.http.HttpServletResponse;
            import javax.servlet.annotation.WebServlet;
            """;

        for (int i = 0; i < methodNames.size(); i++) {
            String code = imports + "@WebServlet(\"/test\")\n"
                + "public class TestServlet extends HttpServlet {\n"
                + "    public void " + methodNames.get(i) + "(HttpServletRequest req, HttpServletResponse resp) {}\n"
                + "}";
            List<EndpointInfo> result = new ArrayList<>();
            detectMethod(result, code, "TestServlet");
            assertEquals(1, result.size(), "Failed for " + methodNames.get(i));
            assertEquals(expectedHttp.get(i), result.get(0).httpMethod());
        }
    }

    @Test
    void webServletWithUrlPatternsAnnotation() {
        List<EndpointInfo> result = new ArrayList<>();
        detectMethod(result, """
            import javax.servlet.http.HttpServlet;
            import javax.servlet.http.HttpServletRequest;
            import javax.servlet.http.HttpServletResponse;
            import javax.servlet.annotation.WebServlet;
            @WebServlet(urlPatterns = {"/api/*", "/rest/*"})
            public class ApiServlet extends HttpServlet {
                public void doGet(HttpServletRequest req, HttpServletResponse resp) {}
            }
            """, "ApiServlet");
        assertEquals(2, result.size());
        assertEquals("/api/*", result.get(0).path());
        assertEquals("/rest/*", result.get(1).path());
    }

    @Test
    void webServletWithValueAnnotation() {
        List<EndpointInfo> result = new ArrayList<>();
        detectMethod(result, """
            import javax.servlet.http.HttpServlet;
            import javax.servlet.http.HttpServletRequest;
            import javax.servlet.http.HttpServletResponse;
            import javax.servlet.annotation.WebServlet;
            @WebServlet(value = "/admin")
            public class AdminServlet extends HttpServlet {
                public void doGet(HttpServletRequest req, HttpServletResponse resp) {}
            }
            """, "AdminServlet");
        assertEquals(1, result.size());
        assertEquals("/admin", result.get(0).path());
    }

    @Test
    void noWebServletAnnotationUsesEmptyPath() {
        List<EndpointInfo> result = new ArrayList<>();
        detectMethod(result, """
            import javax.servlet.http.HttpServlet;
            import javax.servlet.http.HttpServletRequest;
            import javax.servlet.http.HttpServletResponse;
            public class PlainServlet extends HttpServlet {
                public void doGet(HttpServletRequest req, HttpServletResponse resp) {}
            }
            """, "PlainServlet");
        assertEquals(1, result.size());
        assertEquals("", result.get(0).path());
    }

    @Test
    void jakartaServletDetected() {
        List<EndpointInfo> result = new ArrayList<>();
        detectMethod(result, """
            import jakarta.servlet.http.HttpServlet;
            import jakarta.servlet.http.HttpServletRequest;
            import jakarta.servlet.http.HttpServletResponse;
            import jakarta.servlet.annotation.WebServlet;
            @WebServlet("/jakarta")
            public class JakartaServlet extends HttpServlet {
                public void doGet(HttpServletRequest req, HttpServletResponse resp) {}
            }
            """, "JakartaServlet");
        assertEquals(1, result.size());
        assertEquals("GET", result.get(0).httpMethod());
        assertEquals("/jakarta", result.get(0).path());
    }

    @Test
    void missingServletSignatureIsIgnored() {
        List<EndpointInfo> result = new ArrayList<>();
        detectMethod(result, """
            import javax.servlet.http.HttpServlet;
            import javax.servlet.annotation.WebServlet;
            @WebServlet("/test")
            public class BadServlet extends HttpServlet {
                public void doGet(String arg) {}
            }
            """, "BadServlet");
        assertTrue(result.isEmpty());
    }

    @Test
    void methodNotInMappingIsIgnored() {
        List<EndpointInfo> result = new ArrayList<>();
        detectMethod(result, """
            import javax.servlet.http.HttpServlet;
            import javax.servlet.http.HttpServletRequest;
            import javax.servlet.http.HttpServletResponse;
            import javax.servlet.annotation.WebServlet;
            @WebServlet("/test")
            public class TestServlet extends HttpServlet {
                public void doSomething(HttpServletRequest req, HttpServletResponse resp) {}
            }
            """, "TestServlet");
        assertTrue(result.isEmpty());
    }

    @Test
    void servletWithMultipleHttpMethods() {
        String code = """
            import javax.servlet.http.HttpServlet;
            import javax.servlet.http.HttpServletRequest;
            import javax.servlet.http.HttpServletResponse;
            import javax.servlet.annotation.WebServlet;
            @WebServlet("/api")
            public class ApiServlet extends HttpServlet {
                public void doGet(HttpServletRequest req, HttpServletResponse resp) {}
                public void doPost(HttpServletRequest req, HttpServletResponse resp) {}
                public void doDelete(HttpServletRequest req, HttpServletResponse resp) {}
            }
            """;
        CompilationUnit cu = StaticJavaParser.parse(code);
        var methods = cu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class);
        List<EndpointInfo> result = new ArrayList<>();
        for (var method : methods) {
            detector.detect(result, method, "ApiServlet", "ApiServlet.java");
        }
        assertEquals(3, result.size());
        assertEquals("GET", result.get(0).httpMethod());
        assertEquals("POST", result.get(1).httpMethod());
        assertEquals("DELETE", result.get(2).httpMethod());
    }

    @Test
    void pathWithLeadingSlashPreserved() {
        List<EndpointInfo> result = new ArrayList<>();
        detectMethod(result, """
            import javax.servlet.http.HttpServlet;
            import javax.servlet.http.HttpServletRequest;
            import javax.servlet.http.HttpServletResponse;
            import javax.servlet.annotation.WebServlet;
            @WebServlet("/api/v1/orders")
            public class OrderServlet extends HttpServlet {
                public void doGet(HttpServletRequest req, HttpServletResponse resp) {}
            }
            """, "OrderServlet");
        assertEquals(1, result.size());
        assertEquals("/api/v1/orders", result.get(0).path());
    }

    @Test
    void pathWithoutLeadingSlashGetsOneAdded() {
        List<EndpointInfo> result = new ArrayList<>();
        detectMethod(result, """
            import javax.servlet.http.HttpServlet;
            import javax.servlet.http.HttpServletRequest;
            import javax.servlet.http.HttpServletResponse;
            import javax.servlet.annotation.WebServlet;
            @WebServlet("api/orders")
            public class OrderServlet extends HttpServlet {
                public void doGet(HttpServletRequest req, HttpServletResponse resp) {}
            }
            """, "OrderServlet");
        assertEquals(1, result.size());
        assertEquals("/api/orders", result.get(0).path());
    }

    private void detectMethod(List<EndpointInfo> result, String code, String className) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        var methods = cu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class);
        for (var method : methods) {
            detector.detect(result, method, className, className + ".java");
        }
    }
}
