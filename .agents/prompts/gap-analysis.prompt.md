Act as a Senior Product Analyst, Software Architect, and Technical Auditor.

Your task is to perform a comprehensive consistency audit between the product documentation, functional specifications, architectural documentation, and the current implementation of the system.

## Sources to Analyze

You must analyze and correlate information from all of the following sources:

### Product Documentation

* PRD: `docs/PRD.md`
* User Stories: `docs/sdlc/user-stories/*.md`
* Features (Gherkin): `docs/sdlc/features/*.feature`

### Technical Documentation

* ADRs (Architecture Decision Records): `docs/sdlc/adrs/*.md`

### Implementation

* The entire relevant source code of the project.

---

## Objectives

### 1. Validate Alignment Between Documentation and Implementation

Identify:

* Features implemented in the code but not documented.
* Requirements documented but not implemented.
* Partially implemented requirements.
* Implementations that differ from the documented specification.
* Obsolete functionality.
* Undocumented business rules or behaviors.
* Missing, outdated, or inconsistent documentation.

### 2. Validate Consistency Across Documentation Artifacts

Analyze consistency between:

* PRD ↔ User Stories
* PRD ↔ Features
* User Stories ↔ Features
* ADRs ↔ PRD
* ADRs ↔ User Stories
* ADRs ↔ Features
* Documentation ↔ Implementation

Identify:

* Requirements present in one artifact but missing from others.
* Contradictions and inconsistencies.
* Ambiguous requirements.
* Business rules that differ across documents.
* Features that evolved in the implementation without corresponding documentation updates.
* Functional flows that diverge between documentation and implementation.

### 3. Validate End-to-End Traceability

Verify that traceability exists between:

* Business requirements (PRD)
* User Stories
* Features (Gherkin)
* Architectural Decisions (ADRs), when applicable
* Source Code Implementation

Identify any gaps, missing links, or broken traceability chains.

---

## Analysis Instructions

For every requirement, user story, feature, business rule, or functionality identified:

1. Locate the exact source reference.
2. Identify the corresponding implementation.
3. Provide supporting evidence.
4. Explain your reasoning.
5. Assign a confidence level to the conclusion.

Whenever possible, include:

* Document references
* File paths
* Classes
* Functions
* Components
* Services
* Modules

Classify each finding as one of the following:

* Correctly Implemented
* Partially Implemented
* Not Implemented
* Implemented but Not Documented
* Documented but Not Implemented
* Implemented Differently
* Documentation Inconsistency
* Requires Manual Validation

---

# Output Format

Save the audit results in a structured markdown report with the following sections under docs/gap-analysis/gap-analysis-report-<timestamp>.md:  

## 1. Executive Summary

Provide:

* Overall alignment assessment.
* Major risks.
* Areas with the highest divergence.
* Documentation quality assessment.
* Traceability maturity assessment.

---

## 2. Traceability Matrix

| Requirement / Capability | PRD | User Story | Feature | ADR | Code Reference | Status |
| ------------------------ | --- | ---------- | ------- | --- | -------------- | ------ |

---

## 3. Documentation Findings

### 3.1 PRD Requirements Missing from User Stories

### 3.2 PRD Requirements Missing from Features

### 3.3 User Stories Without PRD Coverage

### 3.4 Features Without PRD Coverage

### 3.5 ADRs Without Clear Functional Justification

### 3.6 Documentation Contradictions

### 3.7 Ambiguous or Incomplete Documentation

---

## 4. Implementation Findings

### 4.1 Implemented but Not Documented

List functionality found in the codebase that is not reflected in:

* PRD
* User Stories
* Features
* ADRs

### 4.2 Documented but Not Implemented

List all requirements, stories, or features that cannot be found in the implementation.

### 4.3 Partially Implemented Items

Describe what is implemented and what remains missing.

### 4.4 Implementation Deviations

Describe cases where implementation behavior differs from documented expectations.

### 4.5 Potentially Obsolete or Dead Functionality

Identify code that appears to no longer be represented in current documentation.

---

## 5. Feature (Gherkin) Analysis

For each feature:

* Verify alignment with the PRD.
* Verify alignment with User Stories.
* Verify implementation coverage.
* Identify missing scenarios.
* Identify undocumented implemented scenarios.
* Identify acceptance criteria mismatches.

---

## 6. User Story Analysis

For each User Story:

* Verify alignment with the PRD.
* Verify supporting Features.
* Verify implementation coverage.
* Validate acceptance criteria.
* Identify missing acceptance criteria.
* Identify implemented behaviors not reflected in the story.

---

## 7. ADR Analysis

For each ADR:

* Summarize the architectural decision.
* Identify the business requirements it supports.
* Verify whether the implementation follows the documented decision.
* Highlight architectural deviations.
* Identify decisions that may no longer reflect reality.

---

## 8. Traceability Gaps

Identify:

* Requirements with no User Story.
* User Stories with no Feature.
* Features with no implementation.
* Code with no documented origin.
* ADRs with no implementation evidence.

---

## 9. Documentation Update Plan

Propose a prioritized plan to update:

* PRD
* User Stories
* Features
* ADRs

For each recommendation include:

* Reason for change.
* Priority.
* Impact.
* Suggested owner (Product, Engineering, Architecture, etc.).

---

## 10. Implementation Backlog

Generate a prioritized backlog of all documented requirements that are not fully implemented.

For each item include:

* Source reference.
* Description.
* Priority (High / Medium / Low).
* Business impact.
* Estimated implementation complexity (High / Medium / Low).
* Dependencies.
* Recommended next action.

---

## 11. Recommendations

Provide actionable recommendations to:

* Restore alignment between documentation and implementation.
* Improve traceability.
* Reduce documentation debt.
* Reduce technical debt.
* Improve future governance and change management processes.

---

## Quality Requirements

* Do not make assumptions that are not supported by evidence.
* Always cite the source of every conclusion.
* Clearly distinguish facts from hypotheses.
* Explicitly state any limitations encountered during the analysis.
* Be exhaustive and systematic.
* Favor accuracy and traceability over brevity.
* Treat the PRD, User Stories, Features, ADRs, and source code as potentially authoritative sources, and explicitly highlight any conflicts between them.
* When conflicts exist, do not resolve them automatically; instead, document the conflict and explain its impact.
