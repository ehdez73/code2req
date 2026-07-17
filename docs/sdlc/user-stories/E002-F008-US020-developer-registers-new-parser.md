# US020 — Developer registers a new parser for a language

**Epic:** E002 — Language Extension Framework
**Feature:** F008 — Parser Discovery & Routing
**Priority:** should | **Estimate:** 2 SP
**Status:** Postponed — E002 is formally de-scoped. This story is retained for future reference.
**Depends on:** US018 | **Blocks:** US021

> As a **Developer**, I want **to register a parser implementation for one or more file extensions**, so that **the CLI knows which parser to use for each source file**.

### Acceptance Criteria

- [ ] Registration maps one or more file extensions (e.g., `.js`, `.mjs`) to a single parser
- [ ] Registration can be done programmatically at startup
- [ ] If no parser is registered for an extension encountered during scanning, the file is logged as unsupported and skipped
- [ ] If multiple parsers claim the same extension, the first registered wins with a logged warning
