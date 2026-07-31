## Context

`sanitize()` currently runs: wrap → extract → normalize → existing fixes. This causes `'metadata": {...valid...} ```json {` to be wrapped into `{'metadata": {...valid...} ```json { }` before extraction, making the `}` inside the cruft become part of the JSON.

The fix reverses to: extract → wrap → normalize → existing fixes, and teaches `extractJsonSpan` to include the property-name prefix before `firstBrace`.

## Goals / Non-Goals

**Goals:**
- Reorder `sanitize()` steps to extract before wrap
- Teach `extractJsonSpan` to preserve leading property-name prefixes

**Non-Goals:**
- Changing `normalizePropertyQuotes` or existing fixes
- Modifying `LlmEnrichmentService`

## Decisions

### Decision 1: Extract before wrap

**Rationale:** Extraction removes trailing noise. Wrap adds `{...}` around content missing an outer brace. Doing extract first means wrap never sees trailing ````json {` garbage.

### Decision 2: `extractJsonSpan` checks for property-name prefix before `firstBrace`

**Rationale:** After extraction, `'metadata": {"a":1}` becomes `{"a":1}` because `firstBrace` is at the inner `{`. The fix: scan backward from `firstBrace` — if the preceding non-whitespace characters form a pattern like `'?\w+'?\s*:`, include them in the span.

Implementation: after finding `firstBrace`, check `s.substring(0, firstBrace).trim()` for a property-name pattern ending at the trim boundary. If found, walk back to include the property name.

## Risks / Trade-offs

- **[Risk] Back-scanning could include noise if the response has multiple property-name patterns before the first `{`.** → **Mitigation:** Only the characters immediately adjacent to `firstBrace` are checked. The regex `^['\"]?\w+['\"]?\s*:$` against the trimmed prefix limits false positives.
