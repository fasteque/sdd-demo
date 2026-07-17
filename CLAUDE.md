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

## Tiers

**Identify the tier before doing anything else.**

| Tier                                                | When                                           | Process             |
|-----------------------------------------------------|------------------------------------------------|---------------------|
| **Tier 1 — Hotfix**                                 | Bug, typo, config change with obvious scope    | See workflow below. |
| **Tier 2 — Standard Feature** *(project's default)* | Clear requirements, fits a normal work session | See workflow below. |
| **Tier 3 — Major Feature**                          | Architecture change, unclear scope             | See workflow below. |

**Escalation rule:** if scope grows mid-execution beyond the current tier, stop, re-tier, and restart under the correct process rather than continuing under the wrong one.

### Tier 1 — Hotfix

No OpenSpec artifacts. Skip `/opsx:propose` entirely — the point of Tier 1 is that the fix is small and obvious enough not to need a reviewed plan.

1. If it's a genuine bug: `/ce-debug` — investigates and fixes the root cause directly
2. If it's not a bug (typo, config tweak, dependency bump): just make the edit directly, no command needed
3. `/ce-code-review` — still required, even for a one-line change
4. `/ce-compound` — optional, but recommended if the bug was non-obvious (i.e., if the root cause taught you something worth not re-learning later)
5. Commit and push manually

### Tier 2 — Standard Feature

1. `/opsx:propose "<feature description>"` — human reviews `proposal.md`, `design.md`, `tasks.md`
2. *(optional)* `/ce-brainstorm` — only if the approach is genuinely unclear; skip when the OpenSpec proposal is already clear
3. `/ce-plan` — reads the existing `openspec/changes/<change-name>/tasks.md`, confirms/refines execution approach
4. `/ce-work` — implements against that plan, checking off `tasks.md` as it goes
5. `/ce-code-review` — parallel specialist review of the implementation
6. `/opsx:apply` if not already applied by `ce-work`, to keep OpenSpec's own tracking in sync
7. `/ce-compound` — write down what was learned into `docs/solutions/`, organized by category with YAML frontmatter (`module`, `tags`, `problem_type`) — relevant to check when implementing or debugging in a documented area
8. `/opsx:sync` — merge the delta spec into the living spec
9. `/opsx:archive` — file the OpenSpec change
10. Commit and push manually (this final commit captures the implementation, the compound learnings, the synced spec, and the archived change together; `/ce-commit-push-pr` exists as a one-shot alternative but isn't used here yet — evaluate separately before adopting, since it also opens a PR automatically)

### Tier 3 — Major Feature

Triggers: architecture changes, anything spanning multiple repos/services, or scope that's genuinely unclear at the start.

1. `/ce-strategy` — establishes or updates `STRATEGY.md` (problem, target persona, success metrics). Must exist and be complete before any spec work begins.
2. `/ce-ideate` — explore and rank multiple approaches before committing to one, when the right approach genuinely isn't obvious yet
3. Then: **follow the Tier 2 workflow above exactly, steps 1–10, with one addition** — insert a dedicated `/security-review` pass immediately after step 5 (`/ce-code-review`). This is a native Claude Code skill, not part of OpenSpec or CE, run as a second, separate pass rather than folded into the regular review step.

## Framework boundaries

Two AI frameworks and one build-time toolchain collaborate here. This isn't the full command list (see **Tiers** above for that) — it's specifically the layers where two tools could collide over the same artifact. Each layer has exactly one owner — don't cross it.

| Layer                 | Owner             | Commands                                       | Produces                                                                                                            |
|-----------------------|-------------------|------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| Spec artifacts        | OpenSpec          | `/opsx:propose`, `/opsx:sync`, `/opsx:archive` | `proposal.md`, `design.md`, `tasks.md`, archived change                                                             |
| Execution planning    | CE                | `/ce-plan`                                     | Refined execution plan (reads OpenSpec's `tasks.md`, never replaces it)                                             |
| Implementation        | CE                | `/ce-work`                                     | Code changes; marks tasks `[x]` in OpenSpec's `tasks.md`                                                            |
| Code review           | CE                | `/ce-code-review`                              | Review findings                                                                                                     |
| Knowledge capture     | CE                | `/ce-compound`                                 | `docs/solutions/`                                                                                                   |
| Wire contracts (HTTP) | OpenAPI Generator | `./gradlew openApiGenerate`                    | Generated Kotlin server interfaces + models under `build/generated/openapi` (gitignored, regenerated at build time) |

**Artifact path contract:** OpenSpec writes to `openspec/changes/<change-name>/`. CE commands (`/ce-plan`, `/ce-work`) MUST read from this path. Never duplicate or shadow OpenSpec artifacts.

## Rules

- Never skip `/ce-code-review`
- Never skip `/ce-compound` for feature work
- Treat `tasks.md` as the executable ground truth during execution
- **If scope changes mid-execution:** update `tasks.md` first, then re-run `/ce-plan` before continuing — don't let `ce-work` proceed against a stale plan.
- Write/update tests for changed behavior before considering work done
- Prefer clarity to speed
- For HTTP endpoints covered by `openapi/openapi.yaml`, the spec is authoritative for wire contracts — edit it before writing/changing controller code, then regenerate (`./gradlew openApiGenerate`) before implementing
- Don't hand-declare request/response DTOs for OAS-covered endpoints — implement the generated interface/model instead (see `docs/tech-stack.md`)
- New dependencies must be on the approved list in `docs/tech-stack.md`; anything else requires explicit confirmation first, proposed with rationale in `design.md` — never add a library for a single narrow use case without asking

## Tech stack

@docs/tech-stack.md
