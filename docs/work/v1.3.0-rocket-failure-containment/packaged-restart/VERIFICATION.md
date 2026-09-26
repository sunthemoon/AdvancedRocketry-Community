# V130-ROCKET-01 local packaged persistence smoke

## Identity and scope

- Commit inspected before execution: `01bcb9222a436be6e4224274459db612d7880fe4`.
- Primary artifact: `D:/GitHub/AdvancedRocketry-Community/build/libs/advancedrocketry-community-1.20.1-1.3.0-dev.jar`.
- Primary and installed artifact SHA-256: `d9687101d868b3dca5ce45d263547835faed3a333cdcc1ad50ff7b6d6ed01394`.
- Java 17.0.7; Minecraft 1.20.1; Forge 47.4.10; Windows 11.
- Local isolated fresh world; bind `127.0.0.1:62235`, online-mode=false.
- Runtime copied read-only from the authorized installed `libraries` directory. All 104 copied files were SHA-256 compared; no world, mods, config or credentials copied.
- No installer/download, Gradle, client, online authentication, remote server or long load.
- No repository files changed by this verifier. Only new Temp files were written.
- Five server processes exited 0. The local port was confirmed no longer listening.

## Commands actually executed

PowerShell variables below abbreviate the exact fixed paths used by the executed commands:

```powershell
$P = 'D:/python/pyenv/pyenv-win/shims/python.bat'
$T = 'C:/Users/Administrator/AppData/Local/Temp/arce-v130-rocket-restart-6c762934334d46bc9c76bd782ba8d9b3'
$J = 'C:/Program Files/Java/jdk-17.0.7/bin/java.exe'
$R = 'D:/GitHub/AdvancedRocketry-Community'

& $P -B "$T/local_runner.py" prepare
# exit 0; prepared 104 runtime files, Java not started
& $P -B "$T/local_runner.py" baseline
# exit 0; captured output baseline-command.txt
& $P -B "$R/scripts/run_v050_rocket_server_smoke.py" "$T/server" --baseline-summary "$T/baseline-evidence/summary.json" --evidence-dir "$T/rocket-evidence" --java $J --expected-version 1.20.1-1.3.0-dev
# exit 0; captured output rocket-command.txt
& $P -B "$T/local_runner.py" postcheck
# exit 0; captured output postcheck-command.txt
& $P -B "$T/audit_logs.py"
# exit 0; captured output audit-command.txt
```

The runner's `resolve_java` invoked `java.exe -version` (exit 0). Existing
`run_cycle` launches used `-Xms512M -Xmx1024M -Djava.net.preferIPv4Stack=true
@libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt nogui`.
The unmodified rocket runner added
`-Dadvancedrocketrycommunity.releaseTestHooks=true`. The launch arguments and
executed gameplay commands are inspectable in the unchanged repository helpers.

Read-only preparation used `Get-Content`, `Get-ChildItem`, `rg`, `git status --short`,
`git branch --show-current`, `git rev-parse HEAD` and `Get-FileHash`.
Two exploratory `rg` calls included nonexistent glob/file names and returned 1;
corrected discovery located the existing SavedData class and bounded NBT reader.
This was not a behavior-test failure.

## Actual assertions and observations

| Server cycle | Exit | UTC start / complete | Result |
|---|---|---|---|
| first-start | 0 | 14:19:51 / 14:20:19 | Ready, status identity, save, clean stop |
| restart | 0 | 14:20:19 / 14:20:35 | Same world marker, status identity, save, clean stop |
| v050-rocket-assembled | 0 | 14:20:55 / 14:21:13 | Four blocks assembled, source cleared, operational entity |
| v050-rocket-persisted | 0 | 14:21:13 / 14:21:29 | Entity UUID, authority SNBT and snapshot hash equal after restart; pre-commit recovery record staged |
| v050-rocket-recovered | 0 | 14:21:29 / 14:21:46 | RECOVERED, entity absent, four blocks and chest contents restored |

- Entity UUID: `29cdf47c-c5c4-4e23-9ef5-39fc33f8ac48`.
- Snapshot hash: `feec1e1eddc6fb1978645ce389faed0b66de17cb8b85acbbd15afb24ada09a4c`.
- Recovery transaction: `c33828cd-23ec-48c2-80ee-0605512bbba5`.
- The observed schema fields are entity=2, flight=2, snapshot=1, TravelTarget=1.
- Snapshot container adapter is `advancedrocketrycommunity:vanilla_container_v1`.
- Restored chest's complete observed Items list is exactly 17 diamonds in slot 0
  and 64 iron ingots in slot 26.
- Read-only bounded gzip/NBT inspection of the real saved journal confirms
  DataVersion=3465, schema_version=2, format_epoch=`v0.9.0-beta`, transactions=[].
  The unchanged v050 script's unconditional journal-cleared summary flag was not
  treated as an independent assertion: this postcheck provides actual file evidence.
- Journal fixture SHA-256:
  `8a46b80b3382ec6617707abd54bc70482b5256675c2c73a504d68158707a75d4`.

## Logs and warning audit

All five full process logs are retained under `captures/`, not just selected
markers. Each log has zero ERROR, FATAL, client-linkage and project-logger findings
under the unchanged smoke scanner. WARN counts are 25 / 10 / 10 / 10 / 10.
Warnings are first-run Forge config/default creation, four Forge library
mods.toml notices, two union-resource URL notices, and four deliberate offline-mode
warnings per start. The unstructured terminal-capability warning is also retained.
No warning was suppressed or timeout/behavior assertion changed.
`log-audit.json` records complete warning lines, log hashes and helper hashes.

## Evidence inventory

- `local_runner.py`: one-off preparation/baseline/postcheck only.
- `audit_logs.py`: read-only log audit.
- `runtime-copy.json`: source and copied runtime file hashes.
- `baseline-command.txt`, `rocket-command.txt`, `postcheck-command.txt`, `audit-command.txt`.
- `baseline-evidence/summary.json`, `first-start.txt`, `restart.txt`.
- `rocket-evidence/summary.json`, `lifecycle.txt`.
- `journal-postcheck.json`, `recovered-rocket-transactions.dat`.
- `log-audit.json`.
- `captures/first-start-full.txt`, `restart-full.txt`,
  `v050-rocket-assembled-full.txt`, `v050-rocket-persisted-full.txt`,
  `v050-rocket-recovered-full.txt`.
- `captures/first-start-status.json`, `restart-status.json`,
  `server.properties.v002-startup`.
- `checksums.txt`: generated SHA-256 of the compact evidence bundle.
- Full disposable `server/` is intentionally not needed for repository import.

## Limits and Gate status

This is S1 local packaged clean-restart and test-hook-staged recovery evidence.
It is not forced process loss or power-loss durability (S2), a real-player join,
two-client synchronization, online authentication, external-adapter compatibility,
missing-mod recovery, cross-dimension flight, migration from an old artifact,
performance/soak or visual evidence. No full Required Gate is approved.
The unchanged script's `entity_nbt_restart_byte_equal` label compares the complete
authority SNBT returned by the server, not every byte in the entity region file.

Next work remains within V130-ROCKET-01 integration/review or the scoped next v1.3
adapter contract slice; inherited and complete v1.3 G0-G9 acceptance stays open.

