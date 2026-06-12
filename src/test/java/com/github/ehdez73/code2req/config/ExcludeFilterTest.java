package com.github.ehdez73.code2req.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcludeFilterTest {

    private ExcludeFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ExcludeFilter();
    }

    @Test
    void defaultPatternsExcludeTargetDir(@TempDir Path tempDir) throws IOException {
        Path srcFile = Files.createFile(tempDir.resolve("src.java"));
        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Path targetFile = Files.createFile(targetDir.resolve("generated.java"));

        List<Path> files = List.of(srcFile, targetFile);
        ExcludeResult result = filter.filterWithDefaults(tempDir, files);

        assertTrue(result.included().contains(srcFile));
        assertTrue(result.excluded().contains(targetFile));
    }

    @Test
    void customExtraPatternsAreExcluded(@TempDir Path tempDir) throws IOException {
        Path srcFile = Files.createFile(tempDir.resolve("src.java"));
        Path customDir = Files.createDirectories(tempDir.resolve("out"));
        Path customFile = Files.createFile(customDir.resolve("output.java"));

        List<Path> files = List.of(srcFile, customFile);
        ExcludeResult result = filter.filter(tempDir, files, List.of("**/out/**"));

        assertTrue(result.included().contains(srcFile));
        assertTrue(result.excluded().contains(customFile));
    }

    @Test
    void noMatchingFilesNothingExcluded(@TempDir Path tempDir) throws IOException {
        Path a = Files.createFile(tempDir.resolve("a.java"));
        Path b = Files.createFile(tempDir.resolve("b.java"));

        List<Path> files = List.of(a, b);
        ExcludeResult result = filter.filterWithDefaults(tempDir, files);

        assertEquals(2, result.includedCount());
        assertEquals(0, result.excludedCount());
    }

    @Test
    void wildcardPatternExcludesClassFiles(@TempDir Path tempDir) throws IOException {
        Path src = Files.createFile(tempDir.resolve("Main.java"));
        Path classFile = Files.createFile(tempDir.resolve("Main.class"));

        List<Path> files = List.of(src, classFile);
        ExcludeResult result = filter.filterWithDefaults(tempDir, files);

        assertTrue(result.included().contains(src));
        assertTrue(result.excluded().contains(classFile));
    }

    @Test
    void shouldExcludeReturnsCorrectly(@TempDir Path tempDir) throws IOException {
        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Path file = Files.createFile(targetDir.resolve("gen.java"));

        assertTrue(filter.shouldExclude(tempDir, file, ExcludeFilter.defaultPatterns()));
    }

    @Test
    void shouldNotExcludeNonMatchingFile(@TempDir Path tempDir) throws IOException {
        Path file = Files.createFile(tempDir.resolve("Main.java"));

        assertFalse(filter.shouldExclude(tempDir, file, ExcludeFilter.defaultPatterns()));
    }

    @Test
    void emptyFileListReturnsEmptyResult(@TempDir Path tempDir) {
        ExcludeResult result = filter.filterWithDefaults(tempDir, List.of());
        assertEquals(0, result.includedCount());
        assertEquals(0, result.excludedCount());
    }

    @Test
    void gitDirectoryIsExcluded(@TempDir Path tempDir) throws IOException {
        Path src = Files.createFile(tempDir.resolve("src.java"));
        Path gitDir = Files.createDirectories(tempDir.resolve(".git"));
        Path gitFile = Files.createFile(gitDir.resolve("config"));

        List<Path> files = List.of(src, gitFile);
        ExcludeResult result = filter.filterWithDefaults(tempDir, files);

        assertTrue(result.included().contains(src));
        assertTrue(result.excluded().contains(gitFile));
    }
}
