# TASK W123-review: independent read-only review of W123 (W27c3, the transaction journal's checked policy) before merge

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents. Codex
wrote W123 and both Claude slots are busy, so this is a fresh Codex session with no context from the author.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w123` at `a45fa7aa` (base `4ed1c5f1`). Your review worktree `D:/GitHub/arce-v180-w123-review` is a detached checkout of `a45fa7aa`; read the diff with `git diff 4ed1c5f1..a45fa7aa` there |
| Task it answers | [TASK-W123](TASK-W123-W27C3-TRANSACTION-JOURNAL.md) |
| Contract | [records/W27A-DESIGN.md](records/W27A-DESIGN.md): section 8's "Transaction journal" bullet, the W27c3 row of section 9 and cut B14; W11b2's checked journal policy and exact acknowledgement as the model |
| Record | `docs/work/v1.8.0-root-queue/records/W123.md` on the branch (its design at the top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W123/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W123-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The checked write and the acknowledgement**: what is acknowledged (sequence, root digest, record digests), when the
   acknowledgement is current, and how a definite failure and an UNKNOWN outcome are handled. Can any sequence (a throw,
   a retry, a concurrent assembly or disassembly, a restart) report an acknowledgement that the file on disk does not
   contain, or reuse one?
2. **B14**: a stale acknowledgement asks for one checked write; a failure is reported so W27d can reject all bays of the
   rocket with `ASSEMBLY_JOURNAL_UNPROVED`; a remaining record for the rocket is refused as pending even after a
   successful write of it, because writing a stale record is not proof of absence.
3. **The shared checked-write core**: the record says W123 generalized a shared core. Does the existing transfer journal
   (W11b2) and any other user of that core behave exactly as before? Compare the old and new write paths line by line.
4. **The saved format** is unchanged, as the record says; an older jar reads a file this jar writes, and the reverse.
5. **Existing assembly, disassembly and transaction recovery** behave as before; synchronous checkpoint cost at maximum
   size is unbenchmarked (the record says so): is anything on the server thread newly unbounded?
6. **Tests and limits** (AGENTS.md section 5): no existing test changed (the record says so: confirm), no limit, budget
   or timeout relaxed; classes over 500 lines.
7. **Merge with the shared head** (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`): preview with
   `git merge-tree` and say what the merge must keep.
8. **The record** against the code.

## Rerun

In your review worktree only, with JDK 17.0.7 (`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`: W123's
new unit classes, then the rocket persistence, assembly and transfer journal tests, then the `rocket` package. You may
add a probe test to check a finding; keep each probe in the output folder. Before each Gradle run, check that at least
6 GiB of memory is free and wait if not; run one Gradle process at a time, and no GameTest, `runData` or server; take no
heavy-JVM lock. Delete the worktree's `build` and `.gradle` folders afterwards.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W123-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
