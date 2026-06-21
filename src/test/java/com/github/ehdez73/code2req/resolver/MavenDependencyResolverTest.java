package com.github.ehdez73.code2req.resolver;

import com.github.ehdez73.code2req.config.ManifestLoader;
import com.github.ehdez73.code2req.model.Dependency;
import com.github.ehdez73.code2req.model.DependencyGraph;
import com.github.ehdez73.code2req.model.ProjectManifest;
import com.github.ehdez73.code2req.model.ScanTarget;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class MavenDependencyResolverTest {

    private final MavenDependencyResolver resolver = new MavenDependencyResolver();

    @Test
    void isMavenAvailable_doesNotThrow() {
        assertDoesNotThrow(resolver::isMavenAvailable);
    }

    @Test
    void parseDependencyTree_emptyOutput_returnsEmptyList() {
        List<Dependency> deps = resolver.parseDependencyTree("");
        assertTrue(deps.isEmpty());
    }

    @Test
    void parseDependencyTree_onlyNonDependencyLines_returnsEmptyList() {
        String output = """
            --- dependency:3.6.1:tree ---
            Some header line
            ---
            """;
        List<Dependency> deps = resolver.parseDependencyTree(output);
        assertTrue(deps.isEmpty());
    }

    @Test
    void parseDependencyTree_handlesQuietModeOutput() {
        String output = """
            com.example:my-app:jar:1.0.0
            +- org.springframework.boot:spring-boot-starter:jar:3.4.2:compile
            |  +- org.springframework.boot:spring-boot:jar:3.4.2:compile
            |  |  \\- org.springframework.boot:spring-boot-autoconfigure:jar:3.4.2:compile
            \\- org.slf4j:slf4j-api:jar:2.0.9:compile
            """;
        List<Dependency> deps = resolver.parseDependencyTree(output);
        assertEquals(4, deps.size());

        Dependency first = deps.getFirst();
        assertEquals("org.springframework.boot", first.groupId());
        assertEquals("spring-boot-starter", first.artifactId());
        assertEquals("3.4.2", first.version());
        assertEquals("compile", first.scope());
    }

    @Test
    void parseDependencyTree_skipsRootProjectLine() {
        String output = """
            com.example:my-app:jar:1.0.0
            """;
        List<Dependency> deps = resolver.parseDependencyTree(output);
        assertTrue(deps.isEmpty());
    }

    @Test
    void parseDependencyTree_handlesOutputWithoutScope() {
        String output = """
            +- com.google.guava:guava:jar:31.1-jre
            """;
        List<Dependency> deps = resolver.parseDependencyTree(output);
        assertEquals(1, deps.size());
        assertEquals("com.google.guava", deps.getFirst().groupId());
        assertEquals("guava", deps.getFirst().artifactId());
        assertEquals("31.1-jre", deps.getFirst().version());
        assertNull(deps.getFirst().scope());
    }

    @Test
    void parseDependencyTree_handlesInfoPrefixOutput() {
        String output = """
            [INFO] com.example:my-app:jar:1.0.0
            [INFO] +- org.junit:junit-bom:jar:5.11.0:compile
            """;
        List<Dependency> deps = resolver.parseDependencyTree(output);
        assertEquals(1, deps.size());
    }

    @Test
    void parseDependencyTree_handlesDeeplyNestedTree() {
        String output = """
            com.example:app:jar:1.0.0
            +- org.spring:core:jar:5.0.0:compile
            |  +- org.spring:beans:jar:5.0.0:compile
            |  |  +- org.spring:expression:jar:5.0.0:compile
            |  |  \\- org.spring:context:jar:5.0.0:compile
            |  \\- org.spring:aop:jar:5.0.0:compile
            \\- com.fasterxml:jackson:jar:2.0.0:compile
               +- com.fasterxml:core:jar:2.0.0:compile
               \\- com.fasterxml:annotations:jar:2.0.0:compile
            """;
        List<Dependency> deps = resolver.parseDependencyTree(output);
        assertEquals(8, deps.size());
    }

    @Test
    void resolve_returnsHeuristic_whenPathDoesNotExist() {
        ScanTarget target = new ScanTarget("missing", "/nonexistent/path", null, null, null, null, null);
        DependencyGraph result = resolver.resolve(target);
        assertTrue(result.heuristicMode());
        assertFalse(result.resolved());
        assertNotNull(result.resolutionMessage());
    }

    @Test
    void resolve_returnsHeuristic_whenNoPomXml(@TempDir Path tempDir) throws IOException {
        ScanTarget target = new ScanTarget("empty", tempDir.toString(), null, null, null, null, null);
        DependencyGraph result = resolver.resolve(target);
        assertTrue(result.heuristicMode());
        assertFalse(result.resolved());
        assertNotNull(result.resolutionMessage());
    }

    @Test
    void dependencyGraph_heuristicFactory_createsCorrectState() {
        DependencyGraph graph = DependencyGraph.heuristic("test message");
        assertFalse(graph.resolved());
        assertTrue(graph.heuristicMode());
        assertTrue(graph.dependencies().isEmpty());
        assertEquals("test message", graph.resolutionMessage());
    }

    @Test
    void parseDependencyTree_filtersDashSeparators() {
        String output = """
            com.example:app:jar:1.0.0
            ---
            +- junit:junit:jar:4.13.2:test
            --- dependency:tree ---
            """;
        List<Dependency> deps = resolver.parseDependencyTree(output);
        assertEquals(1, deps.size());
    }

    @Test
    @Disabled("Requires a real project-manifest.yaml and Maven installed - this is more of an integration test")
    void resolveFromManifest() throws IOException {
        ManifestLoader manifestLoader = new ManifestLoader();
        ProjectManifest manifest = manifestLoader.load(Path.of("project-manifest.yaml"));

        for (ScanTarget target : manifest.targets()) {
            DependencyGraph graph = resolver.resolve(target);
            System.out.printf("Target: %s | resolved=%s | heuristic=%s | deps=%d | message=%s%n",
                    target.name(), graph.resolved(), graph.heuristicMode(),
                    graph.dependencies().size(), graph.resolutionMessage());
        }
    }
}
