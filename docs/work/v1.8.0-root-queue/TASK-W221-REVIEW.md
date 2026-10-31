# TASK W221-review: independent read-only review of the governed named-pad evidence

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w221` at `9ac7ba3ed`, cut from `eb6bd6b26`; the slice is `eb6bd6b26..9ac7ba3ed` (10 commits). Review worktree `D:/GitHub/arce-v180-w221-review`, a detached checkout of `9ac7ba3ed` |
| Task it answers | [TASK-W221](TASK-W221-GOVERNED-NAMED-PAD.md), Root decisions R207 and R208 (finding W211-R2) |
| Record | `docs/work/v1.8.0-root-queue/records/W221.md` on the branch (newest sections first) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W221/` (`EVIDENCE-SUMMARY-R208.json` indexes it) |
| Hosted run | the run on `9ac7ba3ed` (Root pushed the branch at 10:06); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W221-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

W221 reports seven governed named-pad rows (PREPARED, REFUSED, STOPPED, BEFORE, MOVED, UNKNOWN, RELEASED) on a real
loader-funded governed flight, each passing with two restarts, after R208's reader and runner fixes; PREPARED used one
R201 renewal for an oracle that omitted vanilla's `keepPacked: 0`; the earlier UNKNOWN reads are explained by
legitimately empty region files.

## What to check

1. **Each row against the product:** the governed named selector (`RocketTransferService` lines 149 to 160),
   `NamedLandingPadStaging`, `RocketGovernedCleanupDispatcher` (release after checked journal absence) and
   `LandingPadService`'s acknowledged-absence rule: each row reaches the boundary it names; refusal and UNKNOWN keep
   the complete source and the pad claim unchanged; exactly one release, and only after acknowledged absence; recompute
   every row from the archived inputs and images after both restarts.
2. **R208's reader and runner:** an empty region file is accepted exactly as vanilla's `RegionFile` does (check the
   cited bytecode), any short nonempty file stays UNKNOWN, diagnostics and inputs are kept, and an attempt stops after
   an UNKNOWN read; the `keepPacked` correction requires the exact native flag rather than ignoring it.
3. **The renewal:** PREPARED's failed attempts stay FAILED; the R201 conditions held (images explained, a test-side
   defect, a failing-first fix, a replay); no other row used a hidden renewal.
4. **The production-source changes** (`EscrowLoaderS6PadProbe`, `EscrowLoaderS6PadJournalProbe`, the
   `EscrowLoaderS6JournalProbe` and `EscrowLoaderS6Probe` edits, `RocketNamedPadReleaseFixture`): all inert unless the
   hooks property, the exact case and the release gate hold; nothing changes selection, claims, cleanup or writes.
   W217's relaunch dependency check still passes with them (`EscrowRelaunchClassesTest`).
5. **Tests** failed first and would catch a wrong claim, a lost claim or an early release; **counts** and **cleanup**
   are as recorded; **merge conflicts** against the shared head and W220 (`codex/v1.8.0-w220`, which also edits
   `EscrowLoaderS6FlightProbe` and the S6 runner).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the new and changed test classes,
`EscrowRelaunchClassesTest` and the `resourceescrow` and `rocket.server` packages; the loader S6 and pad Python tests;
the oracle and replay on the archived inputs. Targeted unit runs need no heavy-JVM lock. Check 6 GiB free memory first.
No GameTest, native case, runData, full build or server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W221-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
