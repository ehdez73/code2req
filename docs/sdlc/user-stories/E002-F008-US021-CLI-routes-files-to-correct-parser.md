# US021 — CLI routes files to the correct parser by extension

**Epic:** E002 — Language Extension Framework
**Feature:** F008 — Parser Discovery & Routing
**Priority:** should | **Estimate:** 3 SP
**Status:** Postponed — E002 is formally de-scoped. This story is retained for future reference.
**Depends on:** US020 | **Blocks:** US022

> As a **Developer**, I want **the CLI to automatically route each source file to the correct parser based on its file extension**, so that **I don't need to manually specify which parser to use**.

### Acceptance Criteria

- [ ] The routing engine looks up the file extension in the parser registry
- [ ] If a parser is found, the file is sent to that parser for analysis
- [ ] If no parser is found, the file is skipped with a logged reason
- [ ] Routing is invisible to the user unless verbose/debug mode is enabled
