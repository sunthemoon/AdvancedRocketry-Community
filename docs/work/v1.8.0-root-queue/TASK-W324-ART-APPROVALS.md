# TASK W324: record the owner's approval of the W275 and W277 art records

Root: Claude. Date: 2026-11-08. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w324`, branch `codex/v1.8.0-w324`, created by Root from the shared head |
| Authority | [OWNER-DECISIONS-20261108](records/OWNER-DECISIONS-20261108.md): the owner's art verdict, "Approve both in full", and Root decision R383 |
| Precedent | [TASK-W276](TASK-W276-D7-APPROVALS.md) and [its record](records/W276.md), which added the approval mechanism and used it for 30 records |
| Record | `docs/work/v1.8.0-root-queue/records/W324.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W324/` |

## What the owner decided

On 2026-11-08 the owner's visual and originality review (D7, ADR-061 section 4.5) passed both remaining pending
provenance records in full: `docs/provenance/v1.8.0-w275-stand-in-art.json` and
`docs/provenance/v1.8.0-w277-stand-in-art.json`, as they stand at the shared head `e07b09627`.

## What to do

1. **Use W276's mechanism.** Read how W276 recorded the approval of the 30 records (its record, its commit `7a175a5c9`,
   `scripts/validate_v180_provenance.py`, `tests/test_v180_d7_approvals.py` and one of the 30 approved records, for
   example `docs/provenance/v1.8.0-gas-charge-pad-new-art.json`).
2. **Record the approval** in both records: status `APPROVED`, reviewer `sunthemoon`, `reviewed_at` `2026-11-08`, the
   `content_digest` unchanged and still binding the record, and a basis citing
   `docs/work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261108.md`, in the same form W276 used. W277's pending review
   already has a `basis` saying the review is outstanding; replace it. W275's record has a top-level
   `human_visual_and_originality_gate` of `OPEN`: if the schema or a validator gives it a closed value, set that value;
   otherwise leave it and state why in your record. Change nothing else in either record.
3. **Tests.** Tests that assert these two records are pending (at least `V180RemainingStandInArtTest`,
   `V180StandInArtTest`, `V180W277ProvenanceAssertions`, `V180ClosureProvenanceTest` and
   `tests/test_v180_d7_approvals.py`, which may assert that W275 stays pending) now assert the exact approval, as W276
   did for the 30. Do not loosen any other assertion. No other provenance record changes.

## Verification

The provenance, importer and ledger Python suites; `scripts/validate_v180_content_ledger.py` in normal,
`--require-accepted` and `--closure` modes; the Java datagen tests above; `./gradlew build` with all unit tests;
`python -B scripts/validate_repository.py --require-approved-identity`. A full GameTest run is not needed: no code,
asset or model changes. If you run any heavy JVM task, take the heavy-JVM lock as WORKER-RULES says (backslash path).

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Any other provenance record; the art itself; authorship findings (W279); the asset plan; the ledger, the status file
and QUEUE.
