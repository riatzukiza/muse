---
category: "kanban"
labels: "review, github, publisher, truth"
type: "task"
write-id: "1788048695246-0.q2ycixhgnyauvbwt66j"
points: "3"
title: "Make self-authored review publication truthful"
priority: "P0"
status: "ready"
uuid: "1bc0a6b5-3286-4e43-afc4-8cf8ae0549a0"
created_at: "2026-08-30T00:10:25.672Z"
---

# Make self-authored review publication truthful

## Outcome

Give the GitHub review publisher an explicit, tested contract for pull requests authored by the same GitHub App identity that publishes the evidence review, without converting a self-review into independent approval.

## Revision-bound evidence

- The eta-mu reusable review publisher submits the model envelope through Muse's `.ημ/review/publish-opencode-review.cjs`.
- A no-finding envelope deterministically selects `event: APPROVE`.
- When an automated pull request is authored by `eta-mu-ai[bot]` and the eta-mu App token publishes the review, GitHub rejects the self-approval with HTTP 422.
- Exact checkout and valid model evidence do not make self-approval admissible; publisher identity is a separate contract boundary.

## Acceptance criteria

- [ ] Determine the authenticated publisher identity and pull-request author before choosing the GitHub review event.
- [ ] Never call GitHub `createReview` with `APPROVE` or `REQUEST_CHANGES` when publisher and author are the same identity.
- [ ] Define an explicit self-authored outcome: either publish COMMENT-only evidence or fail with a machine-readable unsupported state; do not claim an independent approval.
- [ ] Keep different-author APPROVE, COMMENT, and REQUEST_CHANGES behavior unchanged.
- [ ] Add deterministic publisher tests for matching bot identities, different identities, and author metadata that is absent or malformed.
- [ ] Make the eta-mu terminal caller distinguish a completed self-review record from an independent approval when branch policy needs the latter.
- [ ] Document the operator-visible behavior and retain the exact attempted event and identity decision in evidence.

## Non-goals

- Do not bypass branch protection.
- Do not mint or switch to a human credential.
- Do not reinterpret a GitHub 422 response as success.

---
Validated against the current publisher contract: envelope validation deterministically selects APPROVE for zero findings, but publication does not model authenticated publisher identity versus PR author. Scope the repair at Muse publisher law/shape plus eta terminal outcome integration; no credential or branch-protection bypass.

Canonical GitHub projection created as octave-commons/muse issue #12 after the card reached ready; the issue carries this card UUID and exact publisher evidence.
---
## Planning refinement — self-authored evidence and independent approval

This body-only refinement preserves the complete original acceptance criteria,
UUID `1bc0a6b5-3286-4e43-afc4-8cf8ae0549a0`, P0 priority, recorded 3-point estimate,
Ready status, and original engine comment. It introduces no board operation,
implementation, publisher identity grant, workflow activation, or source repin.
The existing Ready projection does not qualify this new planning revision or
waive the canonical planning review and subsequent red/green gates.

### Current source and caller observations

The personal sync candidate `846b47efd1fa5f9e72f6fe8b2cf45cfb347aba0f`
is this plan's base; personal sync PR1 is draft, blocked, and auto-merge off.
Owning-origin Muse main is separately observed at
`c1369c223cf3c57e3e31934a6d746bfcdfe73f5a`. Neither is the Foresight
`fcfc2d17f28640203066ddfe0a22f87db7372a32` Muse gitlink
`b4bdb0a7d019bb33c71aba1bd8daec5933e7ebde`, and no pointer changes here.

At both examined Muse revisions, the publisher validates the envelope and added
lines, then supplies `envelope.event` to `pulls.createReview` without resolving
and comparing authenticated publisher identity and PR-author identity. Existing
tests cover envelope/changed-line publication but do not cover matching
identities or absent/malformed author metadata. These are source observations,
not a reproduced current live HTTP422 failure.

At accepted-by-owning-origin/current-upstream eta-mu main
`09a4454480baa67f6fdc40f6f73f5e48ef0457d1`,
`.github/workflows/opencode-code-review.yml` packages the publisher and tests
from the explicitly selected `muse_revision` or its default
`0b9a91492c8355e6933dc2164d35668cb76d9e60`. Its terminal publication step
awaits `publishReview` and ignores the returned review data. The future eta
adapter must expose the self-evidence/independent-approval distinction; a Muse
merge alone does not satisfy the caller criterion or consume a new publisher.

Foresight fcfc separately pins eta-mu
`0ed56aa74a53a1d1e9c2e55ce95451817a7f3a90`. Its exact caller was retrieved:
it pins Muse `76c57712a48ef48100259231a2e9d54069c2b14a`, invokes the older
OpenCode GitHub action using GITHUB_TOKEN, and does not package or call this
standalone `publishReview` boundary. These distinct caller architectures are
not interchangeable; this plan targets the current owning-origin boundary
and does not upgrade Foresight's eta-mu or Muse pins.

### Scope and portable boundary

1. Describe the identity-comparison input and publication-outcome shape in
   portable law/shape/domain data. Inputs include repository/PR identity,
   current revision, requested event, verified publisher identity, verified PR
   author identity, and explicit identity-resolution success or refusal.
2. Resolve GitHub-native identity and write a review in outer adapters only.
   Keep envelope validation and exact added-line validation before any write.
   Candidate-controlled environment values, submission prose, caller-supplied
   display names, or review counts cannot establish publisher identity.
