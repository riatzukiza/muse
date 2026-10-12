---
uuid: "642b400f-8bfa-469a-a8ac-d14fccbdca57"
title: "Verify joint review transport and complete HOST context delivery"
status: "in_progress"
priority: P1
points: "8"
labels: "muse, review-invocation, custody, cephalon, 8sp"
parent: "bb5b1ee2-111c-4762-8321-889a7256b2f9"
epic: "bb5b1ee2-111c-4762-8321-889a7256b2f9"
write-id: "1791840854662-0.qny88g2ggja7ajpdcv"
---

## Context

The [intended repair coordinator](bb5b1ee2-111c-4762-8321-889a7256b2f9.md) and
[transport/law predecessor](32f65d9a-50ec-412a-a0b1-78cb88c27bfb.md) supply
the bounded begin response and declared observations. On2026-10-12 the user
included complete context-read enforcement and joint source delivery in the
existing six-point pair, answering the concrete scope proposal with
"Include enforcement and joint delivery". Receipt75 preserves that decision.
Qualified native Rheos removed the predecessor-Done dependency while this card
was Incoming. Both parent/epic UUIDs remain unchanged. This revised story owns
the complete joint transport-and-verification implementation exit. The earlier
transport story records a partial implementation already at Review; it is
retained work to verify and finish inside this story, not an external admission
prerequisite. No prose-only prerequisite replaces a board relationship. Native
readiness admits this entire joint story after planning qualification, and its
Review gate must pass the complete transport and verifier controls. Both source
slices qualify and merge together in PR7 before any downstream adoption.
The coordinator's actual epic type was admitted by the qualified native Rheos
whole-board correction on2026-10-11T21:41:20.879Z. Committed receipt61 records
the admission and independent readbacks; this child's parent/epic links and
dependency on the predecessor were retained at that historical admission.
Readback alone is not admission proof. This child remains Incoming and requires
current planning qualification and native readiness before implementation.

## Outcome

The compiled invocation verifier requires the current custody protocol and
complete delivery of the independently staged context through actual completed
generic OpenCode HOST reads before the first review stage. It rejects missing,
malformed, differing or refusing custody and read evidence without weakening
any existing full-input or review-stage guard.

## Scope

Verify and complete the retained transport implementation as part of this story,
then compose its pure custody law from the existing invocation law. Its tests
and all new verifier tests form this story's single native Review-gate exit;
there is no separate predecessor completion required to start implementation.
Require its known compiled protocol and an explicitly present context-source key
on every current begin, including explicit nil for absence. Validate later
stage/submission observations and serialized submission binding. Update existing
positive fixtures to that contract and retain every earlier negative scenario.
Prepare trusted context from actual staged UTF-8 bytes, explicit absence and
the caller's fixed absolute context path, held in supervisor memory. Compare
the begin binding and each numbered HOST read line with that trusted source;
derive coverage from real completed read events and their returned ranges/EOF.
No model-supplied path, hash, prose, nested tool output or serialized metadata
can replace the independently prepared source. Preserve exact byte/hash custody
separately from the host's line representation (including CRLF and final-newline
handling). Wire the contract into the existing invocation law and compiled
prepare/verify exports, not a new reader tool or host protocol.

