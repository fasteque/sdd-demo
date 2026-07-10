---
title: Respecting a repo's spec-of-record override when running /ce-plan and /ce-work
date: 2026-07-10
category: docs/solutions/workflow-issues
module: ce-plan / ce-work OpenSpec integration
problem_type: workflow_issue
component: development_workflow
severity: medium
applies_when:
  - "Repo CLAUDE.md or AGENTS.md declares an external tool (e.g. OpenSpec) as the single source of truth for feature specs/plans"
  - ce-plan or ce-work is invoked with no arguments (or defaults) inside such a repo
  - Generic skill defaults would write a new docs/plans/ artifact or treat the plan file as read-only during execution, conflicting with the repo-specific instruction
tags: [openspec, ce-plan, ce-work, claude-md, spec-of-record, workflow-override, tasks-md]
related_components: [openspec, ce-plan, ce-work, ce-compound]
---

# Respecting a repo's spec-of-record override when running /ce-plan and /ce-work

## Context

The generic Compound Engineering skills have opinionated defaults:

- `/ce-plan`, invoked with no explicit input, asks the user what to plan and then writes a brand-new artifact to `docs/plans/YYYY-MM-DD-NNN-<type>-<name>-plan.md`.
- `/ce-work`, invoked with no explicit input document, globs `docs/plans/*.md` / `*.html` for the newest implementation-ready plan and stops (asking for an explicit path) if none is found.

This repo (`sdd-demo`) does not use that convention. Its root `CLAUDE.md` declares OpenSpec as the single source of truth for feature specs:

> This project uses **OpenSpec** as the single source of truth for feature specs. All feature work starts with `/opsx:propose`, producing `openspec/changes/<change-name>/proposal.md`, `design.md`, `tasks.md`.
> **Compound Engineering commands (`/ce-plan`, `/ce-work`) MUST read from this path.** Do not create a separate brainstorm/plan document for features that already have an OpenSpec change folder — treat `tasks.md` as the plan.
> Workflow (Tier 2): 1. `/opsx:propose` 2. `/ce-plan` (reads the existing tasks.md, confirms/refines execution approach) 3. `/ce-work` (implements against that plan, checking off tasks.md as it goes) 4. `/ce-code-review` 5. `/opsx:apply` 6. `/ce-compound` 7. `/opsx:archive`

If an agent runs `/ce-plan` or `/ce-work` without first reading this file, two failure modes are both plausible and both bad:

1. `/ce-plan` silently follows its generic default and writes a new `docs/plans/...-plan.md` file. This creates a **second, parallel plan artifact** alongside `openspec/changes/list-assets-pagination/tasks.md` — now there are two documents that could each claim to be "the plan," and nothing forces them to stay in sync.
2. `/ce-work` silently follows its generic default, globs `docs/plans/*.md`, finds **nothing** (because this repo deliberately has no such file), and either stalls asking for a path the user doesn't have, or an under-cautious agent improvises a plan from scratch — bypassing the OpenSpec artifacts entirely.

Either outcome breaks the repo's own documented seven-step pipeline (propose → plan → work → review → apply → compound → archive), which assumes `tasks.md` is the single mutable plan.

## Guidance

Before running `/ce-plan` or `/ce-work` with no explicit path argument, check the repo's root `CLAUDE.md` / `AGENTS.md` for a spec-of-record override. Concretely:

- **Do the check first.** During `/ce-plan`'s own Phase 1 local research step, read `CLAUDE.md` (and `AGENTS.md` if present) before deciding where to write anything. Don't let the skill's internal default fire before this check happens.
- **If an override exists, follow it, not the generic default:**
  - `/ce-plan` should refine the existing spec-tool artifacts *in place* (e.g., edit `openspec/changes/<name>/tasks.md` and `design.md` directly via `Edit`) rather than creating a parallel `docs/plans/` file.
  - `/ce-work` should read that artifact directly as its plan input and **skip the `docs/plans/` discovery glob** entirely — there is nothing to find there by design, and searching for it wastes a turn or causes a false stall.
  - If the repo instruction explicitly says to track progress in that artifact ("checking off tasks.md as it goes"), do so — even when it contradicts a generic skill's own stated default (`ce-work`'s skill doc says "do not mutate the plan body... `ce-work` does not mutate the plan"). The repo-specific instruction is more specific and more authoritative for this repo than the generic skill default.
- **When genuinely ambiguous, ask — don't guess.** If it's unclear how to reconcile a generic skill default with a repo override (e.g., the override's wording doesn't cleanly map onto the current invocation), raise a blocking question with explicit options rather than silently picking one. Do the same for other repo-specific process conventions discovered along the way (e.g., branch vs. direct-to-main commit habits) — check for precedent (`git log`) and surface it as a question rather than assuming the generic default.
- **Verify your own edits before compounding on them.** After editing a shared artifact like `tasks.md` in place, re-run any available validators (`openspec validate` in this repo) to confirm the edit didn't break the artifact's structural integrity.

