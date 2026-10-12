# Review context delivery

The optimized `review-invocation` export composes the existing HOST/full-diff
invocation validator with a separate context-delivery decision. This source is
part of the joint transport/verifier story; downstream adoption still requires
source review and qualification.

## Trusted preparation

`prepareReviewInvocationContext(fullDiff, manifest, tools, submissionFile,
actualContextBytesOrNull, fixedAbsoluteContextPath)` now requires six arguments.
The last two come from the supervising host. Supply the actual staged UTF-8
bytes as a `Uint8Array`/Node `Buffer`, or explicit `null` for established absence,
and the fixed absolute path the child can read. Undefined, malformed UTF-8 and
relative paths refuse. The four-argument legacy call refuses.

Preparation derives the context's exact byte count and SHA256 independently of
model/tool metadata. Retain the returned object in trusted supervisor memory.
It contains explicit `contextSource`, `contextPath`, decoded `contextLines` and
UTF-8 `contextLineBytes`. A BOM remains part of the byte identity; the pinned
HOST decoder strips it before line delivery. LF, CRLF and bare CR delimit lines;
an ending delimiter adds no phantom line. Absence and an empty existing file
are distinct: an empty file has a source binding and requires a genuine EOF read.

Preparation cannot authenticate its caller or turn arbitrary JSON into trusted
source custody. The caller still verifies native Git/source identity and staged
bytes before and after the child. The optional `fullDiff` revalidation and
existing child/response custody rules retain their prior responsibilities.

## Admission

Successful review calls must carry `open-hax.review-context/v1`, an explicit
matching source binding and a matching available observation. `review_status`
retains its existing protocol/source/permanent-refusal status contract; it is
not a filesystem observation. The serialized submission must bind the same
protocol and source. Copied hash metadata supplies no read coverage.

When context is present, only actual completed generic HOST `read` events at
the fixed path can supply coverage. Returned numbered content, path/type/footer,
display content/range, preview, clipping flag and loaded-path metadata must
match the independently staged lines and the pinned HOST behavior. Their event
positions and completed `state.time` intervals must precede the first stage's
start. Coverage must include every line plus EOF. A clipped individual line
cannot be repaired by claiming an EOF or by copying complete metadata.

The observed 2,000-line default and 50 KiB content cap may require multiple
bounded reads. For a byte-capped read, `display.totalLines` is the processed
count at the first overflowing line, not the whole file count; a later EOF
establishes the tail. Overlapping complete ranges may count once. Missing,
malformed, late, mismatched or clipped evidence refuses without an accepted
invocation. Model prose and nested tool-output JSON never become control events.

The optional exact `system-reminder` suffix requires nonempty loaded-path
metadata. Its body remains ancillary untrusted instruction text and contributes
no file content, coverage, hash, control event or assessment proof. Unknown or
inconsistent suffix framing refuses.

Successful exported verification returns inspectable `contextReadCoverage`
with status, total/delivered line counts and EOF. This is delivery evidence,
not proof that a model understood the context, not native provider approval,
and not permission to publish. The existing full-input pages, LAST predicate,
five stages, submission, source/session/cardinality and terminal guards still
apply. Length-ended recovery also refuses incomplete context delivery; it
authorizes no retry beyond the existing independently enforced attempt bound.

## Source ownership

`law.review-context` describes the input/observation shapes.
`shape.review-context-delivery` constructs the pinned pure HOST rendering.
`extern.review-invocation` decodes actual foreign bytes/events at the edge.
`domain.review-context-delivery` decides custody and delivery admission;
`domain.review-invocation` composes that decision with retained invocation law.
No provider, paginator, generic reader tool, filesystem writer or retry route
is added.

Primary format evidence is the retained OpenCode 1.18.18 `read.ts` blob
`678ed44510488965bacc87238bf8c80f70ad3c07` and Effect 4.0.0-beta.83 `splitLines`
source. The actual optional suffix uses
`loaded.map((item) => item.content).join("\n\n")`; this corrects the historical
discovery shorthand without changing that historical record.

Software/process documentation: GPL-3.0-or-later.
