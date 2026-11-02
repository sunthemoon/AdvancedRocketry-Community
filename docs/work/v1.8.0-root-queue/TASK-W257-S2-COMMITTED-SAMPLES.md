# TASK W257: the bay exit's continuity check on a ticking connection (W230-S2 fix)

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w257` (new; branch `codex/v1.8.0-w257` from W254's head `3417b983a`, which carries the W230-S3 native harness, merged with the shared head named in your prompt) |
| Finding | `D:/GitHub/ARCE-Task-Evidence/root-queue/W254/FINDING-S2-PLAYER-TICK.md`, with the native trial logs it cites (`trial-3/`, `final/`) |
| Design | [W230-DESIGN](records/W230-DESIGN.md) Revision 1 (rule W230-R2) and the S2 tasks [TASK-W242](TASK-W242-BAY-EXIT-S2.md), [TASK-W242-FIX](TASK-W242-FIX.md) and [TASK-W242-FIX2](TASK-W242-FIX2.md) (R254, R264, R274); S3 in [TASK-W254](TASK-W254-BAY-EXIT-S3.md) (R279) |
| Record | `docs/work/v1.8.0-root-queue/records/W257.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W257/` |

W254 showed that on every ticked connection (every real client) the fall offer closes two ticks after the exit:
vanilla's `ServerGamePacketListenerImpl.tick()` runs `player.doTick()`, whose server-side `travel()` moves the airborne
rider down by gravity before Forge's `PlayerTickEvent` END, and then `absMoveTo` restores the committed position. The
exit service's server-END sample sees that restore as an upward move against the PLAYER_END sample and revokes. The
W242 GameTests never tick their players' connections, so they missed it.

**Root decision R283**, under the owner's delegation: W254's option 2.

1. **Committed baseline.** Keep all three sample points and their hazard and contact probes, but compare continuity
   (no upward move, at most 1 block horizontal and 4 down per tick, no skipped tick, the 8-block radius) only against
   the last committed sample, the one taken at `PlayerTickEvent` START or at the server-tick END. A PLAYER_END sample is
   checked against that committed baseline too, so a real upward move or teleport inside the tick still revokes, but
   it never becomes the baseline. Every other R264 and R274 rule (the single early counter step, no rebaselining, fluid
   and unknown contact, the deadline, the budgets) is unchanged. State in the record how this reads against
   W230-R2's "at most one tick between required samples", and give Root an R-042 wording draft.
2. **Tests that tick the connection.** Give the bay-exit GameTest fixture a mode that ticks the rider's connection
   every server tick, as `ReleaseTestPlayers.pump` does, and run the descent, landing, expiry and correction cases in
   it; they must fail on the current code first. Keep every existing case.
3. **Native rerun.** Rerun W254's native cuts X2, X4, X5 and X7, and the packaged smoke, on the reobfuscated JAR of
   your commit; X4's extended landing must now cancel exactly one fall-damage hit.

No budget, deadline, radius, contact rule or admission setting changes. If option 2 cannot be made to work without one,
stop and report.

## Verification

- The new ticked-connection GameTests failing first, then passing; all bay-exit unit and GameTest batches; the whole
  unit suite.
- The native cuts and the packaged smoke under the heavy-JVM lock.
- The repository validator.

Commit on the branch; do not push. Root pushes for a sharded run, and a fresh session of the other engine reviews S2's
fix together with W254.

## Not in this task

W254's harness beyond what the rerun needs, production admission, the risk register itself, status files and the
ledger.
