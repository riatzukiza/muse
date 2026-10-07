# Self-authored review publication plan

## Problem and complete outcome

Muse issue12 has an existing P0, 3-point Ready card with seven acceptance
criteria. Its publisher currently accepts a validated requested event and
passes it directly to GitHub, without comparing the authenticated publisher
and current PR-author identities. The accepted eta terminal caller awaits the
write but does not consume a distinct publication outcome. The full repair must
make self-authored evidence truthful, preserve different-author behavior, and
keep independent approval policy unsatisfied by a self review.

This is a planning refinement of the existing card, not implementation or a
new operational grant. The complete original criteria and original comment
remain byte-preserved in
`docs/agile/kanban/make-self-authored-review-publication-truthful-ae0549a0.md`.
That card carries the full scope, pure/adapter boundary, positive/adversarial
matrix, red/green sequence, estimate question and integration holds.

## Bound source observations

| Source | Bound revision and meaning |
| --- | --- |
| Personal plan base | `846b47efd1fa5f9e72f6fe8b2cf45cfb347aba0f`, existing personal sync PR1 candidate; draft/blocked/auto-merge off at intake. |
| Current owning-origin Muse main | `c1369c223cf3c57e3e31934a6d746bfcdfe73f5a`; source observation, not automatic personal synchronization or integration. |
| Foresight declared child pin | `b4bdb0a7d019bb33c71aba1bd8daec5933e7ebde` in root `fcfc2d17f28640203066ddfe0a22f87db7372a32`; unchanged. |
| Accepted-by-owning-origin/current-upstream eta caller | `09a4454480baa67f6fdc40f6f73f5e48ef0457d1`, `.github/workflows/opencode-code-review.yml`; caller default Muse publisher is `0b9a91492c8355e6933dc2164d35668cb76d9e60` unless `muse_revision` is explicitly selected. |

Captured publisher source at the plan base, current owning origin and the
caller's default pin shows direct `event: envelope.event` publication. Captured
eta lines580–586 and764–765 select/package that publisher; lines1414–1427 await
`publishReview` without consuming its returned data. This proves a remaining
source contract boundary, not a new live HTTP422 reproduction.

Foresight's separately unchanged eta-mu gitlink is
`0ed56aa74a53a1d1e9c2e55ce95451817a7f3a90`. Its exact caller source is also
captured: it selects Muse `76c57712a48ef48100259231a2e9d54069c2b14a` and invokes
the older OpenCode GitHub action with GITHUB_TOKEN, without packaging/calling
the standalone publisher. Do not project the current owning-origin09a caller
onto that older pin or transfer qualifications between them.

Native issue12 is open, unassigned and has no comments at intake. Complete
current origin/personal Muse PR inventories cover cold builds (issue11) and
current-evidence summaries (issue13), not this identity outcome. Native closed
PR15 describes intake only; PR19/20 immutable-input/trace restoration does not
supply the missing identity comparison. A targeted personal self-authored PR
history search returns zero. These are bounded dedup observations; absence of
an app thread inventory does not establish exclusive ownership. Recheck full
native scope and active ownership before implementing or publishing.

## Authority and outcome boundaries

The trusted publisher resolves native identity; it does not accept candidate
assertions as identity authority. Portable data records resolved/refused state,
requested/chosen event, actual publication and independent eligibility. Outer
adapters own GitHub reads/writes. Do not assume installation-token identity can
be obtained with a user-only endpoint: the supported App identity surface must
be verified by the owning integration before implementation.

The proposed default for a verified self-authored result is COMMENT-only.
Retain an explicit unsupported machine-readable outcome when safe identity
resolution or COMMENT publication is unavailable. Both preserve the original
acceptance; planning review decides the adapter contract before code. Same
identity cannot submit APPROVE or REQUEST_CHANGES. Different verified identities
retain existing behavior. Any publication failure remains a failure. A
successful self COMMENT supplies no independent approval, even with zero
findings, and cannot clear a requirement for independent approval.

This identity boundary adds to all existing complete-input and revision/line
checks. It does not reduce them. New code must preserve portable laws before
runtime adapters and satisfy Muse's zero-warning commands and accepted eta
caller contract tests. No workflow, live credential, identity allowlist,
provider, deployment or branch-protection setting changes here.

## Native board visibility and limitations

The actual installed `@eta-mu/rheos`0.1.0 CLI read Muse's explicitly documented
synchronized JSON compatibility config in a private exact-source fixture. It
reported12 cards, including this UUID as Ready/P0/3 points, and read all original
criteria/comment. All16 copied config/task/event files remained byte-exact and
no files were created by those reads. This is native board visibility, not
current-source EDN qualification or a lawful transition. The lost ef3 artifact
was not recreated or asserted equivalent; JSON usage is permitted by Muse's
own guidance. No card status, checklist, engine comment or event is changed by
this planning refinement.

## Evidence and review obligations

`source-provenance.json` binds12 source captures to Git/native blob identities;
`native-capture-manifest.json` binds26 read-only command stdout/stderr
captures. Source-copy captures are not additional independent observations.
Review the entire card/design/evidence scope, including empty capture streams,
and retain failures as failures. The full-tip proof is recorded separately
outside the source after commit; the receipt identifies this prepared plan,
not a completed implementation, reviewer approval or live App qualification.

The original 3-point estimate remains; review may require lawful cross-owner
breakdown rather than reducing the seven criteria. Foresight134/trusted
publisher separation, exact future Muse consumption by eta, the unqualified
draft sync parent, native review quorum and private red/green evidence remain
explicit prerequisites. There is no implementation or transition authorization
from this document alone.

## Receipt validation scope

The actual current Receipt River child API at
`154440f3c997aa9208194bba59b5edbef3654f78` examined all37 physical receipt
records. The declared new record37 is valid. Sixteen inherited records
(19,20,23–36) are refused under that API for missing repo and, on some rows,
unknown historical kinds or non-UTC-calendar timestamp strings. Their bytes
remain unchanged; this probe does not claim the whole historical river valid
or silently replace the repository's historical writer contract. The full-tip
proof rechecks the declared suffix and retains those exact API refusals.