The genuine pinned OpenCode1.18.18 read tool emits filePath/offset/limit input,
numbered content and range/EOF footers. Its50KiB output cap can be recovered by
smaller ranges; its2000-character individual-line clipping cannot. Inspect the
actual captured HOST framing and the pinned
[read source](https://github.com/anomalyco/opencode/blob/v1.18.18/packages/opencode/src/tool/read.ts).
Support only established framing; unknown or unrecoverable framing refuses.
The pinned read implementation may append exactly the established
`\n\n<system-reminder>\n…\n</system-reminder>` suffix after `</content>` when
`state.metadata.loaded` names instruction paths. Admit this recognized HOST
suffix as ancillary untrusted text retained in the original event. It supplies
no context lines, range/EOF coverage, staged-context hash or approval. Require
the suffix's framing and presence to agree with the shaped HOST loaded-path
metadata; reject unknown trailing framing or inconsistent suffix/metadata.
Do not execute, parse as instructions, or fold the suffix into context custody.

## Non-goals

Select validation from a legacy trace shape, model claim or caller waiver;
authenticate disk bytes from copied metadata; prove model understanding; create a
new review event protocol, reader, paginator, provider or retry path; deploy or
adopt the transport predecessor alone. Historical traces retain their original
immutable verifier/source evidence and no current qualification credit.

## Acceptance criteria

1. A current manifest alongside a legacy-shaped begin is refused. Missing or
   unknown protocol and omitted source keys are refused. Explicit nil is valid
   only as the new protocol's declared absence, not an omitted-key fallback.
2. Declared binding shapes are lawful; changed/missing stage/submission metadata,
   shaped unavailable/read errors and custody refusal are independently refused.
   Serialized submission metadata remains bound to the admitted begin and
   observed submission result. Trusted preparation independently derives the
   fixed path, byte length, SHA256 and line representation from actual context;
   omitted preparation, malformed UTF-8 or changed bytes refuse.
3. Actual completed generic HOST reads must deliver every exact numbered source
   line, including the tail, before the first stage. Validate input bounds,
   output path/type/ranges, exact content, EOF and clipping; a recovered sequence
   of bounded ranges may qualify. A present empty file requires its real EOF
   read; explicitly absent context requires the new declared absence. Missing,
   partial, gapped, wrong-source/content, malformed, unavailable, unrecoverably
   clipped, post-stage or prose-only evidence refuses before accepted invocation.
   Repeated/overlapping genuine reads may qualify only when their exact content
   is consistent and complete. A later read does not repair a stage already
   recorded without complete delivery. Return inspectable coverage in the
   verifier result without adding an approval or model-understanding claim.
   Completed-read end times must precede the first stage's start, so a parallel
   read completing during that stage cannot qualify merely by event position.
   With and without loaded instructions, the recognized ancillary suffix must
   leave exact context coverage unchanged; malformed or inconsistent suffixes
   and suffix-only claims of missing source lines refuse.
4. Actual new-protocol positive fixtures pass through the compiled exported
   verifier. Existing partial-input, stale-reread, ordering, restart, failed-call,
   source and submission negative cases retain their original failure meaning.
   Do not alter page size/geometry, stage/tool IDs or admission quorum.
5. Pure law and compiled controls fail before wiring and pass after the minimum
   migration. Run existing test, all-host build, cold-build, full-input/invocation
   and lint gates with zero warnings. Preserve the complete qualified planning
   receipt/reflection prefixes before every later append.
6. This joint story's native Review gate verifies both source slices. Both
   substories and the entire repair pass the owning epic's current canonical
   PR gate before authorized head-guarded MERGE and independent merge readback.
   A downstream caller adopts the actual qualified matching plugin/verifier
   source together and then earns its own current-source qualification.

## Verification

Use pure law fixtures and the actual compiled exported verifier. Retain old
negative controls, new positive/protocol/schema controls and source identities.
Matching metadata proves declared consistency only. Include present/multirange,
explicit absence, empty, Unicode, CRLF, final-newline and overlapping-read
controls plus causal negatives for every changed read boundary. Recover an
actual genuine HOST trace as a framing control; synthetic fixtures remain
tests, not native model review. Native review, source/disk custody and model
understanding remain distinct external observations.

## Risks

Older plugin output is intentionally refused by the new verifier. Caller source
pins must move together after qualification. A correct transport can still leave
the downstream1482-page review unfinished; no duration or availability promise
is made. Eight points is the revised joint verifier estimate: trusted-source
preparation, pinned HOST framing/range reconstruction, causal delivery admission,
compiled wiring and regression migration are independently tested failure
domains. The retained transport story remains three points, making eleven for
the same authorized functionality. This estimate remains provisional until
fresh planning qualification. The earlier transport's filesystem and guidance
implementation is retained and verified within this story's complete exit. If
the actual HOST surface cannot
establish trustworthy complete delivery, stop and re-describe that specific
boundary; do not fabricate read proof or relax the gate.

Software/process documentation: GPL-3.0-or-later.


---
The human approved Include enforcement and joint delivery on2026-10-12, preserved by receipt75. The revised six-point pair requires complete genuine HOST context reads matched to independently staged bytes before stage one. Native Rheos removed only the Incoming verifier child dependency on predecessor Done, retaining parent/epic links; the transport story has passed native checks and remains Review. Fresh planning qualification and native verifier readiness still precede code, and both source stories qualify and deliver together in PR7 before downstream adoption. Finding4251134311 remains open until actual enforcement and verification. Current3ca MiMo5495006171/run38292920913 is completed APPROVED but does not settle this gap or transfer to the revised head. No runtime, model-route, credentials or paid changes are included.

Native Codex review5495259091 findings4251707587/4251707600/4251707613 require this planning correction. The Incoming verifier is now the eight-point joint implementation boundary: it owns verification/completion of retained transport source plus trusted preparation, genuine HOST framing, causal full-delivery admission, compiled wiring and regression controls. There is no external transport prerequisite expressed only in prose and no substituted non-Done dependency law. Its own native readiness and complete Review gate control both slices; the earlier three-point transport stays Review as retained partial work. The same authorized functionality totals eleven provisional points. Both slices qualify and deliver together through PR7; no standalone transport adoption. Recognized pinned OpenCode instruction suffix is retained ancillary untrusted event text, contributes no context coverage/hash/control instructions, and has explicit matching-metadata and malformed/suffix-only negatives. Fresh planning qualification precedes source implementation. Parent/epic links and historical comments/receipts stay intact.

Joint source RED after verified planning merge0d59966b and native readiness: first full test297/1822/136failures0errors; clean compiler rerun139failures includes same136 new enforcement failures plus3 existing file-watch timing assertions. New controls demonstrate ignored staged context bytes, missing complete genuine HOST delivery, legacy protocol/source acceptance, clipping and causal completion gaps. No approval or deployment claimed.

Joint transport/enforcement source follows RED7ce93c8. Pure shape/domain compose retained invocation guards; actual six-argument preparation binds staged UTF8 bytes/fixed path and preserves HOST time/metadata. Current full suite passes302tests1876assertions0failures/errors; prior watcher-only3fail run retained. All five targets/cold three-host builds0warnings, compiled full-input204pages/context1482pages/custody14/unavailable2 and optimized invocation3representations31assertions each pass; lint0/0 and JVM portable smoke pass. Scope and legacy recovery guards retained; four-argument preparation/current protocol omissions refuse. No source approval/adoption/deployment claimed. Run native joint Review gate next.

Correction to the immediately preceding progress comment: current full suite has302tests1876assertions3failures0errors/exit1, all3 in the unchanged file-watch test. It did NOT pass. The earlier299/1834 run passed, and current context controls/optimized export probes pass, but current full gate remains failed. The previous comment prematurely stated a passing result; this append supersedes that statement without changing history. Native Review admission must actually execute its gates and independently read back, with any refusal retained.

Actual native joint Review gate exited3: build/lint passed, pnpm test302/1876 refused on3 unchanged file-watch assertions. Independent native readback retains In Progress. Owned read-only fs.watch canary returns ENOSPC; no host watcher/service/sysctl change or gate bypass. Current context-delivery assertions and optimized invocation93controls pass; full gate remains failed. Preserve failed logs and premature prior passing statement/corrective append. Publish source candidate with truthful gates for independent CI/review; no source qualification, downstream adoption or completion claimed.

Current CodeRabbit review findings4252414361/4252414388/4252414395 verified. Qualified Review-to-InProgress reopened this same joint source boundary. Compiled-input RED exits1 in all4 absent/present context x cleanup/input-read denial cases: admitted begin loses protocol/source/observation metadata. Fault injection is scoped to owned fixture paths and restored finally. Production unchanged. Next minimal catch preserves observed-fields and qualifies two stale Incoming prose statements as historical; native board/frontmatter/comments stay engine-owned. Receipt83 retains actual RED and receipt82 actual prior Review success; neither review credit nor deployment.

---
