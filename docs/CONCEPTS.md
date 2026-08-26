
# Concepts

Shared domain vocabulary for this project — entities, named processes, and status concepts with project-specific meaning. Seeded with core domain vocabulary, then accretes as ce-compound and ce-compound-refresh process learnings; direct edits are fine. Glossary only, not a spec or catch-all.

## OpenSpec structure

### Capability
A named unit of system behavior tracked by OpenSpec, identified by a directory path under the specs tree (a repo's own `openspec/specs/` or a store's equivalent). A capability owns exactly one Main Spec and can be the target of many Delta Specs over its lifetime, one per change that touches it.

### Main Spec
The single, currently-synced `spec.md` for a Capability — the living statement of what that capability must do right now. Delta Specs merge into it; it is never edited by hand to add new requirements outside that merge process.

### Delta Spec
A change-scoped `spec.md`, living under an in-progress change's own directory rather than the capability's permanent location, that describes how a Capability's Main Spec should change (add, modify, remove, or rename requirements). It is a diff-shaped document, not a copy of the full spec — merging it into the Main Spec is a separate step, not automatic.

### Store
A standalone OpenSpec repository, distinct from the current project's own embedded spec tree, registered so its specs can be referenced by name from within this project's OpenSpec workflows. A store exists to hold specs shared across multiple projects (for example, platform-wide conventions) rather than specs owned by any single one of them.
