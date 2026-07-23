package com.github.ehdez73.code2req.common.util;

import com.github.javaparser.StaticJavaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LibraryTypeResolverTest {

    private static final List<String> SPRING_PREFIXES = List.of("org.springframework");

    @Test
    void isAllowedLibraryMatchesByShortNameAndPrefix() {
        var cu = StaticJavaParser.parse("""
            import org.springframework.web.bind.annotation.GetMapping;
            class C {}
            """);
        assertTrue(LibraryTypeResolver.isAllowedLibrary("GetMapping", cu, SPRING_PREFIXES));
    }

    @Test
    void isAllowedLibraryRejectsWhenPrefixDoesntMatch() {
        var cu = StaticJavaParser.parse("""
            import com.fasterxml.jackson.databind.JsonNode;
            class C {}
            """);
        assertFalse(LibraryTypeResolver.isAllowedLibrary("JsonNode", cu, SPRING_PREFIXES));
    }

    @Test
    void isAllowedLibraryRejectsShortNameNotInImports() {
        var cu = StaticJavaParser.parse("""
            import org.springframework.web.bind.annotation.GetMapping;
            class C {}
            """);
        assertFalse(LibraryTypeResolver.isAllowedLibrary("NonExistent", cu, SPRING_PREFIXES));
    }

    @Test
    void isAllowedLibraryWithFilePathMatchesExplicitImport(@TempDir Path tempDir) throws Exception {
        Path sourceFile = tempDir.resolve("TestService.java");
        Files.writeString(sourceFile, """
            package com.example;
            import org.springframework.stereotype.Service;
            class TestService {}
            """);
        assertTrue(LibraryTypeResolver.isAllowedLibrary(
            "org.springframework.stereotype.Service", sourceFile.toString(), SPRING_PREFIXES));
    }

    @Test
    void isAllowedLibraryWithFilePathRejectsNonMatchingPrefix(@TempDir Path tempDir) throws Exception {
        Path sourceFile = tempDir.resolve("TestService.java");
        Files.writeString(sourceFile, """
            package com.example;
            import java.util.List;
            class TestService {}
            """);
        assertFalse(LibraryTypeResolver.isAllowedLibrary(
            "java.util.List", sourceFile.toString(), List.of("org.springframework")));
    }

    @Test
    void isAllowedLibraryResolvesClassNameToFqn(@TempDir Path tempDir) throws Exception {
        Path sourceFile = tempDir.resolve("TestService.java");
        Files.writeString(sourceFile, """
            package com.example;
            import org.springframework.stereotype.Service;
            class TestService {}
            """);
        assertTrue(LibraryTypeResolver.isAllowedLibrary(
            "Service.method", sourceFile.toString(), SPRING_PREFIXES));
    }

    @Test
    void isAllowedLibraryResolvesWildcardImport(@TempDir Path tempDir) throws Exception {
        Path sourceFile = tempDir.resolve("TestService.java");
        Files.writeString(sourceFile, """
            package com.example;
            import org.springframework.web.bind.annotation.*;
            class TestService {}
            """);
        assertTrue(LibraryTypeResolver.isAllowedLibrary(
            "org.springframework.web.bind.annotation.GetMapping", sourceFile.toString(), SPRING_PREFIXES));
    }

    @Test
    void isAllowedLibraryReturnsFalseForUnknownClassName(@TempDir Path tempDir) throws Exception {
        Path sourceFile = tempDir.resolve("TestService.java");
        Files.writeString(sourceFile, """
            package com.example;
            import java.util.List;
            class TestService {}
            """);
        assertFalse(LibraryTypeResolver.isAllowedLibrary(
            "UnknownClass.method", sourceFile.toString(), SPRING_PREFIXES));
    }

    @Test
    void isAllowedLibraryReturnsFalseWhenNoDotInCall() {
        var cu = StaticJavaParser.parse("class C {}");
        assertFalse(LibraryTypeResolver.isAllowedLibrary("simpleName", "nonexistent.java", SPRING_PREFIXES));
    }

    @Test
    void readFileImportsReturnsEmptyForNullPath() {
        var result = LibraryTypeResolver.readFileImports(null, SPRING_PREFIXES);
        assertEquals(0, result.classNameToFqn().size());
        assertEquals(0, result.wildcardPackages().size());
    }

    @Test
    void readFileImportsReturnsEmptyForNonExistentFile() {
        var result = LibraryTypeResolver.readFileImports("/nonexistent/path/File.java", SPRING_PREFIXES);
        assertEquals(0, result.classNameToFqn().size());
    }

    @Test
    void readFileImportsParsesImports(@TempDir Path tempDir) throws Exception {
        Path sourceFile = tempDir.resolve("Test.java");
        Files.writeString(sourceFile, """
            package com.example;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.*;
            class Test {}
            """);
        var result = LibraryTypeResolver.readFileImports(sourceFile.toString(), SPRING_PREFIXES);
        assertEquals(1, result.classNameToFqn().size());
        assertEquals("org.springframework.web.bind.annotation.GetMapping",
            result.classNameToFqn().get("GetMapping"));
        assertEquals(1, result.wildcardPackages().size());
        assertEquals("org.springframework.web.bind.annotation", result.wildcardPackages().get(0));
    }

    @Test
    void readFileImportsFiltersByFrameworkPrefix(@TempDir Path tempDir) throws Exception {
        Path sourceFile = tempDir.resolve("Test.java");
        Files.writeString(sourceFile, """
            import java.util.List;
            import org.springframework.stereotype.Service;
            class Test {}
            """);
        var result = LibraryTypeResolver.readFileImports(sourceFile.toString(), SPRING_PREFIXES);
        assertEquals(1, result.classNameToFqn().size());
        assertTrue(result.classNameToFqn().containsKey("Service"));
    }

    @Test
    void readFileImportsSkipsStaticImports(@TempDir Path tempDir) throws Exception {
        Path sourceFile = tempDir.resolve("Test.java");
        Files.writeString(sourceFile, """
            import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
            class Test {}
            """);
        var result = LibraryTypeResolver.readFileImports(sourceFile.toString(), SPRING_PREFIXES);
        assertTrue(result.classNameToFqn().isEmpty());
    }
}
