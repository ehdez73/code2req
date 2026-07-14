# Kick-Off Meeting Notes — code2req

**Date:** 2026-06-12
**Mode:** Compact (3-round) — derived from existing PRD v3.5
**Team:** Solo Developer (all roles)

---

## Round 1 — Vision & Scope

### Elevator Pitch
An AI-driven CLI that reverse-engineers legacy enterprise codebases into structured business specifications organized by functional flows, with full traceability back to source code.

### Problem Statement
Legacy enterprise codebases contain embedded business rules, domain validations, and ecosystem invariants obscured by technical debt across REST endpoints, async event handlers, and hidden procedural database layers — making systems hard to understand, test, and re-architect.

### In Scope (Phase 1)
- YAML manifest parsing
- Maven dependency graph resolution (optional, graceful fallback)
- JavaParser AST traversal: component types, HTTP endpoints, event listeners, method calls, custom validators
- In-memory secret redaction
- Exclude pattern filtering
- Structured JSON index output (`code-graph-index.json`)
- SQLite task store ingestion (Spring JDBC, WAL mode, idempotent IDs)
- CLI `scan` command for Phase 1 execution

### Out of Scope
- LLM-powered file analysis (Phase 2)
- Semantic synthesis and manifest output (Phase 3)
- Frontend/UI code analysis
- Any code modification on disk

---

## Round 2 — Users & Personas

| Persona | Role | Primary Goal |
|---|---|---|
| Developer | Full-Stack Engineer | Understand legacy codebases and extract business rules without manual effort |
| Tech Lead | Architect / Evaluator | Verify system-wide traceability and ensure no secret leakage |

---

## Round 3 — Constraints, NFRs & Risks

### Technical Constraints
- Pure Java — no native OS bindings (no Tree-sitter)
- Java 21, Spring Boot 3.4.x, Maven build
- SQLite embedded — no external database
- Spring JDBC (JdbcTemplate) — no ORM

### Key NFRs
- Zero network/LLM calls during Phase 1
- Secrets redacted in-memory before any outbound call
- Idempotent task IDs via SHA-256 composite hash
- WAL mode + busy_timeout for SQLite concurrency

### Top Risks
1. JavaParser syntax failures → annotation-driven fallback
2. Maven unavailability → graceful degradation with warning
3. SQLite locking → WAL mode + connection pool

### Success Metrics
- HTTP endpoint extraction ≥ 95%
- Custom constraint extraction ≥ 95%
- Zero tokens consumed on unchanged re-scans
- Read-only: zero file mutations

---

## Team

| Member | Role |
|---|---|
| Solo Developer | Product Owner, Developer, QA |
