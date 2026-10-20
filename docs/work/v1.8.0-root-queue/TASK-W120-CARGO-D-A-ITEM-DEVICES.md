# TASK W120: cargo leaf D-A, the rocket item loader and unloader devices (admission unwired)

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w120` (new; from the shared branch at the commit named in your prompt, which has D-P merged) |
| Branch | `codex/v1.8.0-w120` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W120.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W120/` |
| Contract | [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md): section 5.1 (the shared contract, the item column), 5.3, 5.4, 5.5 (the item devices' recipes, loot, text, art and provenance), section 7's protection scope, and the D-A row of section 6. [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md) sections 3 and 4 apply unchanged where 5.1 says so |
| Risk and ADR | R-035's cargo paragraph and ADR-065's "W11c and W11d implementation note", including its "Item cargo (D-P)" bullet |
| Mirror | C-A, the fluid devices: [TASK-W109](TASK-W109-CARGO-C-A-FLUID-DEVICES.md) (with Root's decision that C-A takes C-D's resource part) and [records/W109.md](records/W109.md). Follow its structure, naming and tests; differ only where section 5.1's item column differs |
| Prior leaves | D-K ([W110](records/W110.md)) and D-P ([W118](records/W118.md), merged `1e53314c`, with its W118F section): the item kinds, the stack codec, the buffer signature, the container mutation and the device root form `arce_rocket_item_cargo` with a `buffer` field |

## Scope (the D-A row and section 5.1's item column)

Build both W11d devices with admission **unwired**: nothing starts an escrow operation from them yet (that is D-E).
- `RocketItemCargoBlock` and `RocketItemCargoBlockEntity`, the role fixed by the block (LOAD for the loader, UNLOAD for
  the unloader), registered beside the fluid devices (say the names in the record).
- The root `arce_rocket_item_cargo`, schema 1, with C-A's bounds and fields, four slots in place of the tank (section
  5.3's stack bounds), adopting D-P's form. **W118-R6:** its codec closes the field set (an unknown key refuses, so an
  unknown root is kept raw and protects the device) and writes an empty slot as `{}`; say in the record whether D-P's
  test fixtures already match.
- `ITEM_HANDLER` over the four slots: the loader only inserts and the unloader only extracts, under W11b3's rules
  (claimed, valid root, loaded chunk, unlocked, side-effect-free simulation, nothing for stale, off-thread or reentrant
  calls); `isItemValid` applies section 5.3's stack bounds.
- `RocketItemCargoMenu`: four vanilla slots and the player inventory, owner only within 8 blocks, usable both ways by the
  owner; status, binding and cursor (x, y, z and slot) as scalar data. A client-only screen (no `net.minecraft.client`
  reference from common code).
- Drops: the buffer spills as ordinary item drops, as vanilla containers do, and the device item carries nothing, so the
  placer owns a placed device; a protected device spills nothing.
- The linker roles for the two devices (W11b3's binding codec and one-change-per-10-ticks rule).
- `RocketCargoStatus` already has section 5.4's members; with admission unwired the device shows `ADMISSION_DISABLED`.
- Protection as section 5.1's table and section 7: an escrow-locked or unsupported root refuses player removal before
  loot, leaves the explosion list, spills and drops nothing and refuses extraction; `onlyOpCanSetNbt()` is true and the
  item refuses a `BlockEntityTag`; a native save follows section 7 exactly, as C-A's does.
- **D-D's resource part, as C-A took C-D's** (the unchanged `RegisteredContentResourcesTest` and
  `RegisteredItemReachabilityTest` require it): before authoring,
  `docs/provenance/v1.8.0-c17a-rocket-item-cargo-preauthoring.md`; original 16x16 textures drawn in code, with distinct
  load and unload port marks that differ from the fluid devices'; models, blockstates, loot, the two shaped recipes of
  section 5.5 (I `item_io_board` and C `control_board` in place of L and T); the names and status text in en_us and
  zh_cn through the providers; after generation, the provenance `.json` records with generator and target hashes,
  checked by `scripts/validate_v180_provenance.py`, and the art screen
  `tools/audit/screen_generated_art.py --root src/generated/v1.8/resources` with both pinned client JARs (every texture
  CLEAR, no tool or threshold change). No upstream, LibVulpes, Minecraft or Forge asset. D-D's native S1 checks stay
  for a later leaf.

## Tests (the D-A row)

A1 (GameTests): C-A's local security and protection checks repeated for items (capabilities, simulation, stale and
reentrant calls, ownership, range and unload, menu replay, protected removal, non-operator NBT, cut-G2), the buffer
spilling exactly once, and an oversized-NBT stack left unchanged in the buffer; under section 7, raw preservation, a
root over the work budget, an observed native veto and Level saturation, as C-A ran them. A0: the root codec at every
bound, just inside and just outside, and the closed field set.

## Commands

- Each new test class alone, then the whole unit suite once.
- `runData` under the heavy-JVM lock, twice, with a clean `git diff --exit-code` after committing its output.
- `runGameTestServer` under the lock on a **committed** SHA, then the unchanged log checker. A new expected WARN or ERROR
  needs its own exact manifest entry. If the checker refuses only the host-overload warning count, record the count and
  the number of agent sessions running; Root reruns it on a quieter machine.
- `python -B scripts/validate_repository.py --require-approved-identity` and the provenance validator.
- Check that C: and D: have more than 10 GiB free before each heavy start, and that 6 GiB of memory is free before each
  Gradle run; release the lock and delete `build/gametest/world` in the `finally` of the same detached script that took
  it (WORKER-RULES).

## Write scope and not in this task

Write scope: the new device classes, menu, screen, linker roles, their tests, the registration classes, loot, language
and data generation entry points (append this leaf's entries only), the generated output, the provenance records and
the record.

Not in this task: admission and escrow operations, W118-R2 and R7 (D-E), D-D's native S1 checks, S2 cuts, ADRs, the risk
register, status files, the ledger, build scripts and workflows (Root). No Mixin and no network packet. If R-035 or
ADR-065's cargo note does not match what you build, stop and report. A class over 500 lines gets a responsibility check
in the record; one over 800 lines is a stop and report.
