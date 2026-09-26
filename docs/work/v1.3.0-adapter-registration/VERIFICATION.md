# V130-ROCKET-02: frozen public rocket adapter registration

## Scope and implementation

The public API now supports bounded, owner-bound registration of external rocket
container adapters. API minor is 1.1; the mod remains `1.20.1-1.3.0-dev`.
This implements [ADR-022](../../decisions/ADR-022-ROCKET-ADAPTER-REGISTRATION.md),
not the entire v1.3 compatibility scope or its release Gates.

- Baseline: `c46159bc9819d975114cc545dce426dca0290092`.
- Contract: `b0ea71f8ffa5465dc9f94ad84e5fa66bcbf65bba`.
- Independent API tests: `82fb746`; isolated development mod: `ea92e90`;
  registry/envelope/budget tests: `98293bb`.
- Production/build integration: `5466fd61824da969e62c493a588aa812a47e3d44`.

New production types are the three `api.rocket` types and the internal
`RocketAdapterRegistry`, `RocketAdapterPayloads` and `ExternalRocketBlockEntityAdapter`.
Existing changes are common setup in `AdvancedRocketryCommunity`, API version
metadata, internal restore preflight and classifier/test-source-set packaging.
All additions are original community work; no upstream code or assets were copied.

### Design boundaries

1. Each mod receives its own synchronous registration event during queued common
   setup. Ownership comes from the receiving ModContainer. Invalid ownership,
   duplicate/reserved IDs/types, unknown types and capacity excess fail loading.
   Old handles, another thread and post-freeze calls cannot modify the catalog.
2. The production manager and its event listeners are installed together after
   freezing the catalog. Existing vanilla matching stays first. Registration does
   not bypass movable/forbidden tags, permissions, loaded-region checks or host
   assembly/disassembly validation.
3. External data uses a strict positive-version envelope. Host identity fields
   are forbidden in the provider body; the existing payload/snapshot size limits
   include that envelope. Missing/version-mismatched providers block restoration
   without interpreting or deleting opaque data. No passive migration is added.
4. Provider callbacks run on the server thread for loaded targets. A returned
   callback taking more than 5 ms is rejected and warned once; this is not
   preemption, side-effect rollback or a memory-allocation sandbox.
5. Existing vanilla payloads, snapshot schema 1, journal schema 2, recipes and
   packet protocols are unchanged. The six exported classifier classes use the
   actual final runtime bytes; metadata retains its independent JDK-only check.

## Tests and executed commands

Windows / Oracle Java 17.0.7 / Gradle 8.8 / Forge 47.4.10.

New coverage totals 40 JUnit methods and two Required GameTests:

- Four additional API tests check platform-only consumers, intentional internal
  import rejection, archive boundaries and exported constant-pool dependencies.
- Twenty registry tests cover owner/thread/handle/freeze behavior, atomic failed
  registration, reserved/duplicate IDs and the 256/64/1024 capacity boundaries.
- Eleven payload tests cover exact envelope shape/types/version, defensive copies,
  root identity rejection and the complete 262144-byte limit including overhead.
- Five injected-clock budget tests cover the exact 5 ms boundary, rejection after
  return, callback exceptions and monotonic-clock wrap without sleeping.
- A separate development-only mod registers its own non-vanilla two-slot container
  through the real mod bus. Its tests reject retained registration and use the
  production assembly command, server ticks and entity interaction to restore
  inventory/custom item data. It imports only API plus platform classes, not an
  internal manager or replacement runtime.

| Command | Observed result |
|---|---|
| `gradlew.bat compileJava compileRocketApiJava test --tests '*ApiVersionsTest' --tests '*ApiArtifactTest' --offline --no-daemon --no-build-cache` | Exit 0, 29s; 16 focused tests |
| `gradlew.bat compileAdapterTestJava --offline --no-daemon --no-build-cache` | Exit 0, 14s; four deprecated constructor warnings subsequently corrected |
| `gradlew.bat clean build test runData runGameTestServer --offline --no-daemon --no-build-cache` | Exit 0, 2m41s; 130 suites / 663 JUnit, 0 failures/errors/skips; all 134 Required GameTests passed; DataGen written 0 |
| `git diff --exit-code -- src/generated` | Exit 0; generated resources unchanged |

Captures: [first API check](first-api-check.txt),
[first API XML](initial-api-tests.zip), [fixture compilation](fixture-compile.txt),
[complete Gradle output](integration-first.txt),
[native GameTest output](gametest-native.txt), and [complete JUnit XML](junit-full.zip).
The full XML archive was captured before independent focused reruns. Existing
intentional Precision Assembler save-fault/migration injections remain visible;
the final required-test success marker is present. No assertion, timeout or
performance budget was relaxed. The isolated mod dependency range was corrected
to the host's full mod-version identity before integration execution.

## Artifacts

