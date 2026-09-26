# V130-ROCKET-03A independent packaged-consumer loading

## Result and immutable inputs

The two-JAR production dedicated runtime passed fresh startup and a clean
same-world restart. Both owned Java processes exited normally with code 0.

- Integration commit: `e2391891e101a7e2e31493b1923a49466c793c8f`.
- Host: `advancedrocketry-community-1.20.1-1.3.0-dev.jar`, 2171579 bytes,
  SHA-256 `3f8be1e0adce70b1c25fc1eeeffeac5b7f398016abf6670af11b42c52c84c200`.
- Separate fixture: `arce-adapter-compat-test-1.0.0.jar`, 19304 bytes,
  SHA-256 `0432704f79ea3c85d5af64bba4aedd1f0a5fb7dfafeb7ee83ff99468e23e70a8`.
- Java 17.0.7, Forge 47.4.10, Minecraft 1.20.1; normal `forgeserver` launch,
  heap 512-1024 MiB, no development/GameTest mode overrides.
- Loopback `127.0.0.1:52949`, offline, no clients. The port was closed after the
  second clean save/stop; no outside process was terminated.

Only the 104 hash-checked runtime library files were copied into a new unique
Temp directory. No prior world, mod or configuration was reused. Both final JARs
were copied and SHA-256 checked before Java started, and their source/installed
hashes were checked again after both cycles.

## Actual commands

Using `D:/python/pyenv/pyenv-win/shims/python.bat`:

1. `-B packaged_consumer_runner.py prepare`: exit 0, no Java launch.
2. After the integrator supplied final artifacts and released the run resource,
   `-B packaged_consumer_runner.py run`: exit 0, two Java exits 0.
3. Read-only evidence/artifact/source postcheck: exit 0. Full commands and artifact
   paths are in `commands.json` and `artifact-spec.json`.

The runner reuses the unmodified repository `run_dedicated_server_smoke.py`
process/status/save/stop primitives. It separately checks the consumer's status
version, registered-event INFO, real JAR discovery/load receipts and general
linkage failures. The repository helper's hash is recorded and was stable.

## Observed packaged behavior

For both processes, the complete stdout and debug captures show:

- Actual `Loading mod file .../server/mods/advancedrocketry-community-...jar`
  and `Loading mod file .../server/mods/arce-adapter-compat-test-1.0.0.jar` entries.
- Forge valid-mod metadata associates those distinct JARs with the expected
  IDs/versions; the Minecraft status response independently reports both.
- Exactly one successful fixture registration INFO with adapter
  `arce_adapter_test:cargo_inventory`, payload 1, API 1.1 and event count 1.
  That observation is emitted after `event.register` returns. The fixture's
  constructor minimum-API check therefore also allowed this host.
- Forge creates the fixture's `mod:arce_adapter_test` resource pack from its
  independent installed JAR. No block-placement or inventory-callback outcome
  is inferred from pack availability.
- Zero ERROR/FATAL and no `NoSuchMethodError`, `AbstractMethodError`,
  `NoClassDefFoundError`, `ClassNotFoundException`, `IncompatibleClassChangeError`
  or `LinkageError` in either process's complete stdout/debug capture.

The created world retains the harness identity marker, exact startup properties
identity and `level.dat` across restart. These are actual files hashed by the
runner, not a summary flag alone.

## Warning review

Recognized warning-line counts are 25 on initial startup and 10 on restart,
plus one plain-format terminal capability warning in each stdout. They are
initial default-config creation, Forge language-library `mods.toml` notices,
union resource URLs, terminal limitations and the intentionally loopback-only
offline-mode notices. No tick-lag warning occurred in these two short cycles.
This observation is not a performance benchmark or G7 approval.

## GameTest boundary and unverified scope

The cached Forge 47.4.10 `ForgeGameTestHooks` source requires
`!FMLLoader.isProduction()` in both `isGametestEnabled` and `isGametestServer`.
Consequently, adding `forge.enableGameTest` or `forge.gameTestServer` alone does
not make the existing fixture GameTests available in this normal packaged
runtime. No development override, injected command framework or modified Forge
was used; no packaged `test run` execution is claimed.

This is the bounded ROCKET-03A packaged-loading check, not ROCKET-03B. It does
not establish capture/restore callback execution, external inventory persistence,
provider uninstall/reinstall, missing-provider recovery, crash durability,
cross-dimension travel, real clients or complete G0-G9 acceptance. No repository
files were edited and no Gradle task was run by this verifier.

## Evidence

`summary.json` and per-cycle observation JSON link the two complete stdout logs,
two complete debug logs and two raw status responses under `captures/`.
`installed-artifacts.json`, `preparation.json`, `artifact-spec.json`,
`commands.json`, `source-evidence.json` and `checksums.txt` preserve identities.
The original disposable server/world remains under `server/`; exclude that
large directory when archiving the self-contained evidence set.

Next: retain this finite evidence with ROCKET-03A. Execute external payload
restart and missing-provider preservation as the separately scoped ROCKET-03B.
