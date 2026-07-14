package com.github.ehdez73.code2req.common.domain;

/**
 * Task lifecycle states and their valid transitions:
 *
 * <pre>
 *     PENDING
 *        │
 *        ▼
 *     INDEXED ────────────────────► SKIPPED
 *     │   │
 *     │   │ (Phase 3 terminal —
 *     │   │  enrichment optional)
 *     │   ▼
 *     │ ENRICH_PENDING
 *     │    │
 *     │    ▼
 *     │ ENRICHING ────────────► AWAITING_HUMAN_REVIEW
 *     │    │
 *     │┌───┴───┐
 *     │▼       ▼
 *     ENRICHED  FAILED
 *                 │
 *                 ▼
 *             ENRICH_FAILED
 *
 * Recovery transitions (via --resume flags):
 *   FAILED ───────────────────► INDEXED
 *   ENRICH_FAILED ────────────► ENRICH_PENDING
 *   ENRICHING ────────────────► ENRICH_PENDING
 *   AWAITING_HUMAN_REVIEW ───► INDEXED
 *   SKIPPED ─────────────────► INDEXED  (re-plan with lower threshold)
 * </pre>
 */
public enum TaskStatus {
    PENDING("Awaiting indexing"),
    INDEXED("Indexed, awaiting enrichment qualification"),
    ENRICH_PENDING("Qualified, awaiting LLM enrichment"),
    ENRICHING("Being enriched by LLM"),
    ENRICHED("Enrichment completed successfully"),
    SKIPPED("Skipped by planner (no qualifying findings)"),
    FAILED("Indexing or enrichment failed"),
    ENRICH_FAILED("LLM enrichment failed"),
    AWAITING_HUMAN_REVIEW("Hop depth exceeded, manual review needed");

    private final String description;

    TaskStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
