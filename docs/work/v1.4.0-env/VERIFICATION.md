# V140-ENV — planetary exposure and protection

Date: 2026-09-27. Development slice only; no candidate, tag or Gate approval.

## Scope and compatibility

Base: `d120d4152689022473512329a44cd29a29317b2f` on
`codex/v1.4.0-planetary-expansion`. Java 17.0.7, Forge 47.4.10,
build `1.20.1-1.4.0-dev`. Root is the sole tracked writer; independent review
uses separate Temp reports and an exclusive Gradle execution period. The
untracked user documentation bundle is excluded.

[ADR-034](../../decisions/ADR-034-PLANETARY-EXPOSURE-AND-PROTECTION.md)
records the recommended design under standing maintainer authorization,
not a new numbered manual vote or release approval. The optional strict
schema-2 `environment_effects` flag defaults to false; existing definitions
retain their behavior. Mars/Venus opt in. Unique actual surfaces only acquire
additional cold/heat/high-pressure/direct-sunlight hazards; shared Space does
not inherit its orbiting body's conditions. Radiation remains metadata.

The server attempts at most 2 HP every 20 exposed ticks, with ordinary damage
mitigation and cancellation intact. A suffocation-attempt tick suppresses the
extra attempt but consumes its interval. Partial hazard changes retain phase;
full protection, entity replacement, profile/Level change, logout and shutdown
reset it. This state is transient. Three four-slot armor tags and active,
supplied, sealed vent rooms provide distinct passive protection; neither ambient
breathability nor tags manufacture oxygen. Hostile breathable worlds run the
same bounded room scanner and finite supply consumption as vacuum worlds.

Four damage types, three item tags and English/Chinese status/death text are
original generated data. Five existing v1.4 resources change and seven are
added; older generated roots remain untouched. No upstream art or code is
imported. Public API 1.7, display protocol 2 and existing save schemas remain
unchanged. Downgrade requires the matching older data pack and complete backup;
older decoders reject the new field rather than silently discard intent.

## Root commands and results

With `JAVA_HOME=C:/Program Files/Java/jdk-17.0.7`:

```text
gradlew.bat runData test --tests '*Environmental*' --tests '*CelestialSchemaV2*' --offline --no-daemon --console=plain
gradlew.bat test --tests '*Environmental*' --tests '*Planetary*' runGameTestServer --offline --no-daemon --console=plain
gradlew.bat clean build runData runGameTestServer --offline --no-daemon --console=plain
gradlew.bat build runData runGameTestServer --offline --no-daemon --console=plain
gradlew.bat clean build runData --offline --no-daemon --console=plain
```

The initial data/model command exited **0** in **34 s**; DataGen wrote twelve
files. The first combined run passed **57 JUnit / 6 suites** but failed **3 of
228 GameTests**, exit **1**. The clean command's build, **852 JUnit / 152 suites**
and DataGen succeeded, but **2 of 229 GameTests** failed, exit **1**. These are
retained failures, not passing combined commands. The final combined command
exited **0** in **134 s**, with **852 JUnit**, zero failures/errors, and **229
required GameTests passed**. Repeat DataGen wrote **0** of the 27 resources.
The final clean/build/DataGen check exited **0** in **28 s**, after independent
review released Gradle. It restored unchanged test results from cache; it is
not an additional fresh test execution. Main/API/sources JARs remained byte
identical to the frozen, independently checked and native-tested artifacts.

The twelve added unit tests cover strict opt-in/default/legacy JSON and NBT
behavior, invalid values, catalog/Level resolution, threshold edges, all eight
protection combinations, priority, cadence and phase reset. Nine GameTests
exercise registered damage, normal immunity/cancellation, vanilla initial-spawn
grace, tags/slots/counts, exact finite suit oxygen, no duplicate suffocation
damage, exemptions/lifecycle/profile changes, gravity, loaded-column sunlight,
hostile breathable rooms, finite vent consumption and invalidation.

```text
python -B -m unittest tests.test_v140_celestial_schema_smoke tests.test_v140_planetary_worlds_smoke -v
```

The unchanged native harness checks passed **24 Python tests**, exit **0**,
**0.439 s**, using `D:/python/pyenv/pyenv-win/shims/python.bat`, `PYTHONUTF8=1`.
`python -B scripts/validate_v1plus_planning.py` also exited **0** for the eleven
version plans. `git diff --check`, `git diff --cached --check` and the staged
worktree `git diff --exit-code` checkpoint exited **0**. The excluded untracked
user bundle is untouched; an empty tracked diff is not a claim it was removed.

## Retained failures and corrections

- Forge FakePlayer does not tick down the normal ServerPlayer initial-spawn
  invulnerability. The fixture explicitly ages that state using the mapped
  test-only reflection helper. A separate unaged-player regression proves the
  new damage does not bypass normal spawn grace; production damage tags and
  immunity behavior are unchanged.
- The sunlight fixture initially tested below terrain. It now uses a loaded
  helper-owned column near build height. Roof occlusion is observed after the
  ordinary queued lighting update, two ticks later within the original timeout.
  Original open/covered/night/unloaded-column assertions remain.
- Vent FE storage accepts input only; the first exhaustion fixture attempted
  extraction and therefore left its power intact. The corrected fixture starts
  with exactly 22 supply intervals and consumes them through real vent updates,
  then checks zero energy despite stale ACTIVE status. It verifies recovery
  before synthetic chunk-unload invalidation. No production budget was widened.
