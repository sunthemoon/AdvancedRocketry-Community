# TASK W129aM: bring W129a up to the shared head (ADR-074 slice 1, accepted 2026-10-26)

Root: Claude. Date: 2026-10-26. Read [WORKER-RULES](WORKER-RULES.md) first. Then read:

- [ADR-074](../../decisions/ADR-074-OFF-THREAD-REGISTRY-AND-TRANSACTION-WRITERS.md), now ACCEPTED, with its four
  conditions;
- `records/W129a.md` on the branch, and the W129a, W129aF and W129a-rereview rows of [QUEUE](QUEUE.md);
- the W152 to W152J sections of [records/W152.md](records/W152.md), for what N3-B2 added on top of the transaction
  journal: the retained record U, its presence predicate and the checkpoint requests.

You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w129a` (existing, clean, branch `codex/v1.8.0-w129a` at `f8d2b42c4`) |
| Merge | the shared head that records this task, into that branch (local commits only; Root merges) |
| Record | append a "W129aM" section to `records/W129a.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W129aM/` |

## Why

W129a branched from `2426f00d`; 606 shared commits have landed since. Root's trial merge has three conflicts:

- `scripts/gametest_expected_log.json` and `tests/test_check_gametest_log.py` need both sides' additions;
- `RocketTransactionSavedData` is a real conflict. N3-B2 added `ExpectedEntry`, `canAppend`, `acknowledgedEntry`
  and `acknowledgedAbsent`, which read `checkpoint.acknowledged()`. W129a replaced that synchronous checkpoint with
  the off-thread writer's `outcome` and `acknowledged` fields and added `WRITE_PENDING` to `AbsenceStatus`.

## Work

1. **Plan first.** List every use of the transaction journal's checkpoint, acknowledgement, absence query and
   `canAppend` added after `2426f00d`: N3-B1, N3-B2 and its follow-ups, W80 and the bay. For each, say how it behaves
   when a write is requested but not yet acknowledged (`WRITE_PENDING`). A caller that requested a checkpoint and
   re-queried in the same tick now gets the result one write later.
2. **Merge, then adapt.**
   - Resolve the merge with the shared side's methods expressed on W129a's fields.
   - Put each semantic adaptation in its own commit after the merge commit, with a test that fails without it.
   - `WRITE_PENDING` must mean "not proved yet, wait". It never means proved, refused, held, released or retired. No
     caller may change blocks, entities, fuel, U or T on it.
   - Keep W123's acknowledgement rules, W119's results, B14 and every N3-B2 hold exactly.
3. **No relaxation.**
   - No change to schemas, saved formats, bounds, budgets, deadlines, retries or quotas.
   - No checker change. No change to a test's assertions except a named, justified asynchronous-result transition
     like ADR-074 item 5's, listed in the record.

## Verification

- Compile, then the W129a classes, the W123 matrix and the W152 to W152J unit classes alone.
- The `resourceescrow`, `rocket.server`, `rocket.sealed` and `rocket.persistence` packages, then the whole unit suite.
- One complete GameTest with the unchanged log checker.
- B2 again at the bound; B3a only if writer code changes.
- Native, two restarts each: W129a's T1 to T3, R6 and the four R7 cases, and the eight W152 native cases. Stop and
  report before more than 60 counted starts.

Run heavy work under the heavy-JVM lock with the atomic pattern and the 90-second hand-off rule; other workers share
it. Before starting, check that C: and D: each have more than 10 GiB free and that 6 GiB of memory is free. Keep the
evidence under 100,000,000 bytes and delete only the server and export copies you create. This is a persistence
slice: a fresh read-only review follows before Root merges it.

## Not in this task

- W129b and W129c, the satellite registry writer and the barriers.
- W164 (N3-B3) and W152J, which run separately; Root sequences their merges.
- ADRs, the risk register (Root registers ADR-074's protections in R-039 and R-040), `build.gradle`, workflows,
  status files and the ledger.
