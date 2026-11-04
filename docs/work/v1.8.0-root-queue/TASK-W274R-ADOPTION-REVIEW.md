# TASK W274R: independent review of W274's ADR-062 revision 7 adoption

Root: Claude. Date: 2026-11-04. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
you did not write this change. Review only; do not edit, commit or push anything in any checkout.

| Item | Value |
|---|---|
| Change under review | Branch `codex/v1.8.0-w274` at `318137677` (merge base with the shared branch `5c7ed00a9`), checked out read-only at `D:/GitHub/arce-v180-w274r` |
| Implementer's record | `docs/work/v1.8.0-root-queue/records/W274.md` on that branch; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W274/` |
| Task it implements | [TASK-W274](TASK-W274-REVISION-7-ADOPTION.md) |
| Authority | [OWNER-DECISIONS-20261104](records/OWNER-DECISIONS-20261104.md) (on the shared branch; W274's copy predates two later sections Root added) |
| Your output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W274R/REVIEW.md`, plus your logs in that folder |

## What to check

1. **Scope against the owner's words.** Exactly the 95 files of W268's proposal change from REVIEW to EXCLUDE, G7's
   ten OGGs included, and nothing else in the asset plan, allowlist or findings changes. Recompute the counts (365
   EXCLUDE, 0 REVIEW, 152 allowlist entries) and the allowlist SHA-256 pinned in ADR-062 yourself.
2. **Findings.** The 95 added findings in `docs/provenance/v1.8.0-origin-findings.json` are EXCLUDED decisions, name
   `sunthemoon`, 2026-11-04 and the decision record, carry no draft markers, and claim no authorship clearance. The
   32 earlier findings are byte-for-byte unchanged.
3. **ADR-062 revision 7.** Its text matches what the owner decided (G1 to G6 and G8 as proposed, G7 excluded for the
   NEW audio) and claims no Gate, D7 or V1 approval.
4. **The validator and tests.** The new guards in `scripts/validate_v180_content_ledger.py` reject what they say
   they reject; every original assertion in `tests/test_validate_v180_content_ledger.py` is unchanged; the beacon
   fixture route matches the proposal. Run the ledger, provenance, importer and proposal suites the record lists,
   the ledger validator in its normal, `--require-accepted` and `--closure` modes, and
   `python -B scripts/validate_repository.py --require-approved-identity`, in your own temporary directory where a
   suite needs one.
5. **The index W274 appended to the decision record.** It is outside W274's write scope. Check that it only lists
   the 95 files with correct groups and source hashes, says it is derived, and changes none of the owner's words.

## Report

Write REVIEW.md in English: a verdict (APPROVE, APPROVE WITH NOTES, or CHANGES REQUIRED), each finding with its
severity (Critical, High, Medium, Low, Info), the file and line, what is wrong and what would fix it, and the commands
you ran with their results. The last line of your answer is the REVIEW.md path.
