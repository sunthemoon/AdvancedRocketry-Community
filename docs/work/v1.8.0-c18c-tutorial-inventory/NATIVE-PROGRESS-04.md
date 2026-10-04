# C18c native Task04 implementation: phase01 primitives

Date: 2026-10-05. Implementer: delegated worker. Integrator: Root.
Status: IN_PROGRESS. This record is not a native execution or Gate verdict.

## Checkout and authority

The isolated registered worktree is `D:/GitHub/arce-v180-c18c-native-20261005`,
branch `codex/v1.8.0-classic-inventory-native`, fixed committed base
`d04a116e6e6001f166bbf5e8f31fb78623a6b392`. Initial status is clean.
Root's [Task04 disposition](NATIVE-REVIEW-DISPOSITION-04.md) adopts the exact
cumulative [Task04](NATIVE-TASK-04.md) and normative proposal03/protocol03.
Older Task01-03 do not independently authorize implementation.

The normative protocol SHA-256 is
`fc692c32fc3854d80cdbf6ead77fe4c3103925e3a603d68bea43e93504e63edc`.
Its 80-payload input manifest is `EVIDENCE-MANIFEST.json`, SHA-256
`2dd72c12a8bf7bffcc6dd10ade9863f48b7e57ee7e0c361484a19f4cf0df8de1`.
All 80 payloads /875,858 bytes were independently read and verified unchanged
for intake. This does not repeat the previous native/contract review.

## Exclusive write scope

This task may create only the following new worktree files. No existing source,
central hook/registry/build/config/network, generated resource, provenance,
status, ledger, AGENTS or Git index is writable by this worker.

- `scripts/run_v180_classic_inventory_smoke.py`.
- `scripts/test_run_v180_classic_inventory_smoke.py`.
- This record and [NATIVE-HANDOFF-04.md](NATIVE-HANDOFF-04.md).
- Future main files in the existing gametest package:
  `ClassicInventoryAdvancementNativeFixture.java`,
  `ClassicInventoryFixturePlayers.java`,
  `ClassicInventoryFixtureOwnership.java`,
  `ClassicInventoryFixtureRecords.java`.
- Future narrowly scoped tests in the corresponding test package, named
  `ClassicInventoryAdvancementNativeFixtureTest.java`,
  `ClassicInventoryFixturePlayersTest.java`,
  `ClassicInventoryFixtureOwnershipTest.java`,
  `ClassicInventoryFixtureRecordsTest.java`.

The Java paths above are a future write inventory, not implemented files or
source/API admission. Any additional dependency or central integration needs
Root's explicit separate assignment.

## Implemented phase boundary

Root selected only pure Python primitives for this first phase: bounded raw
properties decoding, raw/logical phase bindings, the literal seed command and
exact source-derived native acknowledgement payload grammar. These primitives
do not open files, configure/copy a host, create a receipt, run a command,
observe an authoritative console line, enforce runtime freshness, inspect a
world, construct/join players, load chunks or mutate native records. Direct
invocation must refuse as an incomplete driver rather than imply success.

The properties subset keeps the existing 16,384-byte, 1,023-pair ceiling and
two string nodes/pair plus root <=2,048. It preserves unknown entries and
separates configured, boot, live and stopped raw identity from whole-map logical
equality. No config repair, normalization, default insertion, logging of secret
values or file writes are permitted. Only `seed` yields the fixed command;
`reload` yields none. Parsed acknowledgement coordinates cannot select commands,
and a final changed ChunkPos is not a changed-count or membership/loading proof.

Root's 2026-10-05 task message explicitly interprets raw literal controls as
C0 (U+0000-001F) plus DEL/C1 (U+007F-009F). LF/CRLF are recognized first as physical
delimiters; allowed escaped t/n/r/f decode to controls without raw-control
rejection. This is the test-owned properties subset, not a JSON/gameplay/NBT
policy. The original specification models covered C0 and DEL but not C1; model
coverage was not normative authority. New tests cover raw C0/DEL/C1 in data and
comments, escaped-control distinction, strict UTF-8 and byte boundaries. Native
file selection remains separate: an ineligible native input is not rewritten.

The four Java classes, complete two-host driver, bounded file-path/reparse reads,
native JSON/NBT/record/cache parsers, reflection, console-origin/freshness capture,
ownership/lifecycle cleanup and central hook remain unimplemented. No Java,
Gradle, GameTest, native server or client execution is authorized in this phase.

## Deferred JSON counting contract

