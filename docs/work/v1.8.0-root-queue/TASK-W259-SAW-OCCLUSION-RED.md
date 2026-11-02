# TASK W259: show the saw occlusion GameTest failing first (W256-R3)

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w259` (new; branch `codex/v1.8.0-w259` from the shared head named in your prompt, which includes W256) |
| Finding | W256-R3 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W256-review/REVIEW.md` (SHA-256 `d21bc787b9ff44637655ef1d1ecdc924b1c1c1cb4a344f27f0882930cbe23842`) |
| Record | Append a dated "W259: the saw occlusion GameTest run red" section to `docs/work/v1.8.0-root-queue/records/W256.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W259/` |

**Root decision R285 (W256-R3 part).** W256 added `noOcclusion()` to `saw_blade` and its `saw_occlusion` GameTest in
the same commit and never ran the test against the old registration.

1. On a throwaway commit that is never pushed or merged, change only the `ModBlocks.SAW_BLADE` registration back to
   `metalBlock("saw_blade")`, run the `saw_occlusion` batch once under the heavy-JVM lock, and keep the log: it must
   fail for the occlusion reason, not for setup.
2. Run the same batch on the unchanged branch head and show it passing.
3. Append the two results, with the throwaway commit's SHA, to W256's record and commit only that record change.

No code change is delivered.

## Verification

- The two focused runs with their logs and the log checker's scoped manifest; the repository validator.

Commit on the branch; do not push. Root merges the record change.

## Not in this task

Any change to the saw block, its test or other batches, status files and the ledger.
