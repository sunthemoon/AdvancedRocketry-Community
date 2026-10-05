# C18c Task04: private terminal report fields

Date: 2026-10-05. Milestone: v1.8.0. Task ID: C18c-01-NATIVE04-REPORT-TERMINALS.
Contract owner/integrator: Root. Code baseline:
`534f3147a7ca72ac6b3a6ee319cf4bf775ab6757`.
This specification requires independent review and a published Root disposition
before an isolated author checkout is assigned. It does not admit a native host.

## Outcome and exclusive write scope

Observe the common report envelope and exactly READY_FOR_STOP/FAILED payloads
from already acquired bytes. Only four NEW author files:

1. `scripts/classic_inventory_fixture_reports.py`
2. `scripts/test_classic_inventory_fixture_reports.py`
3. `docs/work/v1.8.0-c18c-tutorial-inventory/NATIVE-PROGRESS-04-REPORT-TERMINALS.md`
4. `docs/work/v1.8.0-c18c-tutorial-inventory/NATIVE-HANDOFF-04-REPORT-TERMINALS.md`

Existing Python modules/tests, Java, build, schema, registry, generated data,
contracts, governance, status, ledger and Git remain read-only for the author.
No file access, path, CLI, subprocess, callback, host, sequence, receipt, writer,
ownership, freshness, NBT or native serializer is added. Other report events
refuse; they are not returned as opaque validated records.

## Exact private contract

API: `parse_terminal_report(raw: bytes) -> TerminalReportObservation`.
It calls the committed `parse_fixture_json(raw, JsonRole.REPORT)` without
changing that parser, role, limits or diagnostics. JSON errors propagate as the
existing fixed FixtureJsonError; valid syntax with an invalid terminal shape
raises FixtureReportError with the sole message/args/code `RECORD_SHAPE`.
No caller-controlled diagnostic, key/value, exception or path is echoed, and
no chained exception is exposed. This constrains consumer-owned diagnostics,
not arbitrary caller tracebacks, debuggers or same-process mutation.

Require exact decoded field sets, no missing/extra members, no normalization,
coercion, dropped values or default insertion. Preserve the exact immutable
raw bytes and their parser-observed SHA-256. Return frozen, slotted records:

- TerminalReportObservation: raw, sha256, schema, run_id, phase, event, index,
  payload. The schema/index are bounded Python integers, run_id/phase/event
  are exact decoded strings; payload is exactly one of the two records below.
- ReadyForStopPayload: action_complete (bool), owned_handles (int),
  observed_rows (int).
- FailedPayload: stage (str), reason (str), owned_handles_remaining (int),
  cleanup_errors (int).

All values are owned immutable observations. There is no permission-bearing
handle or inferred ready/owned/fresh/success/receipt flag. In particular,
action_complete=false is a type-valid observation, not completion or stop/reuse
authorization. A later complete-cohort validator must establish truth, origin,
launch binding, order, deadlines and clean stop independently.

| Exact envelope field | Required shape |
| --- | --- |
| schema | JSON integer grammar, numerical value 1 |
| run_id | 36-character lower-case hexadecimal UUID, exact 8-4-4-4-12 hyphenation; no extra version/variant restriction |
| phase | exact seed or reload |
| event | exact READY_FOR_STOP or FAILED |
| index | JSON integer grammar, 0..11 seed or 0..10 reload |
| payload | exact event object below |

READY_FOR_STOP requires index 11 for seed or 10 for reload. Its exact three
fields are action_complete (JSON Boolean), owned_handles (JSON integer 0),
observed_rows (JSON integer 12 for seed or 11 for reload).

FAILED permits the envelope's complete phase-specific index range. Its exact
four fields are stage (ASCII string, 1..64 bytes), reason (one of the fixed
codes below), owned_handles_remaining (JSON integer 0..2), cleanup_errors
(JSON integer 0..16). Stage includes every ASCII scalar, including escaped
C0 and DEL: no printable-only rule is added. Literal C0 still fails JSON
syntax. Stage is an observation only and must not be rendered as trusted text.

Exact failure reasons: AUTHORITY, PHASE, LOADED_AREA, BINDING, COLLISION,
RECEIPT, RECORD_SHAPE, CACHE_ACCESS, CACHE_SHAPE, CACHE_CHANGED, CONSTRUCTION,
JOIN, LOADED_PROGRESS, INVENTORY_OBSERVATION, REPLAY_OBSERVATION, DISPOSAL,
DEADLINE, INTERNAL. No lower-case, trim or arbitrary failure text is accepted.

Integer fields require JsonNumber.integer_grammar, never Python bool, string,
fraction/exponent grammar or binary64 coercion. Numerically zero `-0` is
accepted wherever zero is in range, without rewriting retained raw bytes.
Negative nonzero values refuse. Check the exact lexeme's bounded length and
range before conversion; do not convert an unbounded integer or change the
interpreter's integer guard. These explicit technical selections preserve
the protocol's integer/ASCII observations rather than add native-canonical
spelling or printable-only restrictions.

## Fixed basis and limits

The common six fields, three/four payload fields, phase indices, counts and
18 reasons come from Task04's cumulative PROTOCOL-03.json, SHA-256
`fc692c32fc3854d80cdbf6ead77fe4c3103925e3a603d68bea43e93504e63edc`,
at `D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-native-proposal03-5bb27f098e/`.
Task04 and its disposition remain mandatory. This leaf freezes only a private
terminal-field subset; no external marker or whole report/cohort adoption follows.

The committed [JSON contract](NATIVE-TASK-04-JSON.md) remains unchanged:
REPORT <=16,384 bytes, root-inclusive depth <=8, key-inclusive nodes <=2,048.
Equality is allowed; parsing enforces limits before field validation even for
unknown keys. The 393,216-byte cohort ceiling, 23 success rows, all host/time
budgets, receipts and origin checks remain separate and unchanged.

## Actual-source verification and publication

Test both terminal shapes/phases, all bounds and wrong scalar types, exact
field sets, UUID spelling, all reasons, ASCII boundaries, escaped controls,
syntax/structural refusal, long numeric tokens, immutable/raw-byte ownership,
and fixed non-echo errors. Check type-valid false completion observations
without treating them as native results. Verify there is no IO/CLI/process or
authority surface. Tests use the real candidate consumer, not protocol models.

Run the new suite plus the unchanged JSON, phase01 and properties suites:

```text
python -B -m unittest -v test_classic_inventory_fixture_reports test_classic_inventory_fixture_json test_run_v180_classic_inventory_smoke test_classic_inventory_fixture_inputs
```

The author uses a Root-created independent D worktree. Helpers, TEMP/TMP/TMPDIR
and fresh disposable fixtures are under a new owned project-parent D evidence
leaf; do not create C scratch, retry refused cleanup targets, touch source
worlds or terminate unowned processes. Preserve exact source pre/post pins,
all original attempts, actual commands/raw exits and qualified cleanup.
Report observed counts, not predetermined verdicts.

A different agent reviews the actual source and reruns applicable checks.
Root then commits/non-force pushes the frozen source and replays that exact
commit before publishing delivery/results. The author does not commit/push.
No Java/Gradle/native is needed or authorized for this pure Python leaf.
Full C18c native inventory/restarts, R-021, remaining content, ledger and
v1.8 G0-G9 stay open. No player progress is restored/granted/reset here.
