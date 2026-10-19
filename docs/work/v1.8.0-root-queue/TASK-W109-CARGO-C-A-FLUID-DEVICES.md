# TASK W109: cargo leaf C-A, the rocket fluid loader and unloader devices (admission unwired)

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w109` (new; from the shared branch at the commit named in your prompt, which has C-P merged) |
| Branch | `codex/v1.8.0-w109` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W109.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W109/` |
| Contract | [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md), frozen at `49f7e08b`: section 5.1 (shared contract, the fluid column), 5.2, 5.4, section 7's protection scope, and the C-A row of section 6's leaf table. [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md) sections 3 and 4 apply unchanged where 5.1 says so |
| Risk and ADR | R-035's cargo paragraph and ADR-065's "W11c and W11d implementation note" (`efdd7f23`), recorded by Root before C-P and C-A |
| Prior leaves | C-I (W99), C-K (W100) and C-P (W106) are merged; read their records for the cargo kinds, cursors, the tank host and the identity rule |

## Scope (the C-A row)

Build both W11c devices with admission **unwired**: nothing starts an escrow operation from them yet (that is
C-E).
- `RocketFluidCargoBlock` and `RocketFluidCargoBlockEntity`, with the role fixed by the block (LOAD for the
  loader, UNLOAD for the unloader), registered as the design names them (or the closest name that fits the
  existing registries; say which in the record).
- The root `arce_rocket_fluid_cargo`, schema 1, at most 32 KiB, depth 20 and 2,048 nodes: owner, a fresh
  placement UUID, the role (which must equal the block's), the binding, one tank of 0 to 16,000 mB in a native
  envelope of at most 8 KiB, and the escrow section (at most 256 bytes and one marker). An unknown, future,
  invalid or mismatched root is kept raw and protects the device, as section 7 and R-035 state.
- `FLUID_HANDLER` on every side: the loader only fills and the unloader only drains, under W11b3's rules
  (claimed, valid root, loaded chunk, unlocked, side-effect-free simulation, zero for stale, off-thread or
  reentrant calls).
- `RocketFluidCargoMenu` with W11b3's scalar container data plus the role and the cursor (relative x, y and z),
  owner only within 8 blocks, and the held-container exchange through `TankContainerExchange.plan`. A client-only
  screen (no `net.minecraft.client` reference from common code).
- W11b3's drop carrier (owner and tank, at most 12 KiB, depth 18, 1,024 nodes) and its owner-on-placement rule;
  the loot tables that use it.
- The linker roles for the two devices (the binding codec and the one-change-per-10-ticks rule are W11b3's).
- `RocketCargoStatus` with section 5.4's members, including W11b3's, so the menu can show a status; with
  admission unwired the device shows `ADMISSION_DISABLED` as W11b3's device does.
- The protection of section 5.1's table: an escrow-locked or unsupported root refuses player removal before
  loot, leaves the explosion list, drops nothing and refuses extraction; `onlyOpCanSetNbt()` is true and the
  item refuses a `BlockEntityTag`. A native save that cannot keep a root follows section 7 exactly (the
  bounded preflight; only an observed incompatibility uses `GuardedChunkSaves`).

Art, models, recipes, final text and provenance are C-D's. If an existing check fails without a name or a
status text for a registered block, add only the English and Chinese strings of section 5.4 and 5.5 through
the existing language providers and say so in the record; if a check needs art or a recipe, stop and report.

## Tests (the C-A row)

A1 (GameTests) for: the local capabilities; simulation, stale and reentrant calls; ownership, range and
unload; menu replay; carrier ownership; protected removal; non-operator NBT; cut-G2. Also, under section 7:
raw preservation, a root over the work budget, an observed native veto and Level saturation at 256
coordinates. Command replacement (`/setblock`, `/fill`) stays a recorded residual, not prevented salvage. A0
(unit) for the root codec at every bound, just inside and just outside.

## Commands

- Each new test class alone, then the whole unit suite once.
- `runData` under the heavy-JVM lock (new blocks, loot and any strings), and commit its output with a clean
  `git diff --exit-code` afterwards.
- `runGameTestServer` under the lock on a **committed** SHA, then the log checker. A new expected WARN or ERROR
  needs its own manifest entry with an exact count, and the record says why.
- `python -B scripts/validate_repository.py --require-approved-identity`.
- Check that C: and D: have more than 10 GiB free before each heavy start; other workers take the lock too.
- At the end, delete your generated `build/gametest/world` and old jars with a plain `rm -rf` after checking the
  lock's `owner.txt` names you, and release the lock. If the tool refuses, record the exact paths; Root deletes
  them.

## Write scope and not in this task

Write scope: the new device classes, menu, screen, carrier use, status enum, linker roles, their tests, the
registration classes, loot and language providers and data generation entry points (append this leaf's
entries only), the generated output, and the record.

Not in this task: admission and escrow operations (C-E), art, recipes and provenance (C-D), item cargo (W11d),
S2 cuts, ADRs, the risk register, status files, the ledger, build scripts and workflows (Root). No Mixin and no
network packet. If R-035 or ADR-065's cargo note does not match what you build, stop and report. A class over
500 lines gets a responsibility check in the record; one over 800 lines is a stop and report.
