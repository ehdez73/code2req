package com.github.ehdez73.code2req.executor.testmining;

import com.github.ehdez73.code2req.model.ExecutionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestFileMatcherTest {

    @TempDir
    Path tempDir;

    private TestFileMatcher matcher;
    private TestFileMatcher matcherWithCustomSuffixes;

    @BeforeEach
    void setUp() {
        matcher = new TestFileMatcher(
            new ExecutionConfig(null, null, null, null, null, null, null, null, null, null));
        matcherWithCustomSuffixes = new TestFileMatcher(
            new ExecutionConfig(null, null, null, null, null, null, null, List.of("Test", "IT", "Spec"), null, null));
    }

    @Nested
    class SameDirectoryTests {

        @Test
        void findsTestFileWithTestSuffix() throws Exception {
            var sourceFile = tempDir.resolve("OrderService.java");
            var testFile = tempDir.resolve("OrderServiceTest.java");
            Files.createFile(sourceFile);
            Files.createFile(testFile);

            var result = matcher.findTestFilePath(sourceFile.toString());
            assertTrue(result.isPresent());
            assertTrue(result.get().endsWith("OrderServiceTest.java"));
        }

        @Test
        void findsTestFileWithITSuffix() throws Exception {
            var sourceFile = tempDir.resolve("OrderRepository.java");
            var testFile = tempDir.resolve("OrderRepositoryIT.java");
            Files.createFile(sourceFile);
            Files.createFile(testFile);

            var result = matcher.findTestFilePath(sourceFile.toString());
            assertTrue(result.isPresent());
            assertTrue(result.get().endsWith("OrderRepositoryIT.java"));
        }

        @Test
        void noMatchWhenNoTestFileExists() throws Exception {
            var sourceFile = tempDir.resolve("StandaloneService.java");
            Files.createFile(sourceFile);

            var result = matcher.findTestFilePath(sourceFile.toString());
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class SrcMainToSrcTestTests {

        @Test
        void findsTestFileUnderSrcTestJava() throws Exception {
            var sourceDir = tempDir.resolve("src/main/java/com/example");
            var testDir = tempDir.resolve("src/test/java/com/example");
            Files.createDirectories(sourceDir);
            Files.createDirectories(testDir);

            var sourceFile = sourceDir.resolve("OrderService.java");
            var testFile = testDir.resolve("OrderServiceTest.java");
            Files.createFile(sourceFile);
            Files.createFile(testFile);

            var result = matcher.findTestFilePath(sourceFile.toString());
            assertTrue(result.isPresent());
            assertTrue(result.get().endsWith("OrderServiceTest.java"));
        }

        @Test
        void noMatchWhenSrcTestJavaMissing() throws Exception {
            var sourceDir = tempDir.resolve("src/main/java/com/example");
            Files.createDirectories(sourceDir);
            var sourceFile = sourceDir.resolve("OrderService.java");
            Files.createFile(sourceFile);

            var result = matcher.findTestFilePath(sourceFile.toString());
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void nullPathReturnsEmpty() {
            assertTrue(matcher.findTestFilePath(null).isEmpty());
        }

        @Test
        void blankPathReturnsEmpty() {
            assertTrue(matcher.findTestFilePath("   ").isEmpty());
        }

        @Test
        void nonJavaFileReturnsEmpty() throws Exception {
            var sourceFile = tempDir.resolve("readme.txt");
            Files.createFile(sourceFile);
            assertTrue(matcher.findTestFilePath(sourceFile.toString()).isEmpty());
        }

        @Test
        void noParentReturnsEmpty() {
            assertTrue(matcher.findTestFilePath("Foo.java").isEmpty());
        }
    }

    @Nested
    class CustomSuffixTests {

        @Test
        void findsTestFileWithSpecSuffix() throws Exception {
            var sourceFile = tempDir.resolve("PaymentService.java");
            var testFile = tempDir.resolve("PaymentServiceSpec.java");
            Files.createFile(sourceFile);
            Files.createFile(testFile);

            var result = matcherWithCustomSuffixes.findTestFilePath(sourceFile.toString());
            assertTrue(result.isPresent());
            assertTrue(result.get().endsWith("PaymentServiceSpec.java"));
        }

        @Test
        void standardSuffixesStillWorkWithCustom() throws Exception {
            var sourceFile = tempDir.resolve("InventoryService.java");
            var testFile = tempDir.resolve("InventoryServiceTest.java");
            Files.createFile(sourceFile);
            Files.createFile(testFile);

            var result = matcherWithCustomSuffixes.findTestFilePath(sourceFile.toString());
            assertTrue(result.isPresent());
        }
    }
}
