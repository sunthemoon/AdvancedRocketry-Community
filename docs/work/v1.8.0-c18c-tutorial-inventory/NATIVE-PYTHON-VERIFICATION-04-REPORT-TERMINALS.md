# Task04 terminal fields: fixed-commit Python verification

Date: 2026-10-05. Source commit:
`3c5f20dc1558178a97db357c7d44188e1b36c01d`, committed and non-force pushed.
Status: private Python terminal-field leaf verified; complete native inventory
and version acceptance remain open. See the [limited source disposition](NATIVE-SOURCE-REVIEW-DISPOSITION-04-REPORT-TERMINALS.md).

## Actual commands and results

Root uses Python `-B`, checkout scripts PYTHONPATH and process-local
TEMP/TMP/TMPDIR under
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18c-terminal-root-integration-20261005-01`.
The fresh properties fixture is a new child of that same D evidence directory.

```text
python -B -m unittest -v test_classic_inventory_fixture_reports test_classic_inventory_fixture_json test_run_v180_classic_inventory_smoke test_classic_inventory_fixture_inputs
```

Actual exit 0: **165 tests /0 failures, errors or skips**, consisting of 40 new
terminal methods plus unchanged 52 JSON /39 phase01 /34 properties methods.
Unittest time is 0.603 seconds; outer suite-command time is 0.781709 seconds.
Raw stdout/stderr and explicit command exit are retained in
[FIXED-REPLAY-COMMANDS-01.json](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18c-terminal-root-integration-20261005-01/FIXED-REPLAY-COMMANDS-01.json);
the suite is command 20. [FIXED-REPLAY-01.json](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18c-terminal-root-integration-20261005-01/FIXED-REPLAY-01.json)
binds that actual command, environment, timings and 18 selected fixed-Git/live
source/task pins before/after. HEAD remains the exact tested commit throughout.
These are named inputs, not a whole repository snapshot.

| Other actual check | Exit | Observation |
| --- | ---: | --- |
| `python -B scripts/validate_v1plus_planning.py` | 0 | Source-publication planning check |
| `git diff --check`, staged stat/check and raw staged blobs | 0 | Exactly four reviewed new files, 34,922 bytes |
| Non-force source push and remote/index checks | 0 | Remote matches source commit; index is empty |
| Global `git diff --exit-code` | 1 | User-owned AGENTS remains dirty and excluded; no clean-diff/Gate pass |
| Native PowerShell `cleanup_fixed_replay01.ps1` | 0 | Only fresh Root fixture removed; 154 entries /99,375 regular bytes /6 reparse leaves |

Cleanup follows the ended synchronous test process. Absolute containment,
bounded no-follow inventory, hashes and per-entry checks precede native
`Remove-Item -LiteralPath -Force` without recursive deletion. The exact fixture
target is absent; platform observations and precheck/result/log remain.
No C file, refused old target, source world, sealed evidence, other-agent
file or unowned process is removed. Author/reviewer new-fixture cleanup is
separate: 298,125 /134,297 regular bytes, respectively, not Root cleanup totals.
The author checkout's four original new files and its retirement have not been
claimed as cleaned by this receipt.

## Evidence and limits

The Root leaf retains AUTHOR-INTAKE-01, SOURCE-REVIEW-INTAKE-01,
SOURCE-PUBLICATION-01 and per-command raw outputs, FIXED-REPLAY-01 and its
raw command ledger, fixture platform observations, FIXTURE-CLEANUP-PRECHECK-01,
FIXTURE-CLEANUP-RESULT-01 and fixture-cleanup01.log. No full source tree, JAR,
world or redundant archive is exported.

Author original failed runs remain sealed; grammar, parser budgets and test
assertions are not relaxed. No Java/Gradle/GameTest/native/client run occurred.
C free space at the new precheck is 9,741,262,848 bytes, below the 10 GB heavy-run
threshold, so no build/native host is admitted. Earlier Java evidence stays at
`a0873a30a2e1ad9fe0b42d11a0b89903fc478d5c`, not this Python commit.
Other report fields, acquisition, ownership/freshness, NBT/driver, real restarts,
R-021, content delivery and all G0-G9 remain unverified/open. Structurally valid
terminal text is not independent evidence that the claimed action happened.
