# TASK W129R-review: focused re-review of revision 2 of W129's note and ADR-074

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents.

| Item | Value |
|---|---|
| Subject | Revision 2 (`1b9849c1`) of `docs/work/v1.8.0-root-queue/records/W129-DESIGN.md`, `docs/decisions/ADR-074-OFF-THREAD-REGISTRY-AND-TRANSACTION-WRITERS.md` (still PROPOSED) and `records/W129.md`, merged by Root as documentation. Your review worktree `D:/GitHub/arce-v180-w129r-review` is a detached checkout of `1b9849c1`; compare it with revision 1 at `f875af6a` |
| What it answers | [TASK-W129R](TASK-W129R-OFF-THREAD-WRITERS-REVISION-2.md) (Root decision R53) and the first review `D:/GitHub/ARCE-Task-Evidence/root-queue/W129-review/REVIEW.md` (SHA-256 `5f5ba8a1…cf11`), with its model and probes in that folder |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W129R-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check (focused)

For each of R53's seven items, say whether revision 2 closes it, with the line that does:
1. **W129-R1**: the shutdown predicate covers both flags; suppression on the exception path; the watchdog claim corrected
   and what is guaranteed if it interrupts teardown; a thrown tick exception in the test plan. Recheck against the
   bytecode extracts the first review kept.
2. **W129-R2**: the drain ends only on a COMMITTED capture covering the final target sequence; pending work and dirty
   state survive an older success; one retry budget across `save` and `close`, its exhaustion reported; durations
   measured or marked unmeasured; world ownership never released while IO may replace files. Rerun the first review's
   model against the revised rules if it fits.
3. **W129-R3**: `SatelliteRegistryProofTest` lines 120 to 137 listed as a semantic transition with the stated
   assertions, its mutation on the owner thread behind a paused IO seam; no-mutation matrices unchanged.
4. **W129-R4**: the overflow check includes the active capture's epoch; capacity refusal, no wraparound; MAX − 1 and MAX
   fixtures.
5. **W129-R5**: the memory gate before W129a's merge, the three-writer measurement before W129b and W129c, a paced tick
   surrogate, the stop-on-miss rule.
6. **W129-R6**: `TerminalLocation` frozen at construction in W129b, and the nested-type audit.
7. **Q1 to Q8**: adopted, or where the revision differs and why.

Also: did the revision introduce anything new that breaks W119's epoch rule, W123's acknowledgement or B14, or change a
saved format? Is ADR-074 ready for Root to accept, and with which conditions?

You may run the first review's model and existing unit tests in your worktree (one Gradle process at a time, 6 GiB of
memory free first, no GameTest, `runData` or server, no heavy-JVM lock), keeping any probe in the output folder. Delete
the worktree's `build` and `.gradle` folders afterwards.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK, for the note and for ADR-074's acceptance.
- For each of the seven items: closed, partly closed (what remains), or open.
- Any new findings, each with an ID (W129R-R1, ...), a severity, the file and line, and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
