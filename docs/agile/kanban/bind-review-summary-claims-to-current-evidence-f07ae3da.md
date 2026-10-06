---
category: "kanban"
labels: "review, evidence, summary, truth"
type: "task"
write-id: "1788048695810-0.kz5hez9zjaj1l1oizxy"
points: "3"
title: "Bind review summary claims to current evidence"
priority: "P0"
status: "ready"
uuid: "9f35579b-e522-4445-a022-daa6f07ae3da"
created_at: "2026-08-30T00:10:41.098Z"
---

# Bind review summary claims to current evidence

## Outcome

Prevent a revision-bound review from presenting numerical or factual claims copied from predecessor commits as though they describe the current exact-head evidence.

## Revision-bound evidence

- Foresight PR #49 exact-head workflow run `33280341858` bound expected, executed, and completion SHA to `f3480655b326f8352e1d410d239a1690503d86e8`.
- Published eta-mu App review `5059407992` repeated predecessor counts `43/180` and `86 receipts / 45 appended`.
- The current immutable head evidence was `44/196`, aggregate `94/426`, and `87 receipts / 46 appended`, recorded in Foresight audit clarification comment `5465470648`.
- The no-finding verdict may still be exact-head while its free-form approval narrative is factually stale; SHA binding alone does not validate summary claims.

## Acceptance criteria

- [ ] Treat pull-request diff text, historical receipts, and predecessor review prose as non-authoritative for claims about the current deterministic run.
- [ ] Define a submission law for current-evidence claims: either derive the published deterministic section mechanically or require machine-checkable references to current artifact fields.
- [ ] Reject or omit unsupported numerical claims rather than publishing them as current evidence.
- [ ] Keep qualitative no-finding summaries possible without inventing gate counts.
- [ ] Add a regression fixture where predecessor counts appear in the diff while current `summary.json` and `deterministic.log` contain different counts.
- [ ] Prove the published summary uses only the current artifact values or contains no numerical assertion.
- [ ] Preserve exact-head, changed-line, and untrusted-diff protections already enforced by the review pipeline.
- [ ] Document which parts of a review body are deterministic evidence, model assessment, and non-authoritative narrative.

## Non-goals

- Do not infer arbitrary prose truth with another model.
- Do not treat a revision match as validation of every sentence.
- Do not weaken the no-finding evidence threshold.

---
Validated against Foresight PR #49 run 33280341858 and review 5059407992: exact-head SHA binding succeeded while predecessor numeric counts were copied into the current approval body. Scope the repair to a machine-checkable current-evidence narrative boundary with a predecessor-in-diff regression; do not add model-on-model fact checking.

Canonical GitHub projection created as octave-commons/muse issue #13 after the card reached ready; the issue carries this card UUID and Foresight run/review/comment evidence.
---

## Proposed 2026-10-06 refinement — current-evidence publication boundary

This appended proposal preserves the existing UUID, ready status, write-id,
original plan text and native event history. Its body is a new planning diff,
not an operational readiness transition or a claim that the historic intake
qualifies the present implementation. Existing issue13 remains the sole outcome.

### Context and current gap

Accepted Muse main is `846b47efd1fa5f9e72f6fe8b2cf45cfb347aba0f`, supplied to the
personal fork by existing synchronization PR1. Personal PR2/origin14 own cold host
builds; issue12 owns self-authored publisher identity. Neither is a hard dependency
for this isolated summary contract. Historic intake PR15 has actual Rheos ready
hops but no demonstrated current canonical planning approval/convergence.

Current `eta-mu.domain.review/submission` receives free-form summary text,
`.ημ/plugins/review_pipeline.cljs` writes it to the envelope, and the CJS publisher
sends `envelope.summary` verbatim. A read-only synthetic validation probe admits
predecessor test/receipt counts without any current deterministic artifact. That
is an observed admission gap, not green qualification or arbitrary prose truth.

### Outcome and chosen scope

Use one mechanically composed publication summary. Current deterministic claims
come only from a validated, revision-bound current artifact view; unsupported
counts are omitted. Reviewer prose is model assessment, not a source for that
section. Keep a fixed qualitative no-finding verdict possible when counts are
unavailable. No model, regular-expression prose scrubber or second truth oracle
judges arbitrary sentences.

Keep practical current-evidence shape/claim/rendering laws portable `.cljc` with
Clojure-shaped boundaries, reusing the current review domain and finding/event
laws. Node/plugin adapters load and decode artifacts, supply trusted revision
facts, serialize the envelope and perform existing publication effects. The
publisher must revalidate the shared boundary before its mockable API effect;
no independent JavaScript domain implementation is introduced.

### Acceptance and verification

All eight original acceptance criteria remain in force and are mapped to the
[focused execution plan](../notes/review-current-evidence-plan.md). Require red
fixtures where old counts appear in the diff/reviewer prose and different current
values appear in supplied artifacts. The eventual published summary must use only
validated current fields or omit numerical claims; the original model prose must
not be silently relabeled current evidence. Missing/malformed/stale bindings fail
closed or omit unsupported optional fields as the contract specifies. The fixed
qualitative no-finding path remains valid after existing admission laws pass.

Preserve exact-head, full-input/page assessment, changed-line, confidence,
classification and event-selection protections. No live GitHub publication is
needed for regression tests: mock the effect and inspect the actual body passed
at the boundary. Preserve Muse npm ownership, Shadow3.4.4 and
existing compiler/test commands; no cold-target or dependency migration is included.

### Non-goals, estimate and risks

No self-review/credential/protection policy, arbitrary prose fact checking, native
review identity or round implementation, Discord/Suno/database/runtime service,
shared cache, generated host entrypoint, board engine or deployment change. The
existing3-point estimate is provisional for the narrow contract plus mocked
adapter seam. If producer schema or compiled pure-law reuse expands it beyond
five points, obtain a reviewed Rheos breakdown before implementation. Do not
invent unavailable test/receipt-count fields or weaken old provenance to make a
fixture pass. Fresh planning qualification remains a prerequisite before red/green.
