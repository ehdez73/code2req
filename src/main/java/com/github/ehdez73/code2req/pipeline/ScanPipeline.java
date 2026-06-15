package com.github.ehdez73.code2req.pipeline;

import com.github.ehdez73.code2req.analyzer.AnalysisContext;
import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.analyzer.JavaAstAnalyzer;
import com.github.ehdez73.code2req.analyzer.declaration.GlobalDeclarationRegistry;
import com.github.ehdez73.code2req.analyzer.declaration.Pass1DeclarationCollector;
import com.github.ehdez73.code2req.config.SecretRedactor;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.store.TaskIdHasher;
import com.github.ehdez73.code2req.store.TaskStore;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ScanPipeline {

    private static final Logger log = LoggerFactory.getLogger(ScanPipeline.class);

    private final Pass1DeclarationCollector pass1Collector;
    private final JavaAstAnalyzer astAnalyzer;
    private final SecretRedactor secretRedactor;
    private final TaskStore taskStore;
    private final TaskIdHasher taskIdHasher;

    public ScanPipeline(
            Pass1DeclarationCollector pass1Collector,
            JavaAstAnalyzer astAnalyzer,
            SecretRedactor secretRedactor,
            TaskStore taskStore,
            TaskIdHasher taskIdHasher) {
        this.pass1Collector = pass1Collector;
        this.astAnalyzer = astAnalyzer;
        this.secretRedactor = secretRedactor;
        this.taskStore = taskStore;
        this.taskIdHasher = taskIdHasher;
    }

    public ScanPipelineResult execute(List<Path> files, StringBuilder report) {
        var phaseStart = Instant.now();
        var registry = new GlobalDeclarationRegistry();

        int pass1Failed = runPass1(files, registry, report);
        int pass2Analyzed = 0;
        int pass2Failed = 0;
        List<AnalysisResult> allResults = new ArrayList<>();

        registry.freeze();

        for (Path file : files) {
            var result = analyzeSingleFile(file, registry);
            if (result == null) {
                pass2Failed++;
            } else {
                allResults.add(result);
                pass2Analyzed++;
            }
        }

        report.append(String.format(
            "  Pass 1 (Declaration Collection): %d file(s), %d failed%n", files.size(), pass1Failed));
        report.append(String.format(
            "  Pass 2 (Full Analysis): %d file(s) analyzed, %d failed%n", pass2Analyzed, pass2Failed));
        report.append(String.format("  Elapsed: %ds%n%n", elapsedSeconds(phaseStart)));

        return new ScanPipelineResult(allResults, registry, pass2Analyzed, pass2Failed);
    }

    private int runPass1(List<Path> files, GlobalDeclarationRegistry registry, StringBuilder report) {
        int failed = 0;
        for (Path file : files) {
            String fp = file.toString();
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                String redacted = secretRedactor.redact(content);
                CompilationUnit cu = StaticJavaParser.parse(redacted);
                pass1Collector.collect(cu, registry, fp);
            } catch (Exception e) {
                log.warn("Pass 1 failed for {}: {}", fp, e.getMessage());
                failed++;
            }
        }
        return failed;
    }

    private AnalysisResult analyzeSingleFile(Path file, GlobalDeclarationRegistry registry) {
        String fp = file.toString();
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to read {}: {}", fp, e.getMessage());
            storeFailedTask(fp);
            return null;
        }

        String redactedContent = secretRedactor.redact(content);
        AnalysisContext context = new AnalysisContext(fp, "", registry);
        AnalysisResult result = astAnalyzer.analyze(fp, redactedContent, context);

        String contentHash = sha256Hex(content);
        String taskId = taskIdHasher.hash(fp, contentHash);
        taskStore.save(new Task(taskId, fp, TaskStatus.SUCCESS, "java", contentHash));

        return result;
    }

    private void storeFailedTask(String filePath) {
        var failedTask = new Task(
            taskIdHasher.hash(filePath, "unreadable"),
            filePath, TaskStatus.FAILED, "java", "unreadable");
        taskStore.save(failedTask);
    }

    private static String sha256Hex(String input) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static long elapsedSeconds(Instant start) {
        return Duration.between(start, Instant.now()).toSeconds();
    }
}
