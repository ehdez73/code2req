# US001 — Developer defines scan targets in project manifest

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F001 — Project Configuration & Manifest Parsing
**Priority:** must | **Estimate:** 3 SP
**Depends on:** — | **Blocks:** US002, US003, US004, US005, US006

> As a **Developer**, I want **to specify which codebases my project should analyze**, so that **the tool scans only the repositories I care about**.

### Acceptance Criteria

- [ ] A `project-manifest.yaml` with valid scan targets is accepted without errors
- [ ] The manifest path is resolved by: user-provided argument first, then fallback to current working directory
- [ ] Each target path can be absolute or relative to the working directory
- [ ] Multiple targets are supported in a single manifest
- [ ] A manifest with zero targets is rejected with a clear message
- [ ] If no manifest is found (by argument or in current directory), the CLI exits with a clear message

### INVEST Flags

- edge-cases
