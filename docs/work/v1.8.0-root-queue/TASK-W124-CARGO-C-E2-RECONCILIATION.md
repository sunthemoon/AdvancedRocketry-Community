# TASK W124: cargo leaf C-E2, ordinary and restart reconciliation for fluid cargo

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md), [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md)
sections 3.2 (items 4 to 6), 3.3 ("Normal settlement") and 4 (the stop-point table), section 6's C-E row and the
"Ordinary prefix/apply" and "Neutral survivor" rows of its cut mapping, and the records of the leaves this builds on:
[W117](records/W117.md) (C-E1, with W117F), [W112](records/W112.md) (C-P part 2: the publication permit),
[W109](records/W109.md) (C-A, the fluid devices), [W115](records/W115.md) (R39 for fluids) and
[W99](records/W99.md) (C-I, the identity rule). You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w124` (new; from the shared branch at the commit named in your prompt, which has W117 merged) |
| Branch | `codex/v1.8.0-w124` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W124.md` (new; the plan at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W124/` |

**Root's split of C-E** (TASK-W117): C-E1 (merged) admits a cargo operation up to an acknowledged PREPARED record; **C-E2
(this task)** reconciles it, in the ordinary path and after a restart; C-E3 adds operator settlement O1 to O4,
normalization, pruning by settlement and the end-to-end flight and disassembly cases. Production admission stays
disabled. Today the reconciliation driver answers `RECONCILIATION_UNIMPLEMENTED` for cargo
(`EscrowReconciliationDriver.java:50-57`, `:68-70`), and `EscrowReconciliationView.java:68`,
`EscrowReconciliationValidation.java:31` and `EscrowReconciliationCheckpoint.java:47-56` accept only the fill's
DESTINATION role and `FUEL_VECTOR_V1`.

This is a persistence slice: commit a one-page plan at the top of the record before any code; an independent read-only
review follows before merge.

## What to do, each in its own commit with a test that fails before the change where a test can show it

1. **A cargo device endpoint adapter**, the counterpart of `EscrowLoaderEndpoint` for the fluid cargo devices: observe
   the device's root, compute its change for C2, prune its marker at C8, and decode its record from disk for readback.
2. **The device's permit-gated publication**: a one-use, server-thread permit that publishes the device tank's delta and
   its marker together, as `FluidFuelLoaderBlockEntity.applyEscrowDebit` does for fills; never a public setter. The
   rocket side already exists (`RocketEntity`'s permit-gated cargo publication, W112).
3. **The driver and validation for composite bindings in both roles**: a load has the device as source and the rocket as
   destination, an unload the reverse; classification compares the composite signature (content hash of the whole
   snapshot), so an AFTER rocket is never classified BEFORE. Roll a BEFORE side forward once by recomputing the after
   snapshot through the preparation's replay (`beginReplay`) and comparing it with the frozen digest; a mixed state rolls
   the BEFORE side forward; AFTER on both sides goes to the barrier; L2 retires the records and C8 drops stale markers
   (section 4's table).
4. **Typed readback**: decode the rocket record with the bounded `AnvilRecordReader`, recompute the content hash and the
   composite signature off-thread on the immutable decode, and compare digests on the server thread; the device record
   through the adapter.
5. **R39 at C2 and readback**: call `op.nativeFluid()` (or the native `EscrowCargoPayload.decode`) before
   `RocketCargoMutation.apply` and before changing the device tank; a refusal holds that operation only as `REPAIR` with
   the existing `OTHER_STATE` reason, as the fill's C2 permit does.
6. **The deferred C-E1 handoff**: once an operation retires, the competing device that W117's tokens queued is served in
   turn; show it natively.

## F1 (W107C, design in W121, under review)

A named (transfer-guarded) fill is held forever by its own transfer fence; W121's note says cargo will reach the same
cycle once C-E2 reconciles it (decision D4). Do not implement your own fix for it. Test cargo with the NONE guard; for a
named transfer, add a test that pins today's hold (it stays PREPARED, nothing is lost) and mark it as waiting for the F1
fix, which Root will extend to cargo.

## Tests (A1, section 6's C-E row as it applies to C-E2)

Real load and unload through D2-C0 to D2-C8 (the fill path's sequence), each with a native restart where the stop-point
table names one; a mixed state and AFTER on both sides after restart; C-I's real stale-PREPARED and both-entity cases and
a reconnect; the competing device's turn after retirement; an R39 hold leaves other components running. A0 for the
adapter, the permit and the classification. The existing fill reconciliation tests stay unchanged and pass.

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

C-E3 (O1 to O4, normalization, settlement pruning, flights and disassembly), F1's fix, item cargo (D-E), enabling
admission, ADRs, the risk register, status files and the ledger (Root).
