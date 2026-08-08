# code2req — AI Reverse Engineering CLI

**code2req (Code to Requirements)** is a CLI tool that reverse-engineers
legacy Java codebases into structured specifications for Spec-Driven
Development (SDD).

[![Java](https://img.shields.io/badge/Java-21-%23ED8B00?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-%236DB33F?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring Shell](https://img.shields.io/badge/Spring%20Shell-3.4.x-%236DB33F)](https://spring.io/projects/spring-shell)
[![SQLite](https://img.shields.io/badge/SQLite-embedded-%23003B57?logo=sqlite)](https://www.sqlite.org/)
[![JavaParser](https://img.shields.io/badge/JavaParser-AST-%23007396)](https://javaparser.org/)
[![License: Apache](https://img.shields.io/badge/License-APACHE2.0-red.svg?logo=apache)](LICENSE)

## Features

| Command | Description |
|---------|-------------|
| `scan` | Parse Java sources via JavaParser AST, redact secrets, store in embedded SQLite, export JSON code graph |
| `scan --resume` | Incremental re-scan — skips already-completed files using deterministic SHA-256 task IDs |
| `status` | Query task store by status (`PENDING`, `INDEXED`, `ENRICH_PENDING`, `ENRICHING`, `ENRICHED`, `FAILED`, `ENRICH_FAILED`, `SKIPPED`) |
| `extract` | Embabel agentic functional requirement extraction (GOAP agent + embedded enrichment via `EnrichFlowAction`). Supports `--flow <id>`, `--regroup`, `--dry-run`, `--resume`, `--force` |
| `flow list` | Display all detected and analyzed flows with `--status`, `--type`, `--filter`, `--verbose` |
| `generate` | Synthesize spec documents from extraction cache |
| `run` | Execute full pipeline: scan → extract → generate |
| `snapshot create` / `snapshot list` / `snapshot restore` | Create, list, and restore SQLite snapshots for crash recovery |
| `task list` / `task findings` / `task set-status` | Inspect and manage individual tasks |
| `clean` | Wipe all scanned data (SQLite store + output files) |
| `validate` | Verify project manifest YAML structure |

Phase 1 runs fully offline — zero network or LLM calls.

## Quick Start

**Prerequisites:** Java 21+, Maven

Set the API key for your chosen LLM provider (default uses OpenRouter):

```bash
export OPENROUTER_API_KEY=sk-or-v1-...
```

Build and start the interactive shell:

```bash
./mvnw clean compile
./mvnw spring-boot:run
```

Inside the shell:

```
scan --manifest project-manifest.yaml
status
```

See [LLM Model Selection](#llm-model-selection) for alternative models and providers.

## LLM Model Selection

code2req uses Spring profiles to switch between different LLM models and providers. Maven
profiles (`-P`) activate the corresponding Spring profile and add the required dependencies
for the Embabel agent.

| Profile | Run command | Model | Provider | Env var |
|---|---|---|---|---|---|
| *(default)* | `./mvnw spring-boot:run` | `openai/gpt-oss-20b:free` | OpenRouter | `OPENROUTER_API_KEY` |
| `local` | `./mvnw spring-boot:run -Plocal` | `gemma4` (Docker) | Local endpoint | *(not required)* |
| `local-gemma-coder` | `./mvnw spring-boot:run -Plocal-gemma-coder` | Gemma-4-12B-Coder (Docker) | Local endpoint | *(not required)* |
| `opencode` | `./mvnw spring-boot:run -Popencode` | `hy3` | OpenCode | `OPENCODE_API_KEY` |
| `opencode-free` | `./mvnw spring-boot:run -Popencode-free` | `deepseek-v4-flash-free` | OpenCode | `OPENCODE_API_KEY` |

- **default** — uses OpenRouter's free tier. Set `OPENROUTER_API_KEY` or
  `SPRING_AI_OPENAI_API_KEY` in your environment.
- **local / local-gemma-coder** — target a local OpenAI-compatible endpoint
  (e.g., vLLM, Ollama) at `http://localhost:12434`. Execution mode switches to
  `sync` for easier debugging. The API key is ignored.
- **opencode** — uses the OpenCode inference platform (`https://opencode.ai/zen/go`). Set `OPENCODE_API_KEY`.
- **opencode-free** — uses the OpenCode inference platform (`https://opencode.ai/zen`) with the rate-limited `deepseek-v4-flash-free` model. Execution mode switches to `sync`.

### Local Docker Inference

For the `local` and `local-gemma-coder` profiles, pull the model first using
Docker's AI model runner (requires Docker Desktop with model runner support):

```bash
# Gemma 4 (12B) Coder — used by `local-gemma-coder` profile
docker model pull hf.co/yuxinlu1/gemma-4-12B-coder-fable5-composer2.5-v1-GGUF:Q4_K_M

# Gemma 4 — used by `local` profile
docker model pull docker.io/ai/gemma4:latest
```

The Docker AI endpoint listens on `http://localhost:12434` by default. You can
also use any other OpenAI-compatible local server (vLLM, Ollama, llama.cpp,
etc.) — just point `spring.ai.openai.base-url` to your server's address.

Each Maven profile sets `-Dspring.profiles.active=<profile>` and pulls in the
appropriate Embabel agent dependency (`embabel-agent-starter-dockermodels` for
local profiles, `embabel-agent-starter-openai-custom` for OpenCode profiles).
The Spring profile then overrides `spring.ai.openai.chat.options.model` and
related settings from `application-<profile>.properties`.

## Architecture (3-Phase Pipeline)

| Phase | What it does | Commands | Status |
|-------|-------------|----------|--------|
| **1** | Deterministic indexing — AST parsing, secret redaction, SQLite task store, JSON index export | `scan`, `scan --resume`, `validate`, `clean` | ✅ Active |
| **2** | Embabel GOAP agent — entry-point-driven functional requirement extraction with embedded LLM enrichment (`EnrichFlowAction`) and spec synthesis | `extract`, `flow list`, `generate` | ✅ Active |
| **All** | End-to-end pipeline orchestration | `run` (scan → extract → generate) | ✅ Active |

## Key Design Decisions

- **No ORM** — Spring JDBC (`JdbcTemplate`) with SQLite (WAL mode)
- **Pure-Java AST** — JavaParser, no native bindings
- **Idempotent tasks** — SHA-256 composite hash of path + content
- **In-memory redaction** — files on disk never modified
- **Fault-tolerant** — single-file failures never block the full scan

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
  db-path: .code2req_cache.db
```

## Build

```bash
./mvnw clean compile          # build only
./mvnw spring-boot:run        # run (default profile)
```

To run with a different LLM, see [LLM Model Selection](#llm-model-selection).

## License

[APACHE 2.0](LICENSE)
