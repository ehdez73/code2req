package com.github.ehdez73.code2req.common.domain;

/**
 * Task lifecycle states and their valid transitions:
 *
 * <pre>
 *     PENDING
 *        │
 *        ▼
 *     INDEXED ────────────────────► SKIPPED
 *        │
 *        ▼
 *     ENRICH_PENDING
 *        │
 *        ▼
 *     ENRICHING ────────────► AWAITING_HUMAN_REVIEW
 *        │
 *    ┌───┴───┐
 *    ▼       ▼
 * ENRICHED  FAILED
 *              │
 *              ▼
 *          ENRICH_FAILED
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
    PENDING,
    ENRICH_PENDING,
    ENRICHING,
    INDEXED,
    SKIPPED,
    ENRICHED,
    FAILED,
    ENRICH_FAILED,
    AWAITING_HUMAN_REVIEW
}
