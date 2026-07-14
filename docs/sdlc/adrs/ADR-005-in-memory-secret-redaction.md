# ADR-005: In-Memory Secret Redaction Strategy

**Status:** Accepted
**Date:** 2026-06-12
**Author:** Solo Developer

## Context

The tool processes source files that may contain hardcoded secrets — passwords, API keys, authentication tokens, and other credentials. Before any content is sent to an LLM (Phase 2) or included in output artifacts, these secrets must be removed to prevent leakage.

Key requirements:
- Zero-touch policy — source files on disk must never be modified
- Redaction must happen before any outbound processing
- Placeholders must preserve the type of redacted value for debugging
- Must work with pattern-based detection (no runtime secret scanning service)

## Decision

Implement **in-memory secret redaction** using pattern-based detection that operates entirely on file content loaded into memory. Source files on disk are read but never written to.

Redacted values are replaced with structured placeholders: `[REDACTED:type]` where `type` identifies the secret category (e.g., `password`, `api_key`, `token`, `connection_string`).

The redaction pipeline:
1. Read source file content into memory
2. Apply pattern matching against known credential patterns (assignment patterns, annotation values, environment variable defaults)
3. Replace matched values with `[REDACTED:type]` placeholders
4. Pass redacted content to downstream processing (output generation, LLM prompts)
5. Original file on disk remains completely untouched

## Consequences

### Positive
- Zero risk of source file corruption — read-only by design
- No cleanup needed — no temporary files or backup copies
- Pattern detection is auditable and testable
- Placeholder types preserve debugging context without exposing secrets
- Works fully offline — no outbound secret scanning API calls

### Negative
- Pattern-based detection may miss non-standard credential patterns (e.g., encrypted keys, obfuscated strings)
- May produce false positives on benign values that match credential patterns (mitigated by documented coverage scope)
- No contextual understanding — cannot distinguish a production password from a test fixture without heuristics

### Neutral
- Coverage scope is explicitly documented; users are informed of limitations

## Alternatives Considered

- **File-level redaction (copy + modify on disk)**: Rejected — violates zero-touch constraint; creates cleanup burden
- **Outbound proxy redaction (redact at the network layer)**: Rejected — Phase 1 has no network calls; adds complexity for Phase 2
- **Third-party secret scanning service**: Rejected — violates offline requirement; adds external dependency
- **No redaction (assume no secrets in source)**: Rejected — high risk; R001 in context identifies secret leakage as a high-impact risk

## Related
- NFR002 — Hardcoded secrets must be redacted in-memory before any LLM processing
- R005 — Secret redaction misses non-standard patterns or false positives
- US011 — Developer prevents secret leakage
