---
uuid: "d1d94066-abb7-4439-af0e-cfdd8df4f337"
title: "Plan bounded lossless review delivery and ownership"
status: "incoming"
type: "task"
priority: "P1"
points: "5"
labels: "review, transport, shared-login, 5sp"
dependency: []
category: "kanban"
write-id: "1793026677514-0.8ivois15bufgpzw68kk"
created_at: "2026-10-26T14:57:57.514Z"
---

## Context

Protected ETA run [40264714320](https://github.com/open-hax/eta-mu/actions/runs/40264714320) timed out after 120 minutes with 63 of 146 pages assessed and publication skipped. This is failed diagnostic evidence on a now-stale Knoxx target, not a judgment.

The pinned [Muse compatibility emitter c1369c2](https://github.com/riatzukiza/muse/blob/c1369c223cf3c57e3e31934a6d746bfcdfe73f5a/src/cljs/eta_mu/domain/review.cljc#L78) fixes pages at 8,192 UTF-16 units and 128 lines. The complete original manifest-bound input is 1,191,389 UTF-8 bytes and 1,191,002 UTF-16 units. Earlier notes called the latter bytes; this is a unit correction, not an input change.

See [the full-input feasibility report](../../review-paging-feasibility.md): isolated evaluation of the pinned primitive with only its numeric page cap varied gives 146, 73 and 37 pages at 8,192, 16,384 and 32,768 units. Every candidate retains the 128-line bound and reconstructs the original SHA-256. No runtime/model deadline or coverage acceptance follows from page count.

## Outcome

Complete an independently reviewed ownership and delivery plan. This card ends with accepted planning artifacts, not implementation, a model judgment or deployment.

## Scope and ownership

- Establish the transport primitive owner and named consumption seam. Muse may project target artifacts and codecs, but cannot acquire actor, session, workflow, review-policy, evidence or publication authority from a compatibility file's location.
- Specify unchanged default behavior and a closed candidate cap set with trusted immutable operator-profile selection before session creation. Preserve complete input, Unicode, coordinates and coverage requirements in the proposed implementation contract.
- Record a stop/reconsider decision if complete-input page-count reduction is absent or an owning review rejects the seam. Do not patch a compatibility copy while ownership is ambiguous.
- This is a local root planning card with no local parent/dependency. The external Foresight NRI-2 card `29b50a33-6ecf-4955-85c5-f8ec72c8ac22` and [.agents#47](https://github.com/riatzukiza/.agents/pull/47) are consumer/purpose references, not invented local graph links.
- Separately estimated follow-ups: implementation `7ee34a27-324a-4201-b026-f8d9ae6691ee` (5 points) depends on this card; protected-consumer qualification `6118415f-632c-49e2-8970-cd614c0b8628` (8 points) depends on implementation. Estimates are provisional until planning review accepts them.

## Non-goals

No executable review-tool/profile changes, parser, classifier-to-approval conversion, new credentials, model retry, permission widening, larger timeout, input truncation, account link, deployment or federation cutover. This card does not implement NRI-2/NRI-3 or the typed Kimi publisher.

## Acceptance criteria

- [ ] Independent review accepts the primitive's owner and exact consumption seam, or records refusal and an explicitly blocked handoff; ambiguous ownership never authorizes code.
- [ ] The report binds the immutable source and complete motivating input, records page counts and binding bounds for every candidate, proves reconstruction and unchanged-default parity, and states the go/stop rationale without predicting accepted model completion.
- [ ] The proposed implementation contract specifies default parity, invalid-value refusal, surrogate/CRLF/trailing-text preservation, fresh sessions for changed input/profile, trusted provenance and unchanged incomplete-coverage refusal.
- [ ] Separate implementation and protected-consumer qualification cards have explicit UUID dependencies, outcome boundaries, estimates, non-goals and verification obligations.
- [ ] Native Rheos readback confirms this card and dependency graph; immutable creation/supersession history is retained; all planning findings are settled before a lawful ready transition.

## Verification

Read-only full-input partition measurement and native Rheos creation/readback are planning evidence. Review this diff and immutable source/failure metadata. Do not rerun the failed model invocation or call document approval an executable qualification.

## Risks

Larger responses may increase latency/context pressure. The motivating input is stale for admission. A character cap cannot help line-limited pages; this particular full input is character-limited, but future inputs may differ. Profile and ownership choices remain pending independent review. Completing this plan does not complete either follow-up.
