## Purpose

Allows users to extract, analyze, and re-analyze individual execution flows by their short identifier, merging results into the existing extraction cache without reprocessing all flows.

## ADDED Requirements

### Requirement: User can extract a single flow by short ID

The system SHALL accept a `--flow <short-id>` option on the `extract` command to scope Phase 3 extraction to a single entry point matching the given short ID.

#### Scenario: Extract a flow by valid short ID

- **WHEN** the user runs `extract --flow a3f2b91c` and a flow with that short ID exists in the SQLite index
- **THEN** the system SHALL trace, enrich, and analyze only that flow, merge the result into the extraction cache, and skip group-flows and cross-reference actions

#### Scenario: Extract a flow by full short ID with single match

- **WHEN** the user runs `extract --flow a3f2b91c` and exactly one flow matches that prefix
- **THEN** the system SHALL process that flow

### Requirement: Extract --flow requires prior scan

The system SHALL refuse `extract --flow` when no Phase 1 scan data exists.

#### Scenario: Extract --flow without scan

- **WHEN** the user runs `extract --flow abc12345` and `scan` has not been executed
- **THEN** the system SHALL display "No scan data available. Run 'scan' first."

### Requirement: Ambiguous short ID prefix is rejected

The system SHALL reject an ambiguous short ID prefix and display all matching flows.

#### Scenario: Multiple flows match a prefix

- **WHEN** the user runs `extract --flow abc` and three flows match the prefix "abc"
- **THEN** the system SHALL display each matching flow with its full short ID and name, and instruct the user to use a longer prefix or the full short ID

### Requirement: Non-existent short ID is rejected

The system SHALL reject a short ID that matches no known flow.

#### Scenario: No flow matches the given short ID

- **WHEN** the user runs `extract --flow zzz99999` and no flow matches
- **THEN** the system SHALL display "Flow not found: zzz99999. Use 'flow list' to see available flows."

### Requirement: New flows trigger automatic regroup

When a flow with a given short ID does not exist in the extraction cache (newly added entry point), the system SHALL automatically re-group all flows and re-cross-reference all flows after the individual flow is analyzed and merged.

#### Scenario: Extract a new flow

- **WHEN** the user runs `extract --flow f41c82d0` and the flow does not exist in the extraction cache
- **THEN** the system SHALL trace, enrich, and analyze the flow, merge it into the cache, then execute group-flows and cross-reference-flows on ALL flows in the cache

### Requirement: --flow composes with --dry-run

The system SHALL support `--flow` combined with `--dry-run` to simulate single-flow extraction without LLM calls or cache writes.

#### Scenario: Dry-run single flow

- **WHEN** the user runs `extract --flow a3f2b91c --dry-run`
- **THEN** the system SHALL trace the call graph for that flow, display the traced steps and component types, and SHALL NOT call the LLM or write to the extraction cache

### Requirement: --flow composes with --resume

The system SHALL support `--flow` combined with `--resume` to reuse cached `FLOW_ANALYSIS` findings for the specified flow.

#### Scenario: Resume single flow

- **WHEN** the user runs `extract --flow a3f2b91c --resume` and a cached `FLOW_ANALYSIS` finding exists for that flow
- **THEN** the system SHALL reuse the cached LLM analysis result instead of making a new LLM call, re-trace the call graph, and merge the flow into the cache

#### Scenario: Resume single flow with no cache

- **WHEN** the user runs `extract --flow a3f2b91c --resume` and no cached `FLOW_ANALYSIS` finding exists for that flow
- **THEN** the system SHALL call the LLM to analyze the flow and persist the result as a new `FLOW_ANALYSIS` finding

### Requirement: Existing flows preserve their feature grouping by default

When re-analyzing a flow that already exists in the extraction cache, the system SHALL NOT re-group or re-cross-reference all flows by default. The flow's content SHALL be updated in place within its existing feature.

#### Scenario: Re-analyze an existing flow

- **WHEN** the user runs `extract --flow a3f2b91c` and the flow already exists in the cache with status `ANALYZED`
- **THEN** the system SHALL re-trace, re-enrich, and re-analyze the flow, update its content in the existing feature, and preserve all other flows and relationships unchanged

### Requirement: User can force regroup with --regroup flag

The system SHALL provide a `--regroup` flag that forces re-grouping and re-cross-referencing of all flows after the individual flow is analyzed, regardless of whether the flow is new or existing.

#### Scenario: Force regroup after re-analyzing an existing flow

- **WHEN** the user runs `extract --flow a3f2b91c --regroup` and the flow already exists in the cache
- **THEN** the system SHALL re-trace, re-enrich, and re-analyze the flow, merge it into the cache, then execute group-flows and cross-reference-flows on ALL flows in the cache

### Requirement: Full extract requires --force when cache exists

When `extract` is called without `--flow` and an extraction cache file already exists, the system SHALL require the `--force` flag to proceed and SHALL display a warning indicating that the existing cache will be overwritten.

#### Scenario: Full extract with existing cache without --force

- **WHEN** the user runs `extract` and `extraction-cache.json` already exists with processed flows
- **THEN** the system SHALL display a warning listing the number of cached flows that would be overwritten, and SHALL NOT proceed. It SHALL instruct the user to use `--force` to overwrite or `--flow <id>` to process individually.

#### Scenario: Full extract with existing cache and --force

- **WHEN** the user runs `extract --force` and `extraction-cache.json` exists
- **THEN** the system SHALL delete all cached `FLOW_ANALYSIS` findings, process all entry points, and replace the extraction cache entirely

#### Scenario: Full extract without cache

- **WHEN** the user runs `extract` and no extraction cache exists
- **THEN** the system SHALL proceed normally without requiring `--force`

### Requirement: Cache merge preserves unrelated data

When merging a single flow into the cache, the system SHALL preserve all unrelated flows, features, flow relationships, orphaned methods, and quarantine gaps.

#### Scenario: Merge updated flow into cache with multiple features

- **WHEN** a flow belonging to feature "Owner Management" is re-analyzed and merged, and the cache also contains features "Visit Tracking" and "Vet Management"
- **THEN** the system SHALL update only the targeted flow within its feature, and SHALL NOT modify "Visit Tracking", "Vet Management", their flows, or any flow relationships

#### Scenario: Merged flow changes quarantine status

- **WHEN** a previously quarantined flow is re-analyzed and now passes quarantine (becomes `ANALYZED`)
- **THEN** the system SHALL remove the corresponding `AmbiguityGap` entry from the cache's quarantine gaps list

#### Scenario: Merged flow becomes quarantined

- **WHEN** a previously analyzed flow is re-analyzed and now fails quarantine (becomes `QUARANTINED`)
- **THEN** the system SHALL add a new `AmbiguityGap` entry to the cache's quarantine gaps list

### Requirement: First --flow execution creates cache if none exists

When `extract --flow` is called and no extraction cache file exists, the system SHALL create a new cache containing only that flow as a single-flow feature.

#### Scenario: First per-flow extraction

- **WHEN** the user runs `extract --flow a3f2b91c` and no extraction cache exists
- **THEN** the system SHALL create a new cache with the analyzed flow in its own feature, and automatically group/cross-reference (since the flow is effectively new)
