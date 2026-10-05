# Task04 JSON: fixed-commit Python verification

Date: 2026-10-05. Source commit:
`7affd485fc99b944dd5d3b2e94ab6c4651d3903e`, committed and non-force pushed.
Status: scoped Python implementation passed; native inventory/leaf/version
acceptance remains open. See [limited source disposition](NATIVE-SOURCE-REVIEW-DISPOSITION-04-JSON.md).

## Actual Root commands and observations

Process-local TEMP/TMP/TMPDIR and the properties fixture use
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18c-json-root-integration-20261005-01`.
PYTHONPATH selects this checkout's scripts; Python uses `-B`.

| Command | Exit | Observed result |
| --- | ---: | --- |
| `python -B -m unittest -v test_classic_inventory_fixture_json` | 0 | 52 tests /0 failures, errors or skips; unittest 0.408 s |
| `python -B -m unittest -v test_run_v180_classic_inventory_smoke test_classic_inventory_fixture_inputs` | 0 | 73 tests /0 failures, errors or skips; unittest 0.136 s |
| `python -B scripts/validate_v1plus_planning.py` | 0 | Planning check during source publication |
| `git diff --check` and staged stat/check | 0 | Source publication scope is exactly the four reviewed additions |
| Global `git diff --exit-code` | 1 | User-owned AGENTS remains dirty; not a clean-diff/Gate pass |
| Native `cleanup_fixed01.ps1` | 0 | Exact fresh D fixture removed: 99,375 regular bytes and six aliases |
| Native `retire_worktree01.ps1` | 0 | Only the clean owned author checkout removed with non-force Git; recovered checkout bytes not measured |

Actual total is 125 tests: new JSON 52 plus unchanged phase01 39 and properties
34. Both raw stdout/stderr and explicit exit files are retained. All 12 related
source/task postimages match their named pins and fixed Git blobs before/after;
HEAD remains the tested source commit throughout execution. These are selected
Python inputs, not a whole repository snapshot. No Java/native test ran here;
earlier Java evidence remains at its separate recorded commit.

The cleanup precheck confirms literal target containment, ordinary non-reparse
ancestors, bounded no-follow inventory and owned alias targets. Aliases are
removed non-recursively before rechecking and removing the ordinary fixture.
The target is absent. No C file, previous refused target, source world, sealed
evidence or other process is deleted. New precheck/result/log/exit are retained.

After Root source/replay verification, the author removes only its own four
original worktree files, 40,913 regular bytes, with literal non-recursive
PowerShell checks/removal. Root verifies its six-payload receipt; the resulting
clean owned checkout is then removed using normal Git worktree removal. Its
target/registration are absent. The source commit, Root live files and sealed
source/review records remain; checkout recovery has no measured byte total.

## Evidence and limits

The external Root leaf contains `AUTHOR-INTAKE-01.json`,
`SOURCE-REVIEW-INTAKE-01.json`, `SOURCE-PUBLICATION-01.json` and its per-command
raw outputs, `FIXED-PYTHON-CHECKS-01.json`, two raw test logs/exit records,
`ROOT-FIXTURE-PRECHECK-01.json`, `ROOT-FIXTURE-CLEANUP-01.json` and cleanup log/exit.
No source tree, JAR, world or duplicate archive is included.

Original author and reviewer failures are preserved in their separate sealed
leaves, pinned by the source disposition. Parser limits/grammar/assertions are
unchanged. File acquisition, schema/field authority, native unknown-string
compatibility, driver/player/host admission, Java/NBT, real restarts, R-021,
content-ledger delivery and version G0-G9 remain unverified/open. Private byte
syntax acceptance alone grants none of those authorities.
