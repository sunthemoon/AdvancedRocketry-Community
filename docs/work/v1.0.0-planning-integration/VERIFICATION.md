# v1.0-v2.0 planning integration verification

## Scope and baseline

- Date: 2026-09-05; local Windows validation, not a v1.0 release audit.
- Branch: `docs/v1.0.0-v1plus-planning`.
- Base commit: `34b2e99` (`docs(release): accept v0.9.0 beta 1`).
- Input: the user-supplied `AdvancedRocketry-Community-v1plus-Development-Docs/`.
- Work: integrate 26 installable files and merge three snippets into canonical
  entry points; retain an inventory of all 33 original package files.
- Runtime baseline remains `0.9.0-beta.1`; Java, resources, dependencies, save
  formats, historical release evidence and approved version status are unchanged.
- No feature implementation, remote installation, commit, push, tag or release.

Source identity and adaptations are in the
[source record](../../provenance/v1.0.0-v1plus-planning.md) and
[implementation log](../v1.0.0-implementation-log.md).

## Host and command environment

- Java: `C:\Program Files\Java\jdk-17.0.7`; Gradle 8.8 from the existing wrapper.
- Bash: `C:\Program Files\Git\bin\bash.exe`; Linux-helper behavior uses isolated
  command stubs. No actual Debian desktop or VNC service was started.
- Final Python: `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe`, supplied
  by the user. The repository CI still selects Python 3.12.
- Python checks use canonical process-local `TEMP`/`TMP` equal to
  `C:\Users\Administrator\AppData\Local\Temp` and `PYTHONUTF8=1`.
  Global environment and the user's Python installation are not modified.
- Gradle checks use process-local `JAVA_HOME` and prepend its `bin` to `PATH`.
  Windows `gradlew.bat` executes the same tasks required by the version plan.

## Command results

The documentation integration checks are complete. Output is retained alongside
this report; this is development verification, not a v1.0 release approval.

| Command | Result | Evidence |
|---|---|---|
| `python scripts/validate_v1plus_planning.py --package-root AdvancedRocketry-Community-v1plus-Development-Docs` | Exit 0: 11 plans, explicit G0-G9, 33 original source hashes/sizes | [Log](planning-validation.txt) |
| `bash -n` for each of the three Linux helpers | Exit 0 | [Log](bash-syntax.txt) |
| `python scripts/validate_repository.py --require-approved-identity` | Exit 0: 46 PASS, zero pending/warnings/failures | [Log](governance.txt) |
| `python -u -m unittest discover -s tests -p 'test_v1plus*.py' -v` | Exit 0: 25 tests, 22.188 s, no skips/failures | [Log](planning-and-helper-tests.txt) |
| `python -u -m unittest discover -s tests -v` | Exit 0: 706 tests in 3466.063 s; 702 passed, 4 skipped | [Log](python-suite.txt), [invocation record](python-suite-result.json) |
| `.\gradlew.bat clean build` | Initial download timeout; retry BUILD SUCCESSFUL in 14m 48s | [Initial failure](clean-build-initial.txt), [retry](clean-build.txt) |
| `.\gradlew.bat runData` | Exit 0: BUILD SUCCESSFUL in 5m 14s; generated-resource diff exit 0 | [Log](runData.txt) |
| `.\gradlew.bat test` | Exit 0: BUILD SUCCESSFUL in 15s, UP-TO-DATE; clean build ran 273 tests with zero failures/errors/skips | [Log](test.txt), [JUnit summary](java-test-summary.json) |
| `.\gradlew.bat runGameTestServer` | Exit 0: all 44 required tests passed, BUILD SUCCESSFUL in 1m 41s | [Log](runGameTestServer.txt) |
| `git diff --check` | Exit 0 | [Git checks](git-checks.txt) |
| `git diff --exit-code` | Exit 1: intentional uncommitted documentation changes; not a clean-tree PASS | [Git checks](git-checks.txt) |
| `git diff --exit-code -- docs/status docs/releases src gradle.properties build.gradle` | Exit 0; scoped status also empty | [Git checks](git-checks.txt) |

The four Python skips are the existing optional local-artifact checks for
v0.2.0, v0.3.0, v0.4.0 and v0.5.0: their historical build JARs are not present
in this checkout's `build/libs`. No skip, assertion, timeout or legacy test was
modified. Historical committed release evidence passed its separate checks.
The 25 added tests had no skips. CI's Python 3.12 and actual Debian execution
were not reproduced locally; the recorded full-runtime run used Python 3.13.15.

The [changed-file inventory](changed-files.txt) includes tracked edits and new
repository files, excluding the untouched original input directory. The
[log checksums](log-checksums.txt) bind archived text/JSON evidence, not a
release JAR or an approved candidate commit.

## Interrupted and failed attempts

1. The first wrapper download failed with a 10000 ms read timeout. A distribution
   fetched separately from `downloads.gradle.org` matched the wrapper's pinned
   SHA-256 `a4b4158601f8636cdeeab09bd76afb640030bb5b144aafe261a5e8af027dc612`.
   Only the local wrapper cache was populated; no repository URL, timeout,
   dependency or verification setting was changed. The subsequent build passed.
2. Before the user's Python environment was available, checks used a temporary
   python.org Python 3.12.10 embeddable runtime. An initial whole-suite attempt
   encountered a Windows short/long TEMP-path mismatch; the same targeted test
   passed with canonical process-local TEMP/TMP. The task-owned initial run was
   stopped and its partial failure output retained.
3. The canonical-TEMP whole-suite retry exposed embedded-runtime incompatibility:
   forced isolated mode and ZIP-based standard-library origins conflict with
   existing review-packet security checks. No legacy assertion or security code
   was changed. That retry and the first `runData` download were interrupted
   before completion; neither is reported as a passed test run. Subsequent
   process/handle checks confirmed they had terminated before restarting work.
4. The added planning/helper tests passed under the temporary runtime (25 cases,
   21.943 s). The final whole-suite run uses the supplied full Python interpreter
   and the final helper revision, including `x11vnc -norc`.

Retained Python diagnostics: [short TEMP attempt](python-embedded-short-temp-interrupted.txt),
[canonical TEMP attempt](python-embedded-canonical-temp-interrupted.txt), and
[ZIP-runtime review-packet error](python-embedded-zip-runtime-error.txt).
The original interrupted asset-download log remains in the temporary task log
directory; the completed retry above is the authoritative `runData` result.
The [Gradle invocation record](gradle-results.json) retains start/end timestamps
and exit codes for the resumed sequential tasks.

## Acceptance boundary

The documentation slice does not satisfy all v1.0 Required Gates. There is no
v1.0 candidate, independent release audit, fresh physical-GPU report, two-real-
client evidence, packaged-server/restart/recovery campaign, migration campaign,
soak report or release approval from this task. Shell mocks are not V0/V1/V2
acceptance evidence. Original v0.9 approval is neither renewed nor revoked by
these local development checks.

All eleven plans remain `PLANNED`. The next v1.0 implementation task is the
feature-freeze and code/evidence gap audit, including stale core rows in
`PORTING_MATRIX.md` and the fresh visual evidence required when ADR-013 expires.
