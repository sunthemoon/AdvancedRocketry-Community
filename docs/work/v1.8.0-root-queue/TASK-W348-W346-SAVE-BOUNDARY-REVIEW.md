# TASK W348: read-only review of W346's save-boundary control and native case

Root: Claude. Date: 2026-11-10. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and an independent
reviewer: you did not write this code.

| Item | Value |
|---|---|
| Checkout | `D:/GitHub/arce-v180-w348-review`, a detached checkout of `codex/v1.8.0-w346` at `c4cabd653`, created by Root; read only |
| Scratch | `D:/GitHub/ARCE-Task-Evidence/root-queue/W348/` for probes, logs and any scratch export |
| Decision | Root decision R418 (QUEUE row W348); W346 adds a gated control that stops a Level's saves, so it needs a separate read-only review before merge (TASK-W346, Review) |
| Read first | `records/W346-DESIGN.md`, `records/W346.md`, `records/W342-DESIGN.md` section 4 and `TASK-W346-B65-NATIVE-SAVE-BOUNDARY.md` in the checkout; W346's evidence under `D:/GitHub/ARCE-Task-Evidence/root-queue/W346/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W348/REVIEW.md` |

## Review questions

1. **The gate.** Is `BaySaveBoundaryReleaseTestCommands` reachable only with the release-test property, permission
   2 and `EscrowTestEndpoints.requireEnabled`, each checked before any dimension is resolved or any state is read or
   changed? Does the gate test fail if any one of the three is removed? Probe it with scratch mutants, as W340 did.
2. **No production effect.** Does any production path read the held set or depend on the new class? Is `noSave`
   cleared by a clean stop, as the design says (check vanilla `stopServer` in the javap logs)? Can a held Level stay
   held across a restart?
3. **The save-path claims.** Check W346-DESIGN's bytecode claims against W338's javap logs: that
   `saveEverything(true, false, false)` skips a held Level entirely, that the chunk-unload path stores entities even
   when `noSave` is set, and that the destination chunk keeps its tickets until the kill in this case. Check in the
   repository that nothing retires a COMMITTED T' while the destination exists only in memory, within the window this
   case uses.
4. **The native oracle.** Does `scripts/run_v180_bay_save_boundary.py` prove the state at the kill (no rocket of L at
   either end on disk, committed T' with consumed snapshots in the journal file, the registry, no receipt) from the
   stopped world, and both restarts as TASK-W346 item 3 requires? Does it read anything that `noSave` stopped as
   evidence? Run its unit tests and, with fakes, show whether each required check rejects a wrong value.
5. **The M1 control.** Does W346's scratch evidence show that the native case fails when recovery never chooses
   REBUILD_DESTINATION, for the right reason?
6. **The driver.** Does the `forced-W346-R` family pass the runner's own argument parser and keep the existing
   families and their order?

## Rules

Read only: do not edit, commit or push in the checkout. Write probes and scratch exports only under the scratch
directory and delete server copies when done. A native run, if you need one, uses the heavy-JVM lock (backslash
lock path, as WORKER-RULES says). Number each finding, give its evidence (file and line, command and output) and say
whether it blocks the merge.

## Not in this task

Changing code; W345's families; other NOT_COVERED rows.
