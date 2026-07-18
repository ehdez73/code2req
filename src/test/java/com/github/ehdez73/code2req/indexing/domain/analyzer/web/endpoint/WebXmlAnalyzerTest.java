package com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WebXmlAnalyzerTest {

    private final WebXmlAnalyzer analyzer = new WebXmlAnalyzer();

    @Test
    void nonExistentFileReturnsEmpty() {
        Path missing = Path.of("/nonexistent/web.xml");
        assertTrue(analyzer.analyze(missing).isEmpty());
    }

    @Test
    void nonWebAppRootElementReturnsEmpty(@TempDir Path tempDir) throws IOException {
        Path xml = tempDir.resolve("not-web.xml");
        Files.writeString(xml, """
            <?xml version="1.0" encoding="UTF-8"?>
            <not-web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee">
            </not-web-app>
            """);
        assertTrue(analyzer.analyze(xml).isEmpty());
    }

    @Test
    void unknownNamespaceReturnsEmpty(@TempDir Path tempDir) throws IOException {
        Path xml = tempDir.resolve("web.xml");
        Files.writeString(xml, """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app xmlns="http://example.com/unknown">
                <servlet>
                    <servlet-name>myServlet</servlet-name>
                    <servlet-class>com.example.MyServlet</servlet-class>
                </servlet>
            </web-app>
            """);
        assertTrue(analyzer.analyze(xml).isEmpty());
    }

    @Test
    void noServletMappingReturnsEmpty(@TempDir Path tempDir) throws IOException {
        Path xml = tempDir.resolve("web.xml");
        Files.writeString(xml, """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee">
                <servlet>
                    <servlet-name>myServlet</servlet-name>
                    <servlet-class>com.example.MyServlet</servlet-class>
                </servlet>
            </web-app>
            """);
        assertTrue(analyzer.analyze(xml).isEmpty());
    }

    @Test
    void standardServletAndMapping(@TempDir Path tempDir) throws IOException {
        Path xml = tempDir.resolve("web.xml");
        Files.writeString(xml, """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app>
                <servlet>
                    <servlet-name>orderServlet</servlet-name>
                    <servlet-class>com.example.OrderServlet</servlet-class>
                </servlet>
                <servlet-mapping>
                    <servlet-name>orderServlet</servlet-name>
                    <url-pattern>/orders/*</url-pattern>
                </servlet-mapping>
            </web-app>
            """);
        List<EndpointInfo> endpoints = analyzer.analyze(xml);
        assertEquals(1, endpoints.size());
        EndpointInfo ep = endpoints.get(0);
        assertEquals("", ep.httpMethod());
        assertEquals("/orders/*", ep.path());
        assertEquals("com.example.OrderServlet", ep.controllerName());
    }

    @Test
    void namespaceDocumentParsedCorrectly(@TempDir Path tempDir) throws IOException {
        Path xml = tempDir.resolve("web.xml");
        Files.writeString(xml, """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app xmlns="http://java.sun.com/xml/ns/javaee"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_3_0.xsd"
                     version="3.0">
                <servlet>
                    <servlet-name>dispatcher</servlet-name>
                    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
                </servlet>
                <servlet-mapping>
                    <servlet-name>dispatcher</servlet-name>
                    <url-pattern>/</url-pattern>
                </servlet-mapping>
            </web-app>
            """);
        List<EndpointInfo> endpoints = analyzer.analyze(xml);
        assertEquals(1, endpoints.size());
        assertEquals("/", endpoints.get(0).path());
        assertEquals("org.springframework.web.servlet.DispatcherServlet", endpoints.get(0).controllerName());
    }

    @Test
    void j2eeNamespaceDocumentParsedCorrectly(@TempDir Path tempDir) throws IOException {
        Path xml = tempDir.resolve("web.xml");
        Files.writeString(xml, """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app xmlns="http://java.sun.com/xml/ns/j2ee">
                <servlet>
                    <servlet-name>legacy</servlet-name>
                    <servlet-class>com.example.LegacyServlet</servlet-class>
                </servlet>
                <servlet-mapping>
                    <servlet-name>legacy</servlet-name>
                    <url-pattern>/legacy/*</url-pattern>
                </servlet-mapping>
            </web-app>
            """);
        List<EndpointInfo> endpoints = analyzer.analyze(xml);
        assertEquals(1, endpoints.size());
        assertEquals("/legacy/*", endpoints.get(0).path());
    }

    @Test
    void multipleUrlPatternsForOneServlet(@TempDir Path tempDir) throws IOException {
        Path xml = tempDir.resolve("web.xml");
        Files.writeString(xml, """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app>
                <servlet>
                    <servlet-name>myServlet</servlet-name>
                    <servlet-class>com.example.MyServlet</servlet-class>
                </servlet>
                <servlet-mapping>
                    <servlet-name>myServlet</servlet-name>
                    <url-pattern>/api/*</url-pattern>
                    <url-pattern>/rest/*</url-pattern>
                </servlet-mapping>
            </web-app>
            """);
        List<EndpointInfo> endpoints = analyzer.analyze(xml);
        assertEquals(2, endpoints.size());
        assertEquals("/api/*", endpoints.get(0).path());
        assertEquals("/rest/*", endpoints.get(1).path());
    }

    @Test
    void multipleServletsAndMappings(@TempDir Path tempDir) throws IOException {
        Path xml = tempDir.resolve("web.xml");
        Files.writeString(xml, """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app>
                <servlet>
                    <servlet-name>servletA</servlet-name>
                    <servlet-class>com.example.ServletA</servlet-class>
                </servlet>
                <servlet>
                    <servlet-name>servletB</servlet-name>
                    <servlet-class>com.example.ServletB</servlet-class>
                </servlet>
                <servlet-mapping>
                    <servlet-name>servletA</servlet-name>
                    <url-pattern>/a/*</url-pattern>
                </servlet-mapping>
                <servlet-mapping>
                    <servlet-name>servletB</servlet-name>
                    <url-pattern>/b/*</url-pattern>
                </servlet-mapping>
            </web-app>
            """);
        List<EndpointInfo> endpoints = analyzer.analyze(xml);
        assertEquals(2, endpoints.size());
        assertEquals("com.example.ServletA", endpoints.get(0).controllerName());
        assertEquals("/a/*", endpoints.get(0).path());
        assertEquals("com.example.ServletB", endpoints.get(1).controllerName());
        assertEquals("/b/*", endpoints.get(1).path());
    }

    @Test
    void mappingWithoutServletClassIsSkipped(@TempDir Path tempDir) throws IOException {
        Path xml = tempDir.resolve("web.xml");
        Files.writeString(xml, """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app>
                <servlet>
                    <servlet-name>noClass</servlet-name>
                </servlet>
                <servlet-mapping>
                    <servlet-name>noClass</servlet-name>
                    <url-pattern>/missing/*</url-pattern>
                </servlet-mapping>
            </web-app>
            """);
        assertTrue(analyzer.analyze(xml).isEmpty());
    }

    @Test
    void pathNormalizedWithLeadingSlash(@TempDir Path tempDir) throws IOException {
        Path xml = tempDir.resolve("web.xml");
        Files.writeString(xml, """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app>
                <servlet>
                    <servlet-name>s</servlet-name>
                    <servlet-class>com.example.Servlet</servlet-class>
                </servlet>
                <servlet-mapping>
                    <servlet-name>s</servlet-name>
                    <url-pattern>api/*</url-pattern>
                </servlet-mapping>
            </web-app>
            """);
        List<EndpointInfo> endpoints = analyzer.analyze(xml);
        assertEquals(1, endpoints.size());
        assertEquals("/api/*", endpoints.get(0).path());
    }

    @Test
    void malformedXmlReturnsEmpty(@TempDir Path tempDir) throws IOException {
        Path xml = tempDir.resolve("web.xml");
        Files.writeString(xml, "not valid xml");
        assertTrue(analyzer.analyze(xml).isEmpty());
    }

    @Test
    void emptyWebAppReturnsEmpty(@TempDir Path tempDir) throws IOException {
        Path xml = tempDir.resolve("web.xml");
        Files.writeString(xml, """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app>
            </web-app>
            """);
        assertTrue(analyzer.analyze(xml).isEmpty());
    }

    @Test
    void servletWithEmptyServletClassIsSkipped(@TempDir Path tempDir) throws IOException {
        Path xml = tempDir.resolve("web.xml");
        Files.writeString(xml, """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app>
                <servlet>
                    <servlet-name>empty</servlet-name>
                    <servlet-class></servlet-class>
                </servlet>
                <servlet-mapping>
                    <servlet-name>empty</servlet-name>
                    <url-pattern>/empty/*</url-pattern>
                </servlet-mapping>
            </web-app>
            """);
        assertTrue(analyzer.analyze(xml).isEmpty());
    }
}
