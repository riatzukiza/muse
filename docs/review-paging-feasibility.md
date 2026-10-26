# Full-input review paging feasibility

This is a deterministic planning experiment, not an accepted review invocation. Protected run [40264714320](https://github.com/open-hax/eta-mu/actions/runs/40264714320) timed out on a now-stale Knoxx target. No retry, consumer adoption, permission change or source truncation was performed.

## Input and source custody

The run's retained input manifest identifies `actionability-input.txt`: **1,191,389 UTF-8 bytes**, SHA-256 `ed3b5f9a8c0d7dd147aa26db72cc9e846123ab8e06e574ca22714fa41c249869`. The complete file was checked against both manifest size and hash before measurement. It contains **1,191,002 UTF-16 units**, which earlier planning mistakenly called bytes. This correction changes no input bytes.

The source is [Muse c1369c223cf3c57e3e31934a6d746bfcdfe73f5a diff-chunks](https://github.com/riatzukiza/muse/blob/c1369c223cf3c57e3e31934a6d746bfcdfe73f5a/src/cljs/eta_mu/domain/review.cljc#L78), retained full-source SHA-256 `0df06e46f9307f9a80f4dd70013d6fb8f869a99ddff99fc00cf66c0e4810b466`.

## Method and observations

Pinned Babashka 1.13.219 read the actual pinned `diff-chunks` form. In isolated diagnostic namespaces only, it evaluated the form with the numeric 8,192 limit replaced by each candidate. Review-content semantics and the complete input were preserved. The experiment varied the paging transport contract: page counts, IDs and start/end coordinate boundaries differ across profiles. Source repositories and protected consumers were unchanged. The function is not an adopted configurable API; each new input/profile would need a fresh invocation and coverage evidence.

Each run retained the existing 128-line bound and surrogate handling. It asserted exact reconstruction, contiguous coordinates, sequential IDs and both bounds. Every reconstructed UTF-8 hash equals the original manifest hash. The unmodified default reproduced all 146 pages from the failed invocation.

| UTF-16 units per page | Pages | Character-bound nonterminal pages | Line-bound nonterminal pages | Terminal pages |
| --- | --- | --- | --- | --- |
| 8,192 | 146 | 145 | 0 | 1 |
| 16,384 | 73 | 72 | 0 | 1 |
| 32,768 | 37 | 36 | 0 | 1 |

This input is character-limited: increasing the candidate limit halves or approximately quarters delivery calls. The 128-line cap did not bind any nonterminal page. That observation does not generalize to line-limited future inputs.

At the failed run's average 63 pages per 120 minutes, linear extrapolations are about 278, 139 and 70 minutes respectively. These are provisional arithmetic, not latency forecasts: larger responses may take longer per page, and complete assessment and publication add independent costs. The source target is stale for any new admission.

## Planning decision and follow-ups

Proceed with independent ownership/seam review because deterministic call-count reduction exists without dropping input. **Stop implementation** until that review accepts an owning resource and a named consumer boundary. For another motivating input, no useful reduction means stop/reconsider rather than raising limits automatically. A reviewed trusted host profile and a fresh accepted complete invocation remain necessary before adoption.

The planning-only card is `d1d94066-abb7-4439-af0e-cfdd8df4f337`; implementation `7ee34a27-324a-4201-b026-f8d9ae6691ee` depends on it, and protected qualification `6118415f-632c-49e2-8970-cd614c0b8628` depends on implementation. Their estimates and ownership remain subject to planning review.

The original slug identity remains as historical native-created evidence with a supersession comment. Rheos refused the direct incoming-to-rejected transition; a later inspection of the owning FSM established the lawful incoming-to-archived edge. The native CLI archived it and `read-task` confirmed that status. Creation events and prior receipts retain their exact prefixes; no status was hand-edited or forced.

Native creation with explicit UUID chooses a title-derived filename with an identity suffix, and emits point estimates as quoted scalars. This behavior is intentional in the owning creation writer and matches native readback. This revision proposes aligning the authoring guide with that writer, retaining explicit UUIDv4 identities for new cards and the operator-required comma-separated labels. No card was manually renamed and no historical event source-path was rewritten. Independent review must accept the guide revision before it establishes a convention for these planning artifacts; the original findings are not dismissed merely by documenting the proposal.
