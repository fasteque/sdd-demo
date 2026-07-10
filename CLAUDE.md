# Project Conventions for AI-Assisted Development

## Spec ownership

This project uses **OpenSpec** as the single source of truth for feature specs.
All feature work starts with `/opsx:propose`, producing:
- `openspec/changes/<change-name>/proposal.md`
- `openspec/changes/<change-name>/design.md`
- `openspec/changes/<change-name>/tasks.md`

**Compound Engineering commands (`/ce-plan`, `/ce-work`) MUST read from this path.**
Do not create a separate brainstorm/plan document for features that already have
an OpenSpec change folder — treat `tasks.md` as the plan.

## Workflow for this project (Tier 2)

1. `/opsx:propose "<feature description>"` — human reviews proposal.md, design.md, tasks.md
2. `/ce-plan` — reads the existing `openspec/changes/<change-name>/tasks.md`, confirms/refines execution approach
3. `/ce-work` — implements against that plan, checking off tasks.md as it goes
4. `/ce-code-review` — parallel specialist review of the implementation
5. `/opsx:apply` if not already applied by ce-work, to keep OpenSpec's own tracking in sync
6. `/ce-compound` — write down what was learned into `docs/solutions/`, organized by category with YAML frontmatter (`module`, `tags`, `problem_type`) — relevant to check when implementing or debugging in a documented area
7. `/opsx:archive` — file the OpenSpec change, update the living spec

## Tech stack
Kotlin, Spring Boot 4.1, Spring Data MongoDB, Gradle, tests via JUnit 5 + MockMvc.