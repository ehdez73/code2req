---
name: spring
description: >
  Spring framework patterns and conventions for the code2req project.
  Trigger when the user asks about: spring, spring boot, spring shell, spring jdbc,
  jdbctemplate, javaparser, sqlite, hikaricp, spring ai, async, thread pool,
  ast parsing, shell command, @Service, @RestController, @Component, @ShellComponent.
---

# Spring Skill — code2req Coding Conventions

## Spring Shell Commands

Commands live in the `shell/` subpackage. Pattern:

```java
@ShellComponent
public class SomeCommand {
    @ShellMethod(key = "command-name", value = "Human-readable description")
    public String execute(@ShellOption(value = "--flag", defaultValue = "...") String arg) {
        // ...
    }
}
```

- Return `String` for simple output. Methods throw exceptions on error — Spring Shell prints the stack trace.
- Planned commands: `scan`, `plan`, `run`, `status`, `resume`, `validate`. Most accept `--manifest path`.

## Spring JDBC + SQLite

**No ORM** — use `JdbcTemplate` directly. SQLite is embedded (WAL mode, 5000ms busy timeout).

Connection init (already in `application.properties`):

```properties
spring.datasource.hikari.connection-init-sql=PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000; PRAGMA synchronous=NORMAL;
spring.datasource.hikari.maximum-pool-size=10
```

Pattern for DAO methods:

```java
@Repository
public class TaskStore {
    private final JdbcTemplate jdbc;

    public TaskStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Task> findAll() {
        return jdbc.query("SELECT * FROM tasks", (rs, rowNum) -> new Task(
            rs.getString("task_id"),
            rs.getString("file_path"),
            rs.getString("status")
        ));
    }
}
```

- SQLite PRAGMAs must be set per-connection at init time (done via HikariCP `connection-init-sql`).
- Use `SimpleJdbcInsert` for INSERT with auto-generated keys.
- Task IDs are deterministic SHA-256 hashes (not DB-generated).

## JavaParser AST Traversal

Use `VoidVisitorAdapter<Context>` for file-level traversal. Configure `CombinedTypeSolver` with annotation-driven fallback:

```java
CombinedTypeSolver solver = new CombinedTypeSolver();
solver.add(new JavaParserTypeSolver(sourceRootPath));
// Fallback: if type resolution fails, classify by annotation presence
```

Key patterns:
- **Component detection**: inspect class-level annotations (`@RestController`, `@Service`, `@Component`, `@Repository`)
- **Endpoint mapping**: `@RequestMapping`, `@GetMapping`, `@PostMapping`, etc. — extract path + HTTP verb
- **Event listeners**: `@EventListener`, `@KafkaListener(topics = "...")`
- **Scheduled tasks**: `@Scheduled` — extract cron/fixedDelay/fixedRate
- **Custom validators**: `@Constraint(validatedBy = ...)` — resolve the validator class
- **Method calls**: `MethodCallExpr` for internal call chain mapping

Single file parse failures must never block the full scan — catch and log per file.

## Spring @Async

Phase 2 uses `@Async("orchestratorTaskExecutor")`. Pool already configured:

```properties
spring.task.execution.pool.core-size=5
spring.task.execution.pool.max-size=10
spring.task.execution.pool.queue-capacity=1000
spring.task.execution.thread-name-prefix=c2r-executor-
spring.task.execution.shutdown.await-termination=true
spring.task.execution.shutdown.await-termination-period=30s
```

Enable async in config:

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        return new ThreadPoolTaskExecutor(); // or use auto-configured pool
    }
}
```

Return `CompletableFuture<Result>` from `@Async` methods. Use `CompletableFuture.allOf(...)` for phase barrier synchronization.

## Spring AI 
Dependencies for OpenAI + Anthropic are in `pom.xml`. Spring AI BOM is imported. Client beans are auto-configured when credentials are present (not needed for Phase 1).

## Testing

Test dependencies in `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.shell</groupId>
    <artifactId>spring-shell-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

Shell command tests:

```java
@SpringBootTest
@SpringShellTest
class ScanCommandTest {
    @Test
    void scanProducesIndex(@ShellAutowired Shell shell) {
        // ...
    }
}
```

## Spring Boot Configuration Quirks

- **`spring.main.web-application-type=none`** — this is a CLI, never a web app. Do not add `spring-boot-starter-web`.
- **`-Djline.terminal=jline.UnixTerminal`** — required for Spring Shell on macOS/Linux. Set in `pom.xml` `<jvmArguments>`.
- **No `@Transactional`** — SQLite has limited transaction support via JDBC. Use manual transaction management with `DataSourceUtils` or simple `jdbc.update()` calls.
- **Single-file failures never block full scan** — wrap per-file processing in try/catch, log warning, continue.

