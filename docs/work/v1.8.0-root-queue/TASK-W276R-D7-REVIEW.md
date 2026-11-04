# TASK W276R: independent review of W276's D7 approvals

Root: Claude. Date: 2026-11-04. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
you did not write this change. Review only; do not edit, commit or push anything in any checkout.

| Item | Value |
|---|---|
| Change under review | Branch `codex/v1.8.0-w276` at `7a175a5c9` (base `a0280aafb` on the shared branch), checked out read-only at `D:/GitHub/arce-v180-w276r` |
| Implementer's record | `docs/work/v1.8.0-root-queue/records/W276.md` on that branch; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W276/` |
| Task it implements | [TASK-W276](TASK-W276-D7-APPROVALS.md) |
| Authority | [OWNER-DECISIONS-20261104](records/OWNER-DECISIONS-20261104.md), the owner's D7 answer and R306 |
| Your output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W276R/REVIEW.md`, plus your logs in that folder |

## What to check

1. **Exactly the owner's scope.** Exactly the 30 records the decision record names changed their review to
   `APPROVED`, reviewer `sunthemoon`, `reviewed_at` `2026-11-04`, with a basis citing the decision record; every other
   provenance record is unchanged, and `v1.8.0-w275-stand-in-art` stays pending. In each of the 30, nothing outside the
   review block changed, and the `content_digest` still binds the record's content (recompute it the way the
   validators do).
2. **The mechanism.** The new `basis` field is allowed by the schema the records declare and by every validator and
   importer that reads reviews; pending reviews still cannot carry a prefilled decision; the import `verify` commands
   still reproduce the records byte for byte without being weakened.
3. **Tests.** Every changed Java and Python test asserts at least as much as before for the new state (W276 replaced
   "stays pending" assertions with approval assertions); check no other assertion was removed or loosened.
4. **W274R's L1.** The preparation-date guard in `scripts/validate_v180_content_ledger.py` and `adopt_findings.py`
   now rejects `"2026-11-03\n"` and other non-canonical dates, with negative tests.
5. Run the provenance, importer and ledger Python suites, the import verifies W276's record lists, the ledger
   validator in its three modes, the Java tests W276 changed (`./gradlew test --tests` for those classes, under the
   heavy-JVM lock if you run Gradle) and `python -B scripts/validate_repository.py --require-approved-identity`, in your
   own temporary directory where a suite needs one.

## Report

Write REVIEW.md in English: a verdict (APPROVE, APPROVE WITH NOTES, or CHANGES REQUIRED), each finding with its
severity, the file and line, what is wrong and what would fix it, and the commands you ran with their results. The
last line of your answer is the REVIEW.md path.
