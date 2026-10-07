# C18d-SKY-01: reviewed existing-category source integration

Date: 2026-10-07. Integrator: Root. Status: IMPLEMENTED_UNVERIFIED.
This is source integration, not client/runtime delivery or Gate acceptance.

## Committed source association

Root commits the five frozen worker files at
`ebc52503a4be481fc99eab6330618dec9ffceff5` and cherry-picks their exact bytes
onto main at `b363c40350132eb56ccfaa0ea524120ebfff1da3`. The original
[author source record](SOURCE-01.md) retains its dated uncommitted/unrun status;
this successor record supplies the actual commit association. The
[integration receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/sky-switches-root-integration-20261007-01/WORKER-INTEGRATION-01.json),
SHA-256 `daf4856403e64a9df94a672d62821b106ef24d0a2e6d8ab2e4725f5725ab06fc`,
checks all five frozen/committed/main blobs, exact stage/stat, empty worker
status/index and preservation of main's unrelated dirt/bytes (`a4a06f`, exit 0).
Root's three reviewed central bindings accompany this metadata checkpoint;
the complete published source/run identity is recorded by the subsequent CI
observation, not invented as an uncommitted delivery target.

Different-agent [central review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18d-sky-central-independent-review-20261007-01/REVIEW-01.md),
SHA-256 `2aa6b58434cc76e0b8229ffaf38b7116e69920b2d3aca7101cd753fe86f3ede7`,
executes eleven static checks and reports no actionable scoped finding.
Different-agent [worker/combined source review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18d-sky-source-independent-review-20261007-01/REVIEW-01.md),
SHA-256 `9e8372fabbfe56560577741f79f9da0ca2d85a2c8eb1eaf1315524699a6ea7d0`,
independently inspects all eight postimages/linkage and executes 71 static
checks, zero drift across 33 inputs and final 8/8 integrity checks. Both retain
runtime limits; command/display errors are disclosed in their own reports.
Root reads/hashes the complete reports (`ecdca1`, `dd5cb9`, exit 0). Static
checks are not compiler, JUnit, actual viewport or GPU execution.

## Integrated behavior and non-goals

Exactly the two accepted CLIENT keys `sky.planetOverride` and
`sky.stationOverride` default true and are read live. Existing `planetary`
registration retains OverworldEffects plus the planet supplier;
`planetary_space` retains EndEffects plus the station supplier. Category
does not depend on a current body, name or orbit context. One-argument effects
construction stays default-enabled. Disabled adapters delegate all four native
hooks with unchanged arguments/results before custom sky work, and restore
live fallback cloud height. Fallback ground/sky/lightmap/ambient flags and
fog/sunrise getters stay intact. True preserves existing presentation behavior.

The separate visual selector gates sky/fog only. Raw selection, station context,
ambience cadence/exposure/playback, rendering error handling and reload/logout
cleanup are unchanged. Disabled fog returns before RGB/plane/cancellation
mutations and does not undo another subscriber's cancellation. Fallback weather
policy is restored, not forced rain/weather in a Level that does not permit it.

No assets, generator outputs, new registry/dimension IDs, public server API,
packets, server config, persistence or migration is added. No global Overworld
replacement, advancedVisuals, HUD, other C18 feature, hatch interception, risk
acceptance, content-ledger closure or later-version implementation occurs.
The exact [task](TASK-01.md) and accepted ADR-066 subset govern this scope.

## Tests and remaining verification

ClientConfigTest retains mushroom assertions and has three declared cases:
strict exact keys/registered identity, unloaded defaults and loaded independent
false/true/re-enable behavior with unconditional config unload. PlanetaryEffectsTest
retains its original two method bodies and adds five recording-fallback cases.
The ten combined declarations cover hook arguments/both results, preparation
ownership, dynamic height, same-instance toggling, independent suppliers and
unchanged fallback policies. No ClientLevel/viewport/client singleton/GPU is
manufactured. Recording fallback tests do not prove actual client dispatch.
Five adjacent selection/ambience/resource/reload/render-state suites are unchanged.

Fresh exact-committed-source hosted clean build/JUnit, artifact/client audit,
twice native generation/clean checks and complete GameTests remain required.
Local C is below 10 GB; no local Java/JVM/Gradle/native/client command is run.
The preceding independently raw-audited [RESULT-28](../v1.8.0-ci/RESULT-28.md)
still fails Tau and initial airlock supply; its counts are not rebound to this
new sky combination. No assertion, deadline or resource budget is relaxed.

Real-client evidence still needs the exact commit/JAR/Forge/config, GPU/driver
and resource-pack identities. On V1, exercise all four switch combinations,
re-enable, travel between planetary/Space effects and station orbit contexts,
resource reload and cloud/weather/fog fallback where Level policy permits.
Observe unchanged environment and ambience separately. On V2, use opposite
local settings on two actual clients and verify independence without server
state change. No such client observations or G0-G9 approval exist yet.

## Rollback and ownership

Root alone stages, commits, integrates, normally pushes and retires owned ended
worktrees. Source/metadata commits are separable from original CI failures.
Removing this exact presentation diff restores the previous always-enabled
adapters without rewriting server data or IDs. Already published stable keys
must be considered before any later config removal; no deletion is performed
here. User AGENTS and other agents' private/untracked work remain untouched.
