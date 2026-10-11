---
uuid: "bb5b1ee2-111c-4762-8321-889a7256b2f9"
title: "Reference complete staged PR context without overflowing review begin"
status: incoming
priority: P1
points: 3
labels: muse, review-transport, cephalon, 3sp
created_at: "2026-10-11T17:29:06.464697+00:00"
---

## Context

OpenPlanner personal PR3 exact033c001125019908d0c68aa5365749005644edc8,
MiMo run38126421932, retained a65,606-byte review_begin response. Its
57,022-byte inline PR context was already staged as pr-context.md. OpenCode
replaced the structured response with a truncation notice; the following read
of the saved single-line JSON was itself clipped. The invocation verifier
correctly refused unestablished-review-trace. No complete review or approval
exists for that attempt.

The user explicitly included this minimum prerequisite in the finite character
milestone on2026-10-11. It enables source qualification for persistent graph
recall; it does not implement the physical field, mood, or live prompt proof.

### Relationship ownership

The Foresight board owns the character epic
`63a0e4ff-353f-4c90-ab8a-7241d958d54c`
([character epic](https://github.com/riatzukiza/foresight/blob/89946bdf09c1d0141e04e2a02d8a09bb05c26ba4/docs/agile/kanban/cephalon-character-epic.md))
and recall story `a1e9d6af-0233-4dcb-9677-5c76fa9a2701`
([recall story](https://github.com/riatzukiza/foresight/blob/89946bdf09c1d0141e04e2a02d8a09bb05c26ba4/docs/agile/kanban/cephalon-character-recall.md)).
The recall story records that epic as both `epic` and `parent`; the finite
completion and newly accepted prerequisite are recorded in
`foresight/docs/notes/2026-10-08-cephalon-loop-slice-milestone.md`.
Native Rheos reads on2026-10-11 confirmed those UUIDs and relationships.

This Muse card is a standalone prerequisite in Muse's separate board; it does
not create a second epic or copy foreign operational relationships into this
single-board configuration. Personal Muse PR5 (accepted e733 baseline) and its
parent PR1 have independent synchronization gates. This card's implementation
must qualify before the OpenPlanner PR3 reviewer can adopt its merged source;
OpenPlanner must then qualify before Knoxx consumer adoption and liveB3 proof.
These PR dependencies identify the source-order gates, not board transitions,
merged source claims or deployment authorization.

## Outcome

Return a small, explicit reference to the complete staged PR context from
review_begin. Require the reviewer to read that file completely in bounded HOST
read ranges before the first deterministic stage note, retaining it as untrusted
evidence. Keep the complete original file available and keep the verifier's
existing full-diff, stage, source and truncation defenses.

## Scope

Change the review-begin adapter's PR context representation and its emitted,
agent and review-prompt guidance. Extend the existing compiled full-input
verification with an oversized PR context regression, lossless bounded file
readback and absence behavior. Preserve existing domain/law semantics and tool
IDs, schemas, exposure, permissions, complete diff and page assessment geometry.

## Non-goals

No larger diff paginator, page-size change, new context-reader tool, retry
budget, model route, provider allowance, identity override, semantic approval,
publication, maker/runtime change or deployment. The reference is not proof
that a model read or understood the file. Do not present bounded fixture reads
as native model review, full future availability or performance guarantees.

## Acceptance criteria

1. The actual compiled review_begin response references the staged context
   path and does not serialize the context body inline. Missing optional context
   remains explicit absence; no fabricated empty context or existing file claim.
2. An oversized Unicode/multiline context with an omitted-tail sentinel remains
   byte-identical on disk and is recovered completely through bounded ranges.
   The encoded begin response stays below49,152 UTF-8 bytes for the observed
   1,482-page geometry. This fixture bound is not a universal size promise.
3. Explicit guidance requires complete context reads before the first stage,
   continuing past host truncation/line clipping and retaining untrusted-data
   handling. A file reference or preview alone supplies no assessment credit.
4. Identical new regression controls fail on e733f5b for the inline-response
   behavior, then pass after the minimum repair. Existing partial-diff, ordering,
   stale reread, restart, failed-call, submission and source controls still pass.
5. Existing test, all-host build, cold-build, full-review-input/invocation and
   lint gates pass without warnings. Preserve immutable journal prefixes.
6. Fresh personal head/base reviews and qualified merge precede consumer pin
   adoption. Preserve the actual failed native attempt unchanged.

## Verification

Use the existing compiled plugin boundary in scripts/verify-full-review-input.mjs,
not a pasted synthetic verdict. Record RED and GREEN source identities and actual
command exits. Retain the private native failure artifact separately; repository
fixtures contain no credentials or capability URLs. Reproduce the large initial
response with the existing page geometry and a sized multiline Unicode context.
Check staged bytes and tail, response serialization and explicit missing context.

## Risks

Later traversal of1,482 full-diff pages may still exhaust a model's budget; this
repair makes no duration guarantee. HOST read transport can truncate large
ranges or individual long lines, so its returned bounds and warnings must be
checked. Existing invocation law verifies the structured diff trace; this change
does not introduce a separate cryptographic context-read admission system.
The upstream synchronization and any downstream consumer are separately gated.

Software/process documentation: GPL-3.0-or-later.
