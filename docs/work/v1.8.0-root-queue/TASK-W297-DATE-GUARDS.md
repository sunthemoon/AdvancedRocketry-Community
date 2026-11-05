# TASK W297: review-date guards accept only canonical ASCII dates that exist

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w297`, branch `codex/v1.8.0-w297`, created by Root from the shared head |
| Decision | Root decision R335 (QUEUE row W297), from W276R's finding M1 |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W276R/REVIEW.md` (finding M1) and its `logs/date-guard-probe.log` |
| Record | `docs/work/v1.8.0-root-queue/records/W297.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W297/` |

## The defect

The review-date checks in `scripts/validate_v180_content_ledger.py` (about lines 82 and 535),
`docs/work/v1.8.0-w268-review-closure/adopt_findings.py` (about line 36) and `tools/import/import_v010_assets.py`
(about line 248) use `\d`, which in Python matches any Unicode decimal digit, and do not check that the date exists.
So a finding or review with `reviewed_at` `"２０２６-１１-０３"` (full-width digits, as a Chinese input method types
them), Arabic-Indic digits, or `"2026-13-45"` passes, and the full-width form of W268's preparation date skips the
preparation-date refusal.

## What to do

1. Find every review-date or decision-date check of this kind in `scripts/`, `tools/` and the W268 helpers (search for
   the pattern), not only the three above.
2. Make each accept only a string of exactly `[0-9]{4}-[0-9]{2}-[0-9]{2}` in ASCII that round-trips through
   `datetime.date.fromisoformat(s).isoformat() == s`, before any other comparison (such as the preparation-date
   refusal). Share one helper if the modules already share code; otherwise keep each change local.
3. Add negative tests to the existing suites for the full-width preparation date, Arabic-Indic digits, `2026-13-45`,
   `2026-02-30`, and the earlier cases (a final newline, CRLF, spaces, `2026-11-4`), and keep the positive cases.
4. Run every import `verify` and the ledger validator to show the tracked records and findings still pass.

## Verification

The changed suites (ledger, provenance, importers, adopt-findings, the W268 proposal suite); every import `verify` (as
W274's and W276's records list them); `scripts/validate_v180_content_ledger.py` in its three modes; `python -B
scripts/validate_repository.py --require-approved-identity`. No Java or Gradle work is needed.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Record contents, the asset plan, allowlist and findings, the ledger, the status file and QUEUE.
