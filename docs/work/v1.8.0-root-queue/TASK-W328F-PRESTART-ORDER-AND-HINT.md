# TASK W328F: pre-start check order, the storage hint and the lookup guard

Root: Claude. Date: 2026-11-09. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w328f`, branch `codex/v1.8.0-w328f`, created by Root from W329's delivered commit `da3c2a010` (W328 and W329 are both on your base), with this TASK merged in |
| Decision | Root decisions R396 and R397 (QUEUE row W328F), from the independent review `D:/GitHub/ARCE-Task-Evidence/root-queue/W328R/REVIEW.md` |
| Record | `docs/work/v1.8.0-root-queue/records/W328F.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W328F/` |
| Read first | The review above (findings F2 and F3, checks 1, 5 and 9), `records/W328-DESIGN.md`, `records/W328.md`, `records/W329-DESIGN.md` and `records/W329.md` |

## Why

The review of W328 and W329 found no data-loss path, but it asks for changes before the merge. F1 is closed by W329's
`d0d9f02eb`. This task fixes the rest. It is a persistence and startup change, so another engine reviews it read-only
before Root merges W328, W329 and this task together.

## What to do

Each item is its own commit, labelled with the item.

1. **Pre-start order (F2).** `WorldDataMigrationService.discoverSources` checks a managed file's presence, then reads,
   migrates, validates and hashes it, before it checks the next file. The design promised that every status check runs
   before any file is read. Add a metadata-only preflight over every managed path (safe child path, the no-follow
   presence check and the regular-file check) that runs before any file is decoded; then read only the files proven
   present. The existing first checks (the rejoin sidecar and the data directory) stay first. Nothing else in the
   migration changes: no new diagnostic code, no change to backup, staging or commit.
   - Add an ordering regression: a malformed earlier root (for example the celestial file) plus an injected UNKNOWN
     for a later managed file (for example the bay receipts). Startup must refuse with the later file's
     unknown-presence diagnostic, and the earlier file must not have been read. Prove the not-read part directly
     (for example through a read seam or an observable that decoding would change), not only by the diagnostic type.
   - Show in a scratch copy that the regression fails with the preflight removed, then discard the scratch copy.
2. **The storage hint (review check 1).** When startup is refused because a status check failed (an unknown presence
   for a managed file or the data directory), the logged hint must first tell the operator to fix storage access to
   the world folder and restart, and not to delete, move or restore single files. Keep the existing hint after it, so
   an escrow or rejoin world still keeps its whole-world restrictions. Today the rejoin hint wins when the data
   directory's status is unknown, because the rejoin file's status is then unknown too; keep that precedence for the
   restrictions, but lead with the storage instruction. Tell an unknown-presence refusal apart by a typed marker on the
   exception, not by parsing its message. The diagnostic code stays `ARCE-BETA-2000`. Unit tests cover each hint path
   (rejoin, escrow or receipts, plain migration) with an unknown presence, and show that refusals for other reasons
   keep exactly today's text.
3. **The lookup guard (review check 5).** `EscrowLedgerLookup.of` calls vanilla storage
   (`ResourceEscrowSavedData.existing`) before it checks the thread and the Overworld. Check first: off the server
   thread, or without an Overworld, answer NOT_LOADED without touching storage. Add a unit test that shows no storage
   access happens in those cases. If this changes the classes reachable from the relaunch path, update
   `src/test/resources/relaunch-existing-dependencies.txt` with an audit note, as W329's `d0d9f02eb` did. Do not
   change the measured preload list or any assertion.
4. **The pre-classification manifest filter (review check 5).** Before ServerStartingEvent the lookup answers
   UNCLASSIFIED, and `RocketReleaseAccess.joined` then filters already-released passengers out of the manifest
   immediately. That is existing behaviour, kept on purpose. Add a regression that pins it: under UNCLASSIFIED a
   join removes only passengers whose release is already recorded, and moves no fuel or cargo and grants no transfer.
   Use a unit test if `RocketReleaseAccess` allows one without a new production seam. If it does not, do not add a
   seam; say so in the record with the reason.
5. **Labelled corrections in the design notes and records (F2, F3 and check 9).** In `W328-DESIGN.md`, `W328.md`,
   `W329-DESIGN.md` and `W329.md`, add a correction labelled `Correction (W328F, 2026-11-09)` at each place that the
   review shows to be wrong or incomplete. Keep the original sentence and say what holds instead:
   - the pre-start order, as fixed by item 1;
   - the hint precedence, as fixed by item 2;
   - the transfer-fence WARN bound. The fence WARNs are deduplicated against the record's previous reason, not once per
     record (`rocket/server/RocketTransferFences.java`, about lines 158 to 169 and 254 to 261), so a reason that
     alternates (for example `ENDPOINT_JOIN_PROOF_REQUIRED` and `ESCROW_PENDING_OR_TOMBSTONE`) repeats them. State the
     real bound from the code with references: what triggers a transition, the reevaluation budget (two records per
     tick) and the per-record interval (1,200 ticks). The assembly fence's once-per-transaction rule is a separate
     mechanism; do not generalize it to transfer fences;
   - the bay-claim repair WARNs, which the design omitted, reachable when the neutral-source guard refuses a bay
     resume; give their trigger and rate the same way;
   - the pre-classification manifest filter (item 4) as a stated exception to the reason that every action rechecks
     later;
   - the difference between the hold not writing the ledger and normal world saving, which still writes other files.
   Also add a unit test that pins the transfer-fence transition behaviour (a stable reason logs once; an alternating
   reason logs on each change), if the existing fence tests allow it without a production seam.

Root decision R397 for F3: describe the real WARN behaviour; do not change the deduplication. NOT_LOADED is reached
only after a failed status check or load, the per-record reevaluation is throttled, and the fence text is pinned by
existing log expectations. A stronger deduplication can be a later task if the native evidence shows a need.

## Verification

- The new and affected unit tests alone: `*.persistence.migration.*`, `*.UnknownPresenceStartupTest`,
  `*.EscrowLedgerStartupTest`, `*.EscrowLedgerLookupTest`, `*.EscrowRelaunchClassesTest`, the hint tests and any fence
  test you add.
- `./gradlew build` with every unit test, under the heavy-JVM lock.
- The selected GameTest batches under the lock, with
  `JAVA_TOOL_OPTIONS=-Dadvancedrocketrycommunity.gameTestBatches=w329_ledger_not_loaded,w329_ledger_no_file,escrow_core,fluid_fuel_loader_creation,satellite_bay_receipts`.
  The log checker then reports the unselected batches' expectations as missing; report that and nothing else. The full
  GameTest run is the hosted sharded run after Root pushes, so do not run it locally.
- The scratch-copy failure check for item 1.
- `python -B scripts/validate_repository.py --require-approved-identity`.

W325 and W330 also take the heavy-JVM lock, so wait your turn (backslash lock path, as WORKER-RULES says). Call Python
by its full path in any detached script; a `python` shim stopped W328's script before its lock release.

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

The risk register, the ADR, the status file and QUEUE (Root writes the register text from your corrected notes);
changing the transfer-fence deduplication or any pinned log text other than the new storage hint; any other escrow
read; production admission; the satellite bay and cargo harness work (W325, W330).
