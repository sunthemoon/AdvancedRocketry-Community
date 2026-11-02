# W261: static landing-pad tile

> Superseded by R289 ([TASK-W261-FIX](../TASK-W261-FIX.md), 2026-11-02): the landing pad uses upstream's own `rocketpad_` on every face, and the fifteen other tiles are excluded as the redesigned launch pad's (and, for `rocketpad_noedge`, the redesigned concrete block's) textures. See the W261-fix section of [W261.md](W261.md). Note added by Root after the independent review (W261-R2).

R287 assigns P0 to the clean `codex/v1.8.0-w261` worktree at
`7e9b9c15bf753a1ca507063f6ff842299501fa52`. This implementer may write
only the W261 import plan, coverage, pending provenance, pad provider and
v1.8 generated model, pad resource tests and task records. Scripts, logs
and central-file drafts belong to
`D:/GitHub/ARCE-Task-Evidence/root-queue/W261/`. No push, merge, delegation,
status, ledger, asset-plan, workflow or build-script edit is authorized.

## Source and face selection

At original Advanced-Rocketry/AdvancedRocketry commit
`c5cd5af62fc07cd4e0d24f06a16033f181c47c04`,
`src/main/java/zmaster587/advancedRocketry/block/BlockLinkedHorizontalTexture.java`
sets bits 1, 2, 4 and 8 for east, north, west and south neighbours.
Four present neighbours select ordinal 15, `NOEDGE`, serialized `noedge`.
`src/main/resources/assets/advancedrocketry/blockstates/launchpad.json`
maps that state to `launchpad_noEdge`; its lower-case model file
`models/block/launchpad_noedge.json` names `rocketPad_noEdge` for top and
bottom and `rocketPad_xCross` for side. Actual texture filenames are lower
case. The selected source is `textures/blocks/rocketpad_noedge.png`,
SHA-256 `06b1ed723649858c4e21003f4fb92ecd3422ad49706e86570bc201b1f6f57320`.

The upstream `landingpad.json` instead uses the static `rocketpad_` tile
on all faces; it has no connected states. R287 deliberately selects the
surrounded-cell look from the connected launchpad family for the modern
landing pad, not an exact restoration of that separate legacy block.
Use `rocketpad_noedge` for `up` and `down`, exercising R287's bottom-face
exception because the selected cell model uses that same tile underneath.
Keep the existing `minecraft:block/cube_all` parent, NEW `all` texture for
the four sides and particle, unchanged blockstate and inherited item model.
Do not import the connected variant's side texture or neighbour logic.

## Import and retained art

Inventory the single MIT source before copying. Write the plan in Python
as UTF-8 LF bytes and run the existing importer into
`src/imported/v1.8/resources/assets/advancedrocketrycommunity/textures/block/rocketpad_noedge.png`.
No retained NEW texture owns this name, so W246's collision rule needs no
`_classic` suffix. Keep review `PENDING_HUMAN_REVIEW`.
Keep the drawing code and generated `textures/block/landing_pad.png`
unchanged and generated; propose retaining it for the four sides and
particle, not deleting it or treating it as a runtime fallback.

Root-only drafts split the wildcard into one IMPORTED row and fifteen
exact EXCLUDE rows with reason `connected look not adopted (R287)`.
Preserve all other rules' first-match decisions. The ledger draft changes
only this unit's notes; governance receives the new import-verify pair.
The drafts are applied and checked only in an owned clean export, then
that export is removed. No central files in this worktree change.

## Verification and boundaries

Pin the face bindings, unchanged geometry/state/item inheritance, retained
art bytes, one-file byte-copy provenance and absence of the other fifteen
imports. Run the new tests alone, atlas/client/data tests and the whole
unit suite. RunData twice under one owned heavy-JVM lock and compare
generated resource manifests. Run importer, content-ledger, provenance and
resource Python tests, clean-export import verify, the provenance validator
with CI's JAR/output arguments, and repository validation. Use a detached
lock-owning script, check C: and D: each exceed 10 GiB, and release only its
own token in the same script's finally. No runtime behaviour changed, so
this asset-only task follows its specified verification rather than adding
a full GameTest or clean build; hosted regression belongs to Root.

IDs, recipes, collision, landing selection, occupancy, persistence,
networking, other blocks, renderers and assembled-snapshot behaviour stay
unchanged. Real-GPU V1 must inspect a pad grid, side/bottom and particle
appearance, inventory/hand views, assembled snapshots and resource reload.
Automation does not approve provenance, V1 or any v1.8 Required Gate.
