package com.github.ehdez73.code2req.shell;

import com.github.ehdez73.code2req.config.ManifestLoader;
import com.github.ehdez73.code2req.model.OutputConfig;
import com.github.ehdez73.code2req.store.ExecutionFindingStore;
import com.github.ehdez73.code2req.store.FloatingLinkStore;
import com.github.ehdez73.code2req.store.MetricsStore;
import com.github.ehdez73.code2req.store.TaskStore;
import com.github.ehdez73.code2req.store.TopicLinkStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@ShellComponent
public class CleanCommand {

    private static final Logger log = LoggerFactory.getLogger(CleanCommand.class);

    private final TaskStore taskStore;
    private final ExecutionFindingStore executionFindingStore;
    private final TopicLinkStore topicLinkStore;
    private final FloatingLinkStore floatingLinkStore;
    private final MetricsStore metricsStore;
    private final OutputConfig outputConfig;

    public CleanCommand(TaskStore taskStore, ExecutionFindingStore executionFindingStore,
                        TopicLinkStore topicLinkStore, FloatingLinkStore floatingLinkStore,
                        MetricsStore metricsStore, OutputConfig outputConfig) {
        this.taskStore = taskStore;
        this.executionFindingStore = executionFindingStore;
        this.topicLinkStore = topicLinkStore;
        this.floatingLinkStore = floatingLinkStore;
        this.metricsStore = metricsStore;
        this.outputConfig = outputConfig;
    }

    @ShellMethod(key = "clean", value = "Deletes all scanned data: SQLite task store and output JSON files")
    public String clean(
            @ShellOption(value = "--manifest", defaultValue = "project-manifest.yaml",
                         help = "Path to project manifest YAML (optional — uses default output paths)") String manifestPath) {

        var sb = new StringBuilder("=== clean ===\n\n");

        int tasksBefore = taskStore.count();
        int findingsBefore = executionFindingStore.count();
        int topicLinksBefore = topicLinkStore.count();
        int floatingLinksBefore = floatingLinkStore.count();
        int metricsBefore = metricsStore.count();

        log.info("Cleaning all tables: {} tasks, {} findings, {} topic links, {} floating links, {} metrics",
            tasksBefore, findingsBefore, topicLinksBefore, floatingLinksBefore, metricsBefore);

        executionFindingStore.deleteAll();
        topicLinkStore.deleteAll();
        floatingLinkStore.deleteAll();
        metricsStore.deleteAll();
        taskStore.deleteAll();

        sb.append(String.format("  Rows removed: %d tasks, %d findings, %d topic links, %d floating links, %d metrics%n",
            tasksBefore, findingsBefore, topicLinksBefore, floatingLinksBefore, metricsBefore));

        Path specDir = Path.of(outputConfig.specDir());
        Path indexPath = specDir.resolve(outputConfig.indexFile());

        boolean indexDeleted = false;
        try {
            indexDeleted = Files.deleteIfExists(indexPath);
        } catch (IOException e) {
            log.warn("Failed to delete index file {}: {}", indexPath, e.getMessage());
        }

        if (indexDeleted) {
            sb.append(String.format("  Index file deleted: %s%n", indexPath));
        } else if (Files.exists(specDir)) {
            sb.append(String.format("  Index file not found: %s%n", indexPath));
        }

        boolean dirRemoved = false;
        if (Files.isDirectory(specDir)) {
            try (var files = Files.list(specDir)) {
                if (files.findAny().isEmpty()) {
                    Files.delete(specDir);
                    dirRemoved = true;
                }
            } catch (IOException e) {
                log.warn("Failed to inspect or remove spec dir {}: {}", specDir, e.getMessage());
            }
        }

        if (dirRemoved) {
            sb.append(String.format("  Output directory removed: %s%n", specDir.toAbsolutePath()));
        }

        sb.append("\nClean complete.");
        return sb.toString();
    }
}
