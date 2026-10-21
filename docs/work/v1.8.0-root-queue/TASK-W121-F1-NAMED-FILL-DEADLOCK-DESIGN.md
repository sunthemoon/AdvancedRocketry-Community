# TASK W121: design note for W107C's F1, a named fill held forever at L1 (and F2's relocated endpoint)

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md), [records/W107C.md](records/W107C.md) (findings F1,
F2 and F3), [records/W11A-DESIGN.md](records/W11A-DESIGN.md) sections 4, 5 and 7 (the R1 row), and
[records/W11B3-DESIGN.md](records/W11B3-DESIGN.md) sections 2, 4 and 5, first. You are a new session. **This task is a
design note only: change no Java, script or test file.**

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w121` (new; from the shared branch at the commit named in your prompt, which has W107C merged) |
| Branch | `codex/v1.8.0-w121` (local commits only; Root merges) |
| Output | `docs/work/v1.8.0-root-queue/records/W121-DESIGN.md` (the note) and `records/W121.md` (a short record) |
| Evidence | W107C's `D:/GitHub/ARCE-Task-Evidence/root-queue/W107C/` (`w107c-native-02` and `smoke-native-02`); your own notes under `D:/GitHub/ARCE-Task-Evidence/root-queue/W121/` |

## The finding (W107C, merged `f2df941c`)

A loader fill on a rocket that has a landed, COMMITTED transfer journal record (a named, transfer-guarded fill) never
reaches C2. `RocketTransferFences.actionAllowed` treats any unresolved escrow record naming the logical rocket as a lock,
so transfer recovery fences the rocket's own landed record (`ARCE_TRANSFER_FENCED ... reason=ESCROW_PENDING_OR_TOMBSTONE`).
Loader reconciliation needs that record settled (`RocketTransferService.escrowReconciliationBoundary`), so
`EscrowReconciliationView.capture` defers forever. The record stays PREPARED with reason NONE, so the hold is invisible to
the reason table and not eligible for accept-current (W11A section 5 limits that to manual conflicts and stalls). Nothing
is lost, but this contradicts W11A section 7's R1 row ("outstanding work retires"). The NONE-guard fill is unaffected.
Production admission is disabled, so no world reaches this yet; it must be fixed before fuel admission opens.

## What the note must contain (one to two pages)

1. **The exact cycle**, with file and line for each side, and which states (journal phases, escrow phases and guards)
   reach it: only a landed COMMITTED record, or also others (PREPARED, ASCENT, a fail-back)? Does the same cycle exist
   for governed flights, cargo (C-E2 will reuse the reconciliation) or the bay?
2. **Options**, at least: (a) the fence lets the record that guards the escrow operation (its own `guard.transfer`) be
   settled or retired, while still fencing every other action; (b) loader reconciliation accepts the boundary when the
   only fence is its own guard; (c) the record is settled before the fill is admitted (admission refuses a named fill on
   an unsettled record); (d) a persisted reason, so accept-current or offline repair can release the hold. For each, say
   what it changes in the recovery order, the saved format (none is the strong preference), the crash windows (K0 to K10,
   both restarts) and which existing tests and native cuts it would touch.
3. **A recommendation** with its invariants: no resource can be spent twice, no fence is weakened for any action other
   than the guarded settlement, and every hold either resolves automatically or persists a reason an operator can see.
4. **F2:** whether ordinary reconciliation can detect a relocated loader within the chunk and report
   `RELOCATED_ENDPOINT` as W11A section 4 names it, at what cost; or whether `MISSING_ENDPOINT` should stand, with the
   reason. **F3** stays as recorded unless your analysis shows it interacts with F1.
5. **Tests and cuts** that would prove the fix: unit tests of the cycle, the two named R1 native cuts W107C left failing
   (`LF_M_R1_255_DISTINCT`, `LF_M_R1_255_COALESCED`), and any others the change reaches.

Verify claims against the code at the commit you start from; quote lines. You may run existing unit tests read-only to
confirm a behaviour; run no GameTest, native server or `runData`, and take no heavy-JVM lock. Check that 6 GiB of memory
is free before any Gradle run.

## After the note

Root has the note reviewed by an independent read-only session, decides, and then queues the implementation as its own
persistence slice. Not in this task: any code change, B3-S6, production admission, ADRs, the risk register, status files
and the ledger (Root).
