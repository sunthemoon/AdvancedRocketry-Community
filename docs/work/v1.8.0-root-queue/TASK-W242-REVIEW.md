# TASK W242-review: independent read-only review of the bay safe exit (W230-S2)

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w242` at `f0c622a30`: W242's commits (design note `1fd608b81` to `c59ae628b`), W242-fix's commits (`fa0a0ba6e` to `d9fd5990f`) and Root's merges of the shared head (`4d3beafef`, `f0c622a30`). Review the whole change against the shared head, `git diff c5423e924...f0c622a30`. Review worktree `D:/GitHub/arce-v180-w242-review`, a detached checkout of `f0c622a30` that Root creates |
| Design | [W230-DESIGN](records/W230-DESIGN.md) Revision 1 and [TASK-W242](TASK-W242-BAY-EXIT-S2.md) (R254); [TASK-W242-FIX](TASK-W242-FIX.md) with Root decisions R260 and R264 (R264 replaces R260's test) |
| Record | `docs/work/v1.8.0-root-queue/records/W242.md` on the subject branch; logs and risk drafts in `D:/GitHub/ARCE-Task-Evidence/root-queue/W242/` and `W242-fix/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W242-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The design as decided.** Permits, endpoint selection through W239's model, separate live and history state, the
   source-aware ordinary-fall safeguard, the descent-continuity observers and the physical self-exit with English and
   Chinese feedback follow W230-DESIGN Revision 1 and TASK-W242, within section 10's class and line limits, with bay
   admission still off.
2. **R264 exactly.** At most one teleport-counter step beyond the exit's own correction, within the first two server
   ticks after detach, on the same connection, accepted only if the next sample passes every existing continuity
   limit against the previous sample; it never rebaselines; any other correction, fluid contact or climb still
   revokes. Look for an off-by-one in the tick window and for any path that grants a second step.
3. **Server authority and bounds.** Nothing trusts a client position or flag; no lookup can load or generate a chunk
   or add a ticket; every per-player or per-offer structure is bounded and cleared on logout, death, dimension change
   and server stop; no world state lives in an unscoped static collection; the read-only reflection (`f_9767_` and any
   other) writes nothing, and there is no ASM or Access Transformer.
4. **Tests.** No assertion, timeout, budget or checker is weaker. The dry corridor, the sealed external water and the
   test-only dismount listener isolate fixtures without hiding production behaviour (no floor or landing added before
   the stimulus; the listener is removed in cleanup).
5. **The open failures.** In both unfiltered local runs with W242 (1,635 of 1,638 and 1,637 of 1,640) the unchanged
   `doublechestboundaryandlazylootstaysealedundertickingautomation` (400-tick timeout) and
   `akilledrocketleaksitsrejectedreceiptuntiltheoperatorretiresit` (retirement result 0) failed, and the final run
   adds `supersededterrainproofretriesthesamefrontierafterbackoff` (force or IO budget). Base `9ace3837a` passed
   1,577 of 1,577. Several focused cohorts also saw an UNKNOWN level-33 ticket. From the logs and the code, say
   whether W242's code or fixtures can plausibly cause each one (leftover tickets, journal entries, listeners, world
   changes, batch order), and what would settle it.
6. **The risk drafts** for R-042 and R-043 in `W242-fix/RISK-DRAFTS-R264-FINAL.md` match the code.
7. **The sharded branch run** on `f0c622a30` once it finishes.

## Rerun

In your review worktree only: the bay-exit unit classes, the whole unit suite and the repository validator. Do not run
GameTests, servers or clients.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK). Findings with an ID (W242-R1, ...), severity, file and line,
problem and suggested change. The commands you ran and their results. The SHA-256 of REVIEW.md on the last line.
