package com.github.ehdez73.code2req.infrastructure.cli.command;

import com.github.ehdez73.code2req.infrastructure.snapshot.SnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;
import java.util.Map;

@ShellComponent
public class SnapshotCommand {

    private static final Logger log = LoggerFactory.getLogger(SnapshotCommand.class);

    private final SnapshotService snapshotService;

    public SnapshotCommand(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @ShellMethod(key = "snapshot create", value = "Create a point-in-time snapshot of local state (SQLite DB + JSON index)")
    public String snapshot(
            @ShellOption(value = "--name", defaultValue = ShellOption.NULL,
                         help = "Snapshot name (defaults to auto-generated timestamp)") String name) {

        log.info("Creating snapshot{}", name != null ? " '" + name + "'" : "");

        try {
            String effectiveName = snapshotService.createSnapshot(name);
            return "Snapshot saved: snapshots/" + effectiveName + "/";
        } catch (Exception e) {
            log.error("Snapshot failed", e);
            return "Error: " + e.getMessage();
        }
    }

    @ShellMethod(key = "snapshot list", value = "List available snapshots with name, date, and file sizes")
    public String snapshotList() {
        var sb = new StringBuilder("=== Snapshots ===\n\n");

        List<Map<String, Object>> snapshots = snapshotService.listSnapshots();

        if (snapshots.isEmpty()) {
            return "No snapshots found. Run 'snapshot create --name <label>' to create one.";
        }

        for (var snap : snapshots) {
            sb.append("  ").append(snap.get("name")).append("\n");
            sb.append("    Date: ").append(snap.get("date")).append("\n");

            @SuppressWarnings("unchecked")
            Map<String, Object> files = (Map<String, Object>) snap.get("files");
            if (files != null && !files.isEmpty()) {
                sb.append("    Files:\n");
                for (var entry : files.entrySet()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> finfo = (Map<String, Object>) entry.getValue();
                    long size = finfo.containsKey("size") ? ((Number) finfo.get("size")).longValue() : 0;
                    sb.append("      - ").append(entry.getKey())
                      .append(" (").append(formatSize(size)).append(")\n");
                }
            }

            if (snap.containsKey("error")) {
                sb.append("    Error: ").append(snap.get("error")).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    @ShellMethod(key = "snapshot restore", value = "Restore local state (SQLite DB + JSON index) from a named snapshot")
    public String restore(
            @ShellOption(value = "--name", help = "Snapshot name to restore from") String name) {

        log.info("Restoring from snapshot '{}'", name);

        try {
            snapshotService.restore(name);
            return "Restored from snapshot '" + name + "'.\n"
                 + "Connection pool has been refreshed with restored data.\n"
                 + "Run 'status' to verify the restored state.";
        } catch (Exception e) {
            log.error("Restore failed", e);
            return "Error: " + e.getMessage();
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
