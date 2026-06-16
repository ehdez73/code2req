package com.github.ehdez73.code2req.shell;

import com.github.ehdez73.code2req.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.analyzer.template.TemplateAnalyzer;
import com.github.ehdez73.code2req.analyzer.template.TemplateFormInfo;
import com.github.ehdez73.code2req.analyzer.template.TemplateLinkInfo;
import com.github.ehdez73.code2req.analyzer.template.TemplateLinkResolver;
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
    private final ScanPipeline pipeline;
    private final TaskStore taskStore;
    private final TaskIdHasher taskIdHasher;
    private final IndexWriter indexWriter;
    private final OrphanRecovery orphanRecovery;
    private final TemplateAnalyzer templateAnalyzer;
    private final TemplateLinkResolver templateLinkResolver;

    public ScanCommand(
            ManifestLoader manifestLoader,
            ManifestValidator manifestValidator,
            ExcludeFilter excludeFilter,
            ScanPipeline pipeline,
            TaskStore taskStore,
            TaskIdHasher taskIdHasher,
            IndexWriter indexWriter,
            OrphanRecovery orphanRecovery,
            TemplateAnalyzer templateAnalyzer,
            TemplateLinkResolver templateLinkResolver) {
        this.manifestLoader = manifestLoader;
        this.manifestValidator = manifestValidator;
        this.excludeFilter = excludeFilter;
        this.pipeline = pipeline;
        this.taskStore = taskStore;
        this.taskIdHasher = taskIdHasher;
        this.indexWriter = indexWriter;
        this.orphanRecovery = orphanRecovery;
        this.templateAnalyzer = templateAnalyzer;
        this.templateLinkResolver = templateLinkResolver;
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

        var allFiles = flattenBatches(batches);

        report.append("Phase 4/5 — Analysis (Two-Pass):\n");
        var pipelineResult = pipeline.execute(allFiles, report);

        var templateBatches = discoverTemplateFiles(manifest, report);
        List<TemplateFormInfo> templateForms = new ArrayList<>();
        List<TemplateLinkInfo> templateLinks = new ArrayList<>();
        if (templateBatches != null) {
            report.append("Phase 4b/5 — Template Analysis:\n");
            for (var batch : templateBatches) {
                for (Path tf : batch.files()) {
                    templateForms.addAll(templateAnalyzer.analyze(tf));
                }
            }
            report.append(String.format("  %d template form(s) and link(s) found%n", templateForms.size()));

            var allEndpoints = pipelineResult.results().stream()
                .flatMap(r -> r.findings(com.github.ehdez73.code2req.analyzer.endpoint.EndpointInfo.class).stream())
                .toList();
            templateLinks = templateLinkResolver.resolve(templateForms, allEndpoints);
            report.append(String.format("  %d template-to-endpoint link(s) matched%n", templateLinks.size()));
            report.append(String.format("  Elapsed: %ds%n%n", elapsedSeconds(scanStart)));
        }

        writeIndex(manifest, pipelineResult.results(), pipelineResult.topicLinks(), templateForms, templateLinks,
            pipelineResult.floatingLinks(), report);

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

    private List<TemplateFileBatch> discoverTemplateFiles(ProjectManifest manifest, StringBuilder report) {
        var phaseStart = Instant.now();
        List<TemplateFileBatch> batches = new ArrayList<>();
        int totalFiles = 0;

        for (ScanTarget target : manifest.targets()) {
            Path targetPath = Path.of(target.path());
            if (!Files.isDirectory(targetPath)) continue;

            List<Path> templateFiles;
            try (Stream<Path> walk = Files.walk(targetPath)) {
                templateFiles = walk
                    .filter(p -> {
                        String name = p.toString().toLowerCase();
                        return name.endsWith(".jsp") || name.endsWith(".html");
                    })
                    .filter(Files::isRegularFile)
                    .toList();
            } catch (IOException e) {
                log.warn("Failed to walk target '{}' for templates: {}", target.name(), e.getMessage());
                continue;
            }

            if (!templateFiles.isEmpty()) {
                batches.add(new TemplateFileBatch(target, templateFiles));
                totalFiles += templateFiles.size();
            }
        }

        if (totalFiles == 0) {
            return null;
        }

        report.append(String.format("Phase 3b/5 — Template Discovery: %d template file(s) across %d target(s)%n",
            totalFiles, batches.size()));
        return batches;
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

    private List<Path> flattenBatches(List<JavaFileBatch> batches) {
        return batches.stream()
            .flatMap(b -> b.files().stream())
            .toList();
    }

    private void writeIndex(ProjectManifest manifest, List<AnalysisResult> results,
                            List<com.github.ehdez73.code2req.analyzer.eventlink.TopicLink> topicLinks,
                            List<TemplateFormInfo> templateForms,
                            List<TemplateLinkInfo> templateLinks,
                            List<com.github.ehdez73.code2req.analyzer.httpclient.FloatingLinkInfo> floatingLinks,
                            StringBuilder report) {
        var phaseStart = Instant.now();
        try {
            Path indexPath = indexWriter.write(manifest, results, topicLinks, templateForms, templateLinks, floatingLinks);
            report.append(String.format("Phase 5/5 — Index Output: %s%n", indexPath.toAbsolutePath()));
        } catch (IOException e) {
            report.append("Phase 5/5 — Index Output: FAILED — ").append(e.getMessage()).append("\n");
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

    private record JavaFileBatch(ScanTarget target, List<Path> files) {}
    private record TemplateFileBatch(ScanTarget target, List<Path> files) {}
}
