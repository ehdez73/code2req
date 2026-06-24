package com.github.ehdez73.code2req.service;

import com.github.ehdez73.code2req.model.ScanTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FilePathResolverTest {

    private final FilePathResolver resolver = new FilePathResolver();

    @Test
    void nullPathReturnsEmpty() {
        Optional<Path> result = resolver.resolve(null, List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void blankPathReturnsEmpty() {
        Optional<Path> result = resolver.resolve("", List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void whitespacePathReturnsEmpty() {
        Optional<Path> result = resolver.resolve("   ", List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void wildcardPathReturnsEmpty() {
        Optional<Path> result = resolver.resolve("**/Foo.java", List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void wildcardPathWithQuestionReturnsEmpty() {
        Optional<Path> result = resolver.resolve("Foo*.java", List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void absoluteExistingFileReturnsNormalizedPath(@TempDir Path tempDir) throws IOException {
        Path file = Files.createFile(tempDir.resolve("target.java"));
        ScanTarget target = new ScanTarget("test", tempDir.toString(), "backend", "java-spring", null, null, null);

        Optional<Path> result = resolver.resolve(file.toAbsolutePath().toString(), List.of(target));

        assertTrue(result.isPresent());
        assertEquals(file.normalize(), result.get());
    }

    @Test
    void nonExistentAbsoluteFileReturnsEmpty(@TempDir Path tempDir) {
        Path nonExistent = tempDir.resolve("nonexistent.java");
        ScanTarget target = new ScanTarget("test", tempDir.toString(), "backend", "java-spring", null, null, null);

        Optional<Path> result = resolver.resolve(nonExistent.toAbsolutePath().toString(), List.of(target));

        assertTrue(result.isEmpty());
    }

    @Test
    void relativeExactPathFound(@TempDir Path tempDir) throws IOException {
        Path sub = Files.createDirectories(tempDir.resolve("src/main/java"));
        Path file = Files.createFile(sub.resolve("MyClass.java"));
        ScanTarget target = new ScanTarget("test", tempDir.toString(), "backend", "java-spring", null, null, null);

        Optional<Path> result = resolver.resolve("src/main/java/MyClass.java", List.of(target));

        assertTrue(result.isPresent());
        assertEquals(file.normalize(), result.get());
    }

    @Test
    void relativePathWithBackslashesNormalized(@TempDir Path tempDir) throws IOException {
        Path sub = Files.createDirectories(tempDir.resolve("src/main"));
        Path file = Files.createFile(sub.resolve("MyClass.java"));
        ScanTarget target = new ScanTarget("test", tempDir.toString(), "backend", "java-spring", null, null, null);

        Optional<Path> result = resolver.resolve("src\\main\\MyClass.java", List.of(target));

        assertTrue(result.isPresent());
        assertEquals(file.normalize(), result.get());
    }

    @Test
    void relativePathWithoutExtensionAppendsJava(@TempDir Path tempDir) throws IOException {
        Path sub = Files.createDirectories(tempDir.resolve("src/main"));
        Path file = Files.createFile(sub.resolve("MyClass.java"));
        ScanTarget target = new ScanTarget("test", tempDir.toString(), "backend", "java-spring", null, null, null);

        Optional<Path> result = resolver.resolve("src/main/MyClass", List.of(target));

        assertTrue(result.isPresent());
        assertEquals(file.normalize(), result.get());
    }

    @Test
    void dotSeparatedPathWithoutExtensionAppendsJava(@TempDir Path tempDir) throws IOException {
        Path sub = Files.createDirectories(tempDir.resolve("com/example"));
        Path file = Files.createFile(sub.resolve("MyClass.java"));
        ScanTarget target = new ScanTarget("test", tempDir.toString(), "backend", "java-spring", null, null, null);

        Optional<Path> result = resolver.resolve("com.example.MyClass", List.of(target));

        assertTrue(result.isPresent());
        assertEquals(file.normalize(), result.get());
    }

    @Test
    void filenameOnlyMatchesByAnyName(@TempDir Path tempDir) throws IOException {
        Path sub = Files.createDirectories(tempDir.resolve("deep/nested/dir"));
        Path file = Files.createFile(sub.resolve("UniqueName.java"));
        ScanTarget target = new ScanTarget("test", tempDir.toString(), "backend", "java-spring", null, null, null);

        Optional<Path> result = resolver.resolve("UniqueName.java", List.of(target));

        assertTrue(result.isPresent());
        assertEquals(file.normalize(), result.get());
    }

    @Test
    void filenameWithoutExtensionAppendsJava(@TempDir Path tempDir) throws IOException {
        Path sub = Files.createDirectories(tempDir.resolve("some/pkg"));
        Path file = Files.createFile(sub.resolve("Finder.java"));
        ScanTarget target = new ScanTarget("test", tempDir.toString(), "backend", "java-spring", null, null, null);

        Optional<Path> result = resolver.resolve("Finder", List.of(target));

        assertTrue(result.isPresent());
        assertEquals(file.normalize(), result.get());
    }

    @Test
    void noMatchReturnsEmpty(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("Existing.java"));
        ScanTarget target = new ScanTarget("test", tempDir.toString(), "backend", "java-spring", null, null, null);

        Optional<Path> result = resolver.resolve("NonExistent.java", List.of(target));

        assertTrue(result.isEmpty());
    }

    @Test
    void triesMultipleTargetsInOrder(@TempDir Path tempDir) throws IOException {
        Path firstRoot = Files.createDirectories(tempDir.resolve("first"));
        Path secondRoot = Files.createDirectories(tempDir.resolve("second"));
        Path file = Files.createFile(secondRoot.resolve("Shared.java"));

        ScanTarget first = new ScanTarget("first", firstRoot.toString(), "backend", "java-spring", null, null, null);
        ScanTarget second = new ScanTarget("second", secondRoot.toString(), "backend", "java-spring", null, null, null);

        Optional<Path> result = resolver.resolve("Shared.java", List.of(first, second));

        assertTrue(result.isPresent());
        assertEquals(file.normalize(), result.get());
    }

    @Test
    void firstMatchingTargetReturnsFirst(@TempDir Path tempDir) throws IOException {
        Path firstRoot = Files.createDirectories(tempDir.resolve("first"));
        Path secondRoot = Files.createDirectories(tempDir.resolve("second"));
        Path file = Files.createFile(firstRoot.resolve("Common.java"));
        Files.createFile(secondRoot.resolve("Common.java"));

        ScanTarget first = new ScanTarget("first", firstRoot.toString(), "backend", "java-spring", null, null, null);
        ScanTarget second = new ScanTarget("second", secondRoot.toString(), "backend", "java-spring", null, null, null);

        Optional<Path> result = resolver.resolve("Common.java", List.of(first, second));

        assertTrue(result.isPresent());
        assertEquals(file.normalize(), result.get());
    }

    @Test
    void emptyTargetsCollectionReturnsEmpty(@TempDir Path tempDir) {
        Optional<Path> result = resolver.resolve("AnyFile.java", List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void isWildcardOrImportWithAsteriskReturnsTrue() {
        assertTrue(FilePathResolver.isWildcardOrImport("**/Foo.java"));
        assertTrue(FilePathResolver.isWildcardOrImport("*.java"));
        assertTrue(FilePathResolver.isWildcardOrImport("Foo*"));
    }

    @Test
    void isWildcardOrImportWithoutAsteriskReturnsFalse() {
        assertFalse(FilePathResolver.isWildcardOrImport("Foo.java"));
        assertFalse(FilePathResolver.isWildcardOrImport("com/example/Foo.java"));
        assertFalse(FilePathResolver.isWildcardOrImport(""));
    }

    @Test
    void redirectImportWithAngleBracketReturnsEmpty() {
        Optional<Path> result = resolver.resolve("<Foo.java>", List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void absolutePathOutsideTargetNotBlocked(@TempDir Path tempDir) throws IOException {
        Path outside = Files.createTempFile("outside", ".java");
        try {
            ScanTarget target = new ScanTarget("test", tempDir.toString(), "backend", "java-spring", null, null, null);

            Optional<Path> result = resolver.resolve(outside.toAbsolutePath().toString(), List.of(target));

            assertTrue(result.isPresent());
            assertEquals(outside.normalize(), result.get());
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void classPathResolvesToJavaFile(@TempDir Path tempDir) throws IOException {
        Path sub = Files.createDirectories(tempDir.resolve("src/main"));
        Path file = Files.createFile(sub.resolve("MyClass.java"));
        ScanTarget target = new ScanTarget("test", tempDir.toString(), "backend", "java-spring", null, null, null);

        Optional<Path> result = resolver.resolve("MyClass.class", List.of(target));

        assertTrue(result.isPresent());
        assertEquals(file.normalize(), result.get());
    }

    @Test
    void classPathWithDotsResolvesToJavaFile(@TempDir Path tempDir) throws IOException {
        Path sub = Files.createDirectories(tempDir.resolve("com/example"));
        Path file = Files.createFile(sub.resolve("Service.java"));
        ScanTarget target = new ScanTarget("test", tempDir.toString(), "backend", "java-spring", null, null, null);

        Optional<Path> result = resolver.resolve("com.example.Service.class", List.of(target));

        assertTrue(result.isPresent());
        assertEquals(file.normalize(), result.get());
    }

    @Test
    void classPathWithoutExtensionResolvesToJava(@TempDir Path tempDir) throws IOException {
        Path sub = Files.createDirectories(tempDir.resolve("some/pkg"));
        Path file = Files.createFile(sub.resolve("Util.java"));
        ScanTarget target = new ScanTarget("test", tempDir.toString(), "backend", "java-spring", null, null, null);

        Optional<Path> result = resolver.resolve("some/pkg/Util.class", List.of(target));

        assertTrue(result.isPresent());
        assertEquals(file.normalize(), result.get());
    }

    @Test
    void classPathWithNoJavaSourceReturnsEmpty(@TempDir Path tempDir) throws IOException {
        Path sub = Files.createDirectories(tempDir.resolve("some/pkg"));
        Files.createFile(sub.resolve("Other.class"));
        ScanTarget target = new ScanTarget("test", tempDir.toString(), "backend", "java-spring", null, null, null);

        Optional<Path> result = resolver.resolve("some/pkg/Other.class", List.of(target));

        assertTrue(result.isEmpty());
    }

    @Test
    void absoluteClassPathResolvesToJavaSource(@TempDir Path tempDir) throws IOException {
        Path file = Files.createFile(tempDir.resolve("Main.java"));
        Path classFile = Path.of(file.toAbsolutePath().toString().replace(".java", ".class"));
        ScanTarget target = new ScanTarget("test", tempDir.toString(), "backend", "java-spring", null, null, null);

        Optional<Path> result = resolver.resolve(classFile.toAbsolutePath().toString(), List.of(target));

        assertTrue(result.isPresent());
        assertEquals(file.normalize(), result.get());
    }

    @Test
    void deeplyNestedFullyQualifiedClassNameResolves(@TempDir Path tempDir) throws IOException {
        Path sub = Files.createDirectories(tempDir.resolve("org/springframework/samples/mvc/data"));
        Path file = Files.createFile(sub.resolve("AbstractContextControllerTests.java"));
        ScanTarget target = new ScanTarget("test", tempDir.toString(), "backend", "java-spring", null, null, null);

        Optional<Path> result = resolver.resolve("org.springframework.samples.mvc.data.AbstractContextControllerTests", List.of(target));

        assertTrue(result.isPresent());
        assertEquals(file.normalize(), result.get());
    }

    @Test
    void fullyQualifiedClassNameWithPathSeparatorsNotTreatedAsFqcn(@TempDir Path tempDir) throws IOException {
        Path sub = Files.createDirectories(tempDir.resolve("com/example"));
        Path file = Files.createFile(sub.resolve("Service.java"));
        ScanTarget target = new ScanTarget("test", tempDir.toString(), "backend", "java-spring", null, null, null);

        Optional<Path> result = resolver.resolve("com/example/Service.java", List.of(target));

        assertTrue(result.isPresent());
        assertEquals(file.normalize(), result.get());
    }

    @Test
    void nonExistentFullyQualifiedClassNameReturnsEmpty(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("com/example"));
        ScanTarget target = new ScanTarget("test", tempDir.toString(), "backend", "java-spring", null, null, null);

        Optional<Path> result = resolver.resolve("com.example.NonExistent", List.of(target));

        assertTrue(result.isEmpty());
    }

    @Test
    void simpleClassNameWithoutDotNotTreatedAsFqcn(@TempDir Path tempDir) throws IOException {
        Path sub = Files.createDirectories(tempDir.resolve("some/pkg"));
        Path file = Files.createFile(sub.resolve("Simple.java"));
        ScanTarget target = new ScanTarget("test", tempDir.toString(), "backend", "java-spring", null, null, null);

        Optional<Path> result = resolver.resolve("Simple.java", List.of(target));

        assertTrue(result.isPresent());
        assertEquals(file.normalize(), result.get());
    }
}
