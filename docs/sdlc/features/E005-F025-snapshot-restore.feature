# Feature: Snapshot & Restore
# Epic: E005 — Snapshot & Restore
# Feature ID: F025
# Stories: US053, US054
# Phase 14 draft generated: 2026-06-21
# Last updated: 2026-06-21

Feature: Snapshot & Restore
  Cross-cutting operational feature enabling point-in-time save and restore
  of all local state (SQLite DB + JSON index) for safe experimentation and rollback.

  Background:
    Given the CLI has been started and is ready
    And scan data exists in the SQLite database and JSON index

  Rule: Snapshot creates a point-in-time copy without modifying live data

    # ---------------------------------------------------------------------------
    # Story US053: Developer creates and lists snapshots
    # ---------------------------------------------------------------------------

    @US053 @E005 @F025 @must @draft
    Scenario: Developer creates a named snapshot
      Given a completed scan with populated SQLite DB and JSON index
      When the developer runs "snapshot --name before-refactor"
      Then a directory "snapshots/before-refactor/" is created
      And the directory contains a transactional copy of the SQLite database
      And the directory contains a copy of the JSON index file
      And the directory contains a snapshot.json metadata file with timestamp and file sizes
      And the live SQLite database and JSON index are unmodified
      And the CLI reports "Snapshot saved: snapshots/before-refactor/"

    @US053 @E005 @F025 @must @draft
    Scenario: Developer creates a snapshot without a name (auto-timestamp)
      Given a completed scan with populated data
      When the developer runs "snapshot"
      Then a directory "snapshots/snapshot_<YYYYMMDD_HHMMSS>/" is created
      And the snapshot contains the DB, JSON index, and metadata files
      And the CLI reports "Snapshot saved: snapshots/snapshot_<...>/"

    @US053 @E005 @F025 @must @draft
    Scenario: Developer lists available snapshots
      Given two existing snapshots in the snapshots/ directory
      When the developer runs "snapshot list"
      Then the CLI displays each snapshot name with its creation date and total size

    @US053 @E005 @F025 @must @draft
    Scenario: Developer creates a snapshot with no data
      Given a clean state with no scan data
      When the developer runs "snapshot"
      Then the CLI reports a graceful message that there is nothing to snapshot
      And no snapshot directory is created

    @US053 @E005 @F025 @must @draft
    Scenario: Clean command never removes snapshots
      Given two existing snapshots in the snapshots/ directory
      When the developer runs "clean"
      Then the snapshots/ directory and its contents remain untouched
      And the SQLite database and JSON index are deleted

  Rule: Restore replaces live data from a snapshot with pool lifecycle management

    # ---------------------------------------------------------------------------
    # Story US054: Developer restores state from a snapshot
    # ---------------------------------------------------------------------------

    @US054 @E005 @F025 @must @draft
    Scenario: Developer restores state from a named snapshot
      Given a named snapshot "snapshots/before-refactor/" with DB and JSON files
      And the current live state has been modified since the snapshot was taken
      When the developer runs "restore before-refactor"
      Then the live SQLite database is overwritten with the snapshot copy
      And the live JSON index is overwritten with the snapshot copy
      And the HikariCP connection pool is drained, recreated, and ready for new operations
      And the CLI reports "Restored from snapshot: before-refactor"

    @US054 @E005 @F025 @must @draft
    Scenario: Developer attempts restore with non-existent snapshot name
      Given no snapshot named "ghost" exists
      When the developer runs "restore ghost"
      Then the CLI reports an error: "Snapshot 'ghost' not found in snapshots/"
      And the live data is not modified

    @US054 @E005 @F025 @must @draft
    Scenario: Developer attempts restore without arguments
      When the developer runs "restore"
      Then the CLI reports usage: "Usage: restore <snapshot-name>"
