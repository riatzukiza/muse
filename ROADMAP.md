# Roadmap — muse's slice

> Hub: **[eta-mu/ROADMAP.md](https://github.com/open-hax/eta-mu/blob/main/ROADMAP.md)** — read that for the seam, the ownership
> table, and the sequencing rule. This file is only muse's slice.
> Last surveyed: 2026-08-04.

## What muse is, on this roadmap

**Keryx.** Given lawful declarations and a target, produce a validated host
artifact **or an explicit incompatibility** — never silently drop unsupported
semantics.

It does **not** own the meaning of shared contracts (katamorph does) or the agent
turn loop (Turn Processor does).

## Muse is the reference implementation of the layer architecture

This matters beyond muse. `AGENTS.md` here carries the same four-category table
the other repos declare, **plus the dependency order that makes disentangling
incremental**:

```
law → shape → extern → domain → infra
```

`law` first (describes a valid instance; no dependencies) · `shape` pure
morphisms depending only on law · `extern` decoding foreign data at the edge ·
`domain` pure decisions over shaped data · `infra` orchestrating effects.

**Muse complies because non-compliance breaks its build.** That is the whole
reason it complies and the others do not — see the hub's "Why drift keeps
happening". Any repo copying muse's rules should copy its enforcement too.

## What affects muse

- **`eta-mu:capability-schema-reconciliation`** (**ready**) — muse's capability
  shape vs katamorph's `CapabilityContract`. Muse's is currently its own.
- **`eta-mu:katamorph-canonical-cutover`** (P0) — muse declares the katamorph dep
  in `deps.edn` and **requires it nowhere**; `dsl/schema.cljc` defines its own
  capability/tool/plugin schemas.
- **`eta-mu:event-ledger-envelope-truth`** — muse carries an ANOMALY workaround
  for the event-ledger dist bug; that workaround needs a retirement path.

## Open question: does muse belong in the eta-mu monorepo?

Consistent with the ownership table. Muse is a *role*, explicitly not "a second
package or competing source of contract truth", so a monorepo home costs nothing
architecturally. Contrast katamorph, whose separateness is doing real
change-control work.

## The doc other repos should read

`docs/design/contract-ownership-and-host-translation.md` is the recovered intent
for the whole constellation — the seam, the ownership table, and the rule that
**code should not be migrated into or out of knoxx to prove the architecture.**
The hub quotes it; this is where it lives.
