# Executed Beta-world verification commands

All commands ran from `D:\GitHub\AdvancedRocketry-Community`. All work/evidence
directories were new; the harness rejects overlap, links, extra mods, a live
loopback port and existing outputs. It aborts only processes it starts.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17.0.7'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:TEMP = 'C:\Users\Administrator\AppData\Local\Temp'
$env:TMP = $env:TEMP
$env:PYTHONUTF8 = '1'
$python = 'D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe'
$root = "$env:TEMP\arce-v100-upgrade"
$jar = 'D:\GitHub\AdvancedRocketry-Community\build\libs\advancedrocketry-community-1.20.1-1.0.0-dev.jar'
$base = '34b2e99b48a33f4ba8905b6a69a38efee1649d3f'
$out = 'docs/work/v1.0.0-beta-world-upgrade/verification'
```

## Real world capture and first upgrade sequence

```powershell
& $python -u scripts/run_v100_beta_world_upgrade.py "$root\beta-baseline-server" --baseline-summary "$root\beta-baseline-evidence\summary.json" --work-dir "$root\populated-final-work" --evidence-dir "$root\populated-final-evidence" --artifact $jar --base-commit $base
```

Exit 0; Beta capture plus three development processes. Earlier invocations used
the same source/arguments with `populated-first`, `populated-second` and
`populated-third` instead of `populated-final`. They returned 1, 1 and 0,
respectively. Every attempt's raw output and executed source revision is retained
under `verification/`; corrections are explained in [the report](VERIFICATION.md).

## Reuse the unchanged original with a new candidate copy

```powershell
& $python -u scripts/run_v100_beta_world_upgrade.py "$root\populated-final-work\beta-fixture-server" --baseline-summary "$root\populated-final-evidence\summary.json" --fixture-summary "$root\populated-final-evidence\summary.json" --work-dir "$root\reuse-work" --evidence-dir "$root\reuse-evidence" --artifact $jar --base-commit $base
```

Exit 0; three more candidate processes. Original manifest, archive and setup
provenance are reused; only the new disposable server receives the tested JAR.
The original and both archives are checked by content after all runs stop.

## Automated verification

```powershell
& $python -m py_compile scripts/v100_beta_world_fixture.py scripts/inspect_v100_world_upgrade.py scripts/run_v100_beta_world_upgrade.py
& $python scripts/run_v100_beta_world_upgrade.py --help
& $python -m unittest discover -s tests -p test_run_v100_beta_world_upgrade.py -v
& $python -m unittest discover -s tests -p 'test_run_v*.py' -v
.\gradlew.bat clean build
.\gradlew.bat test --rerun-tasks
.\gradlew.bat runData
git diff --exit-code -- src/generated
.\gradlew.bat runGameTestServer
& $python scripts/validate_build_artifact.py $jar --expected-version 1.20.1-1.0.0-dev --content-manifest "$out/artifact-manifest.json"
& $python scripts/check_client_imports.py
& $python scripts/check_celestial_identity.py
& $python scripts/validate_repository.py --require-approved-identity
```

All return 0. Standalone upgrade tests initially ran 20 and then 21 cases;
server-harness discovery ran 99, 100 and finally 101 as geometry, live-port and
archive-reuse checks were added. Final discovery includes all 25 new cases.
The complete pre-existing Python suite was not rerun by this slice.

## Final documentation and evidence checks

```powershell
& $python scripts/validate_v1plus_planning.py --package-root AdvancedRocketry-Community-v1plus-Development-Docs
git diff --check
git diff --exit-code -- docs/releases
git diff --exit-code -- src/generated
git diff --exit-code
```

Planning and the first three Git checks return 0. Full Git diff returns 1 for
the intentionally uncommitted development changes. Inline read-only Python
audits additionally re-read every archived world entry, original-world file,
runtime log and observed state; recompute the six conservation comparisons;
verify 718 source input hashes; and check links in active work documents.
Generated JSON audit results and complete checksum inventories are archived
beside the logs. No Git commit, push, tag or approval command was executed.
