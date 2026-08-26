---
title: Surfacing cross-store convention conflicts and existing-spec contradictions while drafting an OpenSpec proposal
date: 2026-08-26
category: docs/solutions/workflow-issues
module: openspec propose / cross-store references & delta-spec consistency
problem_type: workflow_issue
component: development_workflow
severity: medium
applies_when:
  - "Writing an OpenSpec proposal/design for a change that is not itself a repo's own OpenAPI-covered HTTP endpoint (e.g. gateway or infra config), where it is easy to assume a linked API-contracts store's conventions do not apply"
  - "`openspec instructions proposal --change <name> --json` returns a non-empty `references` array pointing at a separate, linked OpenSpec store"
  - "A new change looks purely additive (a new capability or requirement) against an existing capability that already has synced spec.md requirements in the same area"
  - "Drafting a delta spec.md for an existing capability without having first read that capability's full current spec.md end-to-end"
  - "`openspec validate` / `openspec sync` pass cleanly, which only confirms structural validity and does not catch a new requirement that semantically contradicts an existing one"
tags: [openspec, proposal, cross-store-references, spec-conflict, delta-spec, api-contracts, convention-check]
related_components: [openspec, api-contracts, ce-plan, ce-work]
---

# Surfacing cross-store convention conflicts and existing-spec contradictions while drafting an OpenSpec proposal

## Context

Proposing an OpenSpec change is easy to treat as a self-contained drafting exercise: read the user's request, fill in `proposal.md`, `design.md`, `specs/**/spec.md` (delta), and `tasks.md`, done. But a proposal that touches (a) an existing capability, or (b) any implementation-level detail that resembles a naming/casing/shape convention, can silently conflict with constraints that live outside the immediate diff — either in that same capability's own main spec, or in a linked cross-store spec that neither `openspec validate` nor any other structural tooling checks for.

This surfaced concretely during the `add-kong-correlation-id-plugin` change in `sdd-demo`. The user's literal request was to add Kong's `correlation-id` plugin to `kong/kong.yml` with header `Kong-Request-ID`, `echo_downstream: true`, global scope — a request that, read in isolation, looks like a small, purely additive infra tweak. Two non-obvious conflicts surfaced anyway, both before any code was touched:

1. The change's own artifact-instructions response carried a `references` array pointing at a separate linked OpenSpec store, `sdd-demo-api-contracts`, whose `api-naming-conventions` spec turned out to govern the exact header name being introduced — even though the change was gateway config, not an HTTP endpoint in this repo's own `openapi/openapi.yaml`.
2. The capability being touched, `api-gateway`, already had a main spec (`openspec/specs/api-gateway/spec.md`) containing a requirement that directly prohibited what the change was about to add.

Both were caught before implementation began, and the change shipped clean — confirmed by `openspec validate` passing and a zero-P0-P3 result from the session's later multi-agent `/ce-code-review`. This doc captures the checklist that caught them, so the next proposal doesn't rely on catching it by luck.

**(session history)** The `api-gateway` capability and its "No Traffic Policies Applied at the Gateway" requirement were created by an earlier change, `add-kong-gateway`, whose proposal listed "No Kong plugins (rate limiting, auth, logging, transformations, etc.) — explicitly deferred to a future change" among its non-goals — the constraint was an intentional POC scope boundary, not an oversight, and the proposal's own framing implied plugins were expected to arrive in a later follow-up change (this one). Because `add-kong-gateway` created `openspec/specs/api-gateway/spec.md` from scratch, `/opsx:sync` in that session simply wrote a new file rather than exercising any MODIFIED-delta merge on `api-gateway` specifically — this repo had already authored a `## MODIFIED Requirements` delta once before, in a separate change (`adopt-api-error-responses`, against the `asset-management` capability), so the pattern itself wasn't new to the repo, only new to this particular capability. Separately, that earlier `add-kong-gateway` proposal session referenced only local repo files (`compose.app.yaml`, `README.md`, `docs/tech-stack.md`) in its design rationale and did not check any linked store for cross-cutting conventions before writing its spec/design — so the gap this doc addresses (checking the `references` array before finalizing technical details) had not been exercised in this repo even in the one prior session that added a network-facing surface.

## Guidance

Whenever an OpenSpec change's "Modified Capabilities" section is non-empty, OR the change involves any wire-contract-adjacent detail (header/field names, casing, status codes, error shapes) — even if the change itself isn't an HTTP endpoint in this repo's own OpenAPI spec — run this checklist before finalizing the proposal's technical details:

