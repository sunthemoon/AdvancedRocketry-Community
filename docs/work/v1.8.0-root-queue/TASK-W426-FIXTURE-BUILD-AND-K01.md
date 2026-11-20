# TASK W426: make the API fixture mod build independently against C6, and finish package K01

Root: Claude. Date: 2026-11-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w426`, branch `codex/v1.8.0-w426` from the shared branch |
| Decision | Root decision R587 (QUEUE row W426), following R586 |
| Sources | [W424](records/W424.md) (the blocked fixture build: `AdapterPendingRejectionFixture` line 36 uses `ServerLevel.entityManager` and `PersistentEntitySectionManager.storeChunkSections`, which only the host's access transformer exposes); the [fixture README](../../../compat-test-mod/README.md) and `compat-test-mod/` build files; `src/adapterTest/`; the host's `META-INF/accesstransformer.cfg`; C6's API JAR and local Maven publication evidence under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/candidate/` |
| Record | `docs/work/v1.8.0-root-queue/records/W426.md` |
| Write scope | `compat-test-mod/` (its build files and its own resources) and `src/adapterTest/` only where the independent build needs it; tests of that build if any; the record. No change to `src/main`, `src/generated`, the host build files or C6's JAR: the fix must not make a new candidate. |
| Lock | Gradle builds are heavy: take the heavy-JVM lock with the WORKER-RULES loop (C6's campaign yields it for 120 seconds after each family), hold it only while Gradle runs. |

## What to do

1. Decide, from the README's stated purpose of the independent fixture, the right fix: either the fixture build applies
   its own access transformer for exactly the two members it needs (a fixture-owned file, the same two lines, no
   wider access), or the class that needs them stays out of the independent consumer build because it belongs to the
   host's GameTest adapter set only. State which and why; the consumer-boundary check (`verifyConsumerBoundary` and its
   negative-import probe) must still pass and must not be weakened.
2. Build the fixture against C6's API JAR from the evidence folder (or a fresh local publication from C6's commit in a
   detached worktree), under the lock, and complete K01: the fixture binary, its SHA-256, the consumer-boundary
   verdict, added to `SHA256SUMS.txt` and `IDENTITY.json` in the evidence folder (append; do not rewrite earlier lines
   W424 recorded except the BLOCKED marker, which gets a dated correction line).
3. Run the host build's relevant unit tests if `src/adapterTest` changed, and `python -B scripts/validate_repository.py`,
   which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in one reviewable commit plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
