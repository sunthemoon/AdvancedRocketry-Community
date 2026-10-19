# TASK W106: cargo leaf C-P, the tank payload host, the cargo mutation and the production tag

Root: Claude. Date: 2026-10-19. Read [WORKER-RULES](WORKER-RULES.md), then the frozen cargo design
[records/W11CD-DESIGN.md](records/W11CD-DESIGN.md) sections 2.2 to 2.4, 3.2, 5.1, 5.2 and section 6's C-P row, the
[ADR-065](../../decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md) note "W11c and W11d implementation
note: rocket cargo scope and protections" (the protection scope this leaf implements part of) and R-035 in
[docs/11-RISK-REGISTER.md](../../11-RISK-REGISTER.md). Also read [records/W99.md](records/W99.md) and
[records/W100.md](records/W100.md) (C-I and C-K, which this leaf builds on). You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w106` (new; from the shared branch at the commit Root names in the launch prompt) |
| Branch | `codex/v1.8.0-w106` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W106.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W106/` |

This leaf adds a payload host, a snapshot mutation and a persisted adapter ID, so AGENTS section 13's rule for
persistence slices applies: write a one-page implementation plan at the top of the record before the code (which
classes, which bounds, which refusals and which tests), and Root has a read-only review done before the merge.

## What to do (section 6's C-P row; the design is frozen, so a disagreement is a finding to report, not to change)

1. **The tank payload host `pressurized_tank_v1`** (section 2.4): exactly `advancedrocketrycommunity:pressurized_tank`;
   payload `{tank: <TankSave root>}` with schema 1 and its existing bounds (8,192 bytes, depth 16, 1,024 nodes);
   capture refuses `repairRequired()`; capacity is derived from the COMMON multiplier, never stored; restore through
   `PressurizedTankBlockEntity.load` and the existing capture-equality check; it joins `defaults()` and `freeze()`, and
   its type is reserved against external registration like chest, barrel and bay.
2. **The pure cargo mutation `RocketCargoMutation`** (section 2.4): builds the after snapshot from the before snapshot,
   the cursor and the frozen delta; keeps the snapshot ID, dimension, origin, creation time, anchors, stats, propulsion
   and every other block; refuses unless those are identical, every payload and aggregate bound holds and the codec
   round-trips; deterministic, so recomputation gives the same after digests; no after snapshot kept between capture
   and apply.
3. **Selection and eligibility** (section 2.3): the deterministic server-side payload selection in canonical block
   order for the fluid loader and unloader, the persisted cursor within its bounds, and the one side-effect-free
   eligibility predicate (`CARGO_TOO_LARGE`, `INVALID_DATA`), checked before sequence allocation, queueing,
   reservation or mutation, and again at final capture if an input changed.
4. **Bounded preparation and permit-gated publication** (section 2.3's preparation cursor, ADR-065 section 4.1.1): one
   reconstruction per server tick, 16 MiB charged bytes and 16,384 block visits; the transient preparation lock makes
   `RocketRuntime.resourceMutationAllowed`, launch and disassembly refuse; publication revalidates. The publication
   permit stays gated: no production caller admits cargo (admission remains disabled; the loader devices are C-A).
5. **The v1.8 production tag extension** (section 2.4): a generated current-version `rocket_movable` extension for
   `pressurized_tank` under `src/generated/v1.8/resources`, `replace: false`, with preauthoring and generated-hash
   provenance; do not edit the sealed v0.5 provider or output. Trapped chest joins only if a test proves
   capture/restore equality with the unchanged `VanillaContainerRocketAdapter` on an actual vanilla trapped chest;
   otherwise leave it out and say so. If including the extension needs a build-resource change, stop and report: build
   scripts are Root's.
6. **Tests** (the C-P row's A0 and A1): 1, 749 and 1,000 mB; over-capacity, incompatible and unknown Fluid; every nested
   bound including inner-passes-outer-fails for bytes, depth and nodes with O1 and full device and snapshot overhead;
   unchanged propulsion, geometry and snapshot ID with a new content hash; deterministic recomputation; budget
   accounting; A1 mutation and adapter fixtures that capture and restore the after payload and tell cargo apart from
   same-Fluid propulsion; real production-tag assembly proving tank capture with chest and barrel still included; the
   packaged tag check. These are not crash-safe transaction or disassembly evidence (that is C-E and C-S).
7. Run each new test class alone, the whole unit suite, `runData` twice with a clean diff, one full GameTest with the
   unchanged log checker, and the packaged tag check, all on committed source. Known open items you will meet and
   should only report: `RocketJournalScheduleMatrixTest` can time out on this machine under load (W104), and a local
   full GameTest under load can log more host-lag notices than the checker's ceiling.

## Write scope

New classes for the host, mutation, selection, eligibility and preparation (in `rocket` and `resourceescrow` as the
design places them), the minimal hooks in `RocketBlockEntityAdapters`, `RocketRuntime` and the escrow core that the
design names, the data generator entry and generated output for the v1.8 tag extension, its provenance record entry,
tests, and the record. Central registration, language and data-generation entry points: append this leaf's entries
only. No change to receipts, intent or receipt schemas, existing limits, the closed `Fill` enum, proof or lease code,
fuel behaviour, the sealed v0.5 data, build scripts, or admission enablement.

## Commands and not in this task

As WORKER-RULES says. Other workers also run heavy JVMs; take the heavy-JVM lock for `runData`, the GameTest and the
packaged check and release it between runs when another worker waits. Check that C: and D: have more than 10 GiB free
before each heavy start (C: has about 13 GiB). Remove your own lock, temporary files and `build/gametest/world`
afterwards; if the tool refuses, write the paths in the record and final answer at once.

Not in this task: the devices, menus and loot (C-A), integration and the fair queue (C-E), art, recipes and text
(C-D), items (W11d), W100-R2's busy text, ADRs, the risk register, status files and the ledger (Root).
