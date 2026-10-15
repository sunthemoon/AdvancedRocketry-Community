# TASK W82: W11b3 leaves B3-A and B3-D (the fluid fuel loader device, the linker, and their data)

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w82-w11b3-device` (from W81's branch `codex/v1.8.0-w81-w11b3-codecs` at `97133348`, which Root is merging into the shared branch) |
| Branch | `codex/v1.8.0-w82-w11b3-device` (local only; Root merges) |
| Design | [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md), revision 2 (frozen): sections 3 and 4, and the B3-A and B3-D rows of the leaf table in section 5 |
| Input | `records/W81.md` (the codecs, bank selection, refill pacing and `FluidFuelLoaderStatus` in `rocket/fuel/loader`) |
| Record | `docs/work/v1.8.0-root-queue/records/W82.md` (new) |

## What to build

**B3-A:** `FluidFuelLoaderBlock`, `FluidFuelLoaderBlockEntity`, `FluidFuelLoaderMenu`, the client-only screen,
`LinkerItem` (the rocket-binding half only), the drop carrier item, their registrations and the loot entry, as the
design's sections 3 and 4 describe, using W81's codecs. **Admission is not wired**: the loader never moves fuel
into a rocket in this task, and shows `ADMISSION_DISABLED` where a fill would start.

**B3-D:** `V180FluidFuelLoaderData`, original art (16x16, made for this project; reuse no legacy or vanilla art),
models, blockstates, the recipes (the loader takes an **empty** pressurized tank only), advancements, English and
Chinese text for every `FluidFuelLoaderStatus` member and the items, and both provenance files the design names
(the Markdown file and a JSON record with target hashes, I5). Run the W53 generated-art screen on the new textures.
Write the provenance before authoring the art.

**Root's decisions on W81's questions:**
- A Fluid whose native tag could never fit an escrow operation's payload (W11A section 1: 12,288 bytes, depth 16,
  512 nodes for the whole operation) is refused **at intake**: the loader's fluid capability accepts none of it.
  The tank codec's own bounds stay as they are, so stored data still loads. B3-E will also refuse such a fill
  with `UNSUPPORTED_DATA`. Test the intake refusal.
- The refill pacer is not saved; a device waits a full 20 ticks after a load or restart. Accepted.

Central files: you may append this slice's entries to the registry classes, the language data and the
data-generation entry point. Root resolves merge conflicts.

## Tests

The B3-A and B3-D acceptance in the design's leaf table, except what needs the W11b2 branch:
- GameTests with the real loader and a rocket: simulation; stale and reentrant capability; owner, range and
  unloaded rejection; duplicate IDs; menu replay; unload and reload; one-carrier break and place; the carrier owner
  rule (operator and non-operator foreign-owner cases); protected removal by player, explosion and command
  replacement for an unsupported root (and for an escrow-locked fixture if the shared branch can build one);
  non-operator NBT, asserted as `ForceFieldGameTests` line 308 does; the intake refusal above.
- cut-G2 and the old-entity-schema S1 rows need W11b2's rocket integration: list them for B3-E and do not build them.
- runData twice with a clean diff; resource references; recipes and reachability including the filled-tank
  refusal; registry IDs; provenance hashes; loot; `RegisteredContentResourcesTest`.

## Commands

- The whole unit suite once.
- `runData` and `runGameTestServer` under the heavy-JVM lock on a **committed** SHA, then the log checker.
- At the end, delete your generated `build/gametest/world` with a plain `rm -rf`, and make sure the lock is
  released. If the tool refuses, record the exact paths; Root deletes them.

## Not in this task

- Admission (`prepareFill`, B3-E), the authority move (B3-U), S2 cuts, V1 and V2.
- Any upstream or LibVulpes copy.
- ADRs, the risk register, status files and the ledger (Root).