## Why This Matters

Repo-specific instruction files (`CLAUDE.md`, `AGENTS.md`) exist precisely to redirect generic tooling behavior toward a project's actual conventions. A skill that ignores them and falls back to its built-in default silently produces a second, divergent source of truth for the same feature — in this case, an OpenSpec change folder *and* a `docs/plans/` doc, each partially describing the same work with no guarantee they agree. That ambiguity is worse than either extreme (always use `docs/plans/`, or never use it): it means future readers (human or agent) can no longer tell which document is authoritative, and the repo's own documented pipeline (propose → plan → work → review → apply → compound → archive) — which assumes exactly one plan artifact per feature — breaks down. The fix costs one file read (`CLAUDE.md`) at the start of each skill invocation; the failure it prevents costs a confusing, hard-to-detect documentation fork.

## When to Apply

- Any repository where a root instruction file (`CLAUDE.md`, `AGENTS.md`, or equivalent) declares an external spec-of-record tool — OpenSpec, Linear, Jira, a custom internal spec format, etc. — as authoritative for planning or task tracking.
- Specifically: before invoking `/ce-plan` or `/ce-work` with no explicit path argument, since that's exactly when each skill's built-in discovery/creation default is live and can silently diverge from the repo's convention.
- More generally, the same "check for established precedent before trusting a generic default" instinct applies to any convention a generic skill assumes but a repo might override — e.g., feature-branch-per-change vs. direct-to-main commits (discovered here by checking `git log` for how prior OpenSpec changes were committed, then confirming with the user via a blocking question rather than assuming).
- The same instinct — verify before compounding on an action, especially one that's hard to undo once layered on — also applies mid-task, not just at the start: e.g., checking `git log`/`git show --stat` right after a commit to confirm the diff matches the message, before further commits build on top of a mislabeled one.

## Examples

**1. The blocking question before `/ce-plan` wrote anything.**
Invoked with no arguments, the agent read `CLAUDE.md`, found the OpenSpec override, and — rather than silently choosing a behavior — asked:

> "CLAUDE.md says `/ce-plan` should NOT create a separate `docs/plans/` file... instead read `openspec/changes/<name>/tasks.md` and treat that as the plan, just confirming/refining the execution approach... How should I proceed?"
> - Option A: "Follow CLAUDE.md (Recommended)"
> - Option B: "Create a standard ce-plan doc anyway"

The user chose Option A. No `docs/plans/` file was ever created for this feature.

**2. Editing `tasks.md` in place instead of writing a new plan doc.**
The agent opened `openspec/changes/list-assets-pagination/tasks.md` and `design.md`, and used `Edit` to tighten ambiguous execution details directly in the existing artifact:
- Exact file location for the new `AssetPage` DTO.
- Precise `@RequestParam` defaults for `page` and `size`.
- Exact Spring Data `Page<Asset>` → DTO field mapping.
- Concrete test-seeding instructions for the new MockMvc tests.

After editing, the agent re-ran `openspec validate` to confirm the change folder was still well-formed. `/ce-work` later read this same `tasks.md` directly as its plan (skipping the `docs/plans/*.md` glob entirely, since none exists in this repo by design), and — per CLAUDE.md's explicit "checking off tasks.md as it goes" instruction — flipped all nine `- [ ]` items to `- [x]` as work completed, deliberately overriding the generic `ce-work` skill's own stated default of not mutating the plan body.

**3. Self-correcting a mislabeled commit with a safe, non-destructive fix.**
The same "verify before trusting the obvious next step" instinct showed up again, on a smaller scale, during `/ce-work`'s git workflow: the agent forgot to commit the base implementation before running `/simplify` and `/ce-code-review`, so its first `git commit` accidentally bundled the entire feature (DTO + handler + tests) under a commit message that only described the later "fix(review): enforce insertion-order sort" follow-up work. The agent caught the mismatch by running `git log` and `git show --stat` immediately after committing, noticed the diff didn't match the message, and repaired it with:

```
git reset --soft HEAD~1
```

— safe specifically because the commit was unpushed and private (local-only, on `main`, matching this repo's established no-feature-branch precedent). It then created three correctly-scoped commits in order: `feat(assets): ...` (the base implementation), `fix(review): ...` (the sort fix found in code review), and `docs: ...` (the OpenSpec artifacts and the residual-findings doc, recorded at `docs/residual-review-findings/<head-sha>.md` after the user chose "Accept and record" for the two deferred code-review findings at the Residual Work Gate).

## Related

- `docs/residual-review-findings/c158db1.md` — the code-review residual record from the same feature (unbounded `size`, `AssetPage` vs. `PagedModel<T>`); a different, narrower concern about the implementation itself rather than the planning workflow.
