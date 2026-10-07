# C18d-SKY-01 source handoff

Date: 2026-10-07. Version: v1.8.0. Worker: c16a04_fluids. Status:
READY_FOR_REVIEW; uncommitted source, no Java execution or Gate acceptance.
The adopted [task](TASK-01.md) and accepted ADR-066 revision 3 section 7.2
govern this existing planet/station presentation slice.

## Declared write scope

Isolated worktree `D:/GitHub/arce-v180-sky-switches-20261007`, branch
`codex/v1.8.0-sky-switches`, fixed base
`bf0116c8dc7209f4bec64662813e421af0bf15be`. Only these five paths are owned:

1. `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/sky/PlanetaryDimensionEffects.java`
2. `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/sky/ClientSkyEvents.java`
3. `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/sky/PlanetarySkyClient.java`
4. `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/client/sky/PlanetaryEffectsTest.java`
5. This `SOURCE-01.md`.

Root alone owns ClientConfig/ClientConfigTest/ClientBootstrap and all canonical
task/status/log, registration, Git publication and actual verification. No server,
asset, persistence, network, new setting/category or renderer framework is added.
No upstream or official source/art is copied. Changes are original NEW/MIT.

## Implemented boundaries and integration dependencies

`PlanetaryDimensionEffects(DimensionSpecialEffects, BooleanSupplier)` retains
the one-argument default-enabled constructor. `overridesSky()` reads the live
supplier; no flag is cached. False delegates the four existing sky/cloud/weather/
rain hooks once with their original arguments and propagates the fallback's
boolean result. Disabled sky returns before selection, setupFog or custom render
work; the fallback owns any fog preparation it performs. `getCloudHeight()`
returns the fallback's current height when false and the existing NaN when true.
Ground/sky/lightmap/ambient flags and fog/sunrise delegation remain unchanged.
Enabled cloud/weather/rain suppression and existing enabled custom-sky guards,
fog preparation and error fallback remain intact.

`PlanetarySkyClient.visualSelection(ClientLevel)` returns no presentation
selection unless the installed effects instance is a currently enabled
PlanetaryDimensionEffects. It does not clear or gate raw `selection`, and the
ambience tick/cache/reload/cleanup bodies are unchanged. The two fog subscribers
use visualSelection; disabled presentation reaches no RGB/plane/shape setter or
new cancellation. They do not undo another subscriber's cancellation. Actual
viewport dispatch is not represented by standalone event fixtures.

Root must bind the existing `planetary` registration to the live CLIENT
`sky.planetOverride` supplier and `planetary_space` to `sky.stationOverride`.
Their existing OverworldEffects/EndEffects fallbacks remain category-specific.
This worker does not author those ClientConfig/Bootstrap changes or decide new
Overworld-global, sound, environment, packet or server behavior. The isolated
base's old default-enabled registration is not evidence of that new integration.

## Tests and actual verification boundary

The two original PlanetaryEffectsTest methods (fallback/lightmap policy and
quiet relative non-looping sound) remain byte-identical. Five new JUnit methods
declare checks of disabled four-hook argument and false/true result propagation,
no premature fog preparation, fallback-owned preparation exactly once,
same-instance false/true/re-enable cloud/weather behavior, independent category
suppliers and unchanged fallback flags/fog/sunrise in both modes. The recording
fallback uses inert null native arguments and plain PoseStack/Matrix4f values;
it constructs no fake ClientLevel, viewport event, client singleton or GPU.
All seven JUnit declarations remain unexecuted for these bytes.

Fresh author-side Python static controls and read-only Git checks bind actual
postimages, exact allowed diff/inverses, retained old method bodies, live-supplier
ordering, raw/ambience preservation, delegated arguments, scope and whitespace.
Literal argv/exits and results are in the
[external report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18d-sky-switches-author-20261007-c16-4d7ab9/REPORT-01.md).
The primary API association remains the task's report
`f29fbe9969f368f20a0aeaabf69a0b35fa2fcd5710011ea67a9f3c1521507978`,
not a binary/JVM check performed by this worker.

Java/JVM, clean build, unit execution, Gradle, DataGen, native GameTest/client,
packaged restart, V1/V2, reload/travel and two-client rapid switching are unrun.
No local Java is authorized while C is below 10 GB. Recording fallback tests
cannot prove client dispatch, GPU output, actual enabled sky rendering or fog
nonmutation in a live viewport. Different-agent actual-diff review, exact-source
hosted tests and the task's real client observations remain required; existing
SkySelection/AmbientController/SkyResources/reload/render-state suites are not
weakened. No Required Gate, content-ledger delivery or architecture exception is
closed by this source checkpoint.

## Release and rollback

The final five postimages are frozen for independent review; no main HEAD/index
hold is requested. Root alone commits/integrates/pushes and owns worktree
retirement. Reverting this exact source diff restores previous always-enabled
presentation without changing persisted IDs/data or raw ambience selection.
