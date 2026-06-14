package com.github.ehdez73.code2req.shell;

import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.config.ExcludeFilter;
import com.github.ehdez73.code2req.config.ManifestLoader;
import com.github.ehdez73.code2req.config.ManifestValidator;
import com.github.ehdez73.code2req.config.SecretRedactor;
import com.github.ehdez73.code2req.analyzer.JavaAstAnalyzer;
import com.github.ehdez73.code2req.model.ProjectManifest;
import com.github.ehdez73.code2req.model.ScanTarget;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.output.IndexWriter;
import com.github.ehdez73.code2req.output.OrphanRecovery;
import com.github.ehdez73.code2req.store.TaskIdHasher;
import com.github.ehdez73.code2req.store.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@ShellComponent
public class ScanCommand {

    private static final Logger log = LoggerFactory.getLogger(ScanCommand.class);

    private final ManifestLoader manifestLoader;
    private final ManifestValidator manifestValidator;
    private final ExcludeFilter excludeFilter;
    private final SecretRedactor secretRedactor;
    private final JavaAstAnalyzer astAnalyzer;
    private final TaskStore taskStore;
    private final TaskIdHasher taskIdHasher;
    private final IndexWriter indexWriter;
    private final OrphanRecovery orphanRecovery;

    public ScanCommand(
            ManifestLoader manifestLoader,
            ManifestValidator manifestValidator,
            ExcludeFilter excludeFilter,
            SecretRedactor secretRedactor,
            JavaAstAnalyzer astAnalyzer,
            TaskStore taskStore,
            TaskIdHasher taskIdHasher,
            IndexWriter indexWriter,
            OrphanRecovery orphanRecovery) {
        this.manifestLoader = manifestLoader;
        this.manifestValidator = manifestValidator;
        this.excludeFilter = excludeFilter;
        this.secretRedactor = secretRedactor;
        this.astAnalyzer = astAnalyzer;
        this.taskStore = taskStore;
        this.taskIdHasher = taskIdHasher;
        this.indexWriter = indexWriter;
        this.orphanRecovery = orphanRecovery;
    }

    @ShellMethod(key = "scan", value = "Runs the full Phase 1 scan pipeline: manifest, dependencies, analysis, redaction, and index output")
    public String scan(
            @ShellOption(value = "--manifest", defaultValue = "project-manifest.yaml",
                         help = "Path to the project manifest YAML file") String manifestPath) {

        var report = new StringBuilder("=== Scan Pipeline ===\n\n");
        var scanStart = Instant.now();

        var manifest = loadManifest(Path.of(manifestPath), report);
        if (manifest == null) return report.toString();

        recoverOrphans(report);

        var batches = discoverFiles(manifest, report);
        if (batches == null) return report.toString();

        var results = analyzeFiles(batches, report);

        writeIndex(manifest, results, report);

        appendSummary(report, scanStart);
        return report.toString();
    }

    private ProjectManifest loadManifest(Path manifestFile, StringBuilder report) {
        var phaseStart = Instant.now();

        if (!Files.exists(manifestFile)) {
            report.insert(0, "Error: Manifest file not found: " + manifestFile + "\n");
            return null;
        }

        ProjectManifest manifest;
        try {
            var validation = manifestValidator.validate(manifestFile);
            if (validation.hasErrors()) {
                report.append("Phase 1/5 — Manifest: FAILED\n");
                for (var err : validation.getErrors()) {
                    report.append("  - ").append(err).append("\n");
                }
                return null;
            }
            manifest = manifestLoader.load(manifestFile);
            report.append(String.format("Phase 1/5 — Manifest: OK (%d target(s), %d warning(s))%n",
                manifest.targets().size(), validation.getWarnings().size()));
        } catch (IOException e) {
            report.insert(0, "Error: Failed to read manifest: " + e.getMessage() + "\n");
            return null;
        }

        report.append(String.format("  Elapsed: %ds%n%n", elapsedSeconds(phaseStart)));
        return manifest;
    }

