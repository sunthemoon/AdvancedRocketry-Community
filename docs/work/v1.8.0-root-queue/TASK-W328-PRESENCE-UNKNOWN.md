# TASK W328: unknown file presence for the bay receipts and the escrow ledger

Root: Claude. Date: 2026-11-09. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w328`, branch `codex/v1.8.0-w328`, created by Root from the shared head |
| Decision | Root decisions R390 and R391 (QUEUE row W328), from W327's audit `D:/GitHub/ARCE-Task-Evidence/v18-integrated-20261109/risk/AUDIT.md`, findings L1, L2 and L5 |
| Record | `docs/work/v1.8.0-root-queue/records/W328.md` and the design note `records/W328-DESIGN.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W328/` |

## The findings

- **L1.** `BayReceipts.start` (`resourceescrow/BayReceipts.java:51-57`) enters NOT_LOADED both when the receipt file
  exists but did not load and when its presence is UNKNOWN (a status check that fails with anything other than
  `NoSuchFileException`, `persistence/migration/CheckedSavedDataFile.java:93-108`). In both cases the WARN says "the
  receipt file exists but did not load", which misleads an operator when no file exists.
- **L2.** The escrow ledger, which the four cargo kinds share with the fuel loader, decides presence with
  `Files.exists` (`resourceescrow/EscrowRuntime.java:277`; the pre-start check in
  `persistence/migration/WorldDataMigrationService.java:118-121` does the same for every checked file). So an unprovable
  path becomes NO_FILE rather than an unknown state. The first fill or cargo admission would then create a ledger; its
  first write checks the path again (`CheckedSavedDataFile.java:48`, `:111-116`), but if the failure has cleared by
  then, the new ledger could replace an existing one through `REPLACE_EXISTING` (`:28-29`). This is latent while
  production admission is off, and must be closed before any cargo switch is enabled.
- **L5.** The Javadoc at `rocket/server/BayTransferRecovery.java:228-232` says "REPAIR adds no ticket", but a
  checkpoint claim held for REPAIR still renews its origin and destination tickets (`:243-249`).

Root decision R390 keeps NOT_LOADED fail-closed for an unknown bay-receipt presence: an unprovable state must not be
read as absent. This task does not change that policy.

## What to do

1. **Design note first** (`records/W328-DESIGN.md`, one page, committed before the code): what the escrow ledger and
   the pre-start check do when presence is UNKNOWN. Root's direction is that UNKNOWN is never treated as absent: the
   escrow ledger stays unavailable for the session (no ledger is created, escrow and cargo admission and reconciliation
   refuse as they do for an unloadable ledger), with one bounded WARN naming the file and the reason, and a restart
   that proves presence or absence clears it. For the pre-start check, say whether an unknown status should refuse
   startup, as a future or invalid root does, or be passed on as UNKNOWN to the store's own start; recommend one, with
   its scope (object, Level or server), duration, logs and recovery, as AGENTS.md section 3.3 asks for protections that
   refuse work. Include the proposed risk-register text; Root edits the register.
2. **Implement** the design, L1's two distinct WARN reasons (keep the `ARCE_BAY_RECEIPTS_NOT_LOADED` token; add a
   reason, for example `reason=LOAD_FAILED` and `reason=PRESENCE_UNKNOWN`), and L5's corrected Javadoc. If the GameTest
   log manifest lists the old WARN text, update only that entry.
3. **Tests.** Unit tests with real files and an injected status failure for the bay receipts and the escrow ledger:
   UNKNOWN never yields NO_FILE, creates no file and replaces nothing, and a later clean start loads the existing file
   unchanged. A GameTest or unit test for the bay's NOT_LOADED from UNKNOWN presence and its WARN reason. Each new test
   fails without its fix.

## Verification

The new and affected test classes alone, then `./gradlew build` with all unit tests; the full GameTest run with
`scripts/check_gametest_log.py` under the heavy-JVM lock (backslash lock path, as WORKER-RULES says; W325 and W326 hold
it per runner invocation, so wait your turn); `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. A separate read-only session reviews the change before merge (AGENTS.md section 13:
persistence and save refusal). Root pushes for a sharded run and merges.

## Not in this task

The NOT_LOADED policy itself (R390); production admission; the receipt or ledger formats and schema versions; the
risk register, the status file and QUEUE; the native runs of W325 and W326.
