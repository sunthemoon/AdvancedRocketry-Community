# TASK W380J: X03-B, the review fixes, the two refusal seeds and the final accepted sequence

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w380`, branch `codex/v1.8.0-w380`, at W380I's record commit `e0075fe58` |
| Decision | Root decision R501 (QUEUE row W380J) |
| Review | W380R's review: `D:/GitHub/ARCE-Task-Evidence/root-queue/W380-review/REVIEW.md` (outside Git). Read all of it. |
| Design | [W367-DESIGN](records/W367-DESIGN.md), revision 3, sections 3.5, 3.6, 5.3 and 9.1 to 9.5. This branch has revision 2, so read revision 3 from `D:/GitHub/ARCE-Task-Evidence/root-queue/accounting-tasks/W367-DESIGN-r3.md`. |
| Prior work | `records/W380.md` on this branch (the W380 to W380I sections). W380I's evidence is in `D:/GitHub/ARCE-Task-Evidence/root-queue/W380I/`. |
| Record | Add a section "W380J follow-up (R501)" to `records/W380.md`. |
| Write scope | `scripts/run_v180_x03_repair.py`, `scripts/x03_repair_oracle.py`, `scripts/repair_v180_rebuild_source.py` (finding 5 only), their tests, and the record. No production code, no Java, no other runner. |

## What to do

Root's rulings on W380R's findings (R501):

1. **Finding 1: add the two native refusal seeds of section 5.3.** Section 5.3 stands; it is not amended.
   - Seed FF_STALE_PREPARED and FF_RECONNECT natively with `seed_world`, as FF_KEEP_SOURCE is seeded.
   - Call `refusal(...)` for each: the reasons must include `SECOND_PHYSICAL_ROCKET` for FF_STALE_PREPARED and
     `PASSENGER_ASSIGNMENT` for FF_RECONNECT. Other reasons may appear beside them, as the table says; record them.
   - Check that each seeded world has the expected shape before the plan (`seed.check`), so a refusal cannot come
     from a broken seed.
   - Add runner tests for both seeds.
2. **Finding 2: remove the two allowances that no run used and no record derives.**
   - In the launch step, new chunks may appear only in the Moon generation box of section 9.4 (radius 13). Remove the
     overworld new-chunk allowance. Keep an overworld box entry only if a file-level rule needs it, and then derive it
     in the record.
   - Remove the stations-root allowance (`advancedrocketrycommunity_stations.dat` new or with a changed
     `orbital_clock/logical_tick`). Section 3.6 admits no entry without an accepted reason, and neither W380I cycle
     wrote that file.
   - Add tests that a new overworld chunk and a new or changed stations root now fail the functional comparison.
3. **Finding 3:** negative control 2 must require the tampered object's exact key:
   `OBJECT_CHANGED:` plus the location plus `:`. Add a test.
4. **Finding 4:** in accepting mode, compare scalar leaves by their serialized bytes, as measurement mode does, so
   that 0.0 and -0.0 differ and an unchanged NaN is equal. Add tests for both.
5. **Finding 5:** in the tool's `same_root`, return equal first when the two typed roots are equal, then apply the
   clock rule. Add a test with an unchanged satellite root that lacks the two clock leaves. Nothing else in the
   tool changes.
6. **Findings 6 and 8: record only.**
   - Say that the two negative controls demonstrate one detector, the independent oracle. The tool's census is not
     run on a control copy, and by design it would accept both mutations.
   - Correct "observation and census" after the replacement: the runner runs the oracle's coordinator there.
   - Note that `required_fuel=317` is not the expected debit (159 mB is), and that the seed's forced-stop logs, with
     `ENDPOINT_PROOF_REQUIRED`, are never given to `plan`.
7. **Finding 7** is for X03-D's operator text later; do nothing now.

Then:

8. **The final accepted sequence.** The oracle changes, so the accepted evidence must bind the final harness. Run the
   complete X03-B sequence once more natively, on a locally built JAR of this branch under the heavy-JVM lock, exactly
   as W380I did, with the three refusal seeds, both cycles, both negative controls, and the whole-world replacement
   with its observation. The status must be `PASS_DEVELOPMENT_ROOT_2`, and `results.json` must hold the harness
   hashes of your final commit. If a new contradiction appears, stop, report it with its evidence, and do not admit
   it yourself. W380I's run stays as history.
9. **Verification.** Run the six Python suites W380R ran, and `EscrowCargoCutProbeTest`. No Java changes, so no full
   GameTest is needed.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Never relax an oracle.
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Use the heavy-JVM lock with the 90-second gap, keep at least
  10 GiB free on C: and D:, and delete server copies after each run. Launch detached Python with the real `python.exe`,
  not the pyenv shim. Keep evidence under `D:/GitHub/ARCE-Task-Evidence/root-queue/W380J/`.
- Commit on the branch in reviewable commits, one per finding or close group, with messages in the style of AGENTS.md
  section 6. Do not push.
- The last line of your answer is the path of `records/W380.md`.