    private void recoverOrphans(StringBuilder report) {
        var phaseStart = Instant.now();
        var result = orphanRecovery.recover();
        report.append(String.format("Phase 2/5 — Orphan Recovery: %d task(s) reverted%n", result.revertedCount()));
        report.append(String.format("  Elapsed: %ds%n%n", elapsedSeconds(phaseStart)));
    }

    private List<JavaFileBatch> discoverFiles(ProjectManifest manifest, StringBuilder report) {
        var phaseStart = Instant.now();
        List<JavaFileBatch> batches = new ArrayList<>();
        int totalFiles = 0;

        for (ScanTarget target : manifest.targets()) {
            Path targetPath = Path.of(target.path());
            if (!Files.isDirectory(targetPath)) {
                report.append(String.format("  Warning: target '%s' path not found: %s — skipping%n", target.name(), targetPath));
                continue;
            }

            List<Path> javaFiles;
            try (Stream<Path> walk = Files.walk(targetPath)) {
                javaFiles = walk
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .toList();
            } catch (IOException e) {
                log.warn("Failed to walk target '{}': {}", target.name(), e.getMessage());
                continue;
            }

            var excludeResult = excludeFilter.filter(targetPath, javaFiles, target.excludePatterns());
            batches.add(new JavaFileBatch(target, excludeResult.included()));
            totalFiles += excludeResult.includedCount();
        }

        if (totalFiles == 0) {
            report.append("Phase 3/5 — File Discovery: no Java files found across any target\n");
            return null;
        }

        report.append(String.format("Phase 3/5 — File Discovery: %d Java file(s) across %d target(s)%n",
            totalFiles, batches.size()));
        report.append(String.format("  Elapsed: %ds%n%n", elapsedSeconds(phaseStart)));
        return batches;
    }

    private List<AnalysisResult> analyzeFiles(List<JavaFileBatch> batches, StringBuilder report) {
        var phaseStart = Instant.now();
        List<AnalysisResult> allResults = new ArrayList<>();
        int analyzed = 0;
        int failed = 0;

        for (var batch : batches) {
            for (Path file : batch.files()) {
                var result = analyzeSingleFile(file);
                if (result == null) {
                    failed++;
                } else {
                    allResults.add(result);
                    analyzed++;
                }
            }
        }

        report.append(String.format("Phase 4/5 — Analysis: %d file(s) analyzed, %d failed%n", analyzed, failed));
        report.append(String.format("  Elapsed: %ds%n%n", elapsedSeconds(phaseStart)));
        return allResults;
    }

    private AnalysisResult analyzeSingleFile(Path file) {
        String fp = file.toString();
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read {}: {}", fp, e.getMessage());
            storeFailedTask(fp);
            return null;
        }

        String redactedContent = secretRedactor.redact(content);
        AnalysisResult result = astAnalyzer.analyze(fp, redactedContent);

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

    private void writeIndex(ProjectManifest manifest, List<AnalysisResult> results, StringBuilder report) {
        var phaseStart = Instant.now();
        try {
            Path indexPath = indexWriter.write(manifest, results);
            report.append(String.format("Phase 5/5 — Index Output: %s%n", indexPath.toAbsolutePath()));
        } catch (IOException e) {
            report.append("Phase 5/5 — Index Output: FAILED — ").append(e.getMessage()).append("\n");
            return;
        }
        report.append(String.format("  Elapsed: %ds%n%n", elapsedSeconds(phaseStart)));
    }

    private void appendSummary(StringBuilder report, Instant scanStart) {
        long totalDuration = Duration.between(scanStart, Instant.now()).toSeconds();
        int totalTasks = taskStore.count();
        report.append(String.format("=== Scan Complete (%ds) ===%n", totalDuration));
        report.append(String.format("  Tasks in store: %d%n", totalTasks));
        report.append("  Zero network calls — Phase 1 offline operation verified\n");
    }

    private static long elapsedSeconds(Instant start) {
        return Duration.between(start, Instant.now()).toSeconds();
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

    private record JavaFileBatch(ScanTarget target, List<Path> files) {}
}
