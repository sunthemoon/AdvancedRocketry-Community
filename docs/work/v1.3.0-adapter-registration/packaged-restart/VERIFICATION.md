# V130-ROCKET-02 independent packaged-host smoke

## Artifact and scope

- Production commit: `5466fd61824da969e62c493a588aa812a47e3d44`.
- Artifact: `advancedrocketry-community-1.20.1-1.3.0-dev.jar`.
- SHA-256: `3f8be1e0adce70b1c25fc1eeeffeac5b7f398016abf6670af11b42c52c84c200`.
- Windows, Java 17.0.7, Forge 47.4.10, Minecraft 1.20.1; heap 512-1024 MiB.
- Fresh disposable world, one host mod only; loopback `127.0.0.1:65240`, offline.
- No repository edits, Gradle task, external fixture mod, real client or remote
  server was used. The production constructor/common-setup change was exercised
  through packaged loading and the actual rocket command/tick/recovery services.

Preparation copied only 104 checked runtime library files from the prior local
runtime into a new unique Temp directory. Every copied file was SHA-256 checked.
No prior world, mods or configuration was copied. Java started only after the
integrator released the shared runtime resource. The final host JAR was copied
and checked against the specified hash before starting the first server.

## Commands and observations

Exact argument vectors and exit codes are in `commands.json`.

1. `local_runner.py prepare`: exit 0; runtime-copy manifest and helper hashes.
2. `local_runner.py baseline`: exit 0; fresh start, status identity, save/stop,
   same-world restart, status identity, save/stop. Both Java exits were 0.
3. Unmodified repository `scripts/run_v050_rocket_server_smoke.py`: exit 0;
   three Java exits were 0. Actual command/tick assembly created a four-block
   rocket. Its UUID, snapshot hash and full authority SNBT matched after restart.
   The existing release-test command staged a pre-commit recovery record; the
   next clean restart recovered the blocks/container and removed entity authority.
4. `local_runner.py postcheck`: exit 0; read the actual on-disk SavedData through
   the bounded repository NBT reader. DataVersion 3465 was validated, journal
   schema was 2 and `transactions` was an empty list. Exact recovered inventory
   was slot 0: 17 diamonds, slot 26: 64 iron ingots, with no additional entries.
5. `audit_logs.py`: exit 0; audited all five complete process logs, checked the
   helper hashes, and confirmed the loopback port no longer accepted connections.

The journal bytes are retained as `recovered-rocket-transactions.dat`, SHA-256
`8a46b80b3382ec6617707abd54bc70482b5256675c2c73a504d68158707a75d4`.
`journal-postcheck.json` contains the observed journal and exact inventory.
The independent postcheck does not trust the historical smoke summary's hardcoded
`durable_journal_cleared` field as evidence: it checks actual disk NBT separately.

Observed entity/flight/snapshot/travel-target schemas were 2/2/1/1; the vanilla
adapter remained `advancedrocketrycommunity:vanilla_container_v1`.

## Logs and termination

All five complete logs reported zero ERROR, FATAL and client-linkage findings;
project logger ERROR/WARN/FATAL counts were zero. Copies under `captures/` are
byte-identical to the original process outputs retained under `server/`.
The runtime reported no remaining listener, and a subsequent read-only process
query found no `java.exe`. Only owned child processes were managed; no process
outside this test was stopped.

The runs were not warning-free. Retained warnings include initial default-config
creation, Forge language-library metadata, union resource URLs, loopback offline
mode and terminal capability notices. In the assembly phase there was one
Minecraft `Can't keep up` warning (2733 ms / 54 ticks) immediately following the
script's fresh-chunk forceload/setup/assembly sequence. No repeat occurred in the
following persistence/recovery phases. Its precise cost attribution was not
profiled, so this smoke does not establish performance budget compliance.

## Limits and Gate status

This is a short packaged-host startup/restart and staged recovery check, not a
crash/power-loss test or proof of disk fsync atomicity. The script intentionally
forceloads its test chunk and removes that ticket at cleanup; this does not prove
an arbitrary no-forced-chunks workload. It does not exercise external-provider
packaging/uninstall/reinstall, cross-dimension transport, real clients, multiplayer
or full G0-G9 acceptance. No Gate or version status is approved here.

Next: preserve this finite host evidence with V130-ROCKET-02; packaged external
provider and uninstall/reinstall coverage remain in V130-ROCKET-03.
