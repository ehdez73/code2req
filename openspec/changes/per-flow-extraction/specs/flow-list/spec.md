## Purpose

Provides users with visibility into all detected and analyzed execution flows by merging entry points from the Phase 1 index with processed flows from the extraction cache.

## ADDED Requirements

### Requirement: User can list all flows

The system SHALL display a unified table of all flows, merging entry points detected during scan (from SQLite) with flows already processed and persisted in the extraction cache. For each flow the table SHALL include its short ID, type, status, and human-readable name.

#### Scenario: No data available

- **WHEN** the user runs `flow list` and neither `scan` nor `extract` has been executed
- **THEN** the system SHALL display the message "No data. Run 'scan' first."

#### Scenario: Flows detected but not analyzed

- **WHEN** the user runs `flow list` after `scan` but before `extract`
- **THEN** the system SHALL display all entry points from SQLite with status `PENDING`

#### Scenario: Flows analyzed and cached

- **WHEN** the user runs `flow list` after `extract` has completed
- **THEN** the system SHALL display flows from the extraction cache with status `ANALYZED` or `QUARANTINED`, merging with any additional entry points from SQLite that were not yet extracted (status `PENDING`)

#### Scenario: Flow exists in both SQLite and cache

- **WHEN** the same `flowId` exists as an entry point in SQLite and as a processed flow in the extraction cache
- **THEN** the system SHALL display the cached flow's status (`ANALYZED` or `QUARANTINED`), overriding the `PENDING` status from SQLite

### Requirement: Flow list supports filtering by status

The system SHALL allow filtering the flow list by status using the `--status` option.

#### Scenario: Filter by analyzed status

- **WHEN** the user runs `flow list --status analyzed`
- **THEN** the system SHALL display only flows with status `ANALYZED`

#### Scenario: Filter by quarantined status

- **WHEN** the user runs `flow list --status quarantined`
- **THEN** the system SHALL display only flows with status `QUARANTINED`

#### Scenario: Filter by pending status

- **WHEN** the user runs `flow list --status pending`
- **THEN** the system SHALL display only flows with status `PENDING`

### Requirement: Flow list supports filtering by entry point type

The system SHALL allow filtering the flow list by entry point type using the `--type` option.

#### Scenario: Filter by HTTP type

- **WHEN** the user runs `flow list --type http`
- **THEN** the system SHALL display only HTTP endpoint flows

#### Scenario: Filter by scheduled type

- **WHEN** the user runs `flow list --type scheduled`
- **THEN** the system SHALL display only scheduled task flows

### Requirement: Flow list supports text-based filtering

The system SHALL allow filtering flows by partial text match on flow ID or name using the `--filter` option.

#### Scenario: Filter by partial name match

- **WHEN** the user runs `flow list --filter "owner"`
- **THEN** the system SHALL display only flows whose short ID or name contains the string "owner" (case-insensitive)

### Requirement: Flow list supports verbose mode

The system SHALL provide a verbose mode via `--verbose` that displays additional details per flow including step count, complexity level, and number of unresolved links.

#### Scenario: Verbose listing

- **WHEN** the user runs `flow list --verbose`
- **THEN** the system SHALL display for each flow: short ID, type, status, name, step count, complexity level, and unresolved link count

#### Scenario: Verbose shows stale indicator after re-analysis without regroup

- **WHEN** the user runs `flow list --verbose` and a flow has been re-analyzed via `extract --flow` without `--regroup`
- **THEN** the system SHALL indicate that the flow has stale cross-references with a `[stale links]` label next to its status

### Requirement: Short flow identifiers

The system SHALL generate a deterministic 8-character hexadecimal short ID for each flow using `SHA-256(fullFlowId)`, truncated to the first 8 characters.

#### Scenario: Same flowId produces same short ID

- **WHEN** a flow with `flowId` "OwnerController.java:/app/OwnerController.java:showOwner GET /owners/{id}" is processed
- **THEN** the system SHALL produce the same short ID every time across executions

#### Scenario: Different flowIds produce different short IDs

- **WHEN** two flows have different `flowId` values
- **THEN** the system SHALL generate different short IDs
