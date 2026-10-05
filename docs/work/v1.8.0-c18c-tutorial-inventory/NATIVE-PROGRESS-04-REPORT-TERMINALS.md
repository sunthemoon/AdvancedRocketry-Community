# Terminal report fields: author progress

Date: 2026-10-05. Task: C18c-01-NATIVE04-REPORT-TERMINALS. Delegated author:
c16a04_fluids. Phase: ready for independent actual-source review; no delivery
or independent source acceptance.

## Intake and exclusive scope

Registered checkout: `D:/GitHub/arce-v180-c18c-report-terminals-20261005`;
branch `codex/v1.8.0-inventory-report-terminals`; fixed base/HEAD
`3e5610e55474480c5e8a83a2e838f58773bf7c31`. Root owns Git and integration.
The effective live Root AGENTS is 12,047 bytes, SHA-256
`1be0391c3f69f566ae3f627e37107adfbd0bbfa6f581b105ff6bd2a2c2630dc0`.

Only four new paths are writable: `scripts/classic_inventory_fixture_reports.py`,
`scripts/test_classic_inventory_fixture_reports.py`, this progress record and
`NATIVE-HANDOFF-04-REPORT-TERMINALS.md`. Existing modules, tests, contracts,
central files, configuration, governance, ledger and status remain unchanged.

## Development candidate

The consumer delegates syntax/budgets directly to the committed REPORT JSON
parser, then requires exact envelope and terminal payload fields. It returns
frozen slotted records, with bounded numerical integers and preserved raw
bytes/hash. Integer lexeme length and numerical range are checked before
conversion. Numerical -0 is permitted only where zero is in range. Every ASCII
stage scalar is type-valid, including escaped controls; stage is untrusted text.
False action_complete remains type-valid, never inferred permission/success.

No parser rule, ID, schema or numerical ceiling changes. There is no file,
CLI, process, callback, native, receipt, sequence, ownership or writer authority.
JSON diagnostic exceptions propagate unchanged; terminal-shape errors expose
only RECORD_SHAPE.

## Actual checks and retained failures

The first four-suite run exited 1: 164 tests, five errors in new numeric test
fixtures. Their helper omitted a closing field-name quote, so the original
JSON parser correctly refused malformed syntax before terminal validation.
The helper spelling was corrected and a direct exact-byte control was added.
The second run exited 1: 165 tests, one failure because the conversion spy
also rejected a preceding valid schema field. The final spy still forbids
every oversized conversion (all admitted conversions must have at most two
characters); it permits the bounded valid fields preceding the rejected target.
The original raw failures and source companions remain separately retained.
No production byte, existing test, assertion for long-token refusal or contract
ceiling was changed to obtain the final result.

The final run exited 0: 165 tests = 40 new terminal tests + unchanged 52 JSON,
39 phase01 and 34 properties-input tests, no failures/errors/skips. Unittest
reports 0.610 s; outer duration is 0.791520 s. Fresh controls separately observe
1,173 bounded failure-field combinations, compare exact protocol fields/reasons,
check dependency/no-IO/error propagation source facts and verify 22 named input
pins. Existing tracked/index diffs are empty; only the four owned new paths exist.

The three fresh properties fixture trees were cleaned with native PowerShell
literal, non-recursive entry deletion after no-follow ownership/reparse checks.
Actual exit 0: 462 entries /298,125 regular-file bytes. No old rejected target,
source worktree file or source world was removed. Platform observations and
cleanup precheck/raw/result remain in the evidence leaf.

Fresh helpers, evidence and process-local temporary files are under
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18c-terminal-author-597fa310b8`.
No Java/native/Git mutation or old helper/rejected cleanup retry was performed.
Different-agent source review and Root fixed-commit replay remain required;
native C18c delivery, R-021, the ledger and G0-G9 remain open.
