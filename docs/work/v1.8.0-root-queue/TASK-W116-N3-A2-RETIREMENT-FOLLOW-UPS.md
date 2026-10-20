# TASK W116: P1-N3 leaf N3-A2, the ordinary retirement follow-ups (W98-R3, R6, R10, R12)

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md), [records/W96-DESIGN.md](records/W96-DESIGN.md) (the
P1-N3 design with Root decision R34), [records/W98.md](records/W98.md) (N3-A1 and W98F), R-035 in
`docs/11-RISK-REGISTER.md` (the P1-N3 and W98 sentences), and the W98 review
`D:/GitHub/ARCE-Task-Evidence/root-queue/W98-review/REVIEW.md` (SHA-256 `493187a7…042b`), findings W98-R3, R6, R10 and
R12, first. Root decision R35 (in [TASK-W98F](TASK-W98F-REVIEW-FOLLOW-UPS.md)) assigned these four to this leaf. You are
a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w116`, branch `codex/v1.8.0-w116`, created by Root from the shared branch (the launch prompt names the SHA) |
| Record | `docs/work/v1.8.0-root-queue/records/W116.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W116/` |

This changes how an ordinary return and its recovery wait or proceed, so it follows the persistence-slice rule: commit
a one-page plan in the record before the code; an independent read-only review follows before merge.

## What to do, each in its own commit with a test that fails before the fix where a test can show it

1. **W98-R3, a discarded copy's column.** For REMOVE_DESTINATION_KEEP_SOURCE, the census includes the discarded copy's
   slot in the destination Level, but only recovery's one 400-tick flight ticket keeps that column loaded. At each
   attempt start, refresh the existing ordinary flight ticket on every census column (W90 permits one column per
   discarded end; add no governed or margin ticket). Add a GameTest in which the discarded column would otherwise have
   unloaded after two failed attempts, and show that the return completes.
2. **W98-R6, an offline crew after a cancelled countdown.** Recovery passes `offlineAllowed = false`
   (`RocketTransferRecoveryService.java:447`), so a countdown cancellation killed before its proof holds the return
   indefinitely while a seat is offline. Pass `offlineAllowed = true` exactly when the PREPARED record's recovered
   source never left its pad (its loaded image is COUNTDOWN, or stationary at the origin), as R34's R3 reasons; every
   other case keeps `false`. Test both sides of that rule.
3. **W98-R10, responsibilities and the record.**
   - Move the static guards (`admits`, `crewReady`, `projection`, `destinationAdmitted`) out of
     `RocketOrdinaryRetirement` (579 lines) into `RocketRetirementProof` or a small guard class; give
     `RocketTransferEscrowRules` one token-aware overload that both callers use; move the pause sink into a gated
     test-hook class. Behavior does not change; `RocketTransferService` (796 lines) must not grow past 800.
   - A mixed barrier batch's ordinary store or force failure must not fail the governed attempts or count in the
     governed `ioFailures` (`EscrowBarrier.java:158-160`, `:172-174`, `:226`), or the record must say exactly when it
     does. Prefer the fix; if it needs a barrier design change, stop and report it with the options.
   - Correct the record's claims (one responsibility; ordinary attempts add no failure counts).
4. **W98-R12, coverage.**
   - Unit tests of the coordinator's state machine: each failure path (DEADLINE, IO, SUPERSEDING_SAVE, BUSY,
     SLOT_UNLOADED, SAVE_OFF) releases the journal reservation, and `clean()` is held no longer than one attempt.
   - GameTests: the R5(c) fallback; contention with a governed proof on a different slot (the shared-slot case,
     W98-R1, is a named R-035 residual until governed admission opens: record what you observe, do not fix it here);
     a superseding vanilla save during a proof; `SURVIVOR_GONE` followed by a rebuild.

No saved format, journal schema, hash, bound, proof rule, lease, budget or timeout changes; no existing assertion is
relaxed; a new WARN or ERROR that a GameTest drives needs its own exact manifest entry. Stop and report if any of these
seems needed.

## Verification

Each changed or new test class alone, then the whole unit suite. Then one full GameTest with the unchanged log checker
on committed source, under the heavy-JVM lock (other workers hold it in turn; wait), releasing the lock and deleting
`build/gametest/world` in the `finally` of the same detached script that took it (WORKER-RULES). If W98's native cuts
(`LF_*` or the W91K cuts) reach a changed path, rerun the affected ones once, two restarts each, under the lock. Check
C: and D: above 10 GiB first. Memory is tight: one Gradle process at a time, and check that 6 GiB is free before each.

## Not in this task

N3-B (I6 and I7, which need a Design M amendment), W98-R1's fix, governed admission, escrow and cargo code beyond the
barrier's failure accounting, ADRs, the risk register, status files and the ledger (Root).
