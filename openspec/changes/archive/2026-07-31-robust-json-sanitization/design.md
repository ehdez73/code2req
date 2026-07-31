## Context

`ExecutionFindingParser.sanitize()` is the single entry point for cleaning LLM responses before JSON parsing. It is called at line 203 of `LlmEnrichmentService.callLlm()`. Currently it handles: markdown fence stripping, trailing-comma removal, spurious quote fixes (e.g., `}"\s*{`), missing comma insertion between adjacent literals, and brace balancing.

However, it does not handle two common LLM failure modes observed in production:
1. **Missing opening brace**: the LLM emits JSON content starting directly with a property name (e.g., `'metadata": {...}`)
2. **JSON in prose**: the LLM wraps JSON in explanatory text, reasoning, or trailing markdown characters (e.g., `{...} ```json {`)

When `sanitize()` produces non-parseable output, `normalize()` returns the sanitized string unchanged and `parseLenient()` also fails — both use `LENIENT_MAPPER.readTree()` which requires at least structurally valid JSON. The cascade triggers error-feedback retries that re-send the full context to the LLM for self-correction, which often produces equally broken responses.

See `proposal.md` for motivation and live-run data.

## Goals / Non-Goals

**Goals:**
- Extract the JSON object from responses that contain surrounding prose or trailing characters
- Normalize single-quoted and mixed-quoted property names to double-quoted
- Wrap JSON content that is missing the opening `{` 
- Keep all changes within `ExecutionFindingParser.sanitize()` and its private helpers

**Non-Goals:**
- Changing the parse flow in `LlmEnrichmentService.callLlm()`
- Modifying `normalize()`, `parseLenient()`, or `ExecutionFindingValidator`
- Handling nested malformations inside string values (e.g., unescaped quotes within strings)
- Removing or changing the retry mechanism

## Decisions

### Decision 1: Run extraction and quote normalization before existing fixes

**Rationale:** The new steps (extract JSON span, normalize quotes) produce cleaner input for the existing fix steps (trailing commas, missing commas, brace balance). Order:
1. Extract JSON span (find first `{`, last balanced `}`)
2. Normalize property-name quotes (regex-based `'(\w+)'(\s*:)` → `"$1"$2` and `'(\w+)"(\s*:)` → `"$1"$2`)
3. Wrap if no `{` found (detect property-name pattern at start, prepend `{`)
4. Existing fixes (markdown fences, trailing commas, spurious quotes, missing commas, brace balance)

**Alternatives considered:**
- **Normalize quotes AFTER existing fixes**: riskier — the quote fix is simpler to reason about on minimally processed text.
- **Merge extraction into a separate method not called by sanitize**: adds complexity to `callLlm()`; better to keep all cleaning in one entry point.

### Decision 2: Extract JSON by scanning for balanced braces with string awareness

**Rationale:** A character-by-character scan tracks `{`/`}` depth and string state (inside `"..."` or not). It records the position of the first `{` and, when depth returns to zero after that point, records that position as the last matching `}`. The substring between them is the JSON object.

This is more robust than:
- **Regex-based extraction**: can't handle nested objects or strings containing `{` or `}`.
- **Splitting on `{`**: loses property names if the response starts mid-object.

### Decision 3: Use regex for property-name quote normalization

**Rationale:** Property names in JSON are simple alphanumeric + underscore strings. Two regex patterns cover the cases:
- `'(\w+)'(\s*:)` → `"$1"$2` (pure single-quoted: `'field':`)
- `'(\w+)"(\s*:)` → `"$1"$2` (mixed quotes: `'field":`)

Both patterns are anchored to `:` to avoid matching single-quoted string values elsewhere in the JSON.

**Alternatives considered:**
- **Full single-quote-to-double-quote conversion**: risks corrupting string values that legitimately contain single quotes.
- **Character-by-character scan with quote tracking**: more complex, no additional benefit over targeted regex.

### Decision 4: Detection of wrap-need uses the same property-name pattern

**Rationale:** If no `{` is found after extraction, the response is checked for a property-name pattern at the start (`'(\w+)["'](\s*:)`). If found, `{` is prepended. This handles the most common missing-brace case without guessing.

## Risks / Trade-offs

- **[Risk] Regex-based quote normalization could match inside string values if a string contains `'word':` pattern.** → **Mitigation:** JSON property names appear immediately after `{`, `,`, or whitespace at the start of a line. The regexes are applied to the entire string, but in practice the LLM never uses `'word':` as a string value — it's always a property name. Also, the `LENIENT_MAPPER` already tolerates single quotes in values.

- **[Risk] Extraction could incorrectly balance braces if a string contains an odd number of `{` or `}`.** → **Mitigation:** The scan tracks string state via `"` detection. Escaped quotes (`\"`) are also tracked. This handles the common case. Maliciously crafted inputs with unescaped `{` in strings are unlikely from an LLM.

- **[Trade-off] The extraction discards all text before the first `{` and after the last `}`.** If the LLM emits valid JSON as a property of a wrapping object that we discard the wrapper of, the extraction would return only the inner object. → **Accepted:** The system prompt instructs the LLM to output only a JSON object. Wrapping objects are not expected.
