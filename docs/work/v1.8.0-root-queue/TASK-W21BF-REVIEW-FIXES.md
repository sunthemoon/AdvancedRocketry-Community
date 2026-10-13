# TASK W21bF: W21b review fixes (F1 performance first)

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w21b-registry-schema4` (W21b, `8c22fce3`) |
| Branch | `codex/v1.8.0-w21b-registry-schema4` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W21b-review/REVIEW.md`, SHA-256 `d2a7401cb474a01c63fe011533ecbfa9d9abdafb441cd41e6b9af64ce0eb9d4a`. Verdict MERGE-WITH-FOLLOWUP |
| Record | `docs/work/v1.8.0-root-queue/records/W21BF.md` |

Read the whole review, including its probe appendix. Fix each finding in its own commit,
together with its tests. W21c, the observatory runtime, is being built on this branch. It
cannot merge until F1 is fixed.

## Root's decisions

1. **F1 (Medium, blocks W21c).**
   - Keep the full-root encode and bounded-read preflight **only** for operations that add
     records: START, and account creation.
   - Make non-final advances, claims, rebinds and credits cost O(touched records). They rely
     on each job's reserved largest lifecycle encoding (`groundSurveyBytes`), which already
     bounds them.
   - Benchmark START on a root near the limit against the docs/17 single-spike budget of
     500 ms.
   - Add a scale test with an explicit budget, for example one advance on the review's
     2,001-instance and 701-job registry staying far below 1 ms.
   - Do not relax any persistence bound.
2. **F2:** check only the touched job, its instance, and their device, processor and
   instance peers, not `RegistryInvariants.plan` over the whole registry. If you keep any
   global check, log its refusal cause once.
3. **F3:** for a non-quarantined instance, `instance release` returns IDEMPOTENT before the
   ground check runs. Test it with the review's probe case.
4. **F4:** add the `…satellite.offline_repair_required` translation key to the V180 language
   provider, in English and Chinese, with `runData` evidence.
5. **F6:** the schema-4 format epoch is the literal `"v1.8.0-ground-surveys"`, not the moving
   constant. Add a test that pins it.
6. **F8:** the native harness records `git HEAD`, checks that the tree is clean, and records
   the jar's SHA-256 when the jar is built.
7. **F5, F7, F9 and F10:** no code. Root records them in the ADR-051 note and the status files.

## Commands

- The whole unit suite once.
- `runData` under the lock.
- `runGameTestServer` under the lock, then the log checker. Report the totals.

## Not in this task

- The observatory and processor runtime (W21c).
- Status files and the ledger.
