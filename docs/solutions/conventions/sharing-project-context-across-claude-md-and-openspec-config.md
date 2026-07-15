---
title: Single-sourcing conventions across CLAUDE.md and openspec/config.yaml
date: 2026-07-15
category: docs/solutions/conventions
module: "docs / project configuration (CLAUDE.md, openspec/config.yaml, docs/tech-stack.md)"
problem_type: convention
component: documentation
severity: low
applies_when:
  - "Two or more AI tooling entry points (CLAUDE.md, openspec/config.yaml's context field, other agent config files) need to share the same project conventions or tech-stack description"
  - "You are deduplicating inline tech-stack/convention text that currently exists in more than one config file"
  - "You need to decide whether a shared-context reference will be mechanically guaranteed to load or merely agent-judgment-based"
tags: [openspec, claude-md, context-sharing, config-dedup, at-import, single-source-of-truth, documentation, yaml-config]
related_components: [tooling, development_workflow]
---

# Single-sourcing conventions across CLAUDE.md and openspec/config.yaml

## Context

The `sdd-demo` repo (Kotlin/Spring Boot, using OpenSpec for specs and Compound Engineering for execution) had the same tech-stack and conventions text duplicated in two places consumed by two different tools:

- `CLAUDE.md`'s `## Tech stack` section — read natively by Claude Code at the start of every session.
- `openspec/config.yaml`'s `context:` field — read by the separate `openspec` CLI binary and surfaced to whichever agent runs OpenSpec's propose/apply skills via `openspec instructions <artifact> --json`.

Both blocks listed near-identical content: Kotlin, Spring Boot 4.1, Spring Data MongoDB, Gradle, JUnit 5 + MockMvc testing conventions, API/style/error-response conventions. The question was whether this could be merged into one file that both tools reference, and — critically — whether OpenSpec, Compound Engineering (`/ce-plan`, `/ce-work`), and Claude Code would actually resolve such a reference, or whether it would just be inert text sitting in a config file.

## Guidance

Treat the two consumers as having fundamentally different reference mechanisms — do not assume a reference that works for one will work the same way for the other:

1. **CLAUDE.md supports a real, native, mechanical import.** The `@path/to/file` syntax is a documented Claude Code feature: referenced files are recursively and automatically inlined into context at session start (up to 5 hops of recursion). This is a hard guarantee with no agent judgment involved.

2. **`openspec/config.yaml`'s `context:` field has no equivalent mechanism.** YAML has no file-include syntax, and the `openspec` CLI is a separate tool (not Claude) that just echoes whatever string is in `context:` verbatim as JSON via `openspec instructions <artifact-id> --change <name> --json` (consumed by the `/opsx:propose`, `/opsx:apply` skills). A pointer sentence placed there ("see docs/tech-stack.md") only gets resolved because the **agent executing the OpenSpec skill** — which happens to have Read tool access and is instructed to apply `context` as a constraint — chooses to go read the referenced file. This is agent-judgment-based, not mechanical, and therefore a materially weaker guarantee than the CLAUDE.md import. It also needs to keep working for non-Claude coding agents: this repo's installed OpenSpec skills ship Cursor/Codex/Kimi plugin variants (`.cursor-plugin`, `.codex-plugin`, `.kimi-plugin` directories alongside the Claude Code skill) in addition to Claude's, so a bare pointer relies on whichever agent is acting choosing to read it — a reasonably safe bet for agentic coding tools with file-read tools, but not a hard guarantee the way `@import` is for Claude Code.

Given that, the applied solution:

1. Create a single source-of-truth file, e.g. `docs/tech-stack.md`, containing the tech stack (Kotlin, Spring Boot 4.1, Spring Data MongoDB, Gradle), API conventions, style conventions, testing conventions, error-response conventions, and a one-line project-nature note.

2. In `CLAUDE.md`, replace the inline conventions section with a native import:
   ```
   ## Tech stack

   @docs/tech-stack.md
   ```

