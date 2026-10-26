# Contract ownership and host translation

## Recovered intent

The shared system is not a single runtime and Katamorph is not a replacement
runtime. The stable seam is:

```text
Katamorph contracts
  -> Muse assembly
  -> target-neutral resolved agent catalog
  -> Muse target projection
  -> host artifact or explicit incompatibility
  -> Turn Processor
  -> in-process Eta-Mu agent or deployed Sol transport
```

Knoxx is a downstream composition target. It remains deferred until these
parts work independently; code should not be migrated into or out of Knoxx to
prove this architecture.

## Ownership

| Part | Owns | Does not own |
| --- | --- | --- |
| Katamorph | Resource identity, shared contract vocabulary, validation, laws, and small pure interpreters | File discovery, deployment, host SDK objects, model calls, or storage connections |
| Muse | Importing and assembling declarations, resolving references, translating lawful data into host shapes, and reporting incompatibilities | The meaning of shared contracts or the agent turn loop |
| Keryx | The herald/interpreter role fulfilled by Muse's translation pipeline | A second package or competing source of contract truth |
| Turn Processor | The host-neutral turn loop and its message/tool lifecycle | Provider catalogs, host configuration files, or service deployment |
| Sol | A service deployment of Turn Processor with HTTP/streaming/session adapters | A second contract language or a separate turn algorithm |
| `eta-mu agent` | The CLI client, EDN session UX, and transport selection | Hardcoded model/provider truth |
| Knoxx | A later application composition using stable upstream parts | Defining the upstream seams while they are still moving |

Calling Katamorph a linter is useful but incomplete. Validation is its first
visible role: it says whether a declaration is admissible and explains why it
is not. It also owns stable identities and laws that multiple interpreters
must agree on. Katamorph may provide pure interpreters where a law has a
portable evaluation, but it does not choose a host or perform I/O.

Muse performs the Keryx role: given lawful declarations and a target, it must
produce a validated host artifact or an explicit incompatibility. It must
never silently drop unsupported semantics.

## First proven projection

`examples/contracts/host-runtime.edn` declares a provider, model family, model,
remote MCP server, and agent without OpenCode vocabulary. The OpenCode config
imports that file. The pipeline is:

1. `eta-mu.opencode.config` classifies imported namespace files as contracts.
2. `eta-mu.shape.katamorph/assemble` validates every projected resource and
   rejects duplicate qualified identities.
3. `eta-mu.shape.agent-catalog/resolve-catalog` preserves those definitions
   while resolving local references into qualified provider, model-family,
   model, default-model, and agent links. Unknown, disabled, or ambiguous
   references fail here, before any host is selected.
4. `eta-mu.shape.target.opencode/catalog->settings` translates supported
   provider, model, family, agent, default-model, and remote-MCP semantics.
5. The daemon deep-merges later raw `:settings` fragments as explicit
   host-only overrides and renders JSONC.

Unsupported provider API shapes, MCP transports, unknown default-model
providers, and target provider-ID collisions fail before a host artifact is
written.

## Drift inventory

These are current sources of overlapping model, provider, agent, or MCP
meaning. They are migration inputs, not all equally canonical.

| Location | Reinvented responsibility | Disposition |
| --- | --- | --- |
| `spaces/eta-mu/packages/eta-mu/.../commands/agent.cljs` | Default model/provider, flags, credentials, in-process turn assembly | Keep client/session UX; replace catalog truth with contracts |
| `spaces/eta-mu/packages/eta-mu/.../commands/sol.cljs` | A second in-process agent command with the same defaults | Replace with the same client path and a selectable Sol transport |
| `spaces/eta-mu/packages/sol/.../domain/models.cljs` | Provider normalization, family inference, model projection, defaults, allowlists | Extract portable vocabulary to Katamorph and shared resolution/projection to Muse; keep Sol-only runtime policy in Sol |
| `spaces/eta-mu/packages/sol/.../domain/contracts/mcp_servers.cljs` | MCP contract normalization and selection | Consume the shared Katamorph resource assembly |
| `spaces/eta-mu/packages/extensions/.../custom_providers.cljs` | Providers and model families encoded directly as JavaScript objects | Generate target data from contracts; retire the duplicate catalog |
| `spaces/eta-mu/packages/mcp-contracts` | Independent disk loader and MCP projection | Fold its general semantics into Katamorph + Muse; retain only compatibility adapters if needed |
| `spaces/eta-mu/packages/turn-processor/.../law/agent.cljs` | Agent law leaves `:model` as `any?` | Link to a real model reference contract without putting catalog interpretation in the turn loop |
| `~/.ημ/config/opencode/settings/providers.edn` | Live OpenCode-shaped provider/model catalog | Treat as migration source and later generated/override data, not canonical truth |
| `~/.ημ/config/opencode/settings/core.edn` | Host-shaped default model selection | Project a contract default where portable; retain explicit host override when intentional |

The current Sol code already uses Turn Processor. The missing migration is not
"move Sol to Turn Processor"; it is to make Sol and the CLI consume the same
contract-resolved runtime configuration.

## Capability vocabulary

Two related meanings currently share the word capability:

- Katamorph `:cap/id` is a portable grant or grouping: what an actor or role is
  allowed to use.
- Muse's executable capability descriptor describes an implementation:
  input, output, effects, and handler linkage for a target/runtime.

They should not be collapsed into one ambiguous schema. A portable grant may
reference an executable capability identity. The handler and host adapter stay
in Muse; authorization relationships stay in Katamorph.

## CLI and session target

`eta-mu agent` should select a transport without changing the user-visible
session model:

```text
EDN session artifact + resolved agent/model contracts
  -> agent client port
       -> in-process adapter: Turn Processor
       -> remote adapter: Sol /api/agent/* + event stream
  -> the same normalized turn events
  -> the same EDN session persistence
```

Credentials remain environment/secret-store inputs referenced by name from a
provider contract; secret values never enter the contract file or session
artifact.

## Stabilization order

1. Prove Katamorph assembly and one Muse target projection. **Implemented.**
2. Complete the target-neutral model/provider/family resolver and agent
   references. **Implemented as the Muse agent catalog.**
3. Make `eta-mu agent` load that assembly and introduce the in-process/Sol
   client port while preserving EDN sessions.
4. Make Sol consume the same resolved catalog and remove duplicated defaults.
5. Add Muse projections for Claude, Codex, Sol, and remaining OpenCode
   facilities such as hooks/plugins.
6. Rebuild downstream compositions, including Knoxx, only after the upstream
   seams are stable.

Do not create a separate Keryx package yet. If Muse later contains several
clearly separable deployables, packaging can follow the proven boundaries
rather than guessing them in advance.
