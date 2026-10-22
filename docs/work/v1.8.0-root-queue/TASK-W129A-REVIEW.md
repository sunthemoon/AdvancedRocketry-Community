# TASK W129a-review: independent read-only review of W129a (the off-thread writer core and the transaction journal writer) before merge

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents or
subagents. Claude wrote W129a, so this is a fresh Codex session with no context from the author.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w129a` at `66b27c04` (base `a355e687`; tested source `a3f84765`). Your review worktree `D:/GitHub/arce-v180-w129a-review` is a detached checkout of `66b27c04`; read the diff with `git diff a355e687..66b27c04` there |
| Task it answers | [TASK-W129a](TASK-W129A-WRITER-CORE-TRANSACTION-JOURNAL.md) |
| Contract | [records/W129-DESIGN.md](records/W129-DESIGN.md) revision 2 (sections 2 to 4.6, 5.3, 5.4, 6 and section 7 item 1), the proposed [ADR-074](../../decisions/ADR-074-OFF-THREAD-REGISTRY-AND-TRANSACTION-WRITERS.md), and the conditions in `D:/GitHub/ARCE-Task-Evidence/root-queue/W129R-review/REVIEW.md` |
| Rules it must keep | [records/W123.md](records/W123.md) (acknowledgement and B14) and [records/W128.md](records/W128.md) (the synchronous baseline and the stop rule) |
| Record | `docs/work/v1.8.0-root-queue/records/W129a.md` on the branch (the plan is at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W129a/` (`heavy-a3f84765-20261022-092820/` for the GameTest, the native cuts and the benchmarks) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W129a-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

ADR-074 is PROPOSED; its acceptance is the owner's decision, and W129a merges only after it, after this review and
after Root's risk-register entries. Root decision R60 (QUEUE) reads B3a as within its budgets and carries the overlap GC
pauses as a residual to W129b's B3; check that reading in item 8.

## What to check

1. **The writer core** (`CheckedRootWriter`): at most one capture in flight; a request during a write is served by a
   fresh capture at the next start; publication only on the owner thread, a foreign or stale completion refused; an
   `Error` becomes UNKNOWN; the 10/200 cadence and backoff on every start path, not reset by requests; the drain's one
   three-attempt budget includes the in-flight attempt; close and abort join before world storage is released, an
   interruption is preserved, abort publishes nothing, a request after close is refused. Look for races between the
   owner thread and the writer thread, lost wake-ups, and any path where a completion is published twice or never.
2. **The capture and encoder.** The capture is immutable (no live collection, tag or position reaches the writer
   thread); the encoding is byte-identical to the old synchronous `save(CompoundTag)` (B2 asserts it at 64 MiB); no
   saved field, schema, file name, format epoch, digest algorithm or bound changed; the moved codec
   (`RocketTransactionCodec`) is byte-for-byte the old logic.
3. **W123 and B14.** Session-local exact acknowledgement, COMMITTED outcome, exact current sequence and sticky UNKNOWN
   are unchanged; NOT_COMMITTED keeps old evidence without authority; WRITE_PENDING comes after the non-operational and
   RAM-record checks and the acknowledgement, and neither a request nor a stale completion proves absence. Every one of
   `RocketTransactionProofTest`'s 14 W123 cases keeps its assertions; say whether awaiting changed what any of them
   proves.
4. **Callers.** Every former caller of the synchronous `checkpoint(server)` now uses `requestCheckpoint`; list each and
   say whether any caller relied on the write being durable when the call returned (D0, release-test probes, the v1.0
   transaction forced-stop harness the worker did not rerun, commands). `RocketTransactionReleaseProbe` now awaits the
   acknowledgement; is that faithful?
5. **The stop path** (sections 4.5 and 4.6): autosave and `/save-all` request only; the stopping save drains; the
   Overworld unload resumes the same drain and joins; `ServerStoppedEvent` is the abort backstop; a clean root is not
   rewritten; shutdown suppresses effects for both flag combinations. Check the native cuts T1 to T3, R6 and the four R7
   cases against section 6.4, including the watchdog-ended teardown not being reported as a drain.
6. **Logging**: one ERROR per failure streak and one INFO when it ends, one ERROR per unexpected writer `Throwable`, one
   WARN per incomplete drain, no per-tick or per-poll line; the manifest entry and the pinned ERROR total.
7. **Nothing weaker.** List every edited pre-existing test, fixture or harness file and say whether any assertion,
   bound, budget or timeout became weaker; `RocketTransferService` and the transfer writer are unchanged; new classes
   stay under 500 lines.
8. **B2 and B3a.** Rerun nothing heavy; read the evidence. Do the benchmarks measure what section 6.1 asks (server-thread
   request and publication, writer time, peak heap; at least 50 overlap rounds with resident roots on the monotonic
   surrogate, success and failure/readback paths)? Is R60's reading right that no stated budget missed, given 34 young-
   generation pauses of 50 ms or more (maximum 82 ms) and 7 missed deadlines in 7,653 ticks with both writers, against
   none with the transfer writer alone?
9. **Merge with the shared head** (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`), which since W129a's
   base merged W133, W131 and W130 (W130 touches `RocketTransferRecoveryService`, `RocketJournalFailurePolicy` and the
   retirement). Preview with `git merge-tree`, list every conflict and say what the merge must keep.
10. **The record** against the code and the evidence, including its list of new holds and log lines for Root.

## Rerun

In your review worktree only, with JDK 17.0.7 (`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`: W129a's
new and changed unit classes, each alone (`CheckedRootWriterTest`, `RocketTransactionWriterTest`,
`RocketTransactionShutdownTest`, `RocketTransactionLoggingTest`, `RocketTransactionProofTest`), then the `persistence`
and `rocket.persistence` packages; and the harness's Python tests. You may add a probe test to check a finding; keep each
probe in the output folder. Before each Gradle run, check that at least 6 GiB of memory is free and wait if not; run one
Gradle process at a time, and no GameTest, `runData`, benchmark or server; take no heavy-JVM lock. Delete the worktree's
`build`, `.gradle` and `logs` folders afterwards.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W129a-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
