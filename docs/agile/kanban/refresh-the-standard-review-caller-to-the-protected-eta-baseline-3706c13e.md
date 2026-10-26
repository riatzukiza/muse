---
category: "kanban"
labels: "review, ci, shared-login, 3sp"
dependency: []
type: "task"
write-id: "1793033008785-0.3vmmqzvxsxg1k006sjk"
points: "3"
title: "Refresh the standard review caller to the protected ETA baseline"
priority: "P1"
status: "in_progress"
uuid: "c31d4187-d2e6-4e78-946f-c3163706c13e"
created_at: "2026-10-26T16:12:35.554Z"
---

## Context

Muse17 standard review run40353203757 failed in the model step on d1e597f. Its original caller pins ETA2b918cd, whose reusable workflow hardcodes the old MiMo default; the failed run retains an empty model-response artifact and no stderr, so the actual provider failure cause remains unestablished. The checked local OpenCode2.1.3 catalog advertises MiMo2.6FlashFree, but that is not a qualification of the hosted pinned1.18.18 client.

Protected ETA368 merged as ad93a9cb786da61be9021d1f00b80b811463dfc7 after its exact-head native gates. Its standard reviewer already provides checked anonymous free MiMo discovery, the physical catalog carrier, pinned client/tool restrictions and preserved model/version/catalog/stderr diagnostics.

## Outcome

Prepare and independently review adoption of that existing protected standard-review caller baseline, then verify a fresh hosted result. This repairs the review-infrastructure prerequisite; it does not implement the proposed paging transport or its protected body-intent consumer.

## Scope

Change the immutable reusable-workflow reference and supply its required PR head SHA from the native pull-request event. Preserve caller permissions, existing explicit inputs, custom diff-stat gate and the same three secrets. Keep anonymous zero-cost MiMo discovery with owner defaults; no paid fallback, permission widening or secret changes. Retain all original failed run bytes.

## Non-goals

No old-run rerun, claimed causal fix from model names alone, new parser/publisher/review law, paging/profile implementation, actor identity, account-link installation or fleet cutover.

## Acceptance criteria

- [ ] The source reference is the independently merged protected ETA baseline and its declared workflow inputs/secrets fit the unchanged caller.
- [ ] actionlint and diff checks pass; caller semantic comparison differs only in the immutable uses reference and explicit native event-head binding. Every declared required input is present.
- [ ] New source gets independent exact-head review and native checks; no old approval is transferred.
- [ ] A fresh hosted run preserves discovery/client/provider/model/tool/input/provenance and actual outcome. Failed or incomplete execution supplies no judgment; only accepted full coverage and native App readback can count.

## Verification

Use native GitHub merge readback for ETA368 and immutable upstream workflow content; inspect the source/caller diff and native hosted result. The existing failed MiMo run is not retried. No separate model probe or provider substitution is needed for this source preparation.

## Risks

Catalog eligibility is not inference availability. The initial candidate run40356983929 was refused at startup because the required head input was absent; no model ran. Its corrected contract check covers every required input. Any new concrete failure must be preserved and repaired only from established evidence. This candidate is not adopted or an accepted assessment until reviews and required qualification succeed.

---
Candidate5f33464 actual hosted run40356983929 refused beforejobs: required pr_head_sha missing. Original staticcheck verified supplied names but missed required-input coverage. Corrected preparation supplies github.event.pull_request.head.sha, preserving all existing inputs/permissions/secrets; scope/body updated before breakdown. New one-off RED required-input check proves missinghead and GREEN verifies ALL required fields plus exact two-field semanticdiff. Oldstartupfailure retained; no modelcalled/no claimed inferencefix, qualification/reviews stillpending.

Review4379920519 correctly identifies lifecycle drift: executable caller repair was prepared while this card stayed incoming. Reconcile prospectively through native gates, preserving earlier events rather than inventing prior ready/adoption. Exact44fae22 independent native review5655406068 and successful hosted40357617300 verify identical caller bytes and9/9 input coverage; currentc4 source still requires its own quorum. Original missing-input startup failure retained. This is the CI prerequisite only; paging cards remain incoming.

---