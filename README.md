# code2req — AI Reverse Engineering CLI

**code2req (Code to Requirements)** is a CLI tool that reverse-engineers
legacy Java codebases into structured specifications for Spec-Driven
Development (SDD).

[![Java](https://img.shields.io/badge/Java-21-%23ED8B00)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-%236DB33F)](https://spring.io/projects/spring-boot)
[![Spring Shell](https://img.shields.io/badge/Spring%20Shell-3.4.x-%236DB33F)](https://spring.io/projects/spring-shell)
[![SQLite](https://img.shields.io/badge/SQLite-embedded-%23003B57)](https://www.sqlite.org/)
[![JavaParser](https://img.shields.io/badge/JavaParser-AST-%23007396)](https://javaparser.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## Features

| Command | Description |
|---------|-------------|
| `scan` | Parse Java sources via JavaParser AST, redact secrets, store in embedded SQLite, export JSON code graph |
| `resume` | Incremental re-scan — skips already-completed files using deterministic SHA-256 task IDs |
| `status` | Query task store by status (`PENDING`, `RUNNING`, `SUCCESS`, `FAILED`) |
| `clean` | Wipe all scanned data (SQLite store + output files) |
| `validate` | Verify project manifest YAML structure |

Phase 1 runs fully offline — zero network or LLM calls.

## Quick Start

**Prerequisites:** Java 21+, Maven

```bash
./mvnw spring-boot:run   # start the interactive shell
```

Inside the shell:

```
scan --manifest project-manifest.yaml
status
```

## Architecture (3-Phase Pipeline)

| Phase | What it does | Status |
|-------|-------------|--------|
| **1** | Deterministic indexing — AST parsing, secret redaction, SQLite task store, JSON index export | ✅ Active |
| **2** | LLM-powered per-file analysis via Spring `@Async` | 🔜 Future |
| **3** | Map-Reduce semantic synthesis | 🔜 Future |

## Key Design Decisions

- **No ORM** — Spring JDBC (`JdbcTemplate`) with SQLite (WAL mode)
- **Pure-Java AST** — JavaParser, no native bindings
- **Idempotent tasks** — SHA-256 composite hash of path + content
- **In-memory redaction** — files on disk never modified
- **Fault-tolerant** — single-file failures never block the full scan
- **Maven depgraph** — optional / non-blocking

See [`docs/sdlc/adrs/`](docs/sdlc/adrs/) and [`docs/sdlc/tech-stack.md`](docs/sdlc/tech-stack.md) for full rationale.

## Project Manifest

Copy `project-manifest.yaml.template` to `project-manifest.yaml` and adapt it to your project:

```yaml
targets:
  - name: my-app
    path: /path/to/source
    layer: backend
    tech_profile: java-spring-legacy
    exclude_patterns:
      - "**/generated/**"
      - "**/*Pb.java"

execution:
  max-concurrent-llm-calls: 5

output:
  spec-dir: spec-output
  index-file: code-graph-index.json
  db-path: .code2req_cache.db
```

## Build

```bash
./mvnw clean compile          # build only
./mvnw spring-boot:run        # run
```

## License

[MIT](LICENSE)
