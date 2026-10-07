# C18d-SKY-01: existing planet and station sky switches

Date: 2026-10-07. Version: v1.8.0. Integrator: Root.
Status: implemented-unverified; source independently reviewed and integrated.
See [source integration](SOURCE-INTEGRATION-01.md).
Contract basis: accepted ADR-066 revision 3 section 7.2.
No Required Gate, content-ledger delivery or new semantic approval is claimed.
Exact source `cbbb78e1` passes build and the ten adapter/config JUnit methods,
plus repeat DataGen/clean checks; full GameTests fail. Root raw audit is in
[RESULT-30](../v1.8.0-ci/RESULT-30.md); the separate independent raw audit is
[RESULT-31](../v1.8.0-ci/RESULT-31.md). Real-client checks remain pending.

## Outcome and narrow binding

Implement the two already accepted CLIENT-only keys `sky.planetOverride=true`
and `sky.stationOverride=true`. The existing `planetary` effects registration
uses the planet switch and its OverworldEffects fallback; `planetary_space`
uses the station switch and its EndEffects fallback. Category follows that
registration, not current body, dimension name or station orbit context.

False restores corresponding fallback sky/cloud/weather/rain hooks and cloud
height, without changing environment, physics, discovery, station context,
server selection or ambience. Preserve fallback ground/sky/lightmap/ambient
flags and fog/sunrise getters in both modes. Disabled fog handlers leave RGB,
near/far/shape and existing cancellation unchanged. True preserves current
custom rendering, weather suppression and unknown-profile/fluid/status/foggy
view/error fallback behavior. Same installed effects instances read the live
switch, so false/true changes need no registration replacement.

Root narrowly implements this accepted mapping under the owner's existing
authorization of reviewed contracts with no unresolved Critical/High/Medium;
no new product choice is supplied. Common/client binding for this leaf is
`PlanetaryDimensionEffects(DimensionSpecialEffects, BooleanSupplier)`, retaining
the current one-argument default-enabled constructor. `overridesSky()` reads
that supplier. `PlanetarySkyClient.visualSelection(ClientLevel)` gates only
sky/fog presentation using the installed effects instance; raw `selection`
continues serving ambience and retains its current cache/lifecycle semantics.
These are internal client bindings, not a new public extension contract.

## Scope and non-goals

Only these two existing categories and their CLIENT config, adapters and tests.
No Overworld-global replacement, advancedVisuals key, HUD, assets, dimension
JSON/ID, sound/reload algorithm, packets, server config, SavedData, new renderer
framework, physical hatch, oxygen/typed/first-event/fall adoption or risk waiver.
Do not gate raw shared selection or alter ambience cadence/exposure/playback.

## Source ownership and dependency

Root owns existing ClientConfig.java, ClientConfigTest.java and ClientBootstrap.java,
all canonical status/plan/log/task integration records and all Git writes.
Root creates isolated `D:/GitHub/arce-v180-sky-switches-20261007`, branch
`codex/v1.8.0-sky-switches`, at committed base
`bf0116c8dc7209f4bec64662813e421af0bf15be`.
Worker owns only existing client/sky/PlanetaryDimensionEffects.java,
ClientSkyEvents.java, PlanetarySkyClient.java, matching client/sky/PlanetaryEffectsTest.java,
and a new SOURCE-01.md in this task directory, in that isolated worktree.
All other files, especially central registry/config/status, are forbidden.

Primary API investigation is [the bounded facts report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18d-sky-primary-api-facts-20261007-01/REPORT-01.md),
SHA-256 `f29fbe9969f368f20a0aeaabf69a0b35fa2fcd5710011ea67a9f3c1521507978`.
Root reads and hashes it (`bf4422`, exit 0). It checks a non-final cloud getter,
four Forge boolean hooks/caller consumption and viewport fog consumption.
Its immutable Forge source is release-associated by official metadata, not
binary-compared to 47.4.10. Internal viewport constructors touch Minecraft;
plain JUnit must not manufacture an unsafe client/event fixture or claim real
dispatch from recording-fallback tests. Exact-source compilation remains required.

## Acceptance and verification

Config tests preserve all mushroom checks and verify exact registered keys,
unloaded true defaults, loaded independent false/true/re-enable values and
cleanup. Adapter tests retain distinct fallback/lightmap and sound assertions;
recording-fallback tests exercise all four disabled hooks, argument/return
propagation, no premature setupFog/custom-render work, dynamic cloud height,
same-instance toggling and independent category suppliers. Enabled cloud/weather
suppression remains asserted. Actual fog dispatch and unchanged raw selection
require source inspection plus client evidence, not fake standalone events.

Different-agent actual-diff review precedes Root integration. Do not remove or
weaken existing assertions, deadlines or budgets. Fresh exact-committed-source
hosted clean build/JUnit, client/artifact audit, twice runData/clean checks and
full GameTests remain required. Rerun existing SkySelection, AmbientController,
SkyResources and reload/render-state tests unchanged. Real GPU V1, two-client
independence V2, rapid switching, reload and travel observations remain open.

C free space is below 10 GB: no local JVM/Gradle/native/server/client or install.
Use apply_patch and bounded PowerShell/Git reads/Python -B only, with own scratch
and process-local TEMP/TMP/TMPDIR in a direct-child D project-parent evidence
leaf. No archive extraction, cleanup of others, Git writes or HEAD holds by
workers. Source snapshots are not delivered until Root commits exact reviewed
bytes. Any unresolved behavior/API gap must be reported, not silently broadened.
