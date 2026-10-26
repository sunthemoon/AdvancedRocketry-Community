# TASK W165-review: independent read-only review of the proposed ADR-075

Root: Claude. Date: 2026-10-26. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | [ADR-075](../../decisions/ADR-075-MOON-LANDING-ON-THE-HEIGHTMAP.md) (PROPOSED) and [records/W165.md](records/W165.md), merged as documentation at `48f590d2c` from `7ed99accb`. Review worktree `D:/GitHub/arce-v180-w165-review`, a detached checkout of the commit named in your prompt |
| Task it answers | [TASK-W165](TASK-W165-MOON-LANDING-RULE-ADR.md), with the owner's 2026-10-26 choice quoted there |
| Background | [records/W156.md](records/W156.md); ADR-033 lines 75-81; ADR-063 sections 5 and 9; ADR-006 |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W165-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The rule against the code.** Check the column and support rule against `RocketLandingPadSelector` as it stands,
   including the Mars and Venus path it reuses. Check what the eight fixed pads and the "landing pad unavailable"
   refusal actually do. Confirm that the refusal changes no journal entry, fuel or transfer record.
2. **The y=80 recheck threshold.** The ADR skips the arrival support recheck at or above y=80 because transfer records
   carry no rule marker. Look for a destination recorded under either rule that this misclassifies, and for an arrival
   below y=80 that the old rule could have recorded.
3. **What stays.** Pads, recorded destinations, recovery rebuilds, landed rockets, stations, other bodies and developer
   travel. Check the exact list of arrivals that keep y=80 behaviour.
4. **Limits.** Confirm the claim that the extra Moon support check (at most 2,048 block reads per candidate pad, loaded
   chunks only) is within existing limits and constants and adds no loading. Say whether it needs Root's attention.
5. **Tests.** The two `MoonArrivalGameTests` transitions keep or strengthen their meaning; the new cases cover empty
   and mixed columns, pads, passengers, recovery identities and restart. Check the W165 record's list of about 25
   source files that fly to the Moon for full-run risk.
6. **The known risks** in the record, and whether any is a blocker rather than a residual.
7. **Saved data and protocol:** confirm that neither changes.

Source inspection is enough. Probes are optional and must run in your review worktree only, under the heavy-JVM lock
if they start a JVM.

## Output format

A verdict for the ADR (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W165-R1, ...), severity, file
and line, problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last
line.
