# TASK W129-review: independent read-only review of W129's design note and proposed ADR-074 (off-thread writers)

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents. A Claude
session wrote the note, so this is a fresh Codex session with no context from the author.

| Item | Value |
|---|---|
| Subject | `docs/work/v1.8.0-root-queue/records/W129-DESIGN.md` (`7d217f11`), `docs/decisions/ADR-074-OFF-THREAD-REGISTRY-AND-TRANSACTION-WRITERS.md` (`d00752ab`, PROPOSED) and `records/W129.md` (`f875af6a`), merged by Root as documentation. Your review worktree `D:/GitHub/arce-v180-w129-review` is a detached checkout of `f875af6a` |
| Task it answers | [TASK-W129](TASK-W129-OFF-THREAD-WRITERS-DESIGN.md) (Root decision R51) |
| Background | [records/W128.md](records/W128.md) (the benchmarks), ADR-050 section 2 and its W27 note, [records/W119.md](records/W119.md) (the epoch rule), [records/W123.md](records/W123.md) (the transaction acknowledgement), `docs/17-V1PLUS-QUALITY-BUDGETS.md`, the existing off-thread writers `RocketJournalWriter` and `EscrowLedgerWriter` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W129-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The epoch rule with an asynchronous write**: a change made while a write is in flight is tagged with that write's
   epoch and cannot look saved until a later write containing it succeeds (the "snapshot epoch" ADR-050 section 2
   requires). With no change during a write, are W119's results exactly unchanged? Construct sequences (a throw, an
   UNKNOWN, a mutation during the write, a second capture queued, a stop or a crash) that could reuse an epoch, advance E
   past an unsaved stamp, or acknowledge state a file does not contain.
2. **W123's acknowledgement and B14** with the new WRITE_PENDING answer: can the bay's absence check be satisfied by a
   write that does not contain the state it relies on?
3. **The server-thread capture**: is it bounded (at most 20,480 references for the registry, 256 for the journal) and
   cheap at the bounds, as estimated? What re-encodes on the server thread still (the note's Q6: ground surveys and
   research accounts at a full registry)?
4. **The barrier callers** that today wait for the write: the follow-up actions after confirmation, the terminal lock on
   the package and chip slots while a launch or decommission is pending, and the duplicate-satellite path the lock
   prevents. Is there another caller that assumes the save finished when `save` returns?
5. **Stop and crash**: the final in-memory state is still written at shutdown on the normal and the crash path; the
   watchdog; vanilla autosave and `/save-all flush` interacting with an in-flight write.
6. **Memory**: concurrent writes of the registry, the transaction journal and the transfer journal at their bounds;
   is the note's single shared write permit needed now?
7. **The ADR**: is ADR-074 complete as an ADR (context, decision, consequences, the stop rule of its item 13, the risk
   entries it needs), and does it change any saved format?
8. **The questions** Q1 to Q8: give your recommendation on each, with reasons.

You may run existing unit tests and W128's benchmark classes (a benchmark only under the heavy-JVM lock, released in
the `finally` of the same detached script, WORKER-RULES); add a probe test in your worktree if it helps, keeping it in
the output folder. Before each Gradle run, check that at least 6 GiB of memory is free; one Gradle process at a time;
no GameTest, `runData` or server. Delete the worktree's `build` and `.gradle` folders afterwards.

## Output format

- A verdict on the note and the ADR: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W129-R1, ...), a severity, the file and line, the problem and a suggested change; then your
  recommendation on Q1 to Q8.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
