---
uuid: "specify-bounded-lossless-review-paging-as-a-host-projection"
title: "Specify bounded lossless review paging as a host projection"
status: "incoming"
type: "task"
priority: "P1"
points: "5"
labels: "review, transport, shared-login, 5sp"
dependency: []
category: "kanban"
write-id: "1793018631767-0.dy3lnpucpue0pj1apsc"
created_at: "2026-10-26T12:43:51.767Z"
---

## Context

The shared Axxium login release is waiting on qualified native assessments, not merely builds. Protected ETA run [40264714320](https://github.com/open-hax/eta-mu/actions/runs/40264714320) timed out after 120 minutes having read and assessed 63 of 146 lossless input pages; publication was skipped. This is failed diagnostic evidence, not an accepted judgment. Its Knoxx head is now stale and must not be reused.

The immutable compatibility implementation [diff-chunks at c1369c2](https://github.com/riatzukiza/muse/blob/c1369c223cf3c57e3e31934a6d746bfcdfe73f5a/src/cljs/eta_mu/domain/review.cljc#L78) fixes each page at 8,192 UTF-16 units and at most 128 lines. It preserves surrogate pairs and exact text concatenation. The 1,191,002-byte assessment envelope contains complete native context, source postimages, changed files, preimages and canonical protocol. No component is declared dispensable. A simple observed-rate extrapolation is about 278 minutes, beyond the accepted 180-minute maximum; this estimate is not a benchmark or a completion guarantee.

Canonical body-intent purpose planning is [riatzukiza/.agents#47](https://github.com/riatzukiza/.agents/pull/47), consumed by Foresight NRI-2 card `29b50a33-6ecf-4955-85c5-f8ec72c8ac22`. That purpose and protected publisher remain separate from this proposal.

## Outcome

Describe and independently review a bounded, lossless page-delivery projection that reduces host/model round trips while preserving every byte of source/context and every existing assessment obligation. Obtain an explicit ownership disposition before implementation.

## Scope and ownership

- Muse may own target linking, codecs and emitted host artifacts. Its compatibility repository is not authority for actor, session, policy, workflow or evidence semantics. A file's current location is implementation lineage, not ownership permission.
- Propose only a pure representation primitive and a trusted host-profile seam. Keep selection of the complete input, review stages, assessment decisions, approval/settlement laws and publication authority with their existing owners. Do not migrate those domains into Muse to deliver this change.
- Owning review must decide whether this primitive belongs in Muse's target projection or must be consumed from an eta-mu/Sol/shared shape owner. If ownership is rejected or ambiguous, stop implementation and update this plan with the owning resource and consumption seam; do not duplicate the primitive.
- Candidate transport limits are a closed set of 8,192, 16,384 and 32,768 UTF-16 units per page, retaining the current 128-line bound. The current limit remains the default. Review these values as resource limits, not claims that a model can process them within a deadline.
- Only an immutable, operator-selected host/purpose profile may select a supported limit before session creation. The model and untrusted review text cannot select or change it. Do not introduce a caller-controlled environment override or silent fallback.

## Non-goals

No HTML/review-intent parser, classification-to-approval conversion, new assessor/provider credentials, semantic review-policy engine, board implementation, automatic retry, budget increase, source truncation, permission widening, deployment, account linking or federation cutover. This card does not implement NRI-2, NRI-3 or the typed Kimi publisher.

## Acceptance criteria

- [ ] Independent planning review settles the transport primitive's owner and named consumption seam before code or profile changes.
- [ ] Missing configuration preserves the current page text, IDs and start/end coordinates exactly. Invalid explicit values refuse before execution; they do not silently become a default.
- [ ] Every supported partition concatenates to the exact original input, preserves Unicode surrogate pairs, CRLF/LF and trailing text, respects both bounds, and neither duplicates nor omits content.
- [ ] Page IDs/coordinates and delivered/assessed coverage are derived from the selected immutable profile and input. Missing, stale, duplicated or partially assessed pages cannot satisfy existing submission requirements. A changed input/profile requires a fresh session and proof.
- [ ] Trusted artifact/profile provenance and the selected transport limits are inspectable. A model request cannot alter them. Old consumers continue to use the default until a separate, reviewed protected consumer adopts an option.
- [ ] Meaningful red/green tests include unchanged-default parity, adversarial long/Unicode inputs, invalid limits, reconstruction, and existing incomplete-coverage refusal; no second implementation of approval or settlement law is added.
- [ ] Local tests/builds are preparation only. Protected ETA adoption, an accepted complete native invocation within its bound, independent App publication/readback and revocation qualification remain explicit downstream obligations.

## Verification

Planning checks: review the immutable source and failure metadata, verify native Rheos creation/readback and unchanged initial status, and check the diff. Do not rerun the failed assessment to validate this document. Implementation later must run the owning repository's zero-warning Clojure and artifact gates and preserve prior page/coverage behavior under the default profile.

A separate protected ETA consumer must bind the reviewed emitter/source/profile and stable current target, preserve all required input and permissions, then demonstrate complete accepted execution within the existing 60–180-minute model-step envelope and publisher headroom. It must retain real failed outcomes and refuse edited/replayed or uncertain publication. Larger pages are a feasibility candidate, not acceptance evidence.

## Risks

A larger response can increase inference latency or exceed a model's practical context. An input cap can silently become truncation unless completeness remains independently checked. Repartitioning can invalidate coverage evidence, hashes and cache keys. Profile selection exposed to a model creates a confused authority boundary. Compatibility-file ownership may require a different upstream home. All of these remain blockers until independently reviewed and verified; no old trace supplies a new verdict.
