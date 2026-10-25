# TASK W160F: the W160 review findings and the remaining W80 native rows

Root: Claude. Date: 2026-10-25. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W160](TASK-W160-W80-REJOIN-COMMAND.md),
`records/W160.md` (on the branch below), the frozen design
[records/W80-DESIGN.md](records/W80-DESIGN.md) (revision 3, Appendix A) and the review
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W160-review/REVIEW.md`, SHA-256 `1859437e…9396`, verdict BLOCK) first. You
are a new session; W160's own session has grown too large to resume.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w160f` |
| Branch | `codex/v1.8.0-w160f` at `0efb2a55c`: Root's merge of W160 (`5e3498171`) onto the shared head `423288647`, which has W152's sealed staging. Local commits only; Root merges |
| Record | append a "W160F" section to `docs/work/v1.8.0-root-queue/records/W160.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W160F/` (W160's evidence stays as it is, apart from part 3's correction files) |

## Root decision R111 (2026-10-25)

The four findings are accepted as the review states them. Former W163 (the remaining native rows) is part 4 of this
task. Each part is its own commit; parts 1 and 2 each come with a regression that fails before the fix.

1. **W160-R3: the readback cadence oracle.** `RocketRejoinReadbackGameTests` counts every `SaveToFile` event as a W80
   prompt, so a vanilla autosave or another save between W80's prompts can complete the exact proof and fail the
   cadence assertion although W80 never prompted early.
   - Observe W80's own prompt calls (a test-visible record of the prompt ticks, with no change to production
     behaviour) and assert the unchanged spacing of at least 100 ticks between them.
   - Keep the failed-write, old-file and PENDING checks.
   - Add a controlled ordinary save between two prompts that may legitimately complete the exact readback, and assert
     P2 after it.
   - Treat the Replace and Temp variants the same way.
   - Do not remove the cadence assertion, enlarge a timeout, forbid autosaves or add the failed-test ERROR to a
     manifest.
2. **W160-R2: the C22 oracle.** Check the complete released set in every captured rocket and in any journal manifest
   that can be reconstructed. Add mutations for each rider independently, at seed, recovery and check. Keep both
   installed-file assertions. Replay the corrected oracle over W160's retained C22 evidence; that replay needs no new
   start.
3. **W160-R4: the stale launcher-log checksum.** Follow the W159 correction process: keep W160's original
   `SHA256SUMS.txt`, and add a correction file that explains the changed launcher output, with a corrected manifest
   written after the emitter ended, or one that leaves launcher output out of the stable set.
4. **W160-R1: the remaining native rows (former W163).** Run them through the command on this integrated source, using
   matching registries:
   - disassembly followed by rejoin;
   - player-first load;
   - later same-seat boarding;
   - multiple and newer conflicts, with the queue and hazard timeout;
   - the pre-W80 root-3 unused-world baseline (W152's jar from `59f08e179` now has the same registries as this
     source);
   - W44 and governed interleaving;
   - C12, the external entity-store rollback;
   - the player file over 2 MiB, with the disk ceiling.

   W160 used 19 of the 60 counted starts, so at most 41 remain, including failed pilots. If the rows need more, stop
   at that point and report a plan. Update Appendix A's mapping in the record. Do not infer a native row from a unit
   test or a GameTest.

The W152H cross-guard merges into the shared branch separately. It only refuses a rocket that sealed staging holds,
which none of these rows reach, so native evidence on this branch counts. Root reruns the combined suites after that
merge. No saved format, journal root, sidecar schema, bound, budget, timeout or protocol change.

## Verification

- Each part's new tests alone, failing before the fix.
- The `rocket.rejoin` package, the W158 release classes and the W160 command classes.
- The native runner tests (`tests/test_run_w158_release_cuts.py`, `tests/test_run_w159_rejoin_cuts.py`,
  `tests/test_w160*.py`) and `tests/test_check_gametest_log.py`.
- The whole unit suite, `runData` with a clean `git diff --exit-code`, and one complete GameTest that passes with the
  unchanged log checker.
- Part 4's native rows, each with its counted starts.

Run heavy work under the heavy-JVM lock, with the atomic pattern and the 90-second hand-off rule; W152H shares the
lock. Before starting, check that C: and D: each have more than 10 GiB free and that 6 GiB of memory is free. Keep the
evidence under 100,000,000 bytes. A fresh Codex session re-reviews afterwards.

## Not in this task

- The sealed cross-guard (W152H part 1).
- Governed records.
- Two-real-client evidence, which waits for the owner's final campaign.
- Power-loss durability.
- ADRs, the risk register, `build.gradle`, status files and the ledger, which stay with Root.
