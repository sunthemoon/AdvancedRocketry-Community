# TASK W362: per-fluid signed accounting, Slice B (saved form and migration)

Root: Claude. Date: 2026-11-13. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w362`, branch `codex/v1.8.0-w362`: the shared head with W361's Slice A (`f820aeecc`) merged in |
| Decision | Root decision R443 (QUEUE row W362) |
| Design | [W357-DESIGN](records/W357-DESIGN.md), sections 4.1, 4.2 (with the four migrator changes), 4.3, 5 and 7.1, and section 8 row B. Root's answers are in its section 10 and in R443 below. Slice A's record is `records/W361.md`. |
| Record | `docs/work/v1.8.0-root-queue/records/W362.md` |
| Write scope | The files of design section 8 row B: `EscrowLedger`, `EscrowCodec`, `EscrowOperation`, `EscrowReceiptCodec`, `EscrowSettlementIntent`, `EscrowSettlementTombstones`, `EscrowSettlementCapacity`, `EscrowLedgerWriter`, `EscrowLedgerBaseline`, `ManagedSavedDataType`, `SavedDataSchemaMigrator`, `ManagedSavedDataPayloadValidator`, `EscrowFlightBenchmarkCeiling`, Slice A's two classes where the saved form needs them, one new activation-constant class, their unit tests, and the record. No GameTest, script, register, ADR, status file or QUEUE change. |

## What to do

1. **The saved form of design section 4.1.**
   - Ledger root 3, with its epoch and the required `fluid_accounts` list.
   - Fluid operation schema 3 with `accounting_baseline`.
   - Intent schema 3 with the commitment field, named `account_commitment`. Use the mixed-component rule of
     sections 2.3 and 4.1.
   - Fluid receipt schema 4 with `accounting_digest` (wire key `f1b`) and `accounting_status`.
   - Item, floor and legacy forms stay as they are.
   - Every lock and release predicate in `EscrowSettlementTombstones` uses one shared settlement-receipt test that
     accepts schemas 3 and 4 alike (W357R finding 3).
2. **Bounds (R443).** The account row ceiling is 2,048 bytes, depth 5, 65 nodes, with at most 14 members in the member
   list. Use Slice A's codec with these limits. Everything else as in design section 5.
3. **Capacity.** Extend `EscrowSettlementCapacity` as section 5 says: charge each account row at the ceiling, count an
   operation and its receipt once at O3, and preserve every existing charge.
4. **Migration (section 4.2 and W357R finding 1).**
   - A dedicated escrow branch in `SavedDataSchemaMigrator`.
   - The root-2 and root-3 epochs pinned separately, with the migrator checking the root-2 epoch.
   - `supportsSchema` exactly 2 and 3 for the escrow ledger.
   - The baseline check split: the source file is compared with the pinned root-2 re-encoding, and the migrated root
     is validated on its own.
   - Lossless: the old embedded encodings and digests are kept, and old live fluid records are held as legacy UNKNOWN.
   - A target that does not fit is refused at pre-start with the original preserved, and so is `BACKUP_LIMIT`.
   - A root-1 file stays refused.
5. **Activation constant.** Add one constant that gates the new format: while it is false, the writer keeps emitting
   root 2 and no world migrates. Add a test that pins it false. The new codecs and the migration are exercised by unit
   tests that call them directly. Root turns the constant on in a separate reviewed commit after Slice D merges.
6. **Re-pins (R441 and R443).** In tests that build the new form explicitly (the constant stays off), use the values
   Root accepted:
   - `EscrowCargoCapacityTest`: 55 and 53 admitted, and 54 records after the floor retirement;
   - `EscrowFlightBenchmarkCeilingTest`: a blob of 7,113 bytes with 7 more on the last record, and the root still
     exactly 1,048,576 bytes.
   Keep the existing root-2 tests and their pinned values unchanged while the constant is off. Measure the other pins
   that W361's record section 3.6 lists (the largest admitted cargo blob, the 512-node case, the mixed-component
   blob, the loader floor cuts) with your real codecs. Report each with its old and new value. Do not re-pin any of
   them before Root accepts the number.
7. **Tests.** The format and migration tests of design section 7.1:
   - root-2 fixtures stay byte- and digest-faithful;
   - root-3 round trips;
   - each lock predicate gives equal results on a schema-3 and a schema-4 receipt;
   - both mixed-component cases;
   - reservations, and a failed or UNKNOWN migration write preserving the original;
   - a full target refused;
   - `BACKUP_LIMIT`;
   - root 1 refused.
8. **Key digest.** Confirm Slice A's key digest choice (record section 5) before any row can be saved, or change it
   and say why.

## Rules

- Do not delete tests, loosen assertions, raise timeouts or change a ceiling (AGENTS.md section 5). A pinned value
  changes only as item 6 says.
- Run the targeted tests and the full unit suite with `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Report the totals.
- Commit on your branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- A separate read-only session reviews this slice before it merges (AGENTS.md section 13).
- The record gives what was done, the commits, the commands and results, every new measurement with its old and new
  value, and anything not done. Its last line, and the last line of your answer, is the path of `records/W362.md`.