1. **Read the entire existing main spec for every modified capability**, not just the requirement you think you're changing. For this change that meant reading the full `openspec/specs/api-gateway/spec.md`, not just skimming for a "gateway policies" section. Specifically scan for negative/prohibition-style requirements — "SHALL NOT," "MUST NOT," "never" — anywhere in that file. A new capability that looks purely additive from the proposal's own framing can still contradict one of these; nothing about "I'm adding X" implies the spec doesn't already say "X is disallowed."
2. **Treat every `references` entry in `openspec instructions <artifact> --change <name> --json` as a check, not a skip.** If the response lists a linked store's spec with a `summary` that plausibly touches a technical detail your proposal is about to finalize — naming, casing, error shape, pagination shape, etc. — fetch it (`openspec show <spec-id> --type spec --store <store-id>`) and read it, even when the change doesn't look like "an API." The reasoning "this store is about API wire contracts, my change isn't an API endpoint" is exactly the reasoning that would have let `Kong-Request-ID` through unchecked in this session.
3. **When a conflict is found, don't silently pick a resolution.** Surface it explicitly — to the user, or in the proposal/design rationale — and record *why* the final choice was made, so a future reader of the archived change understands the deviation from the literal original request rather than being left to wonder why the shipped detail doesn't match what was asked for.
4. **When the delta touches an existing, conflicting requirement, write it as `## MODIFIED Requirements`** — copy the full existing requirement block (title through scenario) and edit it, per the OpenSpec MODIFIED-requirements workflow. Never leave the old requirement untouched in the main spec while adding a new `## ADDED Requirements` entry that contradicts it, and never rely on `openspec validate` or `openspec sync`/`archive` to catch that kind of contradiction — they check delta operation syntax and scenario formatting, not whether a new requirement is semantically consistent with an old, untouched one elsewhere in the same file.

## Why This Matters

Catching conflict 1 avoided shipping a header name (`Kong-Request-ID`) that broke a platform-wide naming rule living in a separate store (`sdd-demo-api-contracts`'s `api-naming-conventions`), which requires Train-Case (`Request-Id`, not `Request-ID`) and, literally, bans a service-specific `X-` prefix for exactly this class of header. `Kong-Request-ID` carries no `X-` prefix, so treating it as covered by that rule means reading "no service-specific `X-` prefix" as an instance of a broader "no vendor-specific prefix" intent rather than the rule's literal text — a reasonable reading given the rule's own stated purpose, but one to flag explicitly rather than treat as a mechanical match, since the letter of the spec only names `X-`. This repo's own tests, and this repo's own `openapi/openapi.yaml`, have no visibility into that convention — it would most likely have surfaced later, in a cross-service naming audit well after the change had already merged and other services had started depending on the (wrong) header name, at which point fixing it means a breaking change instead of a one-line proposal edit.

Catching conflict 2 avoided leaving the archived `api-gateway` main spec self-contradictory: one requirement stating the gateway applies no Kong plugins "or otherwise," another describing the correlation-id plugin's behavior in detail, with no reconciling language between them. This isn't hypothetical — this session's own `/ce-code-review` `project-standards` reviewer independently flagged the *transient* pre-sync state (kong.yml changed, main spec not yet synced) as a residual risk: "the living spec still bans all Kong plugins... kong.yml now contradicts it until sync runs." That flagged risk was temporary only because the delta was written correctly as `## MODIFIED Requirements` — had it been ADDED-only, the exact same contradiction the reviewer flagged as transient would have become permanent the moment `/opsx:sync` ran, because sync and archive have no mechanism to detect it.

## When to Apply

Any `/opsx:propose` (or equivalent OpenSpec change-authoring step) where either condition holds:

- The proposal's "Modified Capabilities" section is non-empty — i.e., the change touches a capability that already has a main spec.
- The change involves naming, casing, or shape details that resemble a wire contract — header names, field names, status codes, error response shapes — even when the change is infra-level (gateway config, middleware, deployment config) rather than an application HTTP endpoint covered by this repo's own OpenAPI spec.

## Examples

**Header naming (Conflict 1):**
- Before: user-requested header `Kong-Request-ID` — vendor-prefixed (`Kong-`), wrong casing (`ID` instead of Train-Case).
- After: `Request-Id`, matching `api-naming-conventions`'s own worked example verbatim. The decision and its rationale were recorded explicitly in `proposal.md`'s Impact section and `design.md`'s Decisions section — not silently substituted with no trace of the original request.

**Delta spec shape (Conflict 2):**
- Before (would validate structurally, wrong at the semantic level): delta contains only `## ADDED Requirements` with a new "Gateway Attaches a Correlation ID" requirement; the existing "No Traffic Policies Applied at the Gateway" requirement in `openspec/specs/api-gateway/spec.md` is left untouched, so the archived main spec ends up asserting both "SHALL NOT apply any Kong plugins... or otherwise" and a detailed description of a Kong plugin's behavior, with nothing reconciling them.
- After (actual, correct shape shipped this session): `## MODIFIED Requirements` carries the full existing requirement block, edited to read "...SHALL NOT apply any Kong plugins... with the sole exception of the `correlation-id` plugin..."; a separate `## ADDED Requirements` section adds the new "Gateway Attaches a Correlation ID to Every Request" requirement. Both operations landed in the same delta spec file, and `openspec validate` passed against that shape with no contradiction left in the resulting `openspec/specs/api-gateway/spec.md` main spec after sync.

## Related

- [Respecting a repo's spec-of-record override when running /ce-plan and /ce-work](../workflow-issues/honor-claude-md-plan-of-record-overrides.md) — a different OpenSpec-adjacent workflow rule for this repo (routing `/ce-plan`/`/ce-work` to read the existing `tasks.md` instead of writing a new plan doc), sharing this doc's underlying instinct of "read the authoritative source before acting instead of trusting a generic default," but at a different pipeline stage (execution planning vs. proposal-time conflict detection) and against a different failure mode (duplicate plan artifact vs. contradictory spec requirement / wrong wire-contract detail).
