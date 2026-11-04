# TASK W276: record the owner's D7 approval of the 30 v1.8 provenance records

Root: Claude. Date: 2026-11-04. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w276`, branch `codex/v1.8.0-w276`, created by Root from the shared head |
| Authority | [OWNER-DECISIONS-20261104](records/OWNER-DECISIONS-20261104.md): the owner's D7 answer ("通过") and Root decision R306 |
| Record | `docs/work/v1.8.0-root-queue/records/W276.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W276/` |
| Also | W274R's finding L1 (`D:/GitHub/ARCE-Task-Evidence/root-queue/W274R/REVIEW.md`) |

## What the owner decided

On 2026-11-04 the owner's provenance and licence review (D7, ADR-061 section 4.5) passed the 30 v1.8 records that
said `PENDING_HUMAN_REVIEW` at `521cb9b95`. The decision record lists them: the upstream imports
`v1.8.0-c18d-k1`, `-k2`, `-k3`, `-k4`, `-k6`, `v1.8.0-c18d-w244`, `v1.8.0-w246-flat-textures`,
`v1.8.0-w246-rebound-item-models`, `v1.8.0-w249-motor-models`, `v1.8.0-w255-bipropellant-motor-models`,
`v1.8.0-w256-saw-model`, `v1.8.0-w258-nuclear-motor`, `v1.8.0-w260-fuel-tanks`, `v1.8.0-w261-landing-pad`,
`v1.8.0-w265-laser-gun` and `v1.8.0-w265-rebound-item-model`; and the NEW-art records `v1.8.0-c16d-acquisition`,
`v1.8.0-c17a-fluid-fuel-loader`, `v1.8.0-c18b-jackhammer`, `v1.8.0-c18b-laser-gun`, `v1.8.0-c18d-new-art`,
`v1.8.0-c18d-presentation`, `v1.8.0-cargo-tank-tag`, `v1.8.0-circuit-plates`, `v1.8.0-control-board`,
`v1.8.0-gas-charge-pad-new-art`, `v1.8.0-item-io-board`, `v1.8.0-liquid-io-board`,
`v1.8.0-tracking-circuit-and-rebalance` and `v1.8.0-w53-art-redraw`. The approval covers the records as they stood
then. `v1.8.0-w275-stand-in-art` (W275, later) is not covered and stays pending.

## What to do

1. **Find the approval mechanism.** Read ADR-061 (sections 4.5 and 4.8), the provenance schema
   (`docs/provenance/schema-v1.json`: `review` with `status`, `content_digest`, `reviewer`, `reviewed_at`,
   `findings`), `scripts/validate_v180_provenance.py`, the import tools under `tools/import/` and their `verify`
   commands, and how records with `review_history` (for example `v1.8.0-gas-charge-pad-new-art.json`) were revised.
   The import `verify` commands reproduce the records byte for byte, so a hand edit of the review block may break
   them; use or extend the mechanism the tools provide for recording a human review, and if none exists, add the
   smallest one (for example, a review overlay that `verify` applies) with tests, rather than weakening `verify`.
2. **Record the approval** in each of the 30 records: status `APPROVED`, reviewer `sunthemoon`, `reviewed_at`
   `2026-11-04`, the `content_digest` the schema and validators require, and a finding or basis citing
   `docs/work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261104.md`. Change nothing else in a record. Where a later
   change superseded part of a record (for example W275's note on the precision assembler model), approve the record
   as it stood and keep the supersession as it is.
3. **W274R's L1.** `scripts/validate_v180_content_ledger.py` (about lines 535 to 544) and
   `docs/work/v1.8.0-w268-review-closure/adopt_findings.py` (about lines 35 to 37) accept `"2026-11-03\n"` as a review
   date, because the date check's pattern ends in `$`. Require a strict full match of the date format before the
   preparation-date comparison in both, and add the final-newline negative case to the tests.

## Verification

The provenance, importer and ledger Python suites; every import `verify` (as W274's record lists them, with the pinned
upstream and both vanilla clients); `scripts/validate_v180_content_ledger.py` in normal, `--require-accepted` and
`--closure` modes; `python -B scripts/validate_repository.py --require-approved-identity`. If any Java or resource test
reads review status, run it too.

Commit on the branch; do not push. Root reviews, has a separate session review the approval change (it records an
owner decision in 30 files), pushes for a sharded run and merges.

## Not in this task

W275's record and any other pending record; authorship findings (W279); the asset plan, allowlist and origin
findings beyond L1's guard; the ledger, the status file and QUEUE.
