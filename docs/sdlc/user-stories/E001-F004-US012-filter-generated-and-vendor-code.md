# US012 — Developer filters out generated and vendor code

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F004 — Secret Redaction & Exclude Filtering
**Priority:** should | **Estimate:** 3 SP
**Depends on:** US006 | **Blocks:** US013, US014

> As a **Developer**, I want **generated sources and third-party library code to be excluded from analysis**, so that **the output contains only hand-written business logic**.

### Acceptance Criteria

- [ ] Exclude patterns use `.gitignore`-style glob matching against file paths
- [ ] By default, common generated paths (e.g., `target/`, `build/`, `generated-sources/`) are excluded
- [ ] Users can override default excludes via manifest configuration
- [ ] Excluded files are counted and reported in the scan summary
