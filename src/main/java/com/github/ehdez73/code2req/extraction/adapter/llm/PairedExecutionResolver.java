package com.github.ehdez73.code2req.extraction.adapter.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
public class PairedExecutionResolver {

    private static final Logger log = LoggerFactory.getLogger(PairedExecutionResolver.class);

    private final TestFileMatcher testFileMatcher;

    public PairedExecutionResolver(TestFileMatcher testFileMatcher) {
        this.testFileMatcher = testFileMatcher;
    }

    public Optional<PairedTestInfo> resolve(String sourceFilePath) {
        Optional<String> testFilePathOpt = testFileMatcher.findTestFilePath(sourceFilePath);

        if (testFilePathOpt.isEmpty()) {
            return Optional.empty();
        }

        String testFilePath = testFilePathOpt.get();
        String testContent;
        try {
            testContent = Files.readString(Path.of(testFilePath));
        } catch (IOException e) {
            log.warn("Test file found but could not be read: {} — {}", testFilePath, e.getMessage());
            return Optional.empty();
        }

        return Optional.of(new PairedTestInfo(testFilePath, testContent));
    }

    public record PairedTestInfo(
        String testFilePath,
        String testContent
    ) {}
}
