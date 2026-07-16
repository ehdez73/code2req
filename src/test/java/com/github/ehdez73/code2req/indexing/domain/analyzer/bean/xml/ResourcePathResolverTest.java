package com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePathResolverTest {

    private final Path testResources = Path.of("src/test/resources/xml").toAbsolutePath().normalize();

    @Test
    void findFirstGlobChar_returnsCorrectPosition() {
        assertEquals(-1, ResourcePathResolver.findFirstGlobChar("simple-beans.xml"));
        assertEquals(0, ResourcePathResolver.findFirstGlobChar("*.xml"));
        assertEquals(14, ResourcePathResolver.findFirstGlobChar("config/spring/*.xml"));
        assertEquals(14, ResourcePathResolver.findFirstGlobChar("config/spring/**/*.xml"));
        assertEquals(6, ResourcePathResolver.findFirstGlobChar("config?-beans.xml"));
        assertEquals(14, ResourcePathResolver.findFirstGlobChar("config/spring/[abc].xml"));
    }

    @Test
    void hasGlobChars_returnsTrueForGlobPatterns() {
        assertTrue(ResourcePathResolver.hasGlobChars("*.xml"));
        assertTrue(ResourcePathResolver.hasGlobChars("config/**/*.xml"));
        assertTrue(ResourcePathResolver.hasGlobChars("config?-beans.xml"));
        assertFalse(ResourcePathResolver.hasGlobChars("simple-beans.xml"));
        assertFalse(ResourcePathResolver.hasGlobChars(""));
    }

    @Test
    void resolveExactPath_findsFileRelativeToGivenDir() {
        List<Path> resolved = ResourcePathResolver.resolve("simple-beans.xml", null, testResources);
        assertFalse(resolved.isEmpty());
        assertTrue(resolved.get(0).endsWith("simple-beans.xml"));
    }

    @Test
    void resolveExactPath_returnsEmptyForMissingFile() {
        List<Path> resolved = ResourcePathResolver.resolve("nonexistent-file.xml", null, testResources);
        assertTrue(resolved.isEmpty());
    }

    @Test
    void resolveWithClasspathPrefix_stripsPrefix() {
        List<Path> resolved = ResourcePathResolver.resolve("classpath:simple-beans.xml", null, testResources);
        assertFalse(resolved.isEmpty());
        assertTrue(resolved.get(0).endsWith("simple-beans.xml"));
    }

    @Test
    void resolveWithClasspathAllPrefix_stripsPrefix() {
        List<Path> resolved = ResourcePathResolver.resolve("classpath*:simple-beans.xml", null, testResources);
        assertFalse(resolved.isEmpty());
        assertTrue(resolved.get(0).endsWith("simple-beans.xml"));
    }

    @Test
    void resolveWithFilePrefix_stripsPrefix() {
        Path absolutePath = testResources.resolve("simple-beans.xml");
        List<Path> resolved = ResourcePathResolver.resolve("file:" + absolutePath, null, null);
        assertFalse(resolved.isEmpty());
        assertTrue(resolved.get(0).endsWith("simple-beans.xml"));
    }

    @Test
    void resolveWithLeadingSlash_stripsSlash() {
        List<Path> resolved = ResourcePathResolver.resolve("/simple-beans.xml", null, testResources);
        assertFalse(resolved.isEmpty());
        assertTrue(resolved.get(0).endsWith("simple-beans.xml"));
    }

    @Test
    void resolveGlob_matchesWildcardPattern() {
        List<Path> resolved = ResourcePathResolver.resolve("simple-*.xml", null, testResources);
        assertFalse(resolved.isEmpty());
        assertTrue(resolved.stream().anyMatch(p -> p.endsWith("simple-beans.xml")));
        assertTrue(resolved.stream().noneMatch(p -> p.endsWith("namespace-beans.xml")));
        assertTrue(resolved.stream().noneMatch(p -> p.endsWith("non-spring.xml")));
    }
}
