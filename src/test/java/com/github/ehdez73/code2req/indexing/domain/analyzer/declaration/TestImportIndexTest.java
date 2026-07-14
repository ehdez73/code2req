package com.github.ehdez73.code2req.indexing.domain.analyzer.declaration;

import com.github.ehdez73.code2req.indexing.domain.model.AllowedLibrariesConfig;
import com.github.ehdez73.code2req.indexing.domain.model.IndexingConfig;
import com.github.javaparser.StaticJavaParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestImportIndexTest {

    private TestImportIndex index;
    private TestImportIndex indexWithPrefixes;

    @BeforeEach
    void setUp() {
        var config = new IndexingConfig(null, List.of("Test", "IT"), null);
        var configWithPrefix = new IndexingConfig(null, List.of("Test", "IT"), List.of("Test"));
        var allowedLibs = new AllowedLibrariesConfig(null, null);
        index = new TestImportIndex(config, allowedLibs);
        indexWithPrefixes = new TestImportIndex(configWithPrefix, allowedLibs);
    }

    @Nested
    class ExplicitImportTests {

        @Test
        void findsTestFileViaExplicitImport() {
            var sourceCu = StaticJavaParser.parse(
                "package com.example;\nclass OrderService {}");
            var testCu = StaticJavaParser.parse(
                "package com.example;\nimport com.example.OrderService;\nclass OrderServiceTest {}");

            index.index("/src/main/java/com/example/OrderService.java", sourceCu);
            index.index("/src/test/java/com/example/OrderServiceTest.java", testCu);

            var result = index.findTestFileBySourcePath("/src/main/java/com/example/OrderService.java");
            assertTrue(result.isPresent());
            assertTrue(result.get().endsWith("OrderServiceTest.java"));
        }

        @Test
        void ignoresFrameworkImports() {
            var sourceCu = StaticJavaParser.parse(
                "package com.example;\nclass OrderService {}");
            var testCu = StaticJavaParser.parse(
                "package com.example;\nimport com.example.OrderService;\nimport org.junit.jupiter.api.Test;\nimport java.util.List;\nclass OrderServiceTest {}");

            index.index("/src/main/java/com/example/OrderService.java", sourceCu);
            index.index("/src/test/java/com/example/OrderServiceTest.java", testCu);

            var result = index.findTestFileBySourcePath("/src/main/java/com/example/OrderService.java");
            assertTrue(result.isPresent());
        }
    }

    @Nested
    class SamePackageInferenceTests {

        @Test
        void infersProductionClassWhenNoExplicitImport() {
            var sourceCu = StaticJavaParser.parse(
                "package com.example;\nclass OrderService {}");
            var testCu = StaticJavaParser.parse(
                "package com.example;\nclass OrderServiceTest {}");

            index.index("/src/main/java/com/example/OrderService.java", sourceCu);
            index.index("/src/test/java/com/example/OrderServiceTest.java", testCu);

            var result = index.findTestFileBySourcePath("/src/main/java/com/example/OrderService.java");
            assertTrue(result.isPresent());
            assertTrue(result.get().endsWith("OrderServiceTest.java"));
        }

        @Test
        void infersWithITSuffix() {
            var sourceCu = StaticJavaParser.parse(
                "package com.example;\nclass OrderRepository {}");
            var testCu = StaticJavaParser.parse(
                "package com.example;\nclass OrderRepositoryIT {}");

            index.index("/src/main/java/com/example/OrderRepository.java", sourceCu);
            index.index("/src/test/java/com/example/OrderRepositoryIT.java", testCu);

            var result = index.findTestFileBySourcePath("/src/main/java/com/example/OrderRepository.java");
            assertTrue(result.isPresent());
            assertTrue(result.get().endsWith("OrderRepositoryIT.java"));
        }
    }

    @Nested
    class PrefixTests {

        @Test
        void infersProductionClassViaPrefix() {
            var sourceCu = StaticJavaParser.parse(
                "package com.example;\nclass OrderService {}");
            var testCu = StaticJavaParser.parse(
                "package com.example;\nclass TestOrderService {}");

            indexWithPrefixes.index("/src/main/java/com/example/OrderService.java", sourceCu);
            indexWithPrefixes.index("/src/test/java/com/example/TestOrderService.java", testCu);

            var result = indexWithPrefixes.findTestFileBySourcePath("/src/main/java/com/example/OrderService.java");
            assertTrue(result.isPresent());
            assertTrue(result.get().endsWith("TestOrderService.java"));
        }
    }

    @Nested
    class MainSourceGuardTests {

        @Test
        void doesNotIndexProductionFileWithTestSuffixInMain() {
            var cu = StaticJavaParser.parse(
                "package com.example;\nimport com.example.SomeHelper;\nclass ManifestTestWriter {}");

            index.index("/src/main/java/com/example/ManifestTestWriter.java", cu);

            assertTrue(index.findTestFileBySourcePath("/src/main/java/com/example/ManifestTestWriter.java").isEmpty());
        }
    }

    @Nested
    class NoMatchTests {

        @Test
        void returnsEmptyWhenNoTestExists() {
            var sourceCu = StaticJavaParser.parse(
                "package com.example;\nclass StandaloneService {}");

            index.index("/src/main/java/com/example/StandaloneService.java", sourceCu);

            var result = index.findTestFileBySourcePath("/src/main/java/com/example/StandaloneService.java");
            assertTrue(result.isEmpty());
        }

        @Test
        void returnsEmptyForUnknownPath() {
            assertTrue(index.findTestFileBySourcePath("/nonexistent/File.java").isEmpty());
        }
    }

    @Nested
    class LifecycleTests {

        @Test
        void clearResetsIndex() {
            var sourceCu = StaticJavaParser.parse(
                "package com.example;\nclass OrderService {}");
            var testCu = StaticJavaParser.parse(
                "package com.example;\nimport com.example.OrderService;\nclass OrderServiceTest {}");

            index.index("/src/main/java/com/example/OrderService.java", sourceCu);
            index.index("/src/test/java/com/example/OrderServiceTest.java", testCu);
            assertTrue(index.findTestFileBySourcePath("/src/main/java/com/example/OrderService.java").isPresent());

            index.clear();

            assertTrue(index.findTestFileBySourcePath("/src/main/java/com/example/OrderService.java").isEmpty());
        }

        @Test
        void canReuseAfterClear() {
            var sourceCu = StaticJavaParser.parse(
                "package com.example;\nclass A {}");
            var testCu = StaticJavaParser.parse(
                "package com.example;\nimport com.example.A;\nclass ATest {}");

            index.index("/src/main/java/com/example/A.java", sourceCu);
            index.index("/src/test/java/com/example/ATest.java", testCu);
            index.clear();

            var sourceCu2 = StaticJavaParser.parse(
                "package com.example;\nclass B {}");
            var testCu2 = StaticJavaParser.parse(
                "package com.example;\nimport com.example.B;\nclass BTest {}");

            index.index("/src/main/java/com/example/B.java", sourceCu2);
            index.index("/src/test/java/com/example/BTest.java", testCu2);

            assertTrue(index.findTestFileBySourcePath("/src/main/java/com/example/B.java").isPresent());
            assertTrue(index.findTestFileBySourcePath("/src/main/java/com/example/A.java").isEmpty());
        }
    }

    @Nested
    class HasTestsTests {

        @Test
        void hasTestsReturnsTrueWhenTestExists() {
            var sourceCu = StaticJavaParser.parse(
                "package com.example;\nclass OrderService {}");
            var testCu = StaticJavaParser.parse(
                "package com.example;\nimport com.example.OrderService;\nclass OrderServiceTest {}");

            index.index("/src/main/java/com/example/OrderService.java", sourceCu);
            index.index("/src/test/java/com/example/OrderServiceTest.java", testCu);

            assertTrue(index.hasTests("/src/main/java/com/example/OrderService.java"));
        }

        @Test
        void hasTestsReturnsFalseWhenNoTest() {
            var sourceCu = StaticJavaParser.parse(
                "package com.example;\nclass StandaloneService {}");

            index.index("/src/main/java/com/example/StandaloneService.java", sourceCu);

            assertFalse(index.hasTests("/src/main/java/com/example/StandaloneService.java"));
        }
    }
}
