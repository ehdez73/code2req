package com.github.ehdez73.code2req.common.util;

import com.github.javaparser.ast.CompilationUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LibraryTypeResolver {

    private LibraryTypeResolver() {}

    public static boolean isAllowedLibrary(String shortName, CompilationUnit cu,
                                            List<String> frameworkPrefixes) {
        return cu.getImports().stream()
            .anyMatch(imp -> {
                String importName = imp.getNameAsString();
                if (importName.endsWith("." + shortName)
                    && frameworkPrefixes.stream().anyMatch(importName::startsWith)) {
                    return true;
                }
                if (importName.endsWith(".*") && !shortName.contains(".")) {
                    String pkg = importName.substring(0, importName.length() - 2);
                    return frameworkPrefixes.stream().anyMatch(pkg::startsWith);
                }
                return false;
            });
    }

    public static boolean isAllowedLibrary(String unresolvedCall, String sourceFilePath,
                                            List<String> frameworkPrefixes) {
        if (frameworkPrefixes.stream().anyMatch(unresolvedCall::startsWith)) {
            return true;
        }
        String className = extractClassName(unresolvedCall);
        if (className == null) {
            return false;
        }
        String rest = unresolvedCall.substring(className.length() + 1);
        FileImports fileImports = readFileImports(sourceFilePath, frameworkPrefixes);
        String fqn = fileImports.classNameToFqn().get(className);
        if (fqn != null) {
            String reconstructed = fqn + "." + rest;
            return frameworkPrefixes.stream().anyMatch(reconstructed::startsWith);
        }
        for (String pkg : fileImports.wildcardPackages()) {
            String reconstructed = pkg + "." + unresolvedCall;
            if (frameworkPrefixes.stream().anyMatch(reconstructed::startsWith)) {
                return true;
            }
        }
        return false;
    }

    private static String extractClassName(String unresolvedCall) {
        int dot = unresolvedCall.lastIndexOf('.');
        if (dot < 0) return null;
        String beforeDot = unresolvedCall.substring(0, dot);
        int lastDot = beforeDot.lastIndexOf('.');
        return lastDot >= 0 ? beforeDot.substring(lastDot + 1) : beforeDot;
    }

    public static FileImports readFileImports(String filePath, List<String> frameworkPrefixes) {
        if (filePath == null) return FileImports.EMPTY;
        try (var lines = Files.lines(Path.of(filePath))) {
            Map<String, String> classNameToFqn = new HashMap<>();
            List<String> wildcardPackages = new ArrayList<>();
            lines.filter(line -> line.trim().startsWith("import "))
                .map(line -> line.trim().substring(7).replace(";", "").trim())
                .filter(imp -> !imp.startsWith("static"))
                .filter(imp -> frameworkPrefixes.stream().anyMatch(imp::startsWith))
                .forEach(imp -> {
                    if (imp.endsWith(".*")) {
                        wildcardPackages.add(imp.substring(0, imp.length() - 2));
                    } else {
                        String simpleName = imp.substring(imp.lastIndexOf('.') + 1);
                        classNameToFqn.put(simpleName, imp);
                    }
                });
            return new FileImports(classNameToFqn, wildcardPackages);
        } catch (IOException e) {
            return FileImports.EMPTY;
        }
    }

    public record FileImports(Map<String, String> classNameToFqn, List<String> wildcardPackages) {
        public static final FileImports EMPTY = new FileImports(Map.of(), List.of());
    }
}
