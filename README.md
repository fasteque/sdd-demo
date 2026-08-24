# sdd-demo

A small Kotlin + Spring Boot + MongoDB REST API, used as a testbed for evaluating
**Spec-Driven Development (SDD)** with AI coding agents — specifically whether an
AI-assisted workflow can be genuinely *governed* (reviewed, scoped, escalated,
made consistent across services) rather than just fast.

This is a personal learning sandbox. The domain (an "Asset Catalog") is
intentionally generic and synthetic — the point of this repo is the *process*,
not the API itself.

## Why this exists

Most "AI + development" demos stop at "AI writes code faster." This repo tests a
different, more specific question:

> Can a Product Owner or architect stay the actual decision-maker — reviewing and
> approving intent *before* code is written — while an AI agent handles
> implementation, in a way that scales beyond one developer and one repository?

That question matters for two practical reasons: evaluating AI-native development
practices ahead of a broader organizational rollout, and designing a workflow that
new developers can be onboarded into directly, rather than inheriting undocumented
tribal practice.

## What's actually being tested

Two composable tools, layered on top of Claude Code, each with a distinct job:

- **[OpenSpec](https://github.com/Fission-AI/OpenSpec)** — spec-first workflow.
  Every change starts as a reviewable English proposal (why, what, explicit
  non-goals, trade-offs) *before* any code exists. Specs are plain Markdown,
  version-controlled, reviewable like any other document.
- **[Compound Engineering](https://github.com/EveryInc/compound-engineering-plugin)**
  — execution discipline. Structured planning, multi-angle code review, and
  automatic capture of lessons learned (`docs/solutions/`), so knowledge compounds
  across features instead of resetting every session.

`CLAUDE.md` in this repo defines exactly how the two tools divide responsibility
(OpenSpec owns spec artifacts, CE owns planning/execution/review/knowledge
capture) and a three-tier process (hotfix / standard feature / major initiative)
that the agent is expected to classify itself into.

## Workflow

| Step | Command | What it does |
|---|---|---|
| 1 | `/opsx:explore` | Think through an idea, investigate the codebase — no files created |
| 2 | `/opsx:propose` | Creates the change and generates planning artifacts (`proposal.md`, `specs/`, `design.md`, `tasks.md`) |
| 3 | *(review)* | Human reads and approves the plan before anything is built |
| 4 | `/opsx:apply` | Implements the tasks, checking them off as it goes |
| 5 | `/opsx:update` | Revises the plan mid-flight if needed, keeping artifacts coherent |
| 6 | `/opsx:sync` | Merges the delta spec into the project's living spec |
| 7 | `/opsx:archive` | Files the completed change away |

See `CLAUDE.md` for the full tier definitions and how Compound Engineering's
`/ce-*` commands interleave with this sequence.

## Multi-repo governance

A companion repo, [`sdd-demo-api-contracts`](https://github.com/fasteque/sdd-demo-api-contracts),
holds platform-wide API conventions (standard error-response shape, pagination
shape, naming rules) using OpenSpec's beta **Stores** feature. This repo
references it (`openspec/config.yaml`), and an AI agent working here checks and
applies those conventions on its own when relevant — tested successfully,
including a real case where a small validation fix correctly escalated itself to
a full feature once it recognized it was touching a mandatory shared contract.

## Findings so far

- A full propose → review → implement → archive loop works end-to-end and
  produces genuinely reviewable design trade-offs, not rubber-stamped documents.
- The process self-classifies its own scope (tier) and **escalates itself** when
  a change turns out bigger than initially assumed — observed live, not
  hypothetical.
- Code review consistently catches real issues (a missing sort guarantee, an
  error message never reaching the client) before they ship.
- Cross-repo conventions are discovered and applied **unprompted**, not just when
  explicitly told to check.
- Lessons from one session (including a process mistake made mid-project) get
  written back into the repo, so later sessions inherit them automatically.

## Known limitations

- OpenSpec's multi-repo **Stores** feature is explicitly beta; command shapes may
  still change between releases.
- Compound Engineering has **no cross-repo knowledge capture** yet — a learning
  written in one service's repo is invisible to another (open upstream issue,
  not yet resolved).
- Enforcement of shared conventions is currently review-based, not mechanical —
  no CI linting or schema validation wired up yet.
- No authentication layer anywhere in this app (out of scope for this PoC).
- This has not been tried against any officially sanctioned tooling — it is a
  personal evaluation, not an endorsement of a specific toolchain.

## Running locally

```powershell
docker run -d --name local-mongo -p 27017:27017 mongo:7
./gradlew bootRun
```

Tech stack details, conventions, and the approved-dependency list live in
`docs/tech-stack.md`.

## Status

Active experiment. Endpoints implemented so far: create, get-by-id, list
(paginated), delete. See `openspec/specs/` for the current, living
specification of what this API actually does.