| Artifact | SHA-256 |
|---|---|
| Runtime JAR | `3f8be1e0adce70b1c25fc1eeeffeac5b7f398016abf6670af11b42c52c84c200` |
| API classifier | `06568c596efb623c80eddfbdd41afd695451f47ceb935b165d149349157e4020` |
| Sources JAR | `7b1e565e5ce326605d739effd5d37721d65e6fb41f4dba859ede53233c4d755f` |

## Independent verification

An independent reviewer inspected the actual production/build/fixture changes
and confirmed the review against final production commit `5466fd6`. No unresolved
finding remained. In particular, cached Forge code confirmed synchronous
per-ModContainer dispatch; runtime listener installation and the fixture's use
of real production entry points were reviewed independently of implementation.

The reviewer then ran only the five focused JUnit classes, with Java 17 and
`--offline --no-daemon --no-build-cache`: **5 suites / 52 tests passed**, no
failures/errors/skips, exit 0 / 22.234s. The test task executed, not a cached test
result. Main/API/sources JAR hashes and sizes stayed unchanged. No Minecraft
process was launched by this reviewer.

Evidence: [review and rerun report](independent/REPORT.md),
[exact command](independent/command.txt), [raw output](independent/gradle.txt),
[focused XML](independent/junit-results.zip) and
[independent checksums](independent/SHA256SUMS).

A separate read-only archive inspection confirmed exactly six exported classes,
all byte-identical to runtime, their six source files in the sources JAR, and no
fixture package/resource or duplicate ZIP entry in any distribution JAR. Root
repeated this check and checked the archived full XML/GameTest markers;
[artifact check output](artifact-check.txt) records the results.

### Short packaged-host restart

A separate verifier used this final host JAR, a newly prepared local Forge runtime
and a fresh world. Only runtime libraries were copied from the existing runtime,
not old mods/worlds/configuration. Binding was loopback-only in offline mode.
No external fixture mod, remote server or client was used.

Five Java processes exited 0: fresh start and same-world restart, followed by
the unchanged repository `run_v050_rocket_server_smoke.py` assembly, entity
persistence and staged-recovery phases. Complete entity authority SNBT, UUID and
snapshot hash survived restart. The recovered four-block rocket contained exactly
17 diamonds and 64 iron ingots. A separate actual-disk NBT read verified journal
schema 2 and an empty transactions list rather than trusting the runner's
unconditional historical summary flag. The saved journal fixture is retained.

All five complete logs had zero ERROR/FATAL/client-linkage/project-logger findings.
There was one Minecraft tick-lag warning, 2733 ms / 54 ticks, following the fresh
test-chunk setup/assembly sequence; its exact cost was not profiled. Other retained
warnings concern ordinary Forge/default-config/terminal/offline startup. This is
not a performance pass. The loopback port was closed and no Java process remained.

Evidence: [independent packaged report](packaged-restart/VERIFICATION.md),
[commands](packaged-restart/commands.json),
[actual journal/inventory observations](packaged-restart/journal-postcheck.json),
[log audit](packaged-restart/log-audit.json) and
[original checksum manifest](packaged-restart/checksums.txt). The helper scripts
are captured one-off evidence, not a new product interface. This is S1 clean/staged
recovery, not S2 forced power loss or external-provider restart compatibility.

The [slice SHA-256 manifest](SHA256SUMS) covers archived evidence. Imported bundles
retain their original bytes, including CRLF where present. Static checks are
recorded in [static check output](static-checks.txt); full historical repository
validation was not rerun. No release evidence is inferred from these finite checks.

## Remaining work and rollback

- The development mod is not an independently published/reobfuscated external
  consumer. Actual classifier dependency remapping, provider restart, controlled
  uninstall/reinstall and cross-dimension compatibility remain V130-ROCKET-03.
- Fault/slow provider behavior is covered in layers by prior transaction-world
  tests and new pure envelope/budget tests; this is not every external callback
  fault combination in a packaged server.
- Provider `Error`/OOM, non-returning callbacks and arbitrary same-JVM side effects
  are not isolated. Existing cleanup/recovery limits remain; no cross-chunk
  fsync/power-loss atomicity guarantee is made.
- Atmosphere/equipment/fuel/environment/satellite providers remain unimplemented.
  No long-load, remote Linux, two-client, GPU or full original-feature acceptance
  was run. ADR-018's deferred full-test schedule is unchanged.
- G0-G9 as a whole are **not satisfied**; v1.3 remains `IN_PROGRESS`. No release
  tag, remote artifact publication or human Gate approval is created.
- Reverting the production and new API/fixture-test commits removes registration
  support. Worlds containing new external payloads must retain those opaque data
  and await a matching provider; reverting is not a format migration.

Continue with V130-ROCKET-03, then the remaining providers in the
[canonical implementation log](../v1.3.0-implementation-log.md).
