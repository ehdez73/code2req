# code2req — AI Reverse Engineering CLI

Java 21, Spring Boot 3.4.x, Spring Shell, Maven.

```
mvn clean compile          # build only
mvn spring-boot:run        # run interactive shell
./mvnw spring-boot:run     # via wrapper (no local Maven needed)
```

## Architecture (3-Phase Pipeline)

- **Phase 1** (active focus): deterministic indexing — JavaParser AST → `code-graph-index.json` → SQLite task store. Zero network or LLM calls.
- **Phase 2** (future): LLM-powered per-file analysis via Spring `@Async("orchestratorTaskExecutor")`.
- **Phase 3** (future): Embabel agentic functional requirement extraction (GOAP planning).

## Key Constraints

- **No ORM** — Spring JDBC (`JdbcTemplate`) with SQLite (WAL mode + `busy_timeout=5000`). See `application.properties` for HikariCP config.
- **JavaParser** AST traversal with annotation-driven fallback when type resolution fails.
- **Secret redaction** is in-memory only (`[REDACTED:type]` placeholders) — files on disk never modified.
- **Maven depgraph** resolution is optional / non-blocking — graceful degrade if Maven unavailable.
- **Task IDs** are deterministic SHA-256 hashes of `file_path | sha256(content) | sha256(test_content) | sha256(model_id) | sha256(prompt_version)`.
- **Single-file failures never block the full scan** — parse errors, redaction misses, or unregistered file extensions log a warning and continue.

## Canonical References for Coding

Before writing code, consult these sources for tech stack decisions and rationale:

- **`docs/sdlc/tech-stack.md`** — maps every technology (Java 21, Spring Boot 3.4.x, JavaParser, SQLite, etc.) to its version, purpose, and governing ADR. Use this to verify library choices and version alignment.
- **`docs/sdlc/adrs/*.md`** — Architecture Decision Records covering: AST parsing strategy (ADR-001), Spring JDBC over ORM (ADR-002), SQLite WAL mode (ADR-003), deterministic task IDs (ADR-004), and in-memory secret redaction (ADR-005). Read the relevant ADR before implementing any feature that touches these areas.
- **`.agents/skills/spring/SKILL.md`** — Spring framework coding patterns: Shell commands, JDBC/SQLite, JavaParser, @Async, testing. Read before writing Spring code.
- Always try to follow coding best practices: SOLID, DRY, KISS, YAGNI, etc.

## Project Structure

```
src/main/java/com/github/ehdez73/code2req/
    Application.java        # @SpringBootApplication entrypoint
    shell/                              # Spring Shell commands
```

- No tests exist yet (`src/test/java/` is empty, just the package skeleton). See `.agents/skills/spring/SKILL.md` for testing patterns.
- `project-manifest.yaml` defines scan targets for external repos (e.g., `spring-petclinic` for manual testing).

## SDLC & Planning

Use the SDLC skill (`.agents/skills/sdlc/`) when working on features/epics:
- ADRs are in `docs/sdlc/adrs/` — read relevant ADR before implementing the feature.
- Gherkin feature files in `docs/sdlc/features/` define acceptance criteria.
- `docs/sdlc/sdlc-context.json` is the canonical record of scope, stories, and architecture decisions.

## JVM Quirks

- `-Djline.terminal=jline.UnixTerminal` is set in `pom.xml` for Spring Boot Maven plugin.
- `spring.main.web-application-type=none` — this is a CLI, not a web app.
- Async pool: core=5, max=10, queue=1000, prefix=`c2r-executor-`, 30s shutdown await.
- See `.agents/skills/spring/SKILL.md` for detailed Spring patterns (Shell commands, JDBC, JavaParser, @Async, testing).

## Gitignore Notable Entries

- `.code2req_cache.db` / `.code2req_cache.db-wal` / `.code2req_cache.db-shm` — SQLite state store
- `spec-output/` — generated spec documents
- `code2req.log`, `spring-shell.log`
