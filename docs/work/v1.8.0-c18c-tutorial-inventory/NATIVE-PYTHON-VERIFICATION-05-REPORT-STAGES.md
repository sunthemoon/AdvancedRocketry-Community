# Task04 nonterminal fields: fixed-commit Python verification

Date: 2026-10-05. Tested source:
`5582e3c49d548c5c002ef6c7cd45b1296f2f2703`, committed and non-force pushed.
Status: private field leaf verified, not complete native inventory acceptance.
See the [limited source disposition](NATIVE-SOURCE-REVIEW-DISPOSITION-04-REPORT-STAGES.md).

## Actual commands and results

Root uses Python `-B`, checkout scripts PYTHONPATH and process-only
TEMP/TMP/TMPDIR under the project-parent evidence directory
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18c-terminal-root-integration-20261005-01`.
The new `stage-fixed-replay-fixtures-01` child is the only replay scratch target.

```text
python -B -m unittest -v test_classic_inventory_fixture_stages test_classic_inventory_fixture_reports test_classic_inventory_fixture_json test_run_v180_classic_inventory_smoke test_classic_inventory_fixture_inputs
```

Actual child exit 0: **200 tests /0 failures, errors or skips**. Module counts are
35 stages +40 terminals +52 JSON +39 phase01 +34 properties. Unittest time is
2.962 seconds, child elapsed is 3.171840 seconds and the full replay helper takes
6.632004 seconds. Raw suite stdout/stderr are command 26 in the
[command ledger](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18c-terminal-root-integration-20261005-01/STAGES-FIXED-REPLAY-COMMANDS-01.json).
The [replay receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18c-terminal-root-integration-20261005-01/STAGES-FIXED-REPLAY-01.json)
binds the exact argv, environment, source SHA and 24 selected Git/live inputs
before/after. There is zero named-input drift; this is not a whole-source snapshot.

| Other actual check | Exit | Observation |
| --- | ---: | --- |
| Source publication helper | 0 | Four exact new postimages /55,219 bytes, staged blobs/stat/check, non-force push and remote/index checks |
| `python -B scripts/validate_v1plus_planning.py` | 0 | Source-publication planning check |
| `git diff --check` | 0 | Whitespace check, not clean-worktree acceptance |
| Global `git diff --exit-code` | 1 | User-owned AGENTS remains excluded and dirty; no Gate waiver |
| `pwsh -NoProfile -File cleanup_stage_fixed_replay01.ps1` | 0 | Only fresh ended replay fixture: 154 entries /99,375 regular bytes /six reparse leaves removed |

Cleanup uses one native Core PowerShell shell, a 512-entry hard ceiling,
no-follow inventory, regular-file hashes, aliases-first removal and checked
empty directories. Every deletion uses nonrecursive `Remove-Item -LiteralPath`.
The exact target is absent. Precheck, actual result and raw stdout/stderr/exit
remain in the Root evidence leaf; physical reclaimed allocation is unmeasured.
No C file, source world, old refused target, sealed payload or other agent's
scratch is removed. The independent review's original cleanup failure is
separate and not rewritten by this successful Root cleanup. Its separately
verified correction removes only its two owned disposable targets, 43 entries
/47,570 regular bytes /one alias, with the original failure intact. This closes
that new scratch debt only, not historical tool-policy-refused C/D targets.
The author-copy cleanup remains incomplete: four files /55,219 bytes and the
checkout are retained after a metadata-precheck failure and a separate native
execution-policy refusal, both before deletion. The retirement helper is
syntax-checked only and never executed. No policy or shell fallback is attempted.

## Evidence and remaining verification

The Root leaf retains STAGES-SOURCE-AUTHOR-INTAKE-01,
SOURCE-AND-C17B-REVIEW-INTAKE-01, STAGES-SOURCE-PUBLICATION-01 and its raw ledger,
STAGES-FIXED-REPLAY-01 and its raw ledger, STAGES-FIXTURE-CLEANUP-PRECHECK-01,
STAGES-FIXTURE-CLEANUP-RESULT-01, and STAGES-TOOL-OBSERVATIONS-02. Original failing
collectors/read selections and earlier sealed evidence remain intact. No source
tree, JAR, world or duplicate ZIP is exported into this evidence.

C free space was 9,928,470,528 bytes at the Root precheck, below the 10 GB heavy-run
threshold. No Java/Gradle/GameTest/native/client command runs here. Earlier Java
tests remain at `a0873a30a2e1ad9fe0b42d11a0b89903fc478d5c`, not this Python SHA.
The historical C-script cleanup is still incomplete. File acquisition, native
origin/cohort/ownership, Java fixture, NBT/driver, real restart, R-021, content
delivery and all G0-G9 remain open. Type-valid report bytes are not independent
evidence that the reported action occurred.
