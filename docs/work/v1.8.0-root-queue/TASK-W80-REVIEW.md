# TASK W80-review: independent read-only review of the offline-passenger release design note

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a
reviewer: change nothing in any repository worktree.

| Item | Value |
|---|---|
| Subject | `docs/work/v1.8.0-root-queue/records/W80-DESIGN.md` on branch `codex/v1.8.0-w80-offline-passenger-design`, commits `cc30fcbd` (note) and `ebf32c45` (record `records/W80.md`) |
| Assignment the note answers | [TASK-W80](TASK-W80-OFFLINE-PASSENGER-RECOVERY-DESIGN.md), items 1 to 6 |
| Code it cites | The shared branch, and W11b2 at `4b4fa530` (read W11b2 only through Git) |
| Checkout | `D:/GitHub/arce-v180-w80-review`, detached at `ebf32c45`, made for you; do not commit in it |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W80-review/REVIEW.md` (create the folder), and its SHA-256 on the last line of your answer |

## What to check

1. **Completeness** against TASK-W80's six items.
2. **Accuracy** of each code citation, in particular the two holds (H1 in `RocketTransferRecoveryService`, H2 in
   `RocketDestinationCompletion`) and the claim that governed (schema 3) flights never reach them.
3. **No player is lost, duplicated or left inside a removed rocket.** Walk the release command and the next login
   for each absent passenger, including a passenger who logs in during the release, one who never returns, and a
   server stop at each of the note's seven crash points. Check the claim that the rejoin entry is removed only after
   Forge reports the player's file saved, and what happens if that save fails.
4. **The journal root change (3 to 4):** is it needed; is the migration, refusal of older jars and downgrade
   behaviour stated; does it collide with W11b2, whose tests assert root 3, or with W44's first-event outbox?
5. **Safety** (AGENTS.md section 3.3): the command's permission and argument checks; bounded persisted data with a
   schema version; no fuel or resource movement; the risk proposal for anything that refuses, drops or isolates
   player data (scope, duration, log volume, recovery).
6. **The recommendation of no time-based release**, and the ten open questions: is each recommended answer sound?
7. **Tests and feasibility:** the unit, GameTest and native restart matrix (43 JVM starts).
8. **Readability** (AGENTS.md section 10).

Take no heavy-JVM lock. Run no Gradle build, GameTest or native server.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or REDESIGN.
- Findings, each with an ID, a severity (High, Medium, Low, Info), the section and line of the note (and the code
  file and line where relevant), the problem, and a suggested change.
- A table of TASK-W80's items 1 to 6: answered, partly, or not.
- The commands you ran.
- The SHA-256 of REVIEW.md on the last line of your answer.
