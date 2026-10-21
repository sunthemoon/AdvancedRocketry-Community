# TASK W132: cargo leaf D-E1, item cargo admission and reconciliation

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md), [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md)
sections 3.2, 3.3, 4, 5.1 and 5.3 and section 6's D-E row, and the records of the fluid leaves this mirrors and the item
leaves it builds on: [W117](records/W117.md) (C-E1, admission and the queue), [W124](records/W124.md) (C-E2,
reconciliation), [W110](records/W110.md) (D-K), [W118](records/W118.md) (D-P, with W118F) and [W120](records/W120.md)
(D-A, the item devices, with W120F). You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w132` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w132` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W132.md` (new; the plan at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W132/` |

**Root's split of D-E**, as for C-E: **D-E1 (this task)** wires item devices through admission and reconciliation (the
item counterparts of C-E1 and C-E2); D-E2 adds item settlement O1 to O4 and the end-to-end cases after C-E3. Production
admission stays disabled.

This is a persistence slice: commit a one-page plan at the top of the record before any code; an independent read-only
review follows before merge.

## What to do, each in its own commit with a test that fails before the change where a test can show it

1. **Admission for items** (C-E1's counterpart): per-kind item switches (already declared by C-E1; check), the item
   devices wired through the preparation ticket, the native item capture and `prepareCargo` on the owner thread, the
   item kinds in the shared resource queue and the turn tokens (with W117F's yield of an unused token).
2. **W118's carried items:** R2, the one-shot `EscrowItemCargoEligibility` adopts C-E1's typed refusals
   (`EscrowCargoRefusals.capacity` and its typed catches) so one-shot and staged item capture agree, and
   `UNRESOLVED_LOOT` and `NO_CARGO_STORAGE` map to their own device statuses, never retried as another candidate; R7, the
   item device statuses; a per-tick budget for item selection (it now calls the eligibility check more often); catching
   `RocketCargoWorkBudget.Exceeded` at admission with the other refusals.
3. **Reconciliation for items** (C-E2's counterpart): the item device endpoint adapter and one-use permit (the four-slot
   buffer delta and marker together), classification with the item buffer signature on the device and the composite on
   the rocket, the replay verified before either endpoint publishes (W124F's rule), typed item-NBT readback, R39's native
   stack check before either side acts, holding only that operation.

Named (transfer-guarded) item cargo stays held until the F1 fix is extended to cargo (R48's D4); pin it where a test
meets it.

## Tests

A1: a real item load and unload through D2-C0 to D2-C8 with native restarts where section 4's table names one, partial
moves and restart, competing devices and their turns, refusal side effects, typed item-NBT readback and an R39 hold;
deterministic source-only and destination-only native cases in both directions (W124F's R2). A0 for the typed refusals,
the statuses, the selection budget, the adapter, the permit and classification. The fluid tests stay unchanged and pass.

No saved format, schema, hash, bound, dictionary, proof, lease, budget or timeout changes; no existing assertion is
relaxed; `EscrowCoordinator` must not grow, new classes stay under 500 lines and `RocketTransferService` (796 lines) must
not grow. A new WARN or ERROR that a GameTest drives needs its own exact manifest entry. Stop and report if any of these
seems needed.

## Verification

Each new or changed test class alone, then the whole unit suite; one full GameTest with the unchanged log checker on
committed source; then the native cases, all under the heavy-JVM lock, releasing it and deleting every server copy and
`build/gametest/world` in the `finally` of the same detached script that took it (WORKER-RULES). If the checker refuses
only the host-overload warning count, record the count and the number of agent sessions running. Check C: and D: above
10 GiB and that 6 GiB of memory is free before each Gradle run or native start.

## Not in this task

D-E2 (item settlement and the end-to-end cases), the F1 fix and its cargo extension, enabling admission, ADRs, the risk
register, status files and the ledger (Root).
