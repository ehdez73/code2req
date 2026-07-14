# US053 — Developer creates and lists snapshots of current state

**Epic:** E005 — Snapshot & Restore
**Feature:** F025 — Snapshot and Restore
**Priority:** must | **Estimate:** 1 SP
**Depends on:** US013, US014, US015 | **Blocks:** US054

> As a **Developer**, I want **to create point-in-time snapshots of my local state (DB + JSON) and list existing ones**, so that **I can safely experiment without fear of losing analysis data**.

### Acceptance Criteria

- [ ] Running `snapshot` without arguments creates a timestamped directory under `snapshots/` with the live SQLite database (via `VACUUM INTO`), the JSON index, and a `snapshot.json` metadata file
- [ ] Running `snapshot --name my-label` creates a snapshot in `snapshots/my-label/` instead of the timestamp default
- [ ] Running `snapshot` when the DB or JSON index is empty reports a graceful message with no errors
- [ ] Running `snapshot list` displays all available snapshots with name, creation date, and total size
- [ ] `snapshot` never modifies or deletes the live data — it is a pure read operation
- [ ] `clean` never removes snapshot directories
