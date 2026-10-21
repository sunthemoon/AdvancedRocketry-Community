# TASK W126: design note for R48's D2, a destination-authority proof for a landed rocket with a retained named floor

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md), [records/W121-DESIGN.md](records/W121-DESIGN.md)
(with Root decision R48 at its top), the review `D:/GitHub/ARCE-Task-Evidence/root-queue/W121-review/REVIEW.md`
(W121-R2, W121-R3 and its D2 recommendation), [TASK-W125](TASK-W125-F1-NAMED-FILL-FIX.md), and
[records/W11A-DESIGN.md](records/W11A-DESIGN.md) sections 4, 5 and 7, first. You are a new session. **This task is a
design note only: change no Java, script or test file.**

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w126` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w126` (local commits only; Root merges) |
| Output | `docs/work/v1.8.0-root-queue/records/W126-DESIGN.md` (the note) and `records/W126.md` (a short record) |
| Evidence | your notes under `D:/GitHub/ARCE-Task-Evidence/root-queue/W126/` |

## The problem (F1b)

After a named (transfer-guarded) fill retires, the landed rocket keeps its COMMITTED transfer record T and a named floor.
At every start, `approved` and the settled set are empty, so classification fences T and the existing loaded-only proof
needs both the destination's and the old launch site's discovery regions loaded (FULL, entity-loaded, entity-ticking).
Until then `resourceMutationAllowed` refuses the rocket's next launch. A rocket refuelled on the Moon cannot launch until
its Overworld launch site is loaded. This is pre-existing (W11b2's operator-settled named fills that keep T and the
floor have it too), and W125's fix keeps it (R48 dropped W121's C8 hand-back because it granted recovery authority
without a loaded census).

## What the note must contain (one to two pages)

1. **What authority a landed, settled rocket actually needs** to refuel, disassemble and launch again, and what the
   source-region proof protects against today (a stale source copy, a duplicate, an orphan). Quote the code.
2. **Options**, at least: (a) a destination-only proof with a stated action scope (which actions it permits, which stay
   forbidden while the source region is unproven); (b) a durable retirement of T, or of its floor, once a fill retires
   and both ends are proven once, so later starts need no source proof; (c) an operator-assisted path that loads the
   whole required source discovery region; (d) documenting the limitation for v1.8 with admission limited to
   same-Level fills. For each: the saved-format effect (none is preferred; a new durable marker needs a compatibility
   decision), the crash windows and both restarts, the effect on governed flights, the bay and cargo, and what could
   let a stale or duplicate copy spend resources.
3. **A recommendation** with its invariants (no resource spent twice, no fence weakened beyond the stated action scope,
   every hold resolves or shows a reason) and the tests and native cuts that would prove it, including the cross-Level
   K2 and K9 cases with the old source genuinely unloaded and no leftover tickets, first and second restart reported
   apart.

Verify claims against the code at the commit you start from. You may run existing unit tests read-only; run no GameTest,
native server or `runData`, and take no heavy-JVM lock. Check that 6 GiB of memory is free before any Gradle run.

## After the note

Root has it reviewed by an independent read-only session, decides, and queues any implementation as its own
persistence slice. Not in this task: code, W125's implementation, cargo, production admission, ADRs, the risk register,
status files and the ledger (Root).
