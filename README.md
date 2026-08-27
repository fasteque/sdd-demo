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
- No app-level authentication — only the asset endpoints are gated, and only at
  the gateway (API key via Kong's `key-auth` plugin); the app itself trusts
  anything Kong forwards to it.
- This has not been tried against any officially sanctioned tooling — it is a
  personal evaluation, not an endorsement of a specific toolchain.

## Running the application

Three ways to run it, depending on what you need. All require Docker
(Docker Desktop or another Docker Compose-compatible engine) running.

### 1. Local development

MongoDB starts and stops automatically — `spring-boot-docker-compose`
manages it via `compose.yaml` for the lifetime of the JVM. No manual
`docker run` needed.

```powershell
./gradlew bootRun
```

App is available at `http://localhost:8080`.

### 2. Running tests

Same auto-start mechanism as above — the test JVM gets a fresh MongoDB
automatically:

```powershell
./gradlew test
```

### 3. Fully containerized (app + MongoDB + Kong Gateway)

Builds the app jar on the host, then runs the app, MongoDB, and Kong
Gateway (OSS, DB-less mode) together in Docker — no Gradle inside the
image. This is a separate stack from `compose.yaml` above (see
`compose.app.yaml`); the local-dev loop is unaffected.

Before the first run, create a local `.env` (gitignored, never committed)
from the checked-in template, and put a real key value in it:

```powershell
cp .env.example .env
# then edit .env and replace the placeholder with a real value
```

```powershell
./gradlew bootJar
docker compose -f compose.app.yaml up --build
```

App is available at `http://localhost:8080`, proxied through Kong
Gateway — the app's own port is not published to the host in this mode,
only reachable from Kong over the internal Docker network. Kong's routing
is declarative, defined in the version-controlled `kong/kong.yml` (one
service, two routes: `/health` is open, and the asset endpoints
(`/assets`, `/assets/{id}`) require an API key via Kong's `key-auth`
plugin — send it as an `apikey` header). The committed `kong/kong.yml`
holds a placeholder instead of the real key; it's substituted in from
`.env` at container startup and never touches version control. A path
matching neither route (i.e. anything other than `/health` or an asset
path) gets a 404 straight from Kong, not the app. MongoDB is likewise not
published to the host — the app reaches it over the internal Docker
network only. Stop with:

```powershell
docker compose -f compose.app.yaml down
```

Add `-v` to also delete the MongoDB data volume.

```powershell
# Example: calling a protected endpoint
# (curl.exe forces the real curl binary -- PowerShell's default `curl` alias
# is Invoke-WebRequest, which does not accept a bare -H flag)
curl.exe http://localhost:8080/assets -H "apikey: <your key from .env>"
```

Or run `kong/test-gateway.sh` (requires a POSIX shell, e.g. Git Bash or WSL)
against the running stack to check the health/auth/routing behavior end to end.

### Building just the image

To build the app image alone, e.g. to run it against your own MongoDB
instance instead of the bundled `compose.app.yaml` one:

```powershell
./gradlew bootJar
docker build -t sdd-demo .
docker run -p 8080:8080 -e SPRING_MONGODB_URI="mongodb://<your-mongo-host>:27017/sdddemo" sdd-demo
```

Tech stack details, conventions, and the approved-dependency list live in
`docs/tech-stack.md`.

## Status

Active experiment. Endpoints implemented so far: create, get-by-id, list
(paginated), delete. See `openspec/specs/` for the current, living
specification of what this API actually does.
