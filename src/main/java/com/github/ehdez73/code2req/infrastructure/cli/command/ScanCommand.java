package com.github.ehdez73.code2req.infrastructure.cli.command;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;
import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisResult;
import com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml.XmlBeanAnalyzer;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.template.TemplateAnalyzer;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.template.TemplateFormInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.template.TemplateLinkInfo;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.template.TemplateLinkResolver;
import com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.WebXmlAnalyzer;
import com.github.ehdez73.code2req.indexing.domain.service.ExcludeFilter;
import com.github.ehdez73.code2req.infrastructure.config.ManifestLoader;
import com.github.ehdez73.code2req.infrastructure.config.ManifestValidator;
import com.github.ehdez73.code2req.common.domain.ProjectManifest;
import com.github.ehdez73.code2req.common.domain.ScanTarget;
import com.github.ehdez73.code2req.common.domain.Task;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.github.ehdez73.code2req.indexing.application.port.output.IndexWriter;
import com.github.ehdez73.code2req.enrichment.domain.service.OrphanRecovery;
import com.github.ehdez73.code2req.indexing.IndexingOrchestrator;
import com.github.ehdez73.code2req.indexing.domain.model.ScanPipelineResult;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FindingType;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskIdHasher;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ShellComponent
public class ScanCommand {

    private static final Logger log = LoggerFactory.getLogger(ScanCommand.class);

    private final ManifestLoader manifestLoader;
    private final ManifestValidator manifestValidator;
    private final ExcludeFilter excludeFilter;
    private final IndexingOrchestrator pipeline;
    private final TaskStore taskStore;
    private final TaskIdHasher taskIdHasher;
    private final IndexWriter indexWriter;
    private final OrphanRecovery orphanRecovery;
    private final TemplateAnalyzer templateAnalyzer;
    private final TemplateLinkResolver templateLinkResolver;
    private final ExecutionFindingStore executionFindingStore;
    private final WebXmlAnalyzer webXmlAnalyzer;
    private final XmlBeanAnalyzer xmlBeanAnalyzer;

    public ScanCommand(
            ManifestLoader manifestLoader,
            ManifestValidator manifestValidator,
            ExcludeFilter excludeFilter,
            IndexingOrchestrator pipeline,
            TaskStore taskStore,
            TaskIdHasher taskIdHasher,
            IndexWriter indexWriter,
            OrphanRecovery orphanRecovery,
            TemplateAnalyzer templateAnalyzer,
            TemplateLinkResolver templateLinkResolver,
            ExecutionFindingStore executionFindingStore,
            WebXmlAnalyzer webXmlAnalyzer,
            XmlBeanAnalyzer xmlBeanAnalyzer) {
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
        this.executionFindingStore = executionFindingStore;
        this.webXmlAnalyzer = webXmlAnalyzer;
        this.xmlBeanAnalyzer = xmlBeanAnalyzer;
    }

    @ShellMethod(key = "scan", value = "Runs the full Phase 1 scan pipeline: manifest, dependencies, analysis, redaction, and index output")
    public String scan(
            @ShellOption(value = "--manifest", defaultValue = "project-manifest.yaml",
                         help = "Path to the project manifest YAML file") String manifestPath,
            @ShellOption(value = "--resume", defaultValue = "false",
                         help = "Resume an interrupted scan, skipping already-completed files") boolean resume) {
        return executeScan(manifestPath, resume);
    }