- Independent review clarified the cadence contract and identified the vent
  fixture mistake. Reader documentation now fully qualifies all three tag IDs.
  Original reports and test attempts remain retained.

## Dedicated-server check

```text
python -B scripts/run_v140_planetary_worlds_smoke.py <fresh-server-native>
  --baseline-jar <MAP01-artifacts>/advancedrocketry-community-1.20.1-1.4.0-dev.jar
  --host-jar <ENV-artifacts>/advancedrocketry-community-1.20.1-1.4.0-dev.jar
  --evidence-dir <native> --java <Java17>/bin/java.exe --accept-eula
```

The unchanged runner and all three native JVMs exited **0**. A real preceding
MAP-01 world is created, upgraded to the ENV artifact, flown Earth/Mars/Venus/
Earth and restarted. Native rocket/cargo/fuel journal, stations and binding
identities survive. The three fuel debits remain **448 / 533 / 515**, finishing
at **504 / 2000**. Logs retain **25 / 10 / 10** platform/configuration warnings
and zero ERROR/FATAL/project warnings or client-linkage failures.

This is a local offline operator-driven regression smoke, not a player login,
environmental damage session or new vent/suit persistence acceptance. No save
schema changes in this slice. The baseline world backup, native data, commands,
logs and artifact identities are retained. As in MAP-02, persisted block-palette
readback and whole-world item immutability are not asserted by this runner.

## Independent checks

The reviewer independently ran:

```text
gradlew.bat test --tests '*Environmental*Test' --tests '*PlayerLifeSupportEngineTest'
  --tests '*VentSupplyEngineTest' --tests '*VolumeScan*Test' --tests '*SuitEquipmentRegistryTest'
  --tests '*SuitOxygenAccessTest' --tests '*Api*Test'
  --rerun-tasks --offline --no-daemon --no-build-cache --console=plain
gradlew.bat runGameTestServer --offline --no-daemon --no-build-cache --console=plain
```

Both exited **0**: forced **77 JUnit / 10 suites**, **48.647 s**, all 16 tasks
executed; **229 required GameTests**, **97.833 s**, reusing the existing test
world without clean. All **1296** inspected source/build/generated inputs and
all three JARs were unchanged before/after. The reviewer separately verified
all **144** native manifest members, process receipts and exact saved rocket,
payload, fuel journal, station, binding and configuration records. These
observations cover operator travel, not real-player environmental gameplay.
Original intentional fault-injection ERROR logs are retained, not counted as
unexplained gameplay exceptions or labeled a warning-free GameTest run.

The final source review reports no remaining concrete correctness finding in
this scope. Independent contract, early source, fixture and final reports are
preserved unchanged in the evidence archive; development review is not release
approval.

Final independent report SHA256:
`341114cc6126e93386244134007f729fb70c9a25ba7fd2791a8466ea9f6ba7e4`.
Its original 36-member manifest is preserved inside the review archive.

## Evidence identities

- Main JAR: `0ac84a92b01ef193ea10f534145dff79983b65660d5b4a225f1333c6c7d47252`.
- API JAR: `50cc9ba02bc979c31e1579a840431247ffe8401114aff013ddf478f0765010bf`,
  byte-identical to MAP-02 and the earlier API 1.7 development artifact.
- Sources JAR: `485d5b790929e2da457135c0bac8b4e895bac8fd12b3cc4f9fc459073a836915`.

[Source identities](source-identity.json), [development artifacts](development-artifacts.json)
and [27-resource packaged-byte inventory](generated-resources.json) bind the
implementation. [Root checks](root-checks.zip) retain logs, XML and earlier
failures, with [counts](root-checks.json). [Native restart](native-restart.zip)
and its [summary](native-summary.json) retain the disposable upgrade fixture.
[Independent review](independent-review.zip) preserves original reports and
reruns. Every archive has a matching `*-files.json` member manifest;
[SHA256SUMS](SHA256SUMS.txt) covers the final evidence files. [Local links](links.json)
check file existence, not remote URLs or anchors. Planning validation and final
staged/worktree whitespace and difference checks are separate from release Gates.

## Verification boundaries and remaining work

The added GameTests call service ticks explicitly; they are not naturally
ticking network-player sessions. Catalog replacement uses the private test
candidate hook, not native `/reload`. Chunk-unload invalidation invokes the
manager event entry point, not real chunk eviction. The cancellation probe
returns false from `hurt`, not from a registered Forge cancellation listener.
These distinctions do not substitute for client or event-integration acceptance.

No real client, V1/V2/GPU, SSH, S2, long-load or complete migration/parity campaign
was run. Action-bar visual appearance and interactions with other mods' messages
remain unverified. Existing oxygen HUD status is not overall environmental
safety. The complete campaign remains deferred under ADR-018 until original
machinery and dimensions are implemented. All G0-G9 and inherited acceptance
remain open; v1.4 stays IN_PROGRESS. Navigation, discovery, custom sky and
remaining migration are still current-version work.

Deferred manual cases: enter Mars and Venus in survival with incomplete and
complete suits, verify the localized warning and oxygen HUD separately, then
compare a supplied sealed vent room with roof breach and depleted FE/O2. Check
respawn/dimension changes, high-solar data-pack daylight versus covered/night
conditions, language/GUI scale and other mods' action-bar messages on real
clients. These are future acceptance steps, not executed evidence.