3. In `openspec/config.yaml`, replace the duplicated inline `context:` block with a short pointer sentence (not an import — YAML/the openspec CLI has no import mechanism):
   ```yaml
   context: |
     Tech stack, API/style/testing/error-response conventions, and project nature:
     read docs/tech-stack.md (relative to repo root) before writing constraints for any artifact.
   ```

## Why This Matters

- Removes duplicated conventions text that would otherwise drift out of sync as the stack evolves (e.g. a Spring Boot version bump would need editing in two places, and would silently miss one if forgotten).
- Makes explicit which reference is mechanically guaranteed (CLAUDE.md's `@import`) versus which one depends on agent behavior (`config.yaml`'s prose pointer). Without this distinction, a future editor might assume both files behave the same way, silently trust an unverified pointer, or over-invest in trying to build a YAML-level include mechanism that doesn't exist.
- Confirms that a plain pointer sentence is not "just inert text" in practice for OpenSpec's real workflow — the acting agent does follow it — but that this is a good-agent-behavior outcome, not a language-level guarantee. This matters for anyone auditing whether the config.yaml context is actually load-bearing.

## When to Apply

- Whenever the same conventions/context need to be shared across CLAUDE.md and another tool's config file (OpenSpec's `config.yaml`, or any other CLI-driven tool with its own instructions/context field).
- Before assuming a "just point one file at the other" fix is equivalent regardless of which file does the pointing — check whether the consuming tool has a real import/include mechanism (like Claude Code's `@path`) or only a plain string field that some agent must choose to resolve.
- When multiple coding-agent integrations (Claude, Cursor, Codex, Kimi, etc.) share the same OpenSpec skill set, since a prose pointer must work across all of them, not just Claude Code.

## Examples

**Before** — duplicated content in both files (abridged):

`CLAUDE.md`:
```
## Tech stack
Kotlin, Spring Boot 4.1, Spring Data MongoDB, Gradle, tests via JUnit 5 + MockMvc.
```

`openspec/config.yaml`:
```yaml
context: |
  Tech stack: Kotlin, Spring Boot 4.1, Spring Data MongoDB, Gradle.
  Testing: JUnit 5 + MockMvc.
  API conventions: ...
  Style conventions: ...
```

**After** — single source of truth in `docs/tech-stack.md`, referenced two different ways depending on what each consumer actually supports:

`CLAUDE.md` (mechanical import, recursively inlined by Claude Code):
```
## Tech stack

@docs/tech-stack.md
```

`openspec/config.yaml` (prose pointer, resolved only because the acting agent reads it):
```yaml
context: |
  Tech stack, API/style/testing/error-response conventions, and project nature:
  read docs/tech-stack.md (relative to repo root) before writing constraints for any artifact.
```

**Verification performed**: Ran `/opsx:propose` end-to-end for a trivial test change (`add-health-check-endpoint`). For every artifact (`proposal`, `design`, `specs`, `tasks`), `openspec instructions <artifact-id> --change add-health-check-endpoint --json` returned a `"context"` field containing exactly the pointer sentence verbatim — confirming the CLI performs no expansion and just echoes the YAML string. The agent then read `docs/tech-stack.md` itself each time and applied its conventions (e.g. MockMvc + JUnit5 testing convention, "no custom error body shape yet" convention) when writing each artifact, without copying the raw context block into the output files — consistent with OpenSpec's own rule that "context and rules are constraints for you, not content for the file." This confirmed the pointer mechanism actually resolves in this repo's real OpenSpec CLI version, not just in theory.

## Related

- `docs/solutions/workflow-issues/honor-claude-md-plan-of-record-overrides.md` — a different repo-conventions concern about CLAUDE.md (honoring its spec-of-record override for `/ce-plan`/`/ce-work` at runtime, rather than static config-content deduplication). Low overlap; no merge warranted, cross-linked here for anyone auditing CLAUDE.md-related conventions.
