---
category: "kanban"
labels: "review, transport, shared-login, 5sp"
dependency: ["d1d94066-abb7-4439-af0e-cfdd8df4f337"]
type: "task"
write-id: "1793032235074-0.uetnlizsvqgxhk1qgam"
points: "5"
title: "Implement the accepted bounded lossless review projection"
priority: "P1"
status: "incoming"
uuid: "7ee34a27-324a-4201-b026-f8d9ae6691ee"
created_at: "2026-10-26T14:57:57.931Z"
---

## Context

Planning card `d1d94066-abb7-4439-af0e-cfdd8df4f337` must first settle ownership and the consuming seam. No implementation authority comes from this incoming card.

## Outcome

Implement a bounded lossless host-page projection in the accepted owning repository and consume its stable boundary without duplicating review, session, approval or settlement law. The five-point estimate is provisional pending ownership review.

## Scope

Preserve the 8,192-unit/128-line default exactly. Candidate 16,384/32,768-unit limits require a trusted immutable operator profile selected before session creation. Invalid explicit values refuse; no untrusted/model override or silent fallback. A changed input or profile requires a fresh session and fresh evidence.

## Non-goals

No protected consumer adoption, live model invocation, publication qualification, parser, policy migration, permission widening, timeout increase, truncation or fleet cutover.

## Acceptance criteria

- [ ] Accepted planning names the owner and seam; this card is moved lawfully before source implementation starts.
- [ ] Meaningful RED/GREEN checks prove default page text/ID/coordinate parity, exact reconstruction, surrogate/CRLF/trailing text preservation, both bounds, invalid-value refusal and immutable profile provenance.
- [ ] Existing delivery/assessment completeness and stale/duplicate/partial-input refusals remain enforced by their owner; no second implementation is introduced.
- [ ] Owning repository checks pass with zero introduced warnings; immutable source/artifact identity and exact-head independent code review are retained.
- [ ] An owned release boundary is available for separately reviewed protected-consumer qualification `6118415f-632c-49e2-8970-cd614c0b8628`.

## Verification

Use the accepted owner's CLJS/CLJC tests, native build/lint/typecheck and artifact parity checks. Local source success is preparation, not accepted model coverage or deployment.

## Risks

If the owner is outside Muse, carry the implementation card/contract to that owner through Rheos-supported planning and reference it here. Stop rather than creating a compatibility semantic fork.

---
CR4379542652 verified: scope now explicitly binds the trusted immutable profile before session creation, with a fresh session and fresh evidence whenever input or profile changes. Matching qualification card corrected. Source wording prepared while normal-review40357617300 ran; applied only after that stable-head44fae22 invocation completed with nativeAppAPPROVED5655406068. This updates planning obligations, not an executable paging/profile implementation or new-head approval.

---