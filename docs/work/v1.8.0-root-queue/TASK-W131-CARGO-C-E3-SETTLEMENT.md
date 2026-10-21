# TASK W131: cargo leaf C-E3, operator settlement and the end-to-end flight and disassembly cases for fluid cargo

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md), [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md)
section 3.3 (normal settlement, the O1 to O4 table, receipts and accounting, reservations), section 4 (disassembly and
launch: the latest cargo, exactly once), section 6's C-E row and the cut mapping's "Neutral survivor normalization and
veto" and "Latest-cargo disassembly" rows, and the records of the leaves this builds on: [W117](records/W117.md) (C-E1),
[W124](records/W124.md) (C-E2, with W124F), [W99](records/W99.md) (C-I) and [W100](records/W100.md) (C-K). You are a new
session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w131` (new; from the shared branch at the commit named in your prompt, which has C-E2 merged) |
| Branch | `codex/v1.8.0-w131` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W131.md` (new; the plan at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W131/` |

**Root's split of C-E** (TASK-W117): C-E1 admits, C-E2 reconciles (both merged), **C-E3 (this task)** adds operator
settlement and the end-to-end cases. Production admission stays disabled. The inventory before C-E1 found: intents
accept cargo members (`EscrowSettlementIntent.java:100`); the rocket's O1 observation is `FUEL_VECTOR_V1`; the device
observation is missing (a non-loader block entity falls into the fixture branch of `EscrowSettlementObserver` and
`EscrowSettlementDisk`); normalization is generic but untested with cargo; O4 already uses the direction-independent
`op.rocket()`; settlement pruning handles only the loader and fixtures (`EscrowSettlementPruning`).

This is a persistence slice: commit a one-page plan at the top of the record before any code; an independent read-only
review follows before merge.

## What to do, each in its own commit with a test that fails before the change where a test can show it

1. **O1 for the cargo device**: observe the fluid cargo device (its native tank signature and quantity) through C-E2's
   endpoint adapter instead of the fixture branch; the rocket's observation stays `FUEL_VECTOR_V1` with the
   invariant-envelope digest, as the O1 row says.
2. **Neutral survivor normalization and veto** with cargo, as the "Neutral survivor" row says.
3. **O2 with both projections** from one bounded native decode (the cargo composite for prefix classification, the
   fuel vector and invariant for the frozen settlement observation), both unload survivor proofs (rocket source and
   device destination) and ABANDON's exact surviving and absent endpoints; R39's native check before O2 acts.
4. **O3, O3J and O4** unchanged in order; **settlement pruning** for the cargo device; O4 READY then a governed launch.
5. **The end-to-end cases** of the C-E row: C-I's governed missing and terminal-authority cases; cancellation; two
   successive ordinary cargo-mutated flights including a mutation by the successor; exactly-once latest-cargo
   disassembly on P1-F8 (section 4).

Named (transfer-guarded) cargo stays held until the F1 fix is extended to cargo (R48's D4); do not add your own variant
of that fix, and pin today's behaviour where a test meets it.

## Tests

A1 (GameTests): O1 to O4 for a load and an unload with real devices, both unload survivor proofs, ABANDON, normalization
and veto, pruning, then a governed launch; the end-to-end cases above, each with the receipt and inspector output pinned.
A0 for the device observation, the O2 projections and pruning. The fill path's settlement tests stay unchanged and pass;
the fuel and floor receipt bytes and inspector output stay pinned (C-K's pins).

No saved format, schema, hash, bound, dictionary, proof, lease, budget or timeout changes; no existing assertion is
relaxed; `EscrowCoordinator` must not grow, new classes stay under 500 lines and `RocketTransferService` (796 lines) must
not grow. A new WARN or ERROR that a GameTest drives needs its own exact manifest entry. Stop and report if any of these
seems needed.

## Verification

Each new or changed test class alone, then the whole unit suite. Then one full GameTest with the unchanged log checker
on committed source, under the heavy-JVM lock, releasing it and deleting `build/gametest/world` in the `finally` of the
same detached script that took it (WORKER-RULES). If the checker refuses only the host-overload warning count, record the
count and the number of agent sessions running. Check C: and D: above 10 GiB and that 6 GiB of memory is free before
each Gradle run.

## Not in this task

Item cargo (D-E), the F1 fix and its cargo extension, enabling admission, C-S cuts beyond what the end-to-end cases need,
ADRs, the risk register, status files and the ledger (Root).
