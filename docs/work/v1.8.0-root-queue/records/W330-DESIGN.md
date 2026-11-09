# W330 cargo harness cut list

Authority: [TASK W330](../TASK-W330-CARGO-C2-HARNESS.md), R394/R395. This is a
test-harness implementation plan, not candidate evidence or admission approval.
The baseline is [W11CD-DESIGN section 6](W11CD-DESIGN.md#6-verification-and-implementation-leaves)
and the explicit gaps in [W326-MATRIX](W326-MATRIX.md). Each native case has one
forced stop and two restarts. Every restart is checked independently from native
ledger, terrain and entity bytes as well as the gated probe's observations.

## Write scope and exclusions

W330 owns cargo test probes, cargo GameTests and their test-support additions,
cargo Python runners/oracles and their unit tests, the integrated wrapper and its
tests, and these two W330 records. Existing loader runners are reused without
changing their fixtures, oracles or timeout defaults. Production admission,
production algorithms, saved formats, build scripts, generated resources, bay
runners, status, risk register, ledger and QUEUE are excluded. No upstream assets
or code are imported. No other agent is delegated work.

## Owner pending cap: family C-Q

Section 6 has no separate native cap row. Its capacity/refusal and unchanged
infrastructure mappings are the closest analogue; W238OwnerPendingTest supplies
the accepted shared 64/server, 8/rocket, 16/owner semantics.

| Cases | Stop boundary | Restart 1 oracle | Restart 2 oracle |
|---|---|---|---|
| `CQ_FLUID_LOAD`, `CQ_FLUID_UNLOAD`, `CQ_ITEM_LOAD`, `CQ_ITEM_UNLOAD` | Sixteen durable pending operations charge owner A across other rocket identities. A real device for the selected kind attempts admission and is refused before preparation/publication. | A remains refused with identical device, rocket and ledger state. An independent owner B is admitted through the ordinary cargo executor, transfers once and retires. | A is still refused without mutation. B's already completed operation is not repeated; its resource totals, epoch and pruned state are unchanged. |

The seed is gated test data, not a separate quota or production bypass. Both
owners' devices and rockets are real. Refusal is checked against complete encoded
ledger/device/rocket state, not only a status string. The seed operations remain
pending across both restarts; neither the per-rocket nor server cap is reached.

## Hosted A1 gaps

These are section 6 C-E rows, not new native families. Every test uses the existing
isolated cargo GameTest world and real cargo admission, with production switches
still disabled. A1 does not pretend to provide restart evidence; C-S5 already
provides the corresponding two-restart recovery cuts.

| W326 row | Case / boundary | Oracle |
|---|---|---|
| C-E-A09 | Fluid `ABANDON_DEVICE`, O1 through O4 and pruning, twin of ItemCargoSettlementGameTests. | Exact device absence, surviving rocket identity/fuel/invariant, one UNKNOWN receipt, READY head and no invented cargo; returning abandoned identity remains locked. |
| C-E-A12 | Real unload followed by stale PREPARED and both entities; protected non-first keeper and changed snapshot identity variants. | Latest cargo stays on its physical authority; recovery neither overwrites it nor discards an unchecked protected keeper. Snapshot identity is mandatory even when geometry matches. |
| C-E-A13 | A disembarked passenger reconnects after real unload under a stale transfer record. | Reconnect cannot restore historical cargo, relocate the passenger through the stale authority, or bypass the recovery fence. |
| C-E-A14 | Exact-source cancellation after real load and governed PREPARED. | Cancellation retains current cargo/source identity, removes only exact governed authority, and does not reconstruct the historical snapshot. |

## Load settlement: families C-S4-LOAD and D-S2-LOAD

Section 6 enumerates unload S2 only and names the load counterpart in A1. R395
adds native load counterparts using the same frozen settlement representation and
production proof order. Fluid starts with rocket 0/device 5,000 mB and transfers
1,000; item starts with rocket 60/device 10 typed items and transfers 4, retaining
the exact 6-item remainder. Their totals are 5,000 mB and 70 typed items.

| Case suffix (prefix `FS_LOAD_` or `IS_LOAD_`) | Stop boundary | Oracle after each restart |
|---|---|---|
| `O1_BEFORE` (fluid only), `O1_AFTER` | Before intent replacement / after replacement before callback. | Before O1 stays REPAIR with no invented choice; after O1 completes the frozen choice, not a new live observation. |
| `BIND_BEFORE` (fluid only), `BIND_AFTER`, `BIND_VETO` | Before/after neutral rocket wrapper publication; moved survivor veto. | Only the prescribed wrapper may bind. Changed placement holds without proofs/receipts until exact restoration, then release is idempotent. |
| `O2_ROCKET_BEFORE`, `O2_ROCKET_AFTER`, `O2_DEVICE_BEFORE`, `O2_DEVICE_AFTER` | Either side of each native survivor proof, rocket now DESTINATION/device SOURCE. | Both exact proofs are reacquired; typed cargo, fuel projection, invariant, identity and marker roles remain mandatory. |
| `O3_BEFORE` (fluid only), `O3_AFTER` | Before/after immutable unreleased receipt replacement. | O3-after recovers solely from durable intent/receipts, reacquires O2/O3J, releases/prunes and performs a governed launch with latest cargo. |
| `O3J_BEFORE` (fluid only), `O3J_AFTER` | Before/after checked removal of a named landed journal record. | Journal lease/removal is re-established before O4; no legacy recovery fallback. |
| `O4_BEFORE` (fluid only), `O4_AFTER` | Before/after released receipt and READY replacement. | One released UNKNOWN receipt, direction-independent READY head, no live record or extra authority. |
| `PRUNE_BEFORE` (fluid only), `PRUNE_AFTER` | Before/after wrapper storage of the retired prefix. | Exact released group prunes idempotently; resources and epoch are preserved. |
| `ABANDON_ROCKET`, `ABANDON_DEVICE` | Acknowledged O1 with one absent endpoint. | Only exact survivor and absence are proved. Missing rocket yields no READY head; surviving rocket yields its frozen READY head. No invented quantity/loss accounting. |
| `VETO_DESTINATION_SAVED` (fluid only) | Rocket destination stored, device source O2 store vetoed. | The raw stores contain 1,000 + 5,000 mB, the same surplus as unload with endpoint roles reversed. Both resources remain locked; frozen mismatch never releases. |

All 20 C-S4 boundaries apply to a fluid load (the veto's endpoint roles reverse).
All 13 D-S2 settlement boundaries apply to an item load. D-S2's changed-container,
LootTable, disassembly, rebuild and protected-root cases are not settlement cuts;
they remain in D-S2 and are not relabelled. Item before-write cuts and an item
store-veto are not in D-S2's enumerated set, so they are not silently added as
claims. The existing normalization veto is retained.

## Shared infrastructure and candidate wrapper

| Wrapper family | Existing runner selection | Boundary and both-restart oracle |
|---|---|---|
| B3-S1-REUSE | Loader N0, N2 definite/UNKNOWN, N3 full/not-loaded/preflight/eligibility. | Existing creation refusal/baseline cuts; unchanged no-side-effect and ambiguity checks. N1 remains cargo's C-S5 case. |
| B3-S4-REUSE | Complete existing `oracle.S4` selection. | Existing storage/reconciliation cuts and their exact failure/UNKNOWN/proof checks. |
| B3-S5-REUSE | Missing/replaced/behind ledger plus all distinct/coalesced/saturated floor variants. | Existing native orphan/floor reservation, refusal and retirement checks. |
| B3-S6-REUSE | Complete existing loader S6 selection, including P1-N2/P1-F9 coverage. | Existing checkpoint-specific proof, recovery, shutdown, settlement and flight oracles, without timeout or fixture changes. |
| G2-REUSE | The existing `run_v180_fuel_loader_s1.py` S1 campaign. | G2 is not exposed by the two cut runners. Delegate the existing complete S1 campaign, requiring its historical JAR/source and persistent start counter, without changing its migration, downgrade, protected-root or two-restart oracles. |

The wrapper requires caller-supplied candidate commit and JAR SHA-256 for native
execution, pins both in every invocation, and requires production equality against
that commit. Inventory remains available without candidate arguments. New native
families are selectable independently, and a case filter supports local fixture
smokes without claiming a full candidate run. Loader runner lock ownership remains
with those runners; the wrapper must not nest the outer cargo lock around them.

## Verification

Run Python cargo/item/integrated and loader runner tests, new Java tests alone,
then the full unit/build suite. Under the atomic backslash heavy-JVM lock run new
GameTests alone, the full GameTests and the unchanged log checker. Run one local
JAR smoke per new native family with both restarts and a scratch negative-control
oracle check, preserving logs and native observations outside Git. Check disks
before heavy work, clean only owned copies, run strict repository validation and
commit the short W330 result record. No local smoke is candidate evidence and no
Required Gate or version is marked passed by this worker.
