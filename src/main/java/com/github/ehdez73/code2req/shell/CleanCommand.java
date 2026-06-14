package com.github.ehdez73.code2req.shell;

import com.github.ehdez73.code2req.config.ManifestLoader;
import com.github.ehdez73.code2req.model.OutputConfig;
import com.github.ehdez73.code2req.model.ProjectManifest;
import com.github.ehdez73.code2req.store.TaskStore;
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
    private final ManifestLoader manifestLoader;

    public CleanCommand(TaskStore taskStore, ManifestLoader manifestLoader) {
        this.taskStore = taskStore;
        this.manifestLoader = manifestLoader;
    }

    @ShellMethod(key = "clean", value = "Deletes all scanned data: SQLite task store and output JSON files")
    public String clean(
            @ShellOption(value = "--manifest", defaultValue = ShellOption.NULL,
                         help = "Path to project manifest YAML (optional — uses default output paths)") String manifestPath) {

        var sb = new StringBuilder("=== clean ===\n\n");

        int tasksBefore = taskStore.count();
        log.info("Cleaning all tasks from store ({} tasks)", tasksBefore);
        taskStore.deleteAll();
        sb.append(String.format("  Tasks removed: %d%n", tasksBefore));

        OutputConfig config = resolveOutputConfig(manifestPath, sb);
        Path specDir = Path.of(config.specDir());
        Path indexPath = specDir.resolve(config.indexFile());

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

    private OutputConfig resolveOutputConfig(String manifestPath, StringBuilder sb) {
        if (manifestPath != null) {
            Path path = Path.of(manifestPath);
            if (Files.exists(path)) {
                try {
                    ProjectManifest manifest = manifestLoader.load(path);
                    OutputConfig config = manifest.outputConfig();
                    if (config != null) {
                        sb.append(String.format("  Using output paths from manifest: spec-dir=%s, index-file=%s%n",
                            config.specDir(), config.indexFile()));
                        return config;
                    }
                } catch (IOException e) {
                    log.warn("Failed to load manifest '{}': {}", manifestPath, e.getMessage());
                }
            }
            sb.append("  Manifest not found or invalid — using default output paths\n");
        }
        return OutputConfig.defaultConfig();
    }
}
