# ADR-001: JavaParser for AST Analysis

**Status:** Accepted
**Date:** 2026-06-12
**Author:** Solo Developer

## Context

The tool needs to parse Java source files to extract structural metadata: component types, HTTP endpoints, event listeners, call chains, custom validators, and scheduled tasks. Two approaches were considered: a pure-Java parser leveraging JavaParser, or using native bindings via Tree-sitter for broader language support in a single engine.

Key drivers:
- Must run without native OS bindings or JNI (portability constraint)
- Must operate entirely offline with no network calls
- Target codebase is primarily Java/Spring-based (Phase 1 scope)

## Decision

Use **JavaParser** as the AST analysis engine for Phase 1.

JavaParser is a pure-Java library that parses Java source files into a CompilationUnit AST. It handles annotation detection, method/field resolution, and type hierarchy traversal — sufficient for Spring stereotype detection, endpoint mapping, and custom validator extraction.

For files that fail to parse (e.g., syntax errors or unsupported language features), annotation-driven fallback classifies components by annotation presence even when full type resolution fails.

## Consequences

### Positive
- Zero native dependencies — no JNI or platform-specific binaries
- Runs identically on all platforms (Linux, macOS, Windows)
- Simple build setup via single Maven dependency
- Online-free operation — no downloads or license checks at runtime
- Well-documented API with active community

### Negative
- Java-only — does not support other languages without additional parsers
- Cannot leverage Tree-sitter's incremental parsing or broader language grammar ecosystem
- May fail on very new Java syntax features (records, sealed classes, pattern matching) until JavaParser releases updates

### Neutral
- Future language support will require separate parser implementations per language (see E002 — Language Extension Framework)

## Alternatives Considered

- **Tree-sitter with JNI bindings**: Rejected — native bindings violate the "pure Java" constraint, adds build complexity for multiple platforms
- **Manual regex/string-based parsing**: Rejected — too fragile for complex Spring annotation patterns and type resolution
- **Apache Antlr with Java grammar**: Rejected — higher complexity, more boilerplate than JavaParser for the same result

## Related
- E002 — Language Extension Framework (future parsers will use separate implementations)
