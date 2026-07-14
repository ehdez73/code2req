# US054 — Developer restores state from a named snapshot

**Epic:** E005 — Snapshot & Restore
**Feature:** F025 — Snapshot and Restore
**Priority:** must | **Estimate:** 1 SP
**Depends on:** US053 | **Blocks:** nothing

> As a **Developer**, I want **to restore my local state (DB + JSON) from a previously created snapshot**, so that **I can rollback to a known good state after failed experiments**.

### Acceptance Criteria

- [ ] Running `restore <name>` overwrites the live SQLite database and JSON index with the files from `snapshots/<name>/`
- [ ] The restore process drains the HikariCP connection pool before overwriting files
- [ ] The restore process reinitialises the connection pool after the files are restored
- [ ] Running `restore <name>` with a non-existent snapshot name reports a clear error message
- [ ] Running `restore` without arguments reports a clear usage message
- [ ] After restore, running `status` shows the same state as before the snapshot was taken
