# Step Library — Canonical Step Definitions

Canonical registry of reusable Gherkin step definitions. Serves as the single source of truth for step wording across all `.feature` files, ensuring consistency and reducing step proliferation.

## Manifest & Configuration

- Given a valid project-manifest.yaml {exists | contains}
- Given a project-manifest.yaml with {condition}
- Given a project-manifest.yaml missing the required field "{field}"
- When the CLI loads the manifest
- When the developer runs manifest validation
- When the CLI runs the scan
- Then the manifest is accepted without errors
- Then validation {passes | fails} with {outcome}

## Dependency Resolution

- Given Maven is {installed | not installed | available} {and | but} {condition}
- When the CLI {resolves | attempts} dependency resolution
- Then heuristic mode activates automatically
- Then a {clear warning | warning} is logged {indicating | with} {reason}

## AST Analysis

- Given a scan target containing {content}
- Given a {component type} with {annotation or method}
- When the CLI analyzes the Java source files
- When the CLI extracts {HTTP endpoints | event listeners | call chains | validation logic | scheduled tasks}
- Then each {item} is {classified | captured | identified} with {detail}
- And the {item} is linked to its {owning component}

## Secret Redaction & Filtering

- Given a Java source file containing {content}
- Given a scan target containing a {path} with {content}
- When the CLI {redacts secrets | applies exclude filters} {before | during} output
- Then {secret value} is replaced with [REDACTED:{type}]
- And the original source file on disk remains unchanged
- And the {exclusion | redaction} is reported in the scan summary

## Index & Persistence

- Given a completed scan with {condition}
- When the CLI generates the output index
- When the CLI persists results to the local database
- When the CLI re-runs the scan
- Then a single structured {format} file is produced at {path}
- Then a {database type} database is created at the configured path

## Scan Orchestration

- Given a valid project-manifest.yaml with existing scan targets
- Given the machine has {no | no outbound} {condition}
- When the developer runs the scan command
- When the scan encounters a failure in the {stage} stage
- Then the pipeline executes in order: {stages}
- Then progress is reported per stage with file counts and elapsed time
- Then the CLI exits with a {zero | non-zero} exit code