Protocol03 gives JSON depth/node ceilings but does not define whether object
keys count as nodes or whether root depth is zero or one. Root explicitly
deferred a structural JSON parser rather than authorize a guessed convention.
Read-only search found these existing, differently scoped definitions:

- `scripts/validate_bootstrap_provenance.py:1735-1760` starts root depth at one,
  counts value/container visits, and validates object-key UTF-8 separately
  without counting those keys. Its own ceiling is depth 64 /100,000 nodes.
- `scripts/collect_v002_manual_evidence.py:605-625` starts root depth at one,
  traverses object values/list items, and applies depth 64 without a node cap.
- `scripts/run_v180_pump_smoke.py:197-223` and
  `scripts/run_v180_signature_smoke.py:469-498` validate their fixed report
  shapes/duplicate fields but do not define general C18c JSON node accounting.

These files do not grant this leaf a new counting rule. None is transplanted;
JSON admission and all record/receipt processing remain unfinished.

## Evidence and failures

Small loose evidence is owned under
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18c-native-impl-01-598dc248af`.
Subprocess-only TEMP/TMP/TMPDIR point to its `process-temp`; Python uses `-B`.
No C output, full-tree export, duplicated JAR/ZIP or old sealed evidence write
is permitted.

An earlier read guessed `INTERNAL-MANIFEST.json`; the actual file is
`EVIDENCE-MANIFEST.json`, subsequently verified above. Intake attempt01 had a
Python `-c` quoting SyntaxError before input reads. Its original log/exit1 remain;
the file-based intake02 exits0. Neither is a source/test/native failure.
The bounded validator search returns source definitions, not parser authority.

Tests and handoff will record actual counts, commands, unchanged input pins and
postimages. No full v1.8 Required Gate is claimed by this partial phase.

## Candidate02: public wording only

Candidate01's four postimages, creation patch, 36/0 unit log, entrypoint exit2,
report and 49-payload evidence manifest remain immutable. Root requested a
separate candidate02 to translate public module docstrings and CLI text into
stable inventory-restart verification language. The production/test docstring
and direct refusal wording change only; parser behavior, all test assertions,
budgets, commands and partial implementation boundary remain unchanged.
The original Root task/control interpretation remains in these internal records,
not the public code explanation. A fresh unit/CLI/static receipt must bind
candidate02 separately; candidate01 success is not relabelled as a new run.

The normative `properties_reader.ignored_semantically` lists `blank_lines`,
but does not explicitly distinguish an empty physical line from a whitespace-only
line. The implementation currently ignores only empty physical lines and rejects
whitespace-only lines under the strict data-line separator/key rules; the
existing `b" "` negative assertion remains unchanged. That empty-only choice
has no separate direct normative clarification. Prior model code is not authority
for it. This is an explicit pending interpretation for independent source review,
not a silently adopted subset or permission to repair native selected input.

## Candidate03: explicit blank-line interpretation

Root's 2026-10-05 task message resolves the preceding candidate02 question:
`blank_lines` means an empty physical line or a line containing only ASCII
space U+0020. TAB/form-feed/NBSP/other Unicode whitespace are not admitted as
blank. LF/CRLF delimiters and raw C0/DEL/C1 refusal remain unchanged, with
literal controls checked before blank classification. Comments still require
initial #/!; indented comments remain unsupported. No trim, canonicalization,
default insertion, config/record write or broader parser authority is introduced.

Candidate03 changes one production condition to ignore ASCII-space-only physical
lines, then preserves every other production statement. The original `b" "`
negative subcase is replaced according to that explicit interpretation; all
other original assertion bodies remain. Three new test methods cover positive
blank lines, other whitespace/indented comments, preserved data trailing spaces,
and actual raw hash differences with logical-map equality. Boot/stopped raw
bindings continue to refuse logically equal but raw-different files.

Fresh new tests against unchanged candidate02 production execute 39 methods
and exit1 with seven error records (five subcases and two method-level errors),
all reporting `PROPERTIES_SEPARATOR`; raw source/logs/results are preserved
separately. Applying only the blank condition gives fresh 39 methods /0 FES,
exit0, unittest 0.020 seconds. This is pure source testing, not a native run.
Candidate01's 49 payloads and candidate02's 44 payloads remain immutable; their
old 36-test results and the independent candidate02 findings are not rewritten.

The former blank interpretation is no longer pending in candidate03. JSON
root-depth/object-key node accounting, complete driver/transport/file/Java/native
scope remain deferred exactly as before. Independent actual-source review is
still required before Root integrates this partial candidate.
