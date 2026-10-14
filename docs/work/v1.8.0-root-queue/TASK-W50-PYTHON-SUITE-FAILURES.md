# TASK W50: three failing Python tests on the shared branch (parallel bug)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w50-python-suite` (from the shared branch) |
| Branch | `codex/v1.8.0-w50-python-suite` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W50.md` (new) |
| Source | `records/W48.md` lines 84-95: the full Python suite (1,418 tests) has three failures on the shared branch that predate W48 |

Hosted CI does not run the full Python suite, so these three slipped through.

1. **`tests/test_generate_v002_g0_evidence.py`,
   `test_generated_evidence_is_complete_deterministic_and_portable`.** The packaged-source list lacks
   the ten `station/notice` Java files that W22 added.
2. **`tests/test_validate_v090_resources.py`, `test_repository_resource_contract_passes`.** The
   translation placeholder contract differs for `station.advancedrocketrycommunity.notice.warp_countdown`.
   W45 saw the same failure in `scripts/validate_v090_resources.py`.
3. **`tests/test_validate_v180_content_ledger.py`, `test_owning_units_are_not_delivered_after_the_asset`.**
   The expected error about `entity:ARSpaceElevatorCapsule` is no longer produced. Root delivered that
   ledger row with W46 (`5c3a0ad2`), and the test used the PLANNED row as its fixture.

## What to do

For each failure:
- Find the cause and state it with evidence.
- Fix the cause, not the symptom. For example:
  - regenerate or extend the packaged-source list by the script that owns it;
  - correct the placeholder contract on whichever side is wrong (the language entry, or the
    validator's expectation), and say which side and why;
  - make the ledger test build its own fixture row, or pick a unit that is still PLANNED, so the
    next delivery cannot break it again.
- Keep each test's intent and every assertion. Do not delete or skip a test.

Then run the **whole** Python suite once, and record its count and its result.
- Use a long `TEMP` path, because the 8.3 short path breaks five validator tests on Windows (see
  W48's record).
- It takes about an hour.

## Commands

- The three tests alone, then the whole Python suite once.
- `git diff --check`.
- No Gradle run is needed unless a fix touches Java or generated data. If one does, run the
  affected Gradle task under the lock and say why.

## Not in this task

- Adding the full Python suite to CI (Root decides that separately).
- Status files and the ledger rows themselves (Root).
