# TASK W262: the bay exit's protection for a real client's landing (W257 review findings)

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w257` (existing; branch `codex/v1.8.0-w257` at `8f02e257e`: W254's harness, W257's committed-sample fix `4bd2990c1`, W257's drive change `ddf008058`, and Root's merges of the shared head) |
| Findings | `D:/GitHub/ARCE-Task-Evidence/root-queue/W257-review/REVIEW.md`, SHA-256 `8c0f201cd853021e0e61ec029abe7db75b2b6701e26a0515add39e4d3ee43083`: W257-R1 to R6 |
| Design and tasks | [W230-DESIGN](records/W230-DESIGN.md) Revision 1; [TASK-W257](TASK-W257-S2-COMMITTED-SAMPLES.md) (R283), [TASK-W254](TASK-W254-BAY-EXIT-S3.md) (R279), [TASK-W242-FIX2](TASK-W242-FIX2.md) (R274) |
| Record | `docs/work/v1.8.0-root-queue/records/W262.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W262/` |

The review found that a real client's landing after a descent is still not protected. A real client sends one move
packet per tick, so in the server tick between its last airborne packet and its landing packet, vanilla's temporary
`travel()` step reaches the floor at `PlayerTickEvent` END; the check-only sample's contact probe then sees ORDINARY
contact and closes the offer, and the committed server-END sample also carries that step's `onGround` (W257-R1). W257
made the gated stand-in send the last airborne and the landing packet in the same tick, which no real client does; that
is what made X4 and the packet-landing GameTest pass. Check-only samples also add vanilla's temporary step to the
tick's committed movement, so a fall faster than about two blocks per tick closes the offer (W257-R2).

**Root decision R288**, under the owner's delegation:

1. **Real-client timing first.** Restore the release drive's real timing (the last airborne packet less than one
   server fall step above the floor, then one full server tick, then the landing packet); keep the same-tick pair only
   as an explicitly named artificial variant if it is still useful. Give native X4 and the ticked packet-landing
   GameTest that timing, and add unit and ticked GameTest cases falling at 2 to 3.9 blocks per tick. All must fail on
   `8f02e257e` for the named causes before any rule changes.
2. **W257-R1, option (a).** At a check-only PLAYER_END sample, ORDINARY floor contact and the `onGround` flag produced
   by vanilla's temporary step do not close the offer; fluid, UNSAFE and UNKNOWN contact still close it, as before.
   `fall()` takes `airborne` from the last committed START sample or from the landing packet's pre-move state, not
   from a server-END sample contaminated by the temporary step. Nothing else about the landing proof changes: the
   recognized vanilla landing frames, the single cancellation, the deadline, the radius and the budgets.
3. **W257-R2, the first option.** A check-only sample is checked on its own displacement from the committed baseline
   against the per-tick limits, without adding the tick's committed usage; it still cannot spend or replenish that
   usage. A packet handled after the server-tick END counts toward the next tick.
4. **W257-R3.** The cut harness runs `save-all flush` after the phase and before the kill for X2, X4, X5 and X8, so the
   restarts check the exited rider's saved position and mount, `histories=0` and both seats against a save that
   contains the exit; add chunk and ticket counts across X2's descent and X5's wait.
5. **W257-R4 to R6.** One R-042 and R-043 draft for Root that states the committed baseline, what the temporary step
   can and cannot revoke, and the residuals, superseding W254's and W257's drafts; the V2 checklist's prerequisites and
   steps 3.2, 3.4 and 3.5 as the review suggests; the release drive's listeners and drives cleaned up after their
   batch (R6).
6. Correct W257's record where it called the old timing "manufactured", by appending a dated correction.

If option (a) cannot make a real-client landing pass without a further change to what counts as an ordinary landing,
stop and report.

## Verification

- The new real-client-timed tests failing on `8f02e257e`, then passing; every bay-exit unit and GameTest batch; the
  whole unit suite.
- The native cut matrix (with the post-exit flush) and the packaged smoke on the reobfuscated JAR of your commit, under
  the heavy-JVM lock; one full GameTest run with the log checker.
- The harness's Python tests and the repository validator.

Commit on the branch; do not push. Root pushes for a sharded run, and a fresh session of the other engine reviews.

## Not in this task

Production admission, budgets, deadlines, the radius, the risk register itself, status files and the ledger.
