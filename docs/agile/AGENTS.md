# Kanban process (muse)

This repo's board lives in `./kanban/` (this directory's child). This file
is deliberately outside the task tree, so process guidance is not projected
as a card.

## Tool

Canonical source Rheos discovers `openhax.kanban.edn`; the published
`eta-mu` CLI installed here discovers the synchronized JSON compatibility
mirror. Both select:

```json
{
  "tasksDir": "./docs/agile/kanban",
  "fsm": "promethean"
}
```

Run everything from the repo root.

## Card anatomy

One Markdown file per card, at the source path returned by Rheos. A filename
is a location, not canonical identity. The native writer may choose a title
slug with a UUID suffix; do not rename its output just to make the basename
equal the full UUID. Existing `<uuid>.md` files remain supported.

For new Muse cards, supply an explicit UUIDv4 with `rheos create --uuid ...`.
The `uuid` frontmatter field is the identity used by parent/dependency links.
Keep historical slug identities and creation events intact; retire superseded
cards through a lawful Rheos transition and record the replacement UUID in a
native comment.

Use the native writer's scalar representation. `--points 3` is an estimate;
the writer emits `points: "3"`, and the reader accepts it as the same scalar
estimate. Labels are comma-separated scalars, as required by the operator.
This guide does not add a YAML type checker or alternate card renderer.

An example of the native frontmatter representation is:

```yaml
---
uuid: "<uuid v4>"
title: "Imperative title"
status: incoming        # Promethean initial state
priority: P1            # P0..P3
labels: "area, 3sp"
created_at: "<ISO-8601>"
points: "3"
category: audit         # free-form grouping
---
```

These representation rules follow the owning Rheos
[creation writer](https://github.com/open-hax/eta-mu/blob/f8389642f4169e0cea1edc8858ab9620febc4303/packages/rheos/src/rheos/backend/domain/task_create.cljs)
and its returned `source-path`. Changes to board parsing, naming or writing
belong upstream in Rheos. The convention revision itself requires independent
planning review before the current planning artifacts can be accepted.

Body is markdown. `---` on its own line after the frontmatter toggles
comment sections (muted in the web UI) — `eta-mu kanban comment` appends
these for provenance.

## Statuses

`icebox → incoming → accepted → breakdown → ready → todo → in_progress → testing → review → document → done`
(`blocked` for scoped impediments, `rejected` for dead ends, and `archived`
for terminal storage).

The CLI's `frontmatter <uuid> status <new>` delegates to Rheos and enforces
the configured FSM, WIP limits, and transition gates. Walk lawful edges;
don't edit status frontmatter directly.

## Loop

1. `eta-mu kanban count` / `list` — board state.
2. `eta-mu kanban search "topic"` — don't duplicate cards.
3. Walk a ready card through `todo` to `in_progress`, then
   `comment <uuid> "plan/progress"` so the ledger of intent travels with
   the card.
4. Finish through `review` → `document` → `done`.

## State

- Rheos writes through `docs/agile/kanban/.events`, a symlink whose physical
  target is `.ημ/kanban-events`. Never replace it with a ledger under docs.
- Muse's historical Receipt River remains the existing repo-root
  `receipts.edn`; do not relocate or rewrite its prior entries.
- Not Trello. `sync trello`/`sync github` exist upstream but are not part
  of this repo's process.

## Web UI

```
eta-mu kanban serve --tasks-dir docs/agile/kanban --port 8791
```
