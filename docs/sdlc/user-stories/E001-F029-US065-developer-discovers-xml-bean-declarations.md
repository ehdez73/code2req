# US065 — Developer discovers Spring XML bean declarations

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F029 — Spring XML Configuration Analysis
**Priority:** must | **Estimate:** 3 SP
**Depends on:** US006 | **Blocks:** US013

> As a **Developer**, I want **the tool to parse Spring XML configuration files and extract bean declarations, alias mappings, namespace elements, and component scans**, so that **all Spring-managed beans defined in XML are visible in the analysis output without manual review of XML configs**.

### Acceptance Criteria

- [ ] `<bean id="..." class="..." scope="..." factory-method="...">` elements are extracted with id, class, scope, and factory-method captured
- [ ] `<alias name="..." alias="...">` mappings are recorded as bean findings with alias and target name
- [ ] Elements in known Spring namespaces (util, jdbc, task, cache, tx, aop, context, lang, jee, jms, mvc, oxm) are resolved to their Java types via the namespace registry where applicable
- [ ] `<context:component-scan base-package="...">` base packages are captured
- [ ] Non-Spring XML files (e.g., Maven POMs) are silently skipped
- [ ] Nested `<beans profile="...">` elements are recursed into for profile-specific beans
- [ ] Each finding records the source file path and line number

### INVEST Flags

- testable
- edge-cases
