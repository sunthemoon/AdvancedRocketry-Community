# Current-artifact CI verification commands

All commands ran in the uncommitted v1.0 development worktree on Windows,
using the existing Java 17 and pyenv installation. Runtime/test data stays in
the disposable `build` directories and the external work directory below.

```powershell
$python = 'D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe'
$root = 'C:\Users\Administrator\AppData\Local\Temp\arce-v100-ci'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17.0.7'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:PYTHONUTF8 = '1'
$env:TEMP = 'C:\Users\Administrator\AppData\Local\Temp'
$env:TMP = $env:TEMP

& $python -m unittest tests.test_ci_artifact_identity -v
& $python -m unittest tests.test_ci_artifact_identity tests.test_validate_repository.WorkflowStructureTests -v
& $python -m unittest tests.test_ci_artifact_identity tests.test_validate_repository -v
& $python scripts/validate_repository.py --require-approved-identity
.\gradlew.bat clean build --no-daemon --stacktrace
& "$root\actionlint\unpacked\actionlint.exe" -version
& "$root\actionlint\unpacked\actionlint.exe" -no-color .github/workflows/forge-bootstrap.yml .github/workflows/repository-docs.yml
& $python "$root\run_remaining_python.py"
& $python "$root\run_workflow_local.py" baseline
```

The first new workflow test runs before workflow wiring and fails in all three
jobs. An initial negative-test helper boundary mistake is retained as a failed
execution. After correction, all 148 repository/CI tests pass. The inherited
short-form Windows `TEMP` is used only in the separately retained failed
128-case invocation; the full-form environment above is used for the successful
148-case and remaining 569-case partitions. No test assertion is relaxed.

The extended bootstrap command began in the preceding compatibility slice and
completed during CI verification:

```powershell
& $python -m unittest tests.test_validate_bootstrap_provenance tests.test_bootstrap_version_dispatch -v
```

It returns 0 after 95 passing cases in 2664.700 seconds; the original bundle
retains its complete log. The remainder driver enumerates all root-level
`tests/test_*.py` modules, rejects unexpected nested tests/import errors and
excludes only the four explicitly recorded modules already run in the 95- and
148-case partitions. Its 569-case run returns 0 with four existing absent-JAR
skips, so the disjoint inventory accounts for all 812 cases.

The local workflow driver reads the current workflow rather than maintaining a
second list of commands. Each job's `summary.json` records the original workflow
argument array, actual Windows argument array, elapsed time, exit code, full-log
hash and available upload-file inventory. The environment-file helper is actually
invoked; its derived values feed the artifact and packaged-server commands.
Setup/checkout/upload actions are not emulated or reported as executed.

The baseline driver's overall exit remains nonzero when the two clean-worktree
checks fail on uncommitted files. It continues after those two checks solely for
independent local testing of the remaining commands; any other failure stops the
sequence. This is not a passing remote CI job. Follow-on core/latest execution
waits for the baseline driver to terminate and rejects unexpected failures before
starting another `clean build`, so active runtimes/build outputs are not deleted.

The independent workflow checker comes from the official
[actionlint 1.7.12 release](https://github.com/rhysd/actionlint/releases/tag/v1.7.12).
`curl.exe --fail --location --proto '=https' --tlsv1.2 --max-time 120` downloads
the published checksum list and Windows AMD64 ZIP to the external work directory;
the exact SHA-256 is verified before extraction/execution. No downloaded tool
binary is imported into the repository or added to CI dependencies.

Remaining job and final audit results are recorded only after completion in the
[verification report](VERIFICATION.md).

## Complete-log correction

The first core/latest commands complete successfully, but the subsequent full-log
audit fails because the workflow omitted session log files from its uploads.
The first archives and failed audit are retained; omitted originals are not
recreated or replaced with another execution's output. After adding the explicit
log upload entries and a driver completeness check, these commands are executed
serially in new work directories:

```powershell
& $python -m unittest tests.test_ci_artifact_identity tests.test_validate_repository -v
& "$root\actionlint\unpacked\actionlint.exe" -no-color .github/workflows/forge-bootstrap.yml .github/workflows/repository-docs.yml
& $python "$root\run_workflow_local.py" baseline attempt-2
& $python "$root\run_workflow_local.py" satellite-acceptance attempt-2
& $python "$root\run_workflow_local.py" latest-compatibility attempt-2
```

The fresh 148-case run and static checker return 0. All three second-attempt
sequences complete with full archives; baseline retains exit 1 for dirty-worktree
checks, core/latest return 0. The coordinator checks outcomes and
`archive_complete` before each subsequent clean build.

```powershell
& $python "$root\audit_ci_results.py"
.\gradlew.bat clean build --no-daemon --stacktrace
```

The final strict audit returns 0 and verifies every referenced full process log;
the default-Forge restoration build returns 0 in 19 seconds. Executed driver and
auditor copies are retained separately from first-attempt scripts. These are
completed local development checks, not hosted CI or a four-hour load run.