3. Distinguish a validated model request, chosen native event, actual successful
   native publication, and independent approval eligibility. A self-authored
   COMMENT may be evidence; it is never independent approval. An unavailable
   or unsupported publication remains a refusal with no approving outcome.
4. Integrate and verify the eta terminal caller through its owner. Preserve
   its complete immutable-input assessment and final-submission binding. Do
   not patch a foreign restoration lane or copy its unaccepted branch.

The proposed self-authored behavior is COMMENT-only after all required input
and identity checks succeed. Retain the original acceptance's explicit
machine-readable unsupported alternative if the reviewed trusted App adapter
cannot establish its identity lawfully. Planning review must resolve this
adapter choice before implementation; neither outcome may submit APPROVE or
REQUEST_CHANGES for a verified self-authored PR. No token switching or new
identity allowlist is proposed.

### Acceptance detail (additional to all original criteria)

- Resolve identity before event selection using an authenticated native
  publisher boundary and current authoritative PR metadata. Compare stable
  identities; do not strip `[bot]` or equate aliases without verified native
  correspondence. Missing, malformed, unavailable, or inconsistent identity
  evidence refuses publication before `createReview` and grants no approval.
- Preserve the requested event and selected event separately. For verified
  identical identities, no APPROVE or REQUEST_CHANGES request reaches GitHub.
  Successful COMMENT-only publication records the actual native review id,
  event, author, repository/PR and revision plus `independent-approval=false`.
  Unsupported or failed writes cannot manufacture a completed native review.
- For different verified identities, preserve all existing validated APPROVE,
  COMMENT and REQUEST_CHANGES behavior and inline comment content. Blocking
  findings remain visible when a self-authored review is COMMENT-only; the
  terminal caller cannot turn that condition into a passing independent gate.
- The eta caller consumes a schema-checked publication outcome, propagates
  refusal/failure, and distinguishes completed self-evidence from independent
  approval. Any policy that requires independent approval remains unsatisfied
  by a self-authored result, even if the model found no defects.
- Keep existing exact-head, native reviewable-line, complete-input, immutable
  manifest, assessment chronology and untrusted-provider guards. Identity
  handling is an additional boundary, not permission to publish partial input.
- Retain operator-visible attempted-event/identity-decision/outcome evidence
  without credentials or token material. Documentation names what was
  published, what remains unsupported, and which policy remains unsatisfied.

### Verification and red/green sequence

Use a new private implementation checkout and private caches, with an injected
GitHub adapter recording calls. Begin with actual predecessor code and failure
proof for the expected identity contract. Do not infer a current GitHub422
reproduction from the historical report. Correct fixture arities/interfaces
before accepting a red signal, and retain unsuccessful preparation evidence.

| Control | Required observation |
| --- | --- |
| Verified same publisher/author; requested APPROVE | No approving request; reviewed COMMENT-only or explicit unsupported outcome; never independent approval. |
| Verified same publisher/author; requested REQUEST_CHANGES | No forbidden self request; preserve blocking findings and unsatisfied gate. |
| Different verified identities; each valid event | Event and inline content unchanged; actual successful native result retained. |
| Absent/null/malformed PR author; lookup failure or inconsistent identity | Refusal before write; no manufactured id, successful completion or approval. |
| Candidate attempts to replace publisher identity or use an unverified bot alias | No authority from submission/config; fail closed unless native identity correspondence is established. |
| Stale/mismatched head or repository/PR tuple | Refusal; no old-head approval transfer. |
| Invalid envelope, unreviewable line, incomplete manifest/input/assessment chronology | Existing refusal unchanged before publication. |
| Native422, network failure, or malformed native publication result | Failure remains failure; caller does not relabel it completed or approving. |
| Successful self COMMENT with no findings vs with blocking findings | Both distinguish evidence from independent approval; blocking findings still block their policy. |
| Terminal eta caller requiring independent approval | Completed self evidence cannot satisfy the requirement; different-author native approval remains eligible only under full review policy. |

Write law/shape tests first, then identity/event decisions, then outer resolution
and write adapters and the eta integration. Use Node boundary fixtures only
for the legacy publisher edge; new portable semantics belong in `.cljc` where
practical and new Muse runtime code follows its CLJS architecture. Run the
actual owning kondo/typecheck/test/build commands with zero warnings, plus
publisher Node tests and eta workflow contract tests. Full native review of
the current candidate and all original criteria remains required; no local
fixture supplies hosted/native review or live App installation qualification.

### Estimate, integration and holds

The original 3 points are unchanged. Review whether the complete cross-owner
outcome fits that estimate; if it needs breakdown, identify an owned eta
integration story and preserve this issue's complete seven-criterion outcome.
No fabricated UUID dependency, duplicate umbrella, or silent scope narrowing
is introduced here. The eta publication caller is a concrete integration seam,
not evidence that a foreign worker owns this particular fix. Active owner
coordination must be rechecked before implementation.

The draft personal sync parent is unqualified. Publisher credential visibility
and independently trusted publication architecture remain owned by their
existing upstream work, including Foresight134; this plan does not grant
candidate workflows secrets. Live App identity and self-publication canaries
require a separately authorized trusted environment. A provider quota pause
is an availability hold, never review completion. Services deployment and
Proxx provider/runtime work are outside this card's scope.

Dedicated design and source/capture provenance:
`docs/notes/self-authored-review-publication-plan.md` and
`.ημ/verification/muse12-self-authored-planning-20261007/`.