    public String executeScan(String manifestPath, boolean resume) {
        var report = new StringBuilder(resume ? "=== Resume Pipeline ===\n\n" : "=== Scan Pipeline ===\n\n");
        var scanStart = Instant.now();

        var manifest = loadManifest(Path.of(manifestPath), report);
        if (manifest == null) return report.toString();

        recoverOrphans(report);

        if (resume) {
            cleanFailedTasks(report);
        }

        var batches = discoverFiles(manifest, report);
        if (batches == null) return report.toString();

        var allFiles = flattenBatches(batches);

        if (resume) {
            allFiles = filterCompleted(allFiles, manifest.targets(), report);
        }

        report.append("Phase 4/5 — Analysis (Two-Pass):\n");
        var pipelineResult = pipeline.execute(allFiles, manifest.targets(), report);

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
                .flatMap(r -> r.findings(com.github.ehdez73.code2req.indexing.domain.analyzer.web.endpoint.EndpointInfo.class).stream())
                .toList();
            templateLinks = templateLinkResolver.resolve(templateForms, allEndpoints);
            report.append(String.format("  %d template-to-endpoint link(s) matched%n", templateLinks.size()));

            persistTemplateFindings(pipelineResult, templateForms, templateLinks, manifest.targets());
            report.append(String.format("  Elapsed: %ds%n%n", elapsedSeconds(scanStart)));
        }

        report.append("Phase 4c/5 — web.xml Endpoint Analysis:\n");
        var webXmlResults = analyzeWebXmlFiles(manifest, report, resume);
        List<AnalysisResult> allResults = new ArrayList<>(pipelineResult.results());
        allResults.addAll(webXmlResults);
        report.append(String.format("  Elapsed: %ds%n%n", elapsedSeconds(scanStart)));

        report.append("Phase 4d/5 — Spring XML Config Analysis:\n");
        var springXmlResults = analyzeSpringXmlFiles(manifest, report, resume);
        allResults.addAll(springXmlResults);
        report.append(String.format("  Elapsed: %ds%n%n", elapsedSeconds(scanStart)));

        allResults = dedupResults(allResults);
        allResults = dedupFindings(allResults);

