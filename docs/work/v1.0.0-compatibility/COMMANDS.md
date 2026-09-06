# Packaged compatibility commands

Executed on Windows on 2026-09-05. All work/server/client directories below are
new disposable directories. Commands never inspect PCL accounts. `prepare-1`
and attempts 1-8 retain distinct outputs and executed sources; they are not
overwritten to present a successful first run.

```powershell
$python = 'D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe'
$java = 'C:\Program Files\Java\jdk-17.0.7\bin\java.exe'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17.0.7'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:PYTHONUTF8 = '1'
$env:TEMP = 'C:\Users\Administrator\AppData\Local\Temp'
$env:TMP = $env:TEMP
$root = "$env:TEMP\arce-v100-compatibility"
$jar = "$root\artifacts\advancedrocketry-community-1.20.1-1.0.0-dev.jar"

& $python scripts/v100_compatibility_runtime.py "$root\runtime-1" `
  --java $java --asset-cache 'C:\Users\Administrator\.gradle\caches\forge_gradle\assets'

& $python scripts/run_v100_compatibility_matrix.py `
  --runtime "$root\runtime-1" --work "$root\matrix-7-work" `
  --evidence "$root\matrix-7-evidence" --artifact $jar `
  --version 1.20.1-1.0.0-dev --java $java

& $python scripts/run_v100_compatibility_matrix.py `
  --runtime "$root\runtime-1" --work "$root\matrix-8-work" `
  --evidence "$root\matrix-8-evidence" --artifact $jar `
  --version 1.20.1-1.0.0-dev --java $java
```

Preparation and attempts 7/8 return 0. Attempts 1-4 return 1 for harness
defects. Attempt 5 returns 0 under its preliminary checker but contains the
invalid-option ERROR and is not promoted to the final matrix. Attempt 6 returns
1 for a handshake timeout. See [the failure table](VERIFICATION.md).

The initial Python urllib request to the Forge checksum endpoint returned
HTTP 403. Curl could access the official files; installer hashes were checked
against the official download page before execution. There was no alternative
unverified mirror import or dependency-version change.

```powershell
.\gradlew.bat clean build --no-daemon --stacktrace
.\gradlew.bat runData --no-daemon --stacktrace
git diff --exit-code -- src/generated
.\gradlew.bat runGameTestServer --no-daemon --stacktrace

# The initially unquoted -P argument selected "47" and failed configuration.
.\gradlew.bat '-Pforge_version=47.4.23' clean build --no-daemon --stacktrace
.\gradlew.bat '-Pforge_version=47.4.23' runGameTestServer --no-daemon --stacktrace

& $python -m unittest discover -s tests -p 'test_run_v*.py' -v
& $python -m unittest tests.test_v100_compatibility_matrix -v
& $python scripts/validate_build_artifact.py $jar `
  --expected-version 1.20.1-1.0.0-dev --content-manifest "$root\checks\artifact-manifest.json"
& $python scripts/validate_repository.py --require-approved-identity
& $python scripts/validate_v1plus_planning.py
```

Both correctly selected builds, both GameTest commands, baseline DataGen and
its diff return 0. Java: 362 tests per lane; GameTest: 44 per lane. Python:
101 server-harness cases and 27 compatibility cases. Strict repository check:
45 pass, no pending, warnings or failures. Planning: 11 plans / 33 unchanged
source inputs. The exact same artifact was also audited from the latest-build
copy, with a separate content manifest.

## Final checks

```powershell
.\gradlew.bat '-Pforge_version=47.4.23' runData --no-daemon --stacktrace
git diff --exit-code -- src/generated
.\gradlew.bat clean build --no-daemon --stacktrace
& $python -m unittest tests.test_v100_compatibility_matrix tests.test_collect_v090_compatibility_evidence -v
& $python scripts/check_client_imports.py
& $python scripts/check_celestial_identity.py
& $python scripts/validate_v1plus_planning.py
git diff --check
git diff --exit-code -- src/generated
git diff --exit-code -- docs/releases
git diff --exit-code
```

Latest DataGen and restoration of the default build return 0 and preserve the
same JAR. Final targeted compatibility tests: 30 pass; together with server
discovery this is 131 distinct cases. Boundary/planning checks and the first
three Git checks return 0. Full Git diff returns 1 for the intentionally
uncommitted development work. No project commit/push/tag command is executed;
provenance tests create Git commits only in their disposable fixtures.

Read-only Python audits recheck all source inputs, runtime asset-index hashes,
all 16 final process log hashes, native logs, installed mod inventories, server
join/leave order, metadata and relative work-document links. Their generated
reports and bundle checksums are retained beside the command logs.

## Bootstrap CLI dispatch regression

```powershell
& $python scripts/validate_bootstrap_provenance.py
& $python -m unittest tests.test_bootstrap_version_dispatch -v
# Narrow the historical-version selection fix; preserve prior review records.
& $python -m unittest tests.test_bootstrap_version_dispatch tests.test_v100_compatibility_matrix tests.test_collect_v090_compatibility_evidence -v
& $python scripts/validate_bootstrap_provenance.py --require-approved-review
& $python -m unittest tests.test_validate_bootstrap_provenance tests.test_bootstrap_version_dispatch -v
```

The first CLI invocation and pre-fix dispatch tests fail and are retained.
The post-fix 35-case run and actual strict CLI invocation return 0; the latter
validates accepted commit `9359257b9fe1eccf7e0043dfa7f626cf1ee44be9`, not the
v1.0 worktree as a v0.0.2 import. The broader 95-case bootstrap suite completed
with exit 0: all 95 pass in 2664.700 seconds. Its full log is archived as
`verification/checks/bootstrap-full-tests.txt`. These include the five routing
cases already counted in the 35-case run: the distinct completed targeted total
for this slice is 226, not 231. This completion does not itself update the
workflow artifact consumers or approve v1.0 provenance; the separate CI task
is tracked in the active implementation log.
