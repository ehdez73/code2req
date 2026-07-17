# US068 — Developer discovers @Bean methods in configuration classes

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F030 — @Bean Method Detection
**Priority:** must | **Estimate:** 2 SP
**Depends on:** US006 | **Blocks:** US013

> As a **Developer**, I want **the tool to detect `@Bean`-annotated methods in `@Configuration` and `@SpringBootApplication` classes**, so that **Java-based Spring bean definitions are captured in the analysis output alongside component-scan and XML-based beans**.

### Acceptance Criteria

- [ ] `@Bean` methods in classes annotated with `@Configuration` are detected and recorded
- [ ] `@Bean` methods in classes annotated with `@SpringBootApplication` are detected and recorded
- [ ] Explicit bean name from `@Bean("name")` single-member form is extracted
- [ ] Explicit bean name from `@Bean(name = "name")` or `@Bean(value = "name")` normal annotation form is extracted
- [ ] First name from an array form `@Bean(name = {"a", "b"})` is used as the primary bean name
- [ ] Implicit bean name falls back to the method name when no explicit name is provided
- [ ] The declared return type of the `@Bean` method is captured
- [ ] The enclosing configuration class name and source file path are recorded
- [ ] Interfaces and abstract classes are skipped — no `@Bean` detection in non-concrete types
- [ ] Classes without `@Configuration` or `@SpringBootApplication` are skipped even if they contain `@Bean`

### INVEST Flags

- testable
- edge-cases
