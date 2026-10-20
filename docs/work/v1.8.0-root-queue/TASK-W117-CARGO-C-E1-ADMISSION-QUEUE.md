# TASK W117: cargo leaf C-E1, admission and the shared resource queue

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md), [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md)
sections 3.1, 3.2 (items 1 to 8), 3.3 (the R40 amendment) and section 6's C-E row, and the records of the leaves this
builds on: [W100](records/W100.md) (C-K), [W109](records/W109.md) (C-A, the fluid cargo devices), [W112](records/W112.md)
(C-P part 2, the preparation tickets), `records/W114.md` (R40, merged before you start) and [W115](records/W115.md)
(R39 for fluids). You
are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w117`, branch `codex/v1.8.0-w117`, created by Root from the shared branch after W114's merge (the launch prompt names the SHA) |
| Record | `docs/work/v1.8.0-root-queue/records/W117.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W117/` |

**Root's split of C-E.** The design's C-E leaf is too large for one slice, so Root splits it in three, each with
production admission disabled: **C-E1** (this task) admits a cargo operation up to an acknowledged PREPARED record;
**C-E2** adds ordinary and restart reconciliation (the device endpoint adapter and permit, the driver for composite
bindings in both roles, typed readback, R39's checks at C2 and readback); **C-E3** adds operator settlement O1 to O4,
normalization, pruning and the end-to-end flight and disassembly cases. After C-E1, reconciliation still answers
`RECONCILIATION_UNIMPLEMENTED` for cargo, and an admitted cargo operation stays PREPARED with both endpoints locked.

This writes ledger records (first-ledger creation and L1), so it follows the persistence-slice rule: commit a one-page
plan in the record before the code; an independent read-only review follows before merge.

## What to do, each in its own commit with a test that fails before the change where a test can show it

1. **Make room in `EscrowCoordinator`** (647 lines). Move `prepareTransfer`, `prepareCargo`, `fillRefusal` and their
   helpers into a separate class (for example beside `EscrowTransferAdmission`) without changing behaviour; W11b3's
   `prepareFill` and its tests stay unchanged. New cargo code goes there, not into the coordinator.
2. **Map size and limit refusals exactly** (R40 and W113's item 4). Today `EscrowCargoEligibility.java:49-54` and
   `EscrowCargoPreparedCapture.java:139-145` turn every failure of `append`, `requireAdmission` and `preflight` into
   `CARGO_TOO_LARGE`, and `prepareCargo` (`EscrowCoordinator.java:208-209`) turns `CARGO_TOO_LARGE` into `ESCROW_FULL`.
   Give each its own outcome, for cargo: an `append` sequence or duplicate failure and a before-wrapper mismatch are
   STALE; the marker limit is `ROCKET_LIMIT`; a full root (`ReservationFull`) is `ESCROW_FULL`; the candidate's own
   component exceeding an operation, receipt, head or flight-table ceiling is `CARGO_TOO_LARGE`. `Transfer` gains
   `CARGO_TOO_LARGE`; the closed `Fill` contract does not change (a fill's own size refusal stays `ESCROW_FULL`).
   Distinguish the cases with typed exceptions or results; the inputs that are refused must not change. Map the new
   outcomes to the device's `RocketCargoStatus`.
3. **Per-kind cargo switches** (section 3.2 item 8). One switch per cargo kind, all off in production and separate from
   the fuel switch, checked before any capture, ticket or preparation; tests turn one on only through a gated test hook
   in the existing style. With a switch off, a device keeps answering `ADMISSION_DISABLED` with no side effect.
4. **Wire the C-A devices.** Behind the fluid switches, the fluid cargo loader and unloader go through
   `RocketCargoPreparations.begin`, the prepared native capture and `prepareCargo`, with their device projections and
   the unchanged-device predicate, on the owner thread. A refusal changes nothing; QUEUED shows the existing pending
   status. Item devices are D-E's.
5. **First-ledger creation for cargo**, in the same order and with the same creation baseline as B3-E's for fills
   (`EscrowLoaderAdmission.create`): the first admitted cargo operation in a world creates the ledger.
6. **One fair resource queue** (section 3.2 item 7). Replace `EscrowFillQueue` with one server-wide queue for the fuel
   loader and the fluid cargo devices: at most 64 commits per tick and at most one whole-snapshot reconstruction per
   tick on the existing session budget (16 MiB, 16,384 visits); fuel entries charge no reconstruction, and a
   maximum-size rocket is not starved by small requests. Keep today's fill pacing (8 visits, one capture per tick,
   256 slots) unless section 3.2 requires otherwise; if they conflict, stop and report.
7. **Turn tokens.** Register `EscrowTransferTurns` for loaders and cargo devices on load, unload and rebind, so a device
   refused with `OTHER_TRANSFER_PENDING` or `ROCKET_BUSY` receives the rocket's next turn. Tokens are RAM only, bounded
   by registered loaded work, discarded on unload or lifecycle end, and never override a hold or become durable.

## Tests

- A0: every refusal of item 2 for cargo, each with no sequence, reservation, queue entry, ticket or write; fills keep
  their existing results; the switch matrix; the queue's limits and its fairness with one maximum-size rocket among many
  small requests; token registration and discard.
- A1 (GameTests, switches on through the gated hook): a real fluid cargo load and unload reach an acknowledged PREPARED
  record with both endpoints locked and reconciliation answering `RECONCILIATION_UNIMPLEMENTED`; first-ledger creation
  by cargo; one live cargo operation per rocket (a second device, a fuel loader and an opposite direction are refused,
  then served in turn through tokens); refusals leave the world, the device, the rocket and the ledger unchanged.

No saved format, schema, hash, bound, dictionary, proof, lease, budget or timeout changes; no existing assertion is
relaxed; `EscrowCoordinator` must not grow and `RocketTransferService` (796 lines) must stay under 800; new classes stay
under 500 lines. A new WARN or ERROR that a GameTest drives needs its own exact manifest entry. Stop and report if any
of these seems needed.

## Verification

Each changed or new test class alone, then the whole unit suite. Then one full GameTest with the unchanged log checker
on committed source, under the heavy-JVM lock (other workers hold it in turn; wait), releasing the lock and deleting
`build/gametest/world` in the `finally` of the same detached script that took it (WORKER-RULES). Check C: and D: above
10 GiB first. Memory is tight: one Gradle process at a time, and check that 6 GiB is free before each.

## Not in this task

C-E2 and C-E3 (reconciliation, the device permit, readback, settlement, pruning), D-E (item devices), enabling
production admission, ADRs, the risk register, status files and the ledger (Root).
