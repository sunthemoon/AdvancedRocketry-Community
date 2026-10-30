# TASK W215-review: independent read-only review of the relaunch first-use remedy

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w215` at `210cca281`, cut from `baab5ce80`; the slice is `baab5ce80..210cca281` (5 commits, 10 files). Production: `a8d4ccd2e` (`resourceescrow/EscrowRelaunchClasses.java`, new). Review worktree `D:/GitHub/arce-v180-w215-review`, a detached checkout of `210cca281` |
| Task it answers | [TASK-W215](TASK-W215-RELAUNCH-FIRST-USE.md), Root decision R184's follow-up |
| Record | `docs/work/v1.8.0-root-queue/records/W215.md` on the branch (the design note at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W215/`; W212's in `D:/GitHub/ARCE-Task-Evidence/root-queue/W212/` |
| Hosted run | the run on `210cca281` (Root pushed the branch at 17:49); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W215-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

W215 initializes 55 audited mod classes on `ServerStartedEvent`. It reports the first relaunch's API cost down from
80 to 118 ms to 46 to 65 ms and its ClassLoad union from 31 to 51 ms to 8 to 11 ms; first-flight checkpoint P99 now
meets 100 ms in all nine timing-only cases and seven of nine full-observation cases (new strip 102.789 ms, generated
contained 103.517 ms still fail).

## What to check

1. **Static initializers.** For each of the 55 classes, read its static initialization (and that of anything it
   initializes in turn): nothing touches the world, a level, a registry, saved data, configuration, networking or a
   server reference; nothing starts a thread, schedules work, logs a warning or allocates a large structure; nothing
   depends on an order that startup could break. Name any class whose initializer is not plainly inert.
2. **Where it runs.** `ServerStartedEvent` on the FORGE bus fires for the dedicated server and the integrated server;
   no class on the list references `net.minecraft.client` or another side-only class; a missing class fails loudly in
   tests and what happens in production if one is renamed (the server must not fail to start in a surprising way, or
   the failure must be deliberate and stated).
3. **The list.** It is W212's measured named-class inventory, the test that keeps it in step is real (fails when the
   relaunch path uses a new mod class or the list names a stale one), and the resource and the code list cannot drift.
4. **No behaviour change.** No launch code runs, no state is read or written, no authority, ordering, proof or
   acknowledgement changes; the startup cost and memory W215 reports, and that they land off player-facing ticks.
5. **The measurement.** Recompute the before and after tables from the retained evidence; same harness, entry points
   and JAR provenance; nothing subtracted; the two remaining full-observation failures stated as failures.
6. **Nothing weakened:** bounds, budgets, timeouts and assertions; the changes to `scripts/run_w206_warmup.ps1` and
   `scripts/w212_instrument.py` are harness-only.
7. **Risk text:** any sentence of R-035 or docs that this makes wrong, for Root.
8. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`) and W211 (`codex/v1.8.0-w211`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: `EscrowRelaunchClassesTest`, `W212TimingTest`
and the `resourceescrow` package; the W212 and W215 Python tests; the summary scripts on the retained evidence. Targeted
unit runs need no heavy-JVM lock. Check 6 GiB free memory first. No GameTest, native case, runData, full build or
server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W215-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
