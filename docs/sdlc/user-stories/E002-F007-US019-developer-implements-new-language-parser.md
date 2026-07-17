# US019 — Developer implements a new language parser

**Epic:** E002 — Language Extension Framework
**Feature:** F007 — Parser Abstraction SPI
**Priority:** should | **Estimate:** 5 SP
**Status:** Postponed — E002 is formally de-scoped. This story is retained for future reference.
**Depends on:** US018 | **Blocks:** US021, US022

> As a **Developer**, I want **to implement the LanguageParser interface for a new language**, so that **the CLI can analyze codebases written in that language**.

### Acceptance Criteria

- [ ] A parser class implements the LanguageParser interface for a specific language (e.g., JavaScript, Python)
- [ ] The parser returns language-appropriate classifications for components
- [ ] Unsupported capabilities return empty collections rather than errors
- [ ] Parse failures for individual files are logged and do not stop the full scan
