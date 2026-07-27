package com.github.ehdez73.code2req.infrastructure.cli.support;

import com.github.ehdez73.code2req.common.domain.Metric;
import com.github.ehdez73.code2req.common.domain.TaskStatus;
import com.github.ehdez73.code2req.infrastructure.persistence.MetricsStore;
import com.github.ehdez73.code2req.infrastructure.persistence.TaskStore;
import org.springframework.stereotype.Service;

@Service
public class SuggestionService {

    private static final String HEADER = "\n=== Suggested Next ===\n";

    private final TaskStore taskStore;
    private final MetricsStore metricsStore;

    public SuggestionService(TaskStore taskStore, MetricsStore metricsStore) {
        this.taskStore = taskStore;
        this.metricsStore = metricsStore;
    }

    public String suggest() {
        int total = taskStore.count();
        if (total == 0) {
            return HEADER + "  No tasks found. Run `scan` to index a project.\n";
        }

        int failedCount = taskStore.countByStatus(TaskStatus.FAILED);
        int enrichPendingCount = taskStore.countByStatus(TaskStatus.ENRICH_PENDING);
        int enrichingCount = taskStore.countByStatus(TaskStatus.ENRICHING);
        int indexedCount = taskStore.countByStatus(TaskStatus.INDEXED);
        int skippedCount = taskStore.countByStatus(TaskStatus.SKIPPED);
        int enrichedCount = taskStore.countByStatus(TaskStatus.ENRICHED);
        int enrichFailedCount = taskStore.countByStatus(TaskStatus.ENRICH_FAILED);
        int pendingCount = taskStore.countByStatus(TaskStatus.PENDING);

        Metric p3 = metricsStore.getLatestForPhase(3);

        var sb = new StringBuilder(HEADER);

        boolean hasActionable = false;

        if (failedCount > 0) {
            sb.append("  ").append(failedCount).append(" FAILED task(s) — re-scan to recover:\n");
            sb.append("    scan --resume\n");
            hasActionable = true;
        }

        if (enrichFailedCount > 0 || enrichingCount > 0 || pendingCount > 0) {
            var detail = new StringBuilder();
            if (enrichFailedCount > 0) detail.append("ENRICH_FAILED=").append(enrichFailedCount).append(", ");
            if (enrichingCount > 0) detail.append("ENRICHING=").append(enrichingCount).append(", ");
            if (pendingCount > 0) detail.append("PENDING=").append(pendingCount);
            if (!detail.isEmpty()) detail.setLength(detail.length() - 2);
            sb.append("  Tasks need recovery (").append(detail).append("):\n");
            sb.append("    extract\n");
            hasActionable = true;
        }

        if (indexedCount > 0 || enrichPendingCount > 0) {
            int readyCount = indexedCount + enrichPendingCount;
            if (p3 != null) {
                sb.append("  ").append(readyCount).append(" task(s) were not reached by any traced flow (INDEXED without enrichment).");
                sb.append(" To include them, verify they are reachable from an entry point.\n");
            } else {
                sb.append("  ").append(readyCount).append(" task(s) ready for extraction");
                if (indexedCount > 0) {
                    sb.append(" (enrichment happens automatically during flow analysis)");
                }
                sb.append(":\n");
                sb.append("    extract\n");
            }
            hasActionable = true;
        }

        boolean onlySkipped = indexedCount == 0 && enrichPendingCount == 0 && enrichedCount == 0
            && failedCount == 0 && enrichFailedCount == 0 && enrichingCount == 0
            && pendingCount == 0 && skippedCount > 0;
        if (onlySkipped) {
            sb.append("  All tasks skipped — no files required enrichment. Proceed directly:\n");
            sb.append("    extract\n");
            hasActionable = true;
        }

        boolean allProcessed = failedCount == 0 && enrichFailedCount == 0 && enrichingCount == 0
            && pendingCount == 0 && indexedCount == 0 && enrichPendingCount == 0 && enrichedCount > 0;
        if (allProcessed && p3 == null) {
            sb.append("  All tasks processed. Proceed to extraction:\n");
            sb.append("    extract\n");
            hasActionable = true;
        } else if (allProcessed && p3 != null) {
            sb.append("  Pipeline complete. Next steps:\n");
            sb.append("    generate\n");
            sb.append("    clean  (reset for a fresh scan)\n");
            hasActionable = true;
        }

        if (!hasActionable) {
            sb.append("  No actionable tasks.\n");
        }

        return sb.toString();
    }
}
