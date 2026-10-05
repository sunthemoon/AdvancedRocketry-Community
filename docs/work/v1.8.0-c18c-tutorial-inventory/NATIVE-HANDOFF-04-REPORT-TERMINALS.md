# Terminal report fields: author handoff

## Completed scope and fixed checkout

The four-new-file development delta is ready for different-agent actual-source
review. It is uncommitted and not delivered. Registered checkout:
`D:/GitHub/arce-v180-c18c-report-terminals-20261005`; branch
`codex/v1.8.0-inventory-report-terminals`; base/HEAD
`3e5610e55474480c5e8a83a2e838f58773bf7c31`. Root owns integration and Git.
Effective Root AGENTS is 12,047 bytes, SHA-256
`1be0391c3f69f566ae3f627e37107adfbd0bbfa6f581b105ff6bd2a2c2630dc0`.

The frozen [terminal task](NATIVE-TASK-04-REPORT-TERMINALS.md) is 7,316 bytes,
SHA-256 `d021ebc1d249751f5a88ce6fca220620dfd0b01765713815a58f040112b8d8c1`.
Its [disposition](NATIVE-REVIEW-DISPOSITION-04-REPORT-TERMINALS.md) is 2,287 bytes,
SHA-256 `235395b8803552bd06aaa7e4da73c2a5290149b5a4a24a09e6172ed11c09d307`.
The earlier code baseline in that contract remains
`534f3147a7ca72ac6b3a6ee319cf4bf775ab6757`; it is not substituted for this
registered author checkout or its actual test source identities.

## Interface, records and diagnostics

`parse_terminal_report(raw: bytes) -> TerminalReportObservation` is pathless.
It calls the existing `parse_fixture_json(raw, JsonRole.REPORT)` once, with
unchanged limits/diagnostics, then requires exact envelope and payload fields.
Existing FixtureJsonError instances propagate; valid syntax with invalid
terminal shape raises only FixtureReportError through its fixed no-argument
constructor, with args/code/str limited to RECORD_SHAPE and
no exception chaining.

Three frozen slotted records have exactly the selected fields:

- TerminalReportObservation: raw, sha256, schema, run_id, phase, event, index,
  payload.
- ReadyForStopPayload: action_complete, owned_handles, observed_rows.
- FailedPayload: stage, reason, owned_handles_remaining, cleanup_errors.

Integer grammar, bounded lexeme length and numerical range are checked before
conversion; Python bool, strings and fraction/exponent numbers are not coerced.
Numerical -0 is accepted only where zero is in range, without rewriting raw
bytes. Every ASCII stage scalar is valid, including escaped C0/DEL; this is
untrusted observation text. False completion is type-valid, not inferred stop,
success, ownership, freshness or receipt authority. No additional permission
handle is returned. No new gameplay/public API/persisted ID or schema is added.

## Four postimages and dependencies

| New source/test file | Bytes | SHA-256 |
| --- | ---: | --- |
| `scripts/classic_inventory_fixture_reports.py` | 4555 | `180cbc69b19a685692af61e05a0446441f2a2fc4636809a6243171b6b1cef957` |
| `scripts/test_classic_inventory_fixture_reports.py` | 20128 | `ab219ba40bef61074fcd8cf6ff4ca9e006711258c5c446008c64c31cdb86a865` |

The other two new postimages are this handoff and
[author progress](NATIVE-PROGRESS-04-REPORT-TERMINALS.md). OWN-FILES and the
creation patch in the loose evidence bind all four final bytes; task records
do not recursively contain their own hashes. Production imports only dataclasses,
re and the committed private JSON consumer. No existing production/test/task,
central/provenance/generated/status/ledger/AGENTS path is modified. All code is
newly authored, with no upstream content or asset import.

## Actual commands and failures

Fresh Python `-B` runs used TEMP/TMP/TMPDIR under the owned D evidence leaf.
The executed selectors were terminal reports, existing JSON, phase01 and
properties-input suites, as required by the exact contract.

1. `tests-01`: exit 1; 164 tests; five fixture errors, 0.585 s. The new token
   replacement omitted a field-name closing quote. Original bytes/raw logs
   remain preserved; malformed JSON refusal was not reclassified as a defect.
2. `tests-02`: exit 1; 165 tests; one spy failure, 0.587 s. The spy incorrectly
   rejected a preceding valid schema conversion while observing an oversized
   later field. This original test and log are preserved.
3. `tests-03`: exit 0; 165 tests = 40 new + unchanged 52/39/34; 0 failures,
   errors or skips, 0.610 s (outer 0.791520 s). The spy now verifies every
   converted lexeme is at most two characters; 6,000-digit fields still refuse
   before conversion. Production has remained byte-identical since tests-01.
4. Fresh `static_controls.py`: exit 0; 1,173 bounded terminal-field observations,
   exact protocol common/payload fields and 18 reasons, no-IO/dependency/error
   wrapping controls, 22 named input postchecks and exact four-new-path scope.
5. Existing tracked/index `git diff --exit-code` and tracked `git diff --check`
   exit 0. New files are bound by a separately readonly-checked creation patch
   and exact independent postimage reconstruction, not ordinary Git diff alone.
6. Own new fixture cleanup: native PowerShell exit 0; 462 entries/298,125 regular
   bytes removed after bounded literal/no-follow checks. All original platform
   observations, raw commands/results and cleanup evidence are retained.

Tests cover both phases/terminal shapes; exact fields/UUID spellings/reasons;
all phase indices, handles/errors/row counts; wrong scalar grammar and long
tokens; negative zero; all ASCII stage scalars and boundaries; false completion;
parser structural precedence/propagation; fixed errors and immutable/raw ownership.

## Evidence, unfinished scope and integration

The small loose bundle is
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18c-terminal-author-597fa310b8`.
It contains commands/logs/results, 22 input pins, the four postimages/patch,
all original failure source companions and checked cleanup. No complete source
tree, world, JAR or redundant archive is exported; no sealed helper/model reruns.

No file/CLI/process/callback/host/sequence/receipt/freshness/ownership/writer/NBT
or native serializer capability is implemented. Only two terminal events are
recognized; all other events refuse rather than become opaque validated records.
Full cohort, launch/order/time binding, stop/reuse and driver remain unfinished.
No Java/Gradle/GameTest/native/client run occurred. R-021, C18c delivery, remaining
content, ledger and all v1.8 Required Gates stay open.

Different-agent actual-source review is required. Root then commits/non-force
pushes the four exact postimages and replays that fixed code commit before
publishing any delivery/result. No central hook is required. Rollback removes
this isolated four-file leaf; no player progress or saved data is migrated.
