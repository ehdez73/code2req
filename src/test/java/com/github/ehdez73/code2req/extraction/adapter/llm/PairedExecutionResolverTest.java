package com.github.ehdez73.code2req.extraction.adapter.llm;

import com.github.ehdez73.code2req.indexing.domain.model.IndexingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PairedExecutionResolverTest {

    @TempDir
    Path tempDir;

    private PairedExecutionResolver resolver;

    @BeforeEach
    void setUp() {
        var matcher = new TestFileMatcher(
            new IndexingConfig(null, null, null));
        resolver = new PairedExecutionResolver(matcher);
    }

    @Test
    void resolvesPairedTestFile() throws Exception {
        var sourceFile = tempDir.resolve("OrderService.java");
        var testFile = tempDir.resolve("OrderServiceTest.java");
        Files.createDirectories(tempDir);
        Files.writeString(sourceFile, "public class OrderService { }");
        Files.writeString(testFile, """
            class OrderServiceTest {
                @Test
                void testCreateOrder() {
                    assertEquals("OK", service.createOrder(input));
                    assertNotNull(result);
                }
            }
            """);

        var result = resolver.resolve(sourceFile.toString());
        assertTrue(result.isPresent());
        assertEquals("OrderServiceTest.java", Path.of(result.get().testFilePath()).getFileName().toString());
        assertFalse(result.get().testContent().isBlank());
    }

    @Test
    void returnsEmptyWhenNoTestFile() throws Exception {
        var sourceFile = tempDir.resolve("StandaloneService.java");
        Files.writeString(sourceFile, "public class StandaloneService { }");

        var result = resolver.resolve(sourceFile.toString());
        assertTrue(result.isEmpty());
    }

    @Test
    void returnsEmptyWhenTestFileCannotBeRead() throws Exception {
        var sourceFile = tempDir.resolve("OrderService.java");
        var testFile = tempDir.resolve("OrderServiceTest.java");
        Files.createDirectories(tempDir);
        Files.createFile(sourceFile);
        Files.createFile(testFile);
        testFile.toFile().delete();

        var result = resolver.resolve(sourceFile.toString());
        assertTrue(result.isEmpty());
    }
}
