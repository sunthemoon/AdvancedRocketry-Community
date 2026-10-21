# TASK W126-review: independent read-only review of W126's D2 design note

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents. A Claude
session wrote the note; both Codex slots stayed busy, so this is a fresh Claude session with no context from the author
(as for W118-review and W120-review).

| Item | Value |
|---|---|
| Subject | `docs/work/v1.8.0-root-queue/records/W126-DESIGN.md` (`aec1825b`) and `records/W126.md` (`c6efe079`), merged into the shared branch by Root as documentation. Your review worktree `D:/GitHub/arce-v180-w126-review` is a detached checkout of `c6efe079` |
| Task it answers | [TASK-W126](TASK-W126-D2-DESTINATION-AUTHORITY-DESIGN.md) |
| Background | [records/W121-DESIGN.md](records/W121-DESIGN.md) with Root decision R48 at its top, `D:/GitHub/ARCE-Task-Evidence/root-queue/W121-review/REVIEW.md`, [TASK-W125](TASK-W125-F1-NAMED-FILL-FIX.md), [records/W98.md](records/W98.md) and [records/W96-DESIGN.md](records/W96-DESIGN.md) (the ordinary retirement proof the note reuses), [records/W11A-DESIGN.md](records/W11A-DESIGN.md) sections 4, 5 and 7 |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W126-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The launch finding**: after any named fill, can the rocket not launch while its landed record T exists, even with
   its old launch site loaded, because its next launch is governed and a governed launch refuses while any transfer
   record names it (`EscrowGovernedPreparation.java:47-49`, `EscrowFlightAuthority.java:43-45`,
   `EscrowFlightAuthorityTest.java:118-119`)? And does F1b recur within the same session, because a new floor changes the
   proof key? Write a probe test if you can show either.
2. **The recommendation (b2)**: retire T before the first escrow fill on a landed rocket, using W98's on-disk proof
   applied at the destination (the landed rocket's own slot proven, the old launch site's slot shown empty), so the fill
   carries no transfer guard and leaves no floor. Is the proof sufficient for that retirement, and what does it load
   (once per landing, the old site's origin column)? Check its effect on the pad reservation, the bay, governed flights,
   cargo, a copy moved off the origin column into an unloaded chunk, and both restarts at each crash point. Does it
   change any saved format?
3. **The interim (d)**: admitting only fills on rockets with no transfer record. Is it enough, and is it implementable
   as a pure admission refusal?
4. **The rejected options** (a), (b1), (b3), (c), (c′) and (e): is any of them safer than (b2)?
5. **The decisions it asks of Root** (D2-1 to D2-5): give your recommendation on each, with reasons, and say how the
   launch finding should change W125's "usable for its next launch" reporting.

You may run existing unit tests and add a probe test in your review worktree; keep each probe in the output folder.
Before each Gradle run, check that at least 6 GiB of memory is free and wait if not; run one Gradle process at a time,
no GameTest, `runData` or server, and take no heavy-JVM lock. Delete the worktree's `build` and `.gradle` folders
afterwards.

## Output format

- A verdict on the note: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W126-R1, ...), a severity, the file and line, the problem and a suggested change; then your
  recommendation on D2-1 to D2-5.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
