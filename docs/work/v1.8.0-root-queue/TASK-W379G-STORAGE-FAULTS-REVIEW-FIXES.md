# TASK W379G: slice S4, fixes for W379R's findings

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w379`, branch `codex/v1.8.0-w379`, at W379F's commit `c7262d1d4` |
| Decision | Root decision R491 (QUEUE row W379G) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W379-review/REVIEW.md` (REJECT: two High, two Medium, one Low; SHA-256 `ba5cba9d840d2864d751db23c5e93c1bc3abf7053f351a6a50b72220d229c384`). The reviewer's probe scripts are in `.../W379-review/probes/`. |
| Record | Add a section "W379G fixes (R491)" to `records/W379.md`, with one row per finding. |
| Write scope | `scripts/run_v180_storage_faults.py`, `tests/test_run_v180_storage_faults.py`, and the record. A small new module beside the runner is allowed if it makes the classification testable. No Java, no existing runner and no oracle changes. |

## Root's answers (R491)

1. **Detection needs production evidence (High 1).**
   - `CAUGHT_LATER` needs a production marker for the faulted store, declared in the manifest before any case runs.
     For example, `[ARCE-BETA-2000] Beta world data check blocked startup; type=<STORE>` with the store's type.
   - A release-test assertion, a crash wrapper, an unrelated service error (such as the Yggdrasil key fetch) or any
     undeclared line never counts.
   - A case with a failed oracle and no declared production marker is `NOT_CAUGHT`, a residual.
   - Apply the same rule to the live branch the review names.
   - Add the kept stale-ledger observation of `W379F/smoke1` as a regression fixture.
   - Do not change the sealed folders. Write the corrected classification of the smokes into a separate correction
     file beside them, with its own checksums and reason.
2. **Unaccepted lines block every favourable live class (High 2).**
   - Before any favourable live class, a nonempty `first_start.unaccepted` makes the case `NEEDS_REVIEW`. This holds
     whether or not the wrapped runner refused the start.
   - Keep the runner's own result separate.
   - Add tests in which runner R passes and the harness finds an unaccepted line (an unrelated ERROR, a FATAL carrying
     a marker, another store's marker).
3. **A missing observation is not an observation (Medium 3).**
   - Track whether each post-exit callback ran, separately from its hash.
   - Require exactly one observation per expected start. Otherwise the case is `NOT_RUN` (instrumentation missed) or
     `INCOMPLETE`.
   - An observed absent file stays distinct from a missing observation.
   - Add a test with a runner that bypasses the replaced process attribute.
4. **`NOT_APPLICABLE` only by declaration (Medium 4).**
   - Only a reason declared in the frozen plan before any case runs gives `NOT_APPLICABLE`.
   - An inability found during the run is `INCOMPLETE`.
   - You may add a separate planning phase that records the needed traces and writes its declarations, with
     evidence, into the manifest before the first case runs. The registry `STALE` case the review names is the
     example.
   - Campaign status checks that each `NOT_APPLICABLE` case was declared.
5. **The final package counts (Low 5).**
   - Include the final manifest, summary and checksum list in the size budget.
   - Check the finished package before returning a successful status.
   - Persist `OVER_BOUND` if finalization exceeds the bound.
   - Add a near-limit test that runs the whole orchestration.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Each finding gets a test that fails
  without its fix.
- Run `tests/test_run_v180_storage_faults.py` and the six neighbouring runner suites. No native run is needed, and no
  heavy-JVM lock.
- Commit on the branch in reviewable commits, one per finding where practical, with messages in the style of AGENTS.md
  section 6. Do not push.
- A Codex re-review follows.
- The last line of your answer is the path of `records/W379.md`.
