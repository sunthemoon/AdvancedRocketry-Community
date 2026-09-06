# Executed packaged-server commands

Working directory: `D:\GitHub\AdvancedRocketry-Community`.
All targets are fresh task-owned directories. The copy helper checks resolved
source/session/evidence separation before copying or removing copied logs.
Original servers and previous failed evidence are preserved.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17.0.7'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:TEMP = 'C:\Users\Administrator\AppData\Local\Temp'
$env:TMP = $env:TEMP
$env:PYTHONUTF8 = '1'
$python = 'D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe'
$task = "$env:TEMP\arce-v100-transactions"
$previous = "$env:TEMP\arce-v100-stabilization"
$base = '34b2e99b48a33f4ba8905b6a69a38efee1649d3f'
$jar = 'D:\GitHub\AdvancedRocketry-Community\build\libs\advancedrocketry-community-1.20.1-1.0.0-dev.jar'
```

## Pre-fix reproduction

The first invocation used the pre-fix JAR SHA `65b10b88...`:

```powershell
& $python scripts/run_v100_transaction_forced_stop.py "$previous\baseline-server" --session-dir "$task\smoke-server" --baseline-summary "$previous\baseline-evidence\summary.json" --evidence-dir "$task\smoke-evidence" --artifact $jar --base-commit $base --case ASSEMBLY:EXTRACTING:2 --case ASSEMBLY:SPAWNED:5 --case DISASSEMBLY:RESTORING:2
```

Exit 1 after EXTRACTING:2 passed and SPAWNED:5 failed. DISASSEMBLY was not
executed by that failed invocation. A second invocation changed only output
directories to `diagnostic-server` / `diagnostic-evidence`, selected only
`ASSEMBLY:SPAWNED:5`, and used the archived diagnostic harness with the same
pre-fix JAR. It also returned 1, retaining read-only block/inventory diagnostics.

## Complete fixed-JAR transaction matrix

After `clean build` produced SHA `cf077ef7...`:

```powershell
$cases = & $python -c "from scripts.run_v100_transaction_forced_stop import CASES; print('\n'.join(dict.fromkeys(['ASSEMBLY:SPAWNED:5', 'DISASSEMBLY:RESTORING:2', *CASES])))"
$caseArgs = @()
foreach ($case in $cases) { $caseArgs += @('--case', $case) }
& $python scripts/run_v100_transaction_forced_stop.py "$previous\baseline-server" --session-dir "$task\matrix-server" --baseline-summary "$previous\baseline-evidence\summary.json" --evidence-dir "$task\matrix-evidence" --artifact $jar --base-commit $base @caseArgs
```

Exit 0; all 25 cases execute once. The priority order retests the observed
failure first; it does not omit any cases or change their assertions.

## Ordinary fixed-JAR lifecycle and flight regression

```powershell
& $python -u scripts/run_dedicated_server_smoke.py $jar --expected-mod-version 1.20.1-1.0.0-dev --work-root "$task\dedicated-work" --session-dir "$task\ordinary-server" --evidence-dir "$task\ordinary-baseline" --port 25611
& $python -u scripts/run_v060_flight_server_smoke.py "$task\ordinary-server" --baseline-summary "$task\ordinary-baseline\summary.json" --evidence-dir "$task\ordinary-flight" --expected-version 1.20.1-1.0.0-dev
```

Both return 0: two dedicated lifecycle processes, then 17 ordinary flight/
restart processes covering 20 round trips and eight clean restart checkpoints.
Transaction pause/fault properties are absent.

## Fixed-JAR flight forced-stop regression

```powershell
& $python -u scripts/run_v100_flight_forced_stop.py "$task\ordinary-server" --session-dir "$task\fixed-flight-server" --baseline-summary "$task\ordinary-baseline\summary.json" --evidence-dir "$task\fixed-flight-evidence" --base-commit $base
```

Exit 0; all ten cases pass across 20 owned processes. It uses a new copy only
after the ordinary server has stopped, with the same verified JAR and loopback
port 25611.
