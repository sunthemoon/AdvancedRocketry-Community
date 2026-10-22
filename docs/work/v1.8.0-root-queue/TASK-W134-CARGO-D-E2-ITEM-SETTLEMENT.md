# TASK W134: cargo leaf D-E2, operator settlement and the end-to-end flight and disassembly cases for item cargo

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md), [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md)
section 3.3 (normal settlement, the O1 to O4 table, receipts and accounting, reservations), section 4 (disassembly and
launch: the latest cargo, exactly once), section 6's D-E row and the cut mapping's "Neutral survivor normalization and
veto", "O2 survivor/absence, O3, O3J, O4 and pruning" and "Latest-cargo disassembly" rows, then the records of the
leaves this builds on: [W132](records/W132.md) (D-E1, with W132F), W131 (C-E3, with W131F; `records/W131.md`, which the
shared branch has once W131 merges), [W118](records/W118.md) (D-P), [W120](records/W120.md) (D-A) and
[W110](records/W110.md) (D-K). You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w134` (new; from the shared branch at the commit named in your prompt, which has D-E1 and C-E3 merged) |
| Branch | `codex/v1.8.0-w134` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W134.md` (new; the plan at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W134/` |

**Root's split of D-E**, as for C-E: D-E1 (W132, merged with W132F as `83d5386b`) admits and reconciles item cargo;
**D-E2 (this task)** adds item operator settlement and the end-to-end cases, the item counterpart of C-E3. Production
admission stays disabled. C-E3 made O1 to O4, normalization, pruning and the end-to-end cases work for the fluid device;
reuse its seams and add the item device beside it instead of copying them. Where C-E3 left an item branch out on
purpose (for example the gated fixture fallback in `EscrowSettlementPruning`, W131F), this task adds it.

This is a persistence slice: commit a one-page plan at the top of the record before any code; an independent read-only
review follows before merge.

## What to do, each in its own commit with a test that fails before the change where a test can show it

1. **O1 for the item device**: observe the item cargo device (its four buffer slots, as native typed NBT) through
   D-E1's endpoint adapter, as C-E3 does for the fluid device; the rocket's observation stays `FUEL_VECTOR_V1` with the
   invariant-envelope digest, as the O1 row says.
2. **Neutral survivor normalization and veto** with item cargo, including an old versus a normalized wrapper and the
   mismatches the row lists.
3. **O2 with both projections** from one bounded native decode, comparing item stacks with their NBT tag types (as
   W132-R3 does in the native oracle); both unload survivor proofs (rocket source and device destination) and
   ABANDON's exact surviving and absent endpoints; R39's native check before O2 acts, holding only that operation.
4. **O3, O3J and O4** unchanged in order; **settlement pruning** for the item device; O4 READY then a governed launch.
5. **The end-to-end cases** of the D-E row: competing item devices; a partial move and a restart; cancellation; two
   successive ordinary cargo-mutated flights including a mutation by the successor; exactly-once latest-cargo
   disassembly on P1-F8 (section 4), with an eligible checked legacy COMMITTED record in the live journal as W131F's
   case has; the refusal comparisons the row lists.

Named (transfer-guarded) cargo stays held until the F1 fix is extended to cargo (R48's D4); do not add your own variant
of that fix, and pin today's behaviour where a test meets it.

**F3 and F1b.** W133 (merged as `695f76b0`, Root decision R55) promotes an assembled rocket with launchable fuel at
launch, so no launch step here may return `INVALID_STATE` for such a rocket. A rocket that still has a retained transfer
record meets the governed no-transfer guard (`TRANSFER_JOURNAL_BLOCKED`, F1b) until W130 (D2) merges; if a case meets
it, say which check refused, report the code and do not work around it. Do not change either fix here.

## Tests

A1 (GameTests): O1 to O4 for a load and an unload with real item devices, typed item-NBT O2 and readback, both unload
survivor proofs, both ABANDON survivors, normalization and veto, pruning, then a governed launch; the end-to-end cases
above, each with the receipt and inspector output pinned. A0 for the device observation, the O2 projections and
pruning. The fill path's and C-E3's settlement tests stay unchanged and pass; the fuel, floor and fluid receipt bytes and
inspector output stay pinned.

No saved format, schema, hash, bound, dictionary, proof, lease, budget or timeout changes; no existing assertion is
relaxed; `EscrowCoordinator` and `RocketTransferService` must not grow, and new classes stay under 500 lines. A new WARN
or ERROR that a GameTest drives needs its own exact manifest entry. Stop and report if any of these seems needed.

## Verification

Each new or changed test class alone, then the whole unit suite. Then one full GameTest with the unchanged log checker
on committed source, under the heavy-JVM lock with the atomic pattern and the hand-off rule in WORKER-RULES, releasing it
and deleting `build/gametest/world` in the `finally` of the same detached script that took it. If the checker refuses
only the host-overload warning count, record the count and the number of agent sessions running. Check C: and D: above
10 GiB and that 6 GiB of memory is free before each Gradle run.

## Not in this task

D-S native cuts, W132-R6, the F1 fix and its cargo extension, F3, enabling admission, ADRs, the risk register, status
files and the ledger (Root).
