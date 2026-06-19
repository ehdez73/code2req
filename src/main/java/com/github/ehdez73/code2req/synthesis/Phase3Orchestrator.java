package com.github.ehdez73.code2req.synthesis;

import com.github.ehdez73.code2req.model.Metric;
import com.github.ehdez73.code2req.orchestrator.CompletionStatus;
import com.github.ehdez73.code2req.store.MetricsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class Phase3Orchestrator {

    private static final Logger log = LoggerFactory.getLogger(Phase3Orchestrator.class);

    private final MetricsStore metricsStore;

    public Phase3Orchestrator(MetricsStore metricsStore) {
        this.metricsStore = metricsStore;
    }

    public Phase3Result execute(CompletionStatus phase2Status, boolean dryRun) {
        if (dryRun) {
            log.info("Phase 3 dry-run: simulation mode, using stubbed synthesis");
            Phase3Result result = Phase3Result.empty();
            persistMetrics(result, dryRun);
            return result;
        }

        log.warn("Phase 3 synthesis not yet implemented (E004 pending) — returning empty result");
        Phase3Result result = Phase3Result.empty();
        persistMetrics(result, dryRun);
        return result;
    }

    private void persistMetrics(Phase3Result result, boolean dryRun) {
        int totalTokens = dryRun ? 0 : 0;
        Metric metric = new Metric(
            UUID.randomUUID().toString(),
            3,
            result.flowsExtracted() + result.ambiguityGaps(),
            result.flowsExtracted(),
            0, 0, 0, 0,
            totalTokens,
            0.0,
            LocalDateTime.now().toString()
        );
        metricsStore.save(metric);
        log.info("Phase 3 metrics persisted: {} flows, {} ambiguity gaps, {} awaiting review",
            result.flowsExtracted(), result.ambiguityGaps(), result.awaitingReview());
    }
}