        writeIndex(manifest, allResults, pipelineResult.topicLinks(), templateForms, templateLinks,
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

    private void cleanFailedTasks(StringBuilder report) {
        int deleted = taskStore.deleteByStatus(TaskStatus.FAILED);
        if (deleted > 0) {
            report.append(String.format("Phase 2b/5 — Clean Stale FAILED: removed %d task(s) for re-scan%n", deleted));
        }
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

    private List<AnalysisResult> analyzeWebXmlFiles(ProjectManifest manifest, StringBuilder report, boolean resume) {
        List<AnalysisResult> results = new ArrayList<>();
        int totalFound = 0;
        int totalEndpoints = 0;
        int skipped = 0;
        int analyzed = 0;

        for (ScanTarget target : manifest.targets()) {
            Path targetPath = Path.of(target.path());
            if (!Files.isDirectory(targetPath)) continue;
            List<Path> webXmlFiles;
            try (Stream<Path> walk = Files.walk(targetPath)) {
                webXmlFiles = walk
                    .filter(p -> p.toString().endsWith("web.xml"))
                    .filter(Files::isRegularFile)
                    .filter(p -> !excludeFilter.shouldExclude(targetPath, p, target.excludePatterns()))
                    .toList();
            } catch (IOException e) {
                log.warn("Failed to walk target '{}' for web.xml: {}", target.name(), e.getMessage());
                continue;
            }

            totalFound += webXmlFiles.size();
            for (Path wf : webXmlFiles) {
                if (resume && isAlreadyCompleted(wf, manifest.targets())) {
                    skipped++;
                    continue;
                }
                List<EndpointInfo> endpoints = webXmlAnalyzer.analyze(wf);
                if (!endpoints.isEmpty()) {
                    var result = new AnalysisResult(wf.toString(),
                        new java.util.ArrayList<>(endpoints));
                    results.add(result);
                    totalEndpoints += endpoints.size();
                    persistAnalysisResult(wf, result, target);
                }
                analyzed++;
            }
        }

        report.append(String.format("  %d web.xml file(s) found, %d analyzed, %d endpoint(s) extracted",
            totalFound, analyzed, totalEndpoints));
        if (skipped > 0) {
            report.append(String.format(", %d skipped", skipped));
        }
        report.append("\n");
        return results;
    }

    private List<AnalysisResult> analyzeSpringXmlFiles(ProjectManifest manifest, StringBuilder report, boolean resume) {
        List<AnalysisResult> results = new ArrayList<>();
        int totalFound = 0;
        int totalFindings = 0;
        int skipped = 0;
        int analyzed = 0;

        for (ScanTarget target : manifest.targets()) {
            Path targetPath = Path.of(target.path());
            if (!Files.isDirectory(targetPath)) continue;

            List<Path> xmlFiles;
            try (Stream<Path> walk = Files.walk(targetPath)) {
                xmlFiles = walk
                    .filter(p -> p.toString().endsWith(".xml"))
                    .filter(Files::isRegularFile)
                    .filter(p -> !excludeFilter.shouldExclude(targetPath, p, target.excludePatterns()))
                    .filter(XmlBeanAnalyzer::isSpringXmlConfig)
                    .toList();
            } catch (IOException e) {
                log.warn("Failed to walk target '{}' for Spring XML config: {}", target.name(), e.getMessage());
                continue;
            }

            totalFound += xmlFiles.size();
            for (Path xf : xmlFiles) {
                if (resume && isAlreadyCompleted(xf, manifest.targets())) {
                    skipped++;
                    continue;
                }
                List<AnalysisFinding> findings = xmlBeanAnalyzer.analyze(xf);
                if (!findings.isEmpty()) {
                    var result = new AnalysisResult(xf.toString(), new ArrayList<>(findings));
                    results.add(result);
                    totalFindings += findings.size();
                    persistAnalysisResult(xf, result, target);
                }
                analyzed++;
            }
        }

        report.append(String.format("  %d Spring XML config file(s) found, %d analyzed, %d finding(s)",
            totalFound, analyzed, totalFindings));
        if (skipped > 0) {
            report.append(String.format(", %d skipped", skipped));
        }
        report.append("\n");
        return results;
    }

    private void persistAnalysisResult(Path file, AnalysisResult result, ScanTarget target) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String contentHash = sha256Hex(content);
            String taskId = taskIdHasher.hash(file.toAbsolutePath().normalize().toString(), contentHash, target.name());
            taskStore.save(new Task(taskId, file.toAbsolutePath().normalize().toString(),
                TaskStatus.INDEXED, "xml", contentHash, target.name()));
            executionFindingStore.deleteByTaskId(taskId);
            for (var entry : FindingType.FINDING_TYPE_MAP.entrySet()) {
                var findings = result.findings(entry.getKey());
                if (!findings.isEmpty()) {
                    executionFindingStore.saveAllForTask(taskId, findings, entry.getValue());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to persist findings for {}: {}", file, e.getMessage());
        }
    }

    private static List<AnalysisResult> dedupResults(List<AnalysisResult> results) {
        return results.stream()
            .collect(Collectors.toMap(AnalysisResult::filePath, r -> r, (a, b) -> a))
            .values().stream()
            .toList();
    }

    private static List<AnalysisResult> dedupFindings(List<AnalysisResult> results) {
        Set<AnalysisFinding> seen = new HashSet<>();
        return results.stream()
            .map(r -> {
                var unique = r.findings().stream()
                    .filter(seen::add)
                    .toList();
                return new AnalysisResult(r.filePath(), unique);
            })
            .toList();
    }

    private List<Path> flattenBatches(List<JavaFileBatch> batches) {
        var allFiles = batches.stream()
            .flatMap(b -> b.files().stream())
            .map(p -> p.toAbsolutePath().normalize())
            .toList();
        var distinct = allFiles.stream().distinct().toList();
        int skipped = allFiles.size() - distinct.size();
        if (skipped > 0) {
            log.info("Skipped {} duplicate file(s) found across multiple scan targets", skipped);
        }
        return distinct;
    }

    private void persistTemplateFindings(ScanPipelineResult pipelineResult,
                                          List<TemplateFormInfo> templateForms,
                                          List<TemplateLinkInfo> templateLinks,
                                          List<ScanTarget> targets) {
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (var form : templateForms) {
            try {
                String json = objectMapper.writeValueAsString(form);
                String targetName = targetNameForFile(Path.of(form.templatePath()), targets);
                String taskId = taskIdHasher.hash(form.templatePath(), "template-" + form.linkType(), targetName);
                String findingType = "FORM".equals(form.linkType()) ? FindingType.TEMPLATE_FORM : FindingType.TEMPLATE_LINK;
                executionFindingStore.save(taskId, findingType, json, true);
            } catch (Exception e) {
                log.warn("Failed to persist template form/link: {}", e.getMessage());
            }
        }
        for (var link : templateLinks) {
            try {
                String json = objectMapper.writeValueAsString(link);
                String targetName = targetNameForFile(Path.of(link.templatePath() != null ? link.templatePath() : "unknown"), targets);
                String taskId = taskIdHasher.hash(link.templatePath() != null ? link.templatePath() : "unknown", "template-link", targetName);
                executionFindingStore.save(taskId, FindingType.TEMPLATE_ENDPOINT_LINK, json, true);
            } catch (Exception e) {
                log.warn("Failed to persist template link: {}", e.getMessage());
            }
        }
    }

    private static String targetNameForFile(Path file, List<ScanTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return "";
        }
        Path normalized = file.toAbsolutePath().normalize();
        for (ScanTarget target : targets) {
            if (normalized.startsWith(Path.of(target.path()).normalize())) {
                return target.name();
            }
        }
        return "";
    }

    private void writeIndex(ProjectManifest manifest, List<AnalysisResult> results,
                            List<com.github.ehdez73.code2req.indexing.domain.analyzer.event.link.TopicLink> topicLinks,
                            List<TemplateFormInfo> templateForms,
                            List<TemplateLinkInfo> templateLinks,
                            List<com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.FloatingLinkInfo> floatingLinks,
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
        report.append(String.format("=== %s (%ds) ===%n", report.indexOf("Resume") >= 0 ? "Resume Complete" : "Scan Complete", totalDuration));
        report.append(String.format("  Tasks in store: %d%n", totalTasks));
        report.append("  Zero network calls — Phase 1 offline operation verified\n");
    }

    private List<Path> filterCompleted(List<Path> files, List<ScanTarget> targets, StringBuilder report) {
        var phaseStart = Instant.now();
        int skipped = 0;
        List<Path> pending = new ArrayList<>();

        for (Path file : files) {
            if (isAlreadyCompleted(file, targets)) {
                skipped++;
            } else {
                pending.add(file);
            }
        }

        report.append(String.format("  Skipped (already indexed/enriched): %d, Remaining: %d%n", skipped, pending.size()));
        report.append(String.format("  Elapsed: %ds%n%n", elapsedSeconds(phaseStart)));
        return pending;
    }

    private boolean isAlreadyCompleted(Path file, List<ScanTarget> targets) {
        String fp = file.toString();
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String contentHash = sha256Hex(content);
            String targetName = targetNameForFile(file, targets);
            String taskId = taskIdHasher.hash(fp, contentHash, targetName);
            Optional<Task> existing = taskStore.findById(taskId);
            return existing.isPresent() && switch (existing.get().status()) {
                case INDEXED, ENRICH_PENDING, ENRICHING, ENRICHED, ENRICH_FAILED -> true;
                default -> false;
            };
        } catch (IOException e) {
            log.warn("Failed to check completion for {}: {}", fp, e.getMessage());
            return false;
        }
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

    private record JavaFileBatch(ScanTarget target, List<Path> files) {}
    private record TemplateFileBatch(ScanTarget target, List<Path> files) {}
}
