# Bounded JSON syntax consumer: author handoff

## Completed scope and interface

The new private byte consumer and its focused tests are ready for independent
actual-source review. This is an uncommitted development candidate, not a
delivered feature. Base commit is
`e091f1b0abb2dc2068c8e7cf0f6e97e48d3aaa03`; the registered isolated worktree is
`D:/GitHub/arce-v180-c18c-json-20261005`, branch
`codex/v1.8.0-inventory-json`. The four-file ownership and effective governance
pin are in [author progress](NATIVE-PROGRESS-04-JSON.md).

The only parsing API is
`parse_fixture_json(raw: bytes, role: JsonRole) -> JsonObservation` in
`scripts/classic_inventory_fixture_json.py`. Exact `bytes` and enum type are
required. Roles are EXTERNAL, REPORT, ADVANCEMENT, STATS and USERCACHE with
the frozen byte/depth/node/entry caps. Observation fields are raw bytes, their
SHA-256, fixed role, immutable value, observed depth/nodes, and applicable
entries (otherwise `None`).

`JsonObject.pairs` preserves ordered immutable `(key, value)` tuples; arrays
are tuples, scalars strings/bools/null. `JsonNumber` retains `lexeme`,
`integer_grammar` and optional lossy finite `binary64`. Integer lexemes are not
converted. This representation grants no field-schema or ownership authority.
Failure args/code/role contain only fixed diagnostics; decoder chaining is
suppressed. Stats framing uses JSON_ENTRIES and wrong root uses JSON_ROOT.

## Source identities and dependencies

| New implementation file | Bytes | SHA-256 |
| --- | ---: | --- |
| `scripts/classic_inventory_fixture_json.py` | 10844 | `fa947139e9f6d759911cd3adc40aef254974c1cbde88603c2ccaa1ad4cec5472` |
| `scripts/test_classic_inventory_fixture_json.py` | 20918 | `05a336800ab14719ec4a9be7e71e848383df67c0713894cba4ec0dcd60e97c96` |

These two files and the two owned task records are the entire delta. The loose
evidence OWN-FILES and creation patch bind all four final postimages; records
do not recursively include their own hashes. Production dependencies are
Python standard-library dataclasses, enum, hashlib, math and types only. No
existing properties module, gameplay source, central registration, public or
persisted ID, schema, writer, network or configuration is changed. Code is
newly authored, with no upstream import or transformed asset.

Contract source is [Task04 JSON](NATIVE-TASK-04-JSON.md), SHA-256
`15725102001c50a8136ad8785bf13d5d2e57fc289ebd572041a49339ee5c178a`, and its
[technical disposition](NATIVE-REVIEW-DISPOSITION-04-JSON.md), SHA-256
`86041069dac2c5cdae9e128caf5ed51c4c8722d36945e7560166045ae06fb9cb`.

## Actual commands and retained failure lineage

All processes used Python `-B` with TEMP/TMP/TMPDIR confined to the owned D
evidence leaf. The raw commands, outputs, exits and tested postimages are there.

1. `python -B -m unittest -v test_classic_inventory_fixture_json`: exit 1,
   48 tests, two fixture failures (0.338 s reported by unittest). The originally
   selected `\\bad` sentinel was valid JSON `\\b` plus `ad`. No parser behavior
   or assertion was relaxed: the tests now use invalid `\\qbad`, and an added
   control proves the sentinel distinction. First source and logs are retained.
2. JSON plus unchanged phase01/properties-input suites: exit 0, 122 tests,
   0 failures/errors/skips (0.468 s). This remains an intermediate record.
3. Final identical selectors after three additional number controls: exit 0,
   125 tests = 52 JSON + 39 phase01 + 34 properties-input, 0 failures/errors/skips
   (0.567 s; outer captured duration 0.732872 s).
4. Fresh `check_structure.py`: exit 0, 1,000 seeded valid-document/counter
   comparisons, standard-library import/no-file-policy checks, 17 named input
   postchecks and exact four-new-file scope. No sealed model/helper was rerun.
5. `git diff --exit-code` and `git diff --check`: exit 0 for existing tracked
   files. The creation patch is separate because ordinary Git diff omits new
   files; its readonly check and exact postimage replay are retained separately.
6. Checked own-fixture cleanup: exit 0, 308 entries/198,750 regular bytes removed
   without reparse traversal. Only newly created test fixtures were eligible.

Boundary tests cover all five inclusive caps and plus-one refusal before excess
decoding/descent; keys and empty containers; strict syntax/Unicode and duplicate
decoded keys; immutable ownership; lexeme fidelity, huge integers/exponents,
lossy projection, overflow/underflow and signed zero; all three entry caps and
unknown/malformed elements; fixed non-echo diagnostics.

## Evidence and unfinished scope

Loose evidence is at
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18c-json-author-8c6f1a290d`.
It contains the input pins, four final source companions, patch, historical
test sources/logs/results, static controls and checked cleanup receipts. No
whole source tree, JAR, world or redundant archive is exported.

No JSON file acquisition, fixed-field validation, receipt freshness, native
serializer compatibility, player/host admission, writer, CLI or complete native
driver is implemented or tested. No Java/Gradle/GameTest/native/client run was
performed. Unknown native strings may be refused by the selected strict subset.
This is not a sandbox or protection against arbitrary caller traceback/locals
output. No version Required Gate, C18c delivery, ledger row or R-021 is closed.

Root owns integration/rollback. Apply only the exact four new postimages after
different-agent source review; create a code commit, replay at that fixed SHA,
then record delivery only if the scoped evidence supports it. Rollback removes
these four new files as one isolated leaf; no saved data requires migration.
