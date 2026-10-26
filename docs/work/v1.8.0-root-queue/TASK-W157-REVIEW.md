# TASK W157-review: independent read-only review of W157 (W80-storage, the checked rejoin sidecar)

Root: Claude. Date: 2026-10-25. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w157` at `a7be23e4` (base `b8d6c9e6`; tested implementation `2a0b7204`). Review worktree `D:/GitHub/arce-v180-w157-review`, a detached checkout of `a7be23e4`; read `git diff b8d6c9e6..a7be23e4` |
| Task it answers | [TASK-W157](TASK-W157-W80-STORAGE.md) |
| Design | [records/W80-DESIGN.md](records/W80-DESIGN.md) (revision 3; sections 2, 6 and Appendices A and B), the ADR-065 W80 implementation note and the W80 part of R-035 |
| Record | `docs/work/v1.8.0-root-queue/records/W157.md` on the branch (its step 0 lists what changed since the design froze) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W157/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W157-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Step 0.** Are the design's storage assumptions still true on the merged code (W123's checked-write core, the
   readback and acknowledgement, startup ordering, journal root 3)? Is every change the record lists handled without
   changing the design's storage contract?
2. **The sidecar.** Schema 1 and its bounds, the codec's rejection of duplicates, bad references and unknown states,
   the first-write race, `nextSequence` monotonicity and overflow, and capacity with retired batches. A world that
   never uses it has no sidecar.
3. **Startup refusal.** A future, malformed, oversized, unreadable or unknown-presence sidecar refuses startup with
   its bytes unchanged across repeated attempts, with one diagnostic per attempt. Check that no other path can delete,
   rewrite or skip it, and that the refusal matches R-035's text.
4. **Writer.** Checked, bounded writes with readback and owner-thread acknowledgement; no server-thread block;
   failures keep their state as the design says; the fixes in `1a59ba16` and `2a0b7204` are sound.
5. **Scope.** No command, release, placement or seat revocation; no journal format, bound, budget, timeout or protocol
   change; nothing weakened; new tests fail on the old code; new log lines have exact manifest entries.
6. **Merge** with the shared head (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`) and with W152
   (`codex/v1.8.0-w152`, in progress): shared files and conflicts.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: W157's new and changed unit classes alone,
then the `rocket.flight.persistence` package and any package the record names; the native runner's Python tests. No
GameTest, server or heavy-JVM lock. Check 6 GiB free memory first. Delete the worktree's `build`, `.gradle` and `logs`
afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W157-R1, ...), severity, file and line, problem
and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
