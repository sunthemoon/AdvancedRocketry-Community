# TASK W328FR: read-only review of W328F, with W328 and W329

Root: Claude. Date: 2026-11-09. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: you
change no checkout.

| Item | Value |
|---|---|
| Checkout | `D:/GitHub/arce-v180-w328fr`, detached at W328F's delivered commit `bdb4371ef`, created by Root. It contains W328 (to `41ebd5485`), W329 (to `da3c2a010`) and W328F (`6af60012e..bdb4371ef`). |
| Decision | Root decisions R396, R397 and R399 (QUEUE row W328FR). AGENTS.md section 13 requires a read-only review by another session before a persistence or startup change merges. |
| Read first | The first review `D:/GitHub/ARCE-Task-Evidence/root-queue/W328R/REVIEW.md`; in the checkout, `docs/work/v1.8.0-root-queue/TASK-W328F-PRESTART-ORDER-AND-HINT.md` and the records `W328F.md`, `W328-DESIGN.md`, `W328.md`, `W329-DESIGN.md` and `W329.md` under `docs/work/v1.8.0-root-queue/records/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W328FR/REVIEW.md`; logs and scratch files under `D:/GitHub/ARCE-Task-Evidence/root-queue/W328FR/` |

## Why

The first review (W328R) found no data-loss path but asked for changes. W329's `d0d9f02eb` closed F1, and Root
reviewed it. W328F fixes F2 and the notes, and corrects the design text for F3. Root reviewed W328F's diff. This
second, independent review decides whether W328, W329 and W328F can merge together.

## Check

1. **Pre-start order (F2).** Every managed path's metadata checks (the safe child path, the no-follow presence check,
   the regular-file check) run before any managed file is decoded. Nothing else in the migration changed: backup,
   staging, commit and the diagnostic codes. The new read seam is reachable only through the package-private test
   constructor, and production uses `BoundedSavedDataIo::read`. Judge the new diagnostic order, where a later unsafe or
   unknown path is reported before an earlier malformed file. Does the ordering regression prove that no file was read,
   and does it fail without the preflight?
2. **The storage hint.** Only a failed status check sets the typed marker, and the marker survives `forType`. The
   storage instruction leads in all three branches (rejoin, escrow, migration) and keeps today's precedence and
   whole-world restrictions. Every other refusal keeps exactly today's text, including the satellite-bounds line.
   The escrow line stays one bounded line without a stack trace.
3. **The lookup guard.** Off the server thread or without an Overworld, `EscrowLedgerLookup.of` answers NOT_LOADED and
   never touches storage. Check that no production caller runs off the server thread, or before the Overworld exists,
   and expected LOADED or UNCLASSIFIED there. The relaunch dependency audit still passes without an inventory change.
4. **The manifest filter (TASK item 4, not done).** W328F found no unit test possible without a new production seam.
   Is that right? Is the existing evidence enough (`RocketReleasePlanTest` and `EscrowLedgerLookupTest`), or should
   Root record a residual or queue the bytecode check that W328F suggests?
5. **The fence WARNs (F3).** Does the new `RocketTransferFencesTest` case pin the real behaviour? Check the corrected
   bound against the code: at most two WARNs per record per 1,200 ticks under NOT_LOADED, and no per-session total.
   Check the bay-claim repair WARN description too: its trigger, its latch, 256 claims and 32 steps per tick.
6. **The labelled corrections.** In all four notes, each correction is accurate and keeps the original sentence. Do
   they cover everything that W328R's F2, F3 and check 9 asked for? Root copies the corrected text into the risk
   register, so name any sentence that is still wrong.
7. **The three together.** Look for interactions between W328, W329 and W328F that neither review saw, especially at
   startup (the pre-start check, `EscrowLedgerStartup`, the lookup's classification flag) and in the protective
   reads. W329's delta after `237424e33` is `d0d9f02eb` (the relaunch audit) and `da3c2a010` (its record).

Give a verdict for each change (W328, W329, W328F): APPROVED or CHANGES REQUIRED, with findings ranked by severity,
each with a file and line, a concrete failure scenario, and a fix. Do not repeat W328R's closed findings unless they
are not actually closed.

## Run

- The affected unit tests alone: `*.persistence.migration.*`, `*.UnknownPresenceStartupTest`,
  `*.PresenceUnknownHintTest`, `*.EscrowLedgerStartupTest`, `*.EscrowLedgerLookupTest`, `*.EscrowRelaunchClassesTest`,
  `*.BayReceiptsStartupReasonTest` and `*.rocket.server.RocketTransferFencesTest`.
- The full unit suite (`gradlew.bat --offline test`), under the heavy-JVM lock.
- The selected GameTest batches under the lock, with
  `JAVA_TOOL_OPTIONS=-Dadvancedrocketrycommunity.gameTestBatches=w329_ledger_not_loaded,w329_ledger_no_file,escrow_core,fluid_fuel_loader_creation,satellite_bay_receipts`.
  Report the log checker's missing expectations from the unselected batches, and nothing else, as W328R did.
- Any probe you need, in your own scratch directory under the output folder, never in the checkout.
- `python -B scripts/validate_repository.py --require-approved-identity`.

Take the lock per step (backslash lock path, as WORKER-RULES says) and release only your own token; W330 and other
workers share it. Call Python by its full path in any detached script. Before you finish, delete your own
`build/gametest/world` and any scratch trees, and confirm with `git status` that the checkout is unchanged.

## Not in this task

Editing, committing or pushing anything; the risk register; the full GameTest run (the hosted sharded run follows);
other escrow, bay or cargo areas beyond what these changes touch.
