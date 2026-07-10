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

This project currently only exercises **Tier 2**. Tier 1 and Tier 3 are documented
here for future reference, not yet used in this repo.

| Tier                                                     | When                                           | Process                                                                                                                                                                                |
|----------------------------------------------------------|------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Tier 1 — Hotfix**                                      | Bug, typo, config change with obvious scope    | `/ce-debug → /ce-code-review`. No spec artifacts required. `/ce-compound` optional but recommended for non-trivial bugs.                                                               |
| **Tier 2 — Standard Feature** *(this project's default)* | Clear requirements, fits a normal work session | See workflow below.                                                                                                                                                                    |
| **Tier 3 — Major Feature**                               | Architecture change, unclear scope             | `/ce-strategy` and/or `/ce-ideate` first to ground the work in strategy (`STRATEGY.md`), then enter the Tier 2 loop, plus a dedicated `/security-review` pass after `/ce-code-review`. |

**Escalation rule:** if scope grows mid-execution beyond the current tier, stop, re-tier, and restart under the correct process rather than continuing under the wrong one.

## Workflow for this project (Tier 2)

1. `/opsx:propose "<feature description>"` — human reviews `proposal.md`, `design.md`, `tasks.md`
2. *(optional)* `/ce-brainstorm` — only if the approach is genuinely unclear; skip when the OpenSpec proposal is already clear
3. `/ce-plan` — reads the existing `openspec/changes/<change-name>/tasks.md`, confirms/refines execution approach
4. `/ce-work` — implements against that plan, checking off `tasks.md` as it goes
5. `/ce-code-review` — parallel specialist review of the implementation
6. `/opsx:verify` — checks the implementation against the original spec (CRITICAL / WARNING / SUGGESTION report)
7. `/opsx:apply` if not already applied by `ce-work`, to keep OpenSpec's own tracking in sync
8. `/ce-compound` — write down what was learned into `docs/solutions/`, organized by category with YAML frontmatter (`module`, `tags`, `problem_type`) — relevant to check when implementing or debugging in a documented area
9. Commit and push manually (review the diff yourself; `/ce-commit-push-pr` exists as a one-shot alternative but isn't used here yet — evaluate separately before adopting, since it also opens a PR automatically)
10. `/opsx:archive` — file the OpenSpec change, update the living spec

**If scope changes mid-execution:** update `tasks.md` first, then re-run `/ce-plan` before continuing — don't let `ce-work` proceed against a stale plan.

## Framework boundaries

Two frameworks collaborate here. Each owns a distinct layer — don't cross it.

| Layer              | Owner    | Commands                                         | Produces                                                                |
|--------------------|----------|--------------------------------------------------|-------------------------------------------------------------------------|
| Spec artifacts     | OpenSpec | `/opsx:propose`, `/opsx:verify`, `/opsx:archive` | `proposal.md`, `design.md`, `tasks.md`, archived change                 |
| Execution planning | CE       | `/ce-plan`                                       | Refined execution plan (reads OpenSpec's `tasks.md`, never replaces it) |
| Implementation     | CE       | `/ce-work`                                       | Code changes; marks tasks `[x]` in OpenSpec's `tasks.md`                |
| Code review        | CE       | `/ce-code-review`                                | Review findings                                                         |
| Knowledge capture  | CE       | `/ce-compound`                                   | `docs/solutions/`                                                       |

**Artifact path contract:** OpenSpec writes to `openspec/changes/<change-name>/`. CE commands (`/ce-plan`, `/ce-work`) MUST read from this path. Never duplicate or shadow OpenSpec artifacts.

## Rules

- Never skip `/ce-code-review`
- Never skip `/ce-compound` for feature work
- Treat `tasks.md` as the executable ground truth during execution
- Write/update tests for changed behavior before considering work done
- Prefer clarity to speed

## Tech stack
Kotlin, Spring Boot 4.1, Spring Data MongoDB, Gradle, tests via JUnit 5 + MockMvc.
