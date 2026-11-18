# TASK W384: X02 native preflight before slice S5

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w384`, branch `codex/v1.8.0-w384`, at the shared head (slice S4 merged as `da9b21dc3`) |
| Decision | Root decision R499 (QUEUE row W384) |
| Design | [W368-DESIGN](records/W368-DESIGN.md), sections 4 to 7; Root's rulings in QUEUE rows W379F (R489), W379G (R491) and W379G's re-review (R496) |
| Prior work | [W379's record](records/W379.md), with the W379F and W379G sections; W379R2's note (QUEUE row W379G) |
| Record | `docs/work/v1.8.0-root-queue/records/W384.md` |
| Write scope | The record, and `scripts/run_v180_storage_faults.py` with its tests only to fix a defect the preflight proves. Each such fix gets a test that fails without it. No production code, no existing runner and no oracle. |

## Why

The X02 runner's planning phase and its restructured offline path have only run with stand-ins (W379G, W379R2). The
bay `MOVE_AFTER` case on `B3_MOVED` has never completed its two restarts. The journal and registry `MOVE_AFTER` cases
have never run. Slice S5 runs the 72-case campaign on the candidate; this preflight finds what breaks before that.

## What to do

On a locally built JAR of the shared head, under the heavy-JVM lock, run at most eight cases through the campaign
runner. This is development evidence, not slice S5.

1. **One offline group** that includes the registry `STALE` case. The planning phase must:
   - record the group's cut once;
   - declare that case `NOT_APPLICABLE` with its evidence binding;
   - write the declaration into the manifest before any case runs.

   Run the group's other cases.
2. **The bay `MOVE_AFTER` case on `B3_MOVED`**, with both restarts.
   - If the first start is refused only for an unrelated network ERROR (the Yggdrasil key fetch), rerun it once, as
     R489 (b) says.
3. **`MOVE_AFTER` on the transfer journal and on the satellite registry** (with the planned skip), with both
   restarts.
4. **Report each case's class and its evidence**, and the campaign status.
   - Check the manifest's phase order and its declarations.
   - Check the lock holds and the 90-second gaps.
   - Check the package size against its bound.

If the runner misbehaves (for example a wrong class, a missed declaration, a crash or a leaked lock), fix it within the
write scope with a test, rerun the affected case, and record both runs. If a case shows a production storage defect, do
not fix production: record the evidence and stop that case.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Never relax an oracle.
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Use the heavy-JVM lock with the 90-second gap, keep at least
  10 GiB free on C: and D:, and delete server copies after each run. Launch detached Python with the real
  `python.exe`, not the pyenv shim. Keep evidence under `D:/GitHub/ARCE-Task-Evidence/root-queue/W384/`.
- Commit on the branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- The last line of the record, and the last line of your answer, is the path of `records/W384.md`.
