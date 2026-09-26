# External-container flight check

## Run

Use Python 3.11+, an explicit JDK 17 executable, a fresh disposable server directory
containing only the prepared Forge 1.20.1-47.4.10 libraries, and a nonexistent
evidence directory outside it. Input JARs must be outside both output directories.

```powershell
python -B scripts/run_v130_adapter_flight_smoke.py C:/Temp/disposable-server `
  --host-jar C:/Temp/artifacts/advancedrocketrycommunity-1.20.1-1.3.0-dev.jar `
  --fixture-jar C:/Temp/artifacts/arce-adapter-test-1.0.0.jar `
  --evidence-dir C:/Temp/new-flight-evidence `
  --java C:/Java/jdk-17/bin/java.exe --accept-eula
```

No installer or download is invoked. The server binds loopback in offline mode
with an allocated port and no online players. Startup is bounded to 240 seconds;
the optional `--startup-timeout` may lower, not raise, that bound. Flight marker
waits are 30 seconds, assembly/disassembly 45, save 60, owned-process stop 90.

The normal fixture provider must remain enabled in all four processes. The runner
does not use the fixture skip property or GameTest mode. Operator-only fuel and
launch hooks are explicitly enabled; this is not player fuel-loader coverage.

## Evidence

`summary.json`, `failure.json` on failure, and `SHA256SUMS` summarize the run.
Each `moon-landing`, `earth-landing`, `disassembly`, `container-restart` directory
contains its result/exit code, launch and command records, actual status, stdout,
debug/latest logs, active config, decoded disk state and unchanged native files.
Configuration comparison excludes only the validated generated second-line Java
timestamp in `server.properties`; all other properties bytes and both TOMLs must
remain identical. Raw configuration bytes and their hashes are always retained.
Landing directories also contain the full `RocketEntityData.snbt` observation.
The next process compares that complete data and the saved transfer inspection
receipt before launching or disassembling. Failure results and raw logs remain
available even if startup or a marker wait fails.

The complete external payload and inventory must match the fixed fixture exactly.
One fuel fill supplies both legs, with 628 and 256 remaining at the respective
landings. Final cargo restoration and restart are separate from the known absence
of a fuel export/refund when the fueled entity is disassembled.

With ROCKET-04 hosts, the disassembly phase also rejects implicit disposal and
an incorrect expected amount, checks unchanged entity/transfer/cargo authority,
then supplies `discard-fuel 256` explicitly. The exact disposal receipt is checked;
this remains intentional disposal, not fuel conservation or player-click coverage.
Older host artifacts predate this command contract; use their matching historical
runner when reproducing their archived evidence.

## Focused checks

```powershell
python -B -m unittest discover -s tests -p test_v130_adapter_flight_smoke.py -v
python -B scripts/run_v130_adapter_flight_smoke.py --help
git diff --check
```

The 20-test focused run passed after the timestamp regression correction. Independent packaged runtime
results must be recorded separately; the script's existence and Python test results
do not approve release gates or imply Minecraft execution has passed.
