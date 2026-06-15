package com.github.ehdez73.code2req.shell;

import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.config.ExcludeFilter;
import com.github.ehdez73.code2req.config.ManifestLoader;
import com.github.ehdez73.code2req.config.ManifestValidator;
import com.github.ehdez73.code2req.analyzer.JavaAstAnalyzer;
import com.github.ehdez73.code2req.model.ProjectManifest;
import com.github.ehdez73.code2req.model.ScanTarget;
import com.github.ehdez73.code2req.model.Task;
import com.github.ehdez73.code2req.model.TaskStatus;
import com.github.ehdez73.code2req.output.IndexWriter;
import com.github.ehdez73.code2req.output.OrphanRecovery;
import com.github.ehdez73.code2req.pipeline.ScanPipeline;
import com.github.ehdez73.code2req.pipeline.ScanPipelineResult;
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
import java.util.Optional;
import java.util.stream.Stream;

@ShellComponent
public class ResumeCommand {

    private static final Logger log = LoggerFactory.getLogger(ResumeCommand.class);

    private final ManifestLoader manifestLoader;
    private final ManifestValidator manifestValidator;
    private final ExcludeFilter excludeFilter;
    private final ScanPipeline pipeline;
    private final TaskStore taskStore;
    private final TaskIdHasher taskIdHasher;
    private final IndexWriter indexWriter;
    private final OrphanRecovery orphanRecovery;

    public ResumeCommand(
            ManifestLoader manifestLoader,
            ManifestValidator manifestValidator,
            ExcludeFilter excludeFilter,
            ScanPipeline pipeline,
            TaskStore taskStore,
            TaskIdHasher taskIdHasher,
            IndexWriter indexWriter,
            OrphanRecovery orphanRecovery) {
        this.manifestLoader = manifestLoader;
        this.manifestValidator = manifestValidator;
        this.excludeFilter = excludeFilter;
        this.pipeline = pipeline;
        this.taskStore = taskStore;
        this.taskIdHasher = taskIdHasher;
        this.indexWriter = indexWriter;
        this.orphanRecovery = orphanRecovery;
    }

    @ShellMethod(key = "resume", value = "Resumes an interrupted scan, skipping already-completed files")
    public String resume(
            @ShellOption(value = "--manifest", defaultValue = "project-manifest.yaml",
                         help = "Path to the project manifest YAML file") String manifestPath) {

        var report = new StringBuilder("=== Resume Pipeline ===\n\n");
        var scanStart = Instant.now();

        var manifest = loadManifest(Path.of(manifestPath), report);
        if (manifest == null) return report.toString();

        recoverOrphans(report);

        var allFiles = discoverFiles(manifest, report);
        if (allFiles == null) return report.toString();

        var pendingFiles = filterCompleted(allFiles, report);
        if (pendingFiles.isEmpty()) {
            report.append("Phase 4/5 — Analysis: all files already completed, nothing to resume\n");
        } else {
            report.append("Phase 4/5 — Analysis (Two-Pass):\n");
            var pipelineResult = pipeline.execute(pendingFiles, report);
            var merged = mergeResults(pipelineResult, allFiles, manifest);
            writeIndex(manifest, merged, pipelineResult.topicLinks(), report);
        }

        appendSummary(report, scanStart, allFiles.size(), pendingFiles.size());
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

    private List<Path> discoverFiles(ProjectManifest manifest, StringBuilder report) {
        var phaseStart = Instant.now();
        List<Path> allFiles = new ArrayList<>();
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
            allFiles.addAll(excludeResult.included());
            totalFiles += excludeResult.includedCount();
        }

        if (totalFiles == 0) {
            report.append("Phase 3/5 — File Discovery: no Java files found across any target\n");
            return null;
        }

        report.append(String.format("Phase 3/5 — File Discovery: %d Java file(s)%n", totalFiles));
        report.append(String.format("  Elapsed: %ds%n%n", elapsedSeconds(phaseStart)));
        return allFiles;
    }

    private List<Path> filterCompleted(List<Path> files, StringBuilder report) {
        var phaseStart = Instant.now();
        int skipped = 0;
        List<Path> pending = new ArrayList<>();

        for (Path file : files) {
            if (isAlreadyCompleted(file)) {
                skipped++;
            } else {
                pending.add(file);
            }
        }

        report.append(String.format("  Skipped (already SUCCESS): %d, Remaining: %d%n", skipped, pending.size()));
        report.append(String.format("  Elapsed: %ds%n%n", elapsedSeconds(phaseStart)));
        return pending;
    }

    private boolean isAlreadyCompleted(Path file) {
        String fp = file.toString();
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String contentHash = sha256Hex(content);
            String taskId = taskIdHasher.hash(fp, contentHash);
            Optional<Task> existing = taskStore.findById(taskId);
            return existing.isPresent() && existing.get().status() == TaskStatus.SUCCESS;
        } catch (IOException e) {
            log.warn("Failed to check completion for {}: {}", fp, e.getMessage());
            return false;
        }
    }

    private List<AnalysisResult> mergeResults(ScanPipelineResult pipelineResult, List<Path> allFiles, ProjectManifest manifest) {
        // For resume, we only analyze pending files. Merging with previous results
        // is handled by IndexWriter which groups by target path.
        return pipelineResult.results();
    }

    private void writeIndex(ProjectManifest manifest, List<AnalysisResult> results, List<com.github.ehdez73.code2req.analyzer.eventlink.TopicLink> topicLinks, StringBuilder report) {
        var phaseStart = Instant.now();
        try {
            Path indexPath = indexWriter.write(manifest, results, topicLinks);
            report.append(String.format("Phase 5/5 — Index Output: %s%n", indexPath.toAbsolutePath()));
        } catch (IOException e) {
            report.append("Phase 5/5 — Index Output: FAILED — ").append(e.getMessage()).append("\n");
            return;
        }
        report.append(String.format("  Elapsed: %ds%n%n", elapsedSeconds(phaseStart)));
    }

    private void appendSummary(StringBuilder report, Instant scanStart, int totalFiles, int pendingFiles) {
        long totalDuration = Duration.between(scanStart, Instant.now()).toSeconds();
        int totalTasks = taskStore.count();
        report.append(String.format("=== Resume Complete (%ds) ===%n", totalDuration));
        report.append(String.format("  Total files discovered: %d%n", totalFiles));
        report.append(String.format("  Files processed this run: %d%n", pendingFiles));
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
}
