# TASK W11b2, continuation 05: the bay recovery hook, then finish the integration

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) and
[TASK-W11B2](TASK-W11B2-ROCKET-INTEGRATION.md) first. You are a new session. Your context is the
W11b2 record, [records/W11B2.md](records/W11B2.md) (at `211a21f9`). Read it in full.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (continue on top of `211a21f9`) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Record | Add a "Continuation 05" section to `records/W11B2.md`. Keep the existing text |

## 1. Merge the shared branch, then add the bay recovery hook (Root's decision)

**Merge first.** Merge `codex/v1.8.0-classic-content` (`882204fe` or later) into this branch. It
brings the merged satellite bay design, `records/W27A-DESIGN.md` (rounds 1 to 3). Resolve any
conflicts under the W28 rule (no nested GameTest scheduling), keeping every assertion.

**Add the hook.** Then implement exactly the hook contract in that design's **section 8.1**:
- the internal `RocketTransferBayRecoveryGate` interface in `rocket/server`, with `claim` and
  `beforeAction`;
- a default implementation that answers UNCLAIMED, so behaviour without a bay is unchanged;
- the fourth in-session set, `claimedTransfers`;
- classification before any ticket, at every entry section 8.1 lists;
- the `step`, `reevaluateOne`, `permit` and `stationRegionInMotion` changes;
- the boundary wiring for `recoverNext`, `recoverById`, `recover`, `tryReconnect`,
  `settleRecoveredSource`, `finishSourceSettlementIfComplete`, `classify` and `actionAllowed`,
  entity join and leave, `tickLive` and `spawnDestination`, and journal remove, rebind and phase
  rechecks.

Keep `RocketTransferService` under its class-size ceiling by extracting the dispatch. Add the nine
section 8.1 tests against a stub gate. The real bay is W27d, not this task.

Also implement the section 2 **shared admission contract**: one admission authority, counting
unique operations across the durable, queued, captured and unknown-outcome states, with REPAIR
and released records counted. Implement it only to the extent the escrow core needs now, and
leave the bay consumers to W27c1. If this does not fit cleanly, record exactly what W11b3 and
W27c1 must add.

## 2. Then finish the record's open items

- **Credited-rocket reconciliation.** Close "credited-world recovery remains unsafe", or state
  exactly why it stays unsafe.
- **Durable operator settlement and tombstones.** Run forced-stop cuts before and after each
  acknowledgement.
- **The full native matrix**, each case with two restarts, pinned to the source and jar:
  - R1 to R3;
  - O1 to O4 and the O-veto;
  - G1 and G2;
  - the failed committers;
  - collection;
  - the repeated integrated K and C cuts.

**The joint-proof GameTest flake** (`jointTerrainSlotProofRetainsTheOtherEndpointLease`,
`SUPERSEDING_SAVE`) is being diagnosed separately as W36. Do not change that test here. Report
any occurrence.

## Commands

- The whole unit suite once.
- `runData` under the lock.
- `runGameTestServer` under the lock, then the log checker.
- The native cuts, with evidence under 100 MB.

## Not in this task

- W11b3 and the bay slices W27b to W27e.
- Production admission.
- ADRs and the risk register (Root).
- Status files and the ledger.
