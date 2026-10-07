# Verification filename correction

Hosted CI 37575241786/job112642551437 and Sandbox 37575241799/job112642552293
failed on the new probe at validate-muse-plan.cljs:1:5: namespace does not match
filename. This is an owned preparation error, not Muse11 cold-build failure or
Foresight134 publisher-visibility failure. Tested merge e9387c55a4e97008ed10802ec2d3454a32479703
has parents 846b47e and 23d0fc4, and its tree equals the original frozen plan.
Actual complete logs preserve 212 tests / 606 assertions / 0failures/errors plus successful
cold-host/full-input builds, separately from 1 lint error / 0 warnings/exit 3.

The ordinary successor renames the live probe to validate_muse_plan.cljs while
retaining its exact executable bytes and namespace validate-muse-plan. All old
captures, archive manifests, source proofs and historical command references
remain unchanged and resolve at their original 23d0fc40f3f9da29fecde341f7dc96df93560208
head. No lint exclusion/suppression, product logic, workflow, card, config,
source pointer or provider change is introduced.

The repair uses private clj-kondo 2026.08.04 matching hosted configuration, whose
release archive digest is verified against native owner release metadata. The
global 2025.07.28 binary is not substituted. The exact owning lint target set
src/cljs src/clj test/cljs .ημ and --fail-level warning is preserved; only its
cache directory is independently owned. Corrected probe execution uses actual
current Receipt River API 154440f3c997aa9208194bba59b5edbef3654f78. Inherited 16
refusals remain disclosed and untouched; new suffix validation is distinct
from whole-ledger qualification. No backend suite/build is repeated for a
byte-identical verification rename; new head requires its own hosted gates
and native planning convergence after publication by root.

## Nonblank-record scope clarification

The live probe filters blank lines before validation and now declares
`:actual-api-all-nonblank-records`, `:record`, and
`:declared-owned-nonblank-record` explicitly. API calls, validation decisions
and behavior remain unchanged. At preserved head 879be5a, nonblank records 37/38
are literal physical lines 39/40: 38 nonblank records have 16 inherited refusals,
while 40 physical lines have 18 refusals including historical blanks 19/21.
This successor appends record 39 at physical 41; it does not normalize either
historical blank or any receipt. Final physical API proof validates the new
owned suffix separately from these inherited failures.
