---
uuid: "a76a0c8d-b9a8-4dd3-a41c-edcd1df3734a"
title: "Validate declared OpenCode tool arguments before plugin execution"
priority: "P1"
points: "3"
labels: "muse, opencode, tool-boundary, validation"
---

## Context

The current native Muse PR15 review at `9f758e8c9d91243478326c7c963e8f85bb391fd6`, run `39061983377`, read numeric page `1` and then supplied string `"1"` as the assessment tool's id, together with a JSON string in `assessment`. The actual third call reached the domain and returned `Read the diff chunk before assessing it.` The reviewer obeyed failed-call STOP; no submission or native verdict was produced.

The existing declaration makes id an optional nullable integer and assessment an optional map. Muse's supported Zod projection currently exports a bare Malli `:map` as `z.any()`. Its OpenCode adapter decodes arguments and invokes the handler without checking that projection. The pinned OpenCode 1.18.31 custom-plugin source also directly executes custom handlers; its built-in `Tool.define` decoder does not wrap those custom definitions. No provider wire-schema capture was retained, so these source facts do not establish the exact historical wire schema or why the model emitted the wrong types.

The existing typed-body owner `a9f2e662-77dc-40a9-9121-f108b8e1ccb2` explicitly excludes new adapter behavior. This focused card owns the newly discovered boundary correction separately. Earlier typed-input, compilation, review and failure evidence retains its original scope.

## Outcome

The existing OpenCode projection refuses malformed declared argument types before a plugin handler or its event/session effects execute. A bare Malli map is exposed as an object-valued map rather than an unrestricted value. Valid supplied values reach the original handler unchanged.

## Scope

Change only the existing bare-map Zod projection in `src/cljs/eta_mu/dsl/zod.cljs`, the existing execution wrapper in `src/cljs/eta_mu/boundaries/opencode.cljs`, and causal controls in the existing actual compiled eight-tool carrier `scripts/verify-full-review-input.mjs`.

Compile one declared validator for each rendered tool. Check it before decoding or invoking the handler. Validation must not coerce, parse a JSON string into a map, or substitute the validator's parsed/transformed result for the original valid arguments. Preserve arbitrary valid map keys and values. Use a payload-free error message rather than reflecting supplied values or host context.

The three-point estimate covers two existing adapter/projection boundaries and one registered compiled public control carrier, followed by unchanged normal gates. It introduces no service, new command, tool, protocol, runtime dependency or model dispatch.

## Non-goals

Do not change tool IDs, descriptions, exposure, permissions, argument declarations, domain laws, integer coercion, stage order, failed-call STOP, recovery limits, reviewer identities, native publisher or approval policy. Do not broaden types, add permissive wrappers, alter a9's target law, or claim complete Malli-to-Zod equivalence. Existing unsupported numeric/string constraint projections are not silently repaired here. No live review retry, native assessment, disposition, rollout, credential, account or paid-provider change is admitted by this card.

## Acceptance criteria

- The unchanged predecessor's real generated OpenCode public execute boundary fails the new rejection control for the retained malformed call shape. Build, import, missing-export, dependency or supervisor failures do not count as semantic RED.
- Actual generated tools refuse string-id/string-assessment, numeric-id/string-assessment, string-id/object-assessment and numeric-id with null or array assessment controls. Initialize the real handler session and read a page first; require its actual event bytes and public status to stay unchanged across each adapter rejection. This observes the selected real handler/event boundary without a substitute protocol or private-var spy. Invalid input is not repaired or retried inside the wrapper.
- Actual cold Zod-to-JSON-schema export describes assessment as an object; a JSON string, array and null refuse the bare map projection. An arbitrary nested object map remains accepted with its original keys and values.
- Existing valid numeric-id/map, legacy id+note, omitted-id empty-component assessment, trusted context and result transport retain their actual existing compiled controls. Existing ordinary-v2 controls compare returned and submitted structured assessments to their supplied values; the new record projection control compares an arbitrary nested object to its parsed result. New id-projection checks retain null and omitted-id admission. These finite observations do not claim arbitrary argument/context object identity or universal preservation; direct source conservation additionally requires the wrapper to pass the original arguments after validation. Original tests, assertions and all eight tool names remain intact.
- Validation preserves original valid arguments and all existing handler/context/result behavior. No private-var access, fabricated invocation, new export, copied approval or simulated native event is used as evidence.
- Unchanged normal `npm test`, five-target build, three cold-host checks, complete review-input controls and zero-warning lint pass on the candidate. Preserve setup deprecations, skips, failures and owned cleanup evidence honestly.
- Planning findings and code findings are settled under canonical pr-flow. Actual native Rheos readiness precedes implementation, and fresh exact-head code review/adoption precedes downstream consumption. No historical approval or full-input credit transfers to changed source.

## Verification

Freeze the exact three-path proposal, predecessor sources and retained malformed native input separately. Run test-only controls on the predecessor, then the exact two production changes through the SAME compiled public carrier. Keep full output, source/module/fixture hashes, native exits, actual warning/skip counts and terminal owned cleanup. The retained private native call may be used inertly; the public fixture uses shape-equivalent synthetic content and must be labelled accordingly. No model invocation is needed for causal RED or GREEN.

Normal automatic CI and Sandbox must qualify the final source independently. Their final numerical summaries and complete capture custody matter; a green aggregate alone does not qualify the source. Any newly exposed negative-control failure is diagnosed without removing assertions or weakening strict STOP.

## Risks

This is a generic OpenCode adapter boundary. Previously unchecked callers with invalid types may now fail earlier; preserve and assess those failures rather than weakening the contract. The check uses the existing supported Zod projection and supplies no claim that every Malli constraint is projected. Rejection is an expected boundary outcome, not a guarantee that a future model will emit valid arguments or finish a review. Planning/readiness and final native review remain real gates.

This manually authored Markdown is a planning proposal. It declares no operational status, write-id, Rheos event, readiness, implementation result, source adoption or deployment.
