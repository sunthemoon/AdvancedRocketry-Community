# TASK W95: W11b3 leaf B3-E, the fluid fuel loader's escrow adapter (admission behind a gated switch)

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w95-b3e` (from the shared branch, which now has W11b2, W81, W82 and W93) |
| Branch | `codex/v1.8.0-w95-b3e` (local commits only; Root merges) |
| Design (frozen) | [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md) (W75, frozen at `52a0a293`): section 2 (admission, reservations, one authority and production ledger creation), section 3 (device and binding contract), section 4 (exact refill and resources); section 5's B3-E row |
| Delivered so far | [records/W81.md](records/W81.md) (B3-M codecs), [records/W82.md](records/W82.md) (the device B3-A and B3-D, admission not wired), [records/W93.md](records/W93.md) (B3-U and `EscrowCoordinator.prepareFill`, package-private, not yet called) |
| Record | `docs/work/v1.8.0-root-queue/records/W95.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W95/` |

## What to do

1. **Recheck sections 2 to 4 against the shared branch first** (W75 read W11b2 at `d178e727`; W93 rechecked section 2
   at `e460bdc1`). Note moved sites in your record; stop and report if a difference changes the design.
2. **B3-E** as the row says: the loader's fair queue calling `prepareFill`; production ledger creation and the
   creation baseline; the loader endpoint adapter and its signature registration; the proof validators; production
   reconciliation in place of `RECONCILIATION_UNIMPLEMENTED`; and restart reconciliation. **Production admission stays
   disabled**: `productionAdmissionEnabled()` stays false, and only a gated switch (release-test hooks) exercises the
   path. Map `prepareFill`'s typed results to `FluidFuelLoaderStatus` members and player text; decide its final
   visibility.
3. **Tests (A1)** as the row says: two competing devices; mixed-bank prefixes; the first fill creating the ledger;
   every refusal comparing both resource envelopes and the queue and count state; actual production recovery, not
   chest or armor-stand fixtures. Also one native restart run through the gated switch that inspects the first tick
   after start, because W93 moved the existing ledger's bind to `ServerStartingEvent` and no native run has covered
   that yet.
4. Run each new class alone, the whole unit suite, and one full GameTest with the unchanged log checker (add an
   expectation only for a line your new test drives). If you add a GameTest batch, say so (about 378 batch names now;
   about 384 reshuffles the run order, which has broken order-dependent tests before).

This slice creates saved data, so Root sends the finished diff to an independent read-only review before merging it.

## Write scope and boundaries

The `rocket/fuel/loader` package, the `resourceescrow/` adapter, validator and reconciliation classes the row names,
the registry and language entries for new statuses, new tests, and the record. No change to W11b2's acknowledgement
orders, proofs, leases or saved formats beyond what sections 2 to 4 specify, no enabled production admission, and no
relaxed assertion, limit or timeout. Stop and report if one seems needed.

## Commands

As WORKER-RULES says. Other workers (W91C, W92, W94) also compile and run tests; take the heavy-JVM lock for every
GameTest and native run. When your heavy run ends, remove your own lock with a plain `rm -rf` of exactly
`D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm` after checking that its `owner.txt` names you; if the tool refuses,
write the path in your record and final answer at once, because other workers wait on it. Check that C: and D: have
more than 10 GiB free before each heavy start (C: has about 13 GiB).

## Not in this task

The S2 cut families B3-S1 to B3-S6, the benchmark B3-B, V1 and V2, W90a and W90b, the P1 leaves (W91, W92), the bay
(W27c1), enabling production admission, ADRs, the risk register, status files and the ledger (Root).
