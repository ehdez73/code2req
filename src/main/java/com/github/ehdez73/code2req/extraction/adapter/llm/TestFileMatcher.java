package com.github.ehdez73.code2req.extraction.adapter.llm;

import com.github.ehdez73.code2req.indexing.domain.analyzer.declaration.TestImportIndex;
import com.github.ehdez73.code2req.indexing.domain.model.IndexingConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Component
public class TestFileMatcher {

    private final TestImportIndex testImportIndex;
    private final List<String> testSuffixes;
    private final List<String> testPrefixes;

    public TestFileMatcher(IndexingConfig indexingConfig) {
        this(indexingConfig, null);
    }

    @Autowired
    public TestFileMatcher(IndexingConfig indexingConfig, TestImportIndex testImportIndex) {
        this.testImportIndex = testImportIndex;
        this.testSuffixes = indexingConfig.resolvedTestSuffixes();
        this.testPrefixes = indexingConfig.resolvedTestPrefixes();
    }

    private boolean isTestFileName(String baseName) {
        if (testSuffixes.stream().anyMatch(baseName::endsWith)) return true;
        return testPrefixes.stream().anyMatch(baseName::startsWith);
    }

    public Optional<String> findTestFilePath(String sourceFilePath) {
        if (sourceFilePath == null || sourceFilePath.isBlank()) {
            return Optional.empty();
        }

        Path path = Paths.get(sourceFilePath);
        String fileName = path.getFileName().toString();

        if (!fileName.endsWith(".java")) {
            return Optional.empty();
        }

        String baseName = fileName.substring(0, fileName.length() - ".java".length());
        if (isTestFileName(baseName)) {
            return Optional.empty();
        }

        if (testImportIndex != null) {
            Optional<String> importMatch = testImportIndex.findTestFileBySourcePath(sourceFilePath);
            if (importMatch.isPresent()) {
                return importMatch;
            }
        }

        Path parent = path.getParent();
        if (parent == null) {
            return Optional.empty();
        }

        for (String suffix : testSuffixes) {
            String testFileName = baseName + suffix + ".java";
            Path testPath = parent.resolve(testFileName);
            if (Files.exists(testPath)) {
                return Optional.of(testPath.toAbsolutePath().normalize().toString());
            }
        }

        for (String prefix : testPrefixes) {
            String testFileName = prefix + baseName + ".java";
            Path testPath = parent.resolve(testFileName);
            if (Files.exists(testPath)) {
                return Optional.of(testPath.toAbsolutePath().normalize().toString());
            }
        }

        if (sourceFilePath.contains("src/main/java")) {
            String[] testDirVariants = {"src/test/java", "src/test/groovy"};
            for (String variant : testDirVariants) {
                String testDirPath = sourceFilePath.replace("src/main/java", variant);
                Path basePath = Paths.get(testDirPath);
                if (Files.exists(basePath)) {
                    return Optional.of(basePath.toAbsolutePath().normalize().toString());
                }
                for (String suffix : testSuffixes) {
                    String suffixed = basePath.toString().replace(".java", suffix + ".java");
                    Path suffixedPath = Paths.get(suffixed);
                    if (Files.exists(suffixedPath)) {
                        return Optional.of(suffixedPath.toAbsolutePath().normalize().toString());
                    }
                }
                for (String prefix : testPrefixes) {
                    Path testDir = basePath.getParent();
                    if (testDir != null) {
                        Path prefixedPath = testDir.resolve(prefix + basePath.getFileName().toString());
                        if (Files.exists(prefixedPath)) {
                            return Optional.of(prefixedPath.toAbsolutePath().normalize().toString());
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    boolean hasTestByImport(String sourceFilePath) {
        return testImportIndex != null && testImportIndex.hasTests(sourceFilePath);
    }
}
