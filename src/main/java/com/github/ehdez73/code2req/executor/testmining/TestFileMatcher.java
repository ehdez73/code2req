package com.github.ehdez73.code2req.executor.testmining;

import com.github.ehdez73.code2req.model.ExecutionConfig;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Component
public class TestFileMatcher {

    private final List<String> testSuffixes;

    public TestFileMatcher(ExecutionConfig executionConfig) {
        this.testSuffixes = executionConfig.resolvedTestSuffixes();
    }

    private boolean isTestFileName(String baseName) {
        return testSuffixes.stream().anyMatch(baseName::endsWith);
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
            }
        }

        return Optional.empty();
    }
}
