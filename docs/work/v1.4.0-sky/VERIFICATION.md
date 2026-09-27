# V140-SKY — bounded planetary presentation

Date: 2026-09-27. Development evidence, not a release candidate or Gate approval.
Base: `251de231612049f62721f8911a4f402a4f1157d0`, branch
`codex/v1.4.0-planetary-expansion`, Java 17.0.7 / Forge 47.4.10.
Root is the sole tracked writer. Independent review uses read-only source and
fresh Temp reports, with an exclusive Gradle period. The separate untracked
development-document bundle is excluded from reads, edits and staging.

## Implemented scope

[ADR-036](../../decisions/ADR-036-PLANETARY-SKY-PROFILES.md) records the selected
design under standing recommended-solution authorization, not a new numbered
manual vote. Four original client resource profiles select Moon, Mars, Venus
and generic shared-Space presentation through the existing synchronized
`visual_profile` and unique physical-Level mapping. Earth keeps its ordinary
renderer. No station-parent disc, gas surface, new environment authority,
discovery policy, route, inventory or save migration is introduced.

Schema-1 profiles enforce 128 winning files, 16,384 bytes/file, nesting 16 and
128-character IDs, strict required fields, duplicate-key/UTF-8 validation and
finite numeric bounds. A reload publishes one immutable map after the barrier;
invalid candidates retain the previous map. Missing/ambiguous mappings fall
back rather than leaking the previous world's profile.

Five cached meshes total 5,472 vertices: sky sphere, horizon, 512 stars, sun
and halo. Geometry is original code, not copied art. GPU allocation/upload/close
is render-thread-only; reload/logout release buffers. A drawing error disables
custom rendering until resource reload, including across logout. The guard
restores incoming shader/color, blend factors/equations, depth-write, culling,
VAO, array buffer and program state rather than imposing defaults.

Fog cannot increase either incoming distance plane. Fluids, powder snow,
blindness and darkness preserve platform restrictions. Surface/Space effects
retain their respective Overworld/End fallback and lightmap flags; both suppress
visible clouds/rain, without changing server weather. Moon/Space type JSON
changes only `effects`; historical generated files remain untouched. Exact
packaging exclusions prevent duplicate old type entries.

Added ambience owns at most one local non-looping sound, uses the ambient
volume category, and has a 400-unpaused-tick initial/inter-play cooldown that
survives world/profile/reload/logout transitions. Cover and transitions stop it.
Mars/Venus reference vanilla event IDs without distributing audio files.
Public API 1.7, network protocols and save schemas remain unchanged.

Frozen development artifacts (clean build reproduces these bytes):

| JAR suffix | Bytes | SHA256 |
|---|---:|---|
| main | 2,594,614 | `676f4c0421a80f7b3dd8a732c77186306799fee4d8f88750d1bc30fb2bf26aea` |
| `-api` | 48,469 | `50cc9ba02bc979c31e1579a840431247ffe8401114aff013ddf478f0765010bf` |
| `-sources` | 1,180,375 | `d7f11442a26b3329d065e09ef775a6cbed73a67469cf3b25b95aaeebe822722c` |

## Coverage and retained corrections

Twenty-five added JUnit cases cover strict profiles, finite geometry/math,
immutable selection/cache invalidation, malformed/oversized reload retention,
resource priority/removal, barrier publication, sound lifecycle/cadence and the
actual platform sound factory. The state-guard test injects fake GL access; it
does not execute a GPU. The sound factory fixture supplies a unit-gain resolved
Sound to test pitch/volume without starting an audio device.

Two added GameTests cover real dedicated-server dimension-effects/settings and
the provisioned Earth arrival candidate with snow/grass refusal. Other existing
flight, transfer, persistence and machinery assertions remain unchanged.

Retained failures, not relabeled as successes:

- `compile-data-01.log`: two compile errors from using a package-private
  diagnostic helper; the client listener now owns its bounded diagnostic.
- `targeted-01.log` and XML: 24 tests, two failures. Invalid sound IDs now use
  checked `tryParse` so malformed resources reject the candidate; the plain-JUnit
  sound fixture now resolves a sound before inspecting effective volume/pitch.
- Source review found incomplete incoming render-state restoration and logout
  clearing the drawing-failure latch. State capture/restore and reload-only
  latch reset correct these; incremental review added both blend equations.
- `clean-all-02.log`: 891 JUnit passed, but four of 233 required GameTests failed
  on Earth return. Native seed `5912724636835073799` has snow/grass at the
  heightmap-selected plane of all eight candidate areas. The unchanged selector
  correctly requires air. The complete failed world, exact-footprint readback
  and independent diagnosis are retained; the earlier passing world's seed was
  not archived and is not claimed known.
- `EarthReturnPadFixture` provisions only 25 previously-air floor cells above
  actual terrain after a full 5x5x4 preflight. Paired batch hooks restore/read
  back original states after success or failure; partial setup restores before
  rethrowing. It changes no production landing rule, timeout, performance budget
  or existing scenario assertion. The regression still rejects snow and grass.
- `fixture-reused-world-01.log`: 891 tests, one maintenance-structure failure
  before GameTests. The additive regression was moved from the frozen five-case
  flight classes into the new fixture class; the structural test was not edited.
  `fixture-reused-world-02.log` then passed 891 JUnit and all 234 GameTests on
  the same previously failed world, with all four fixture restore pairs logged.

## Root commands and observed results

`JAVA_HOME=C:/Program Files/Java/jdk-17.0.7`; Python is the installed
`D:/python/pyenv/pyenv-win/shims/python.bat`, with `PYTHONUTF8=1` and `-B`.

| Command / attempt | Observed result |
|---|---|
| `gradlew.bat build runData runGameTestServer --console=plain` (`fixture-reused-world-02`) | Exit 0, 2m48s; all 891 JUnit actually executed, 234 required GameTests on the previously failed world; zero DataGen writes |
| `gradlew.bat clean build runData runGameTestServer --console=plain` (`clean-all-03`) | Exit 0, 3m1s; 891 JUnit/163 suites restored from the passing cache, fresh-world 234 GameTests, zero writes among 33 generated resources; all three JARs reproduce frozen hashes |
| `python -B -m unittest tests.test_v140_celestial_schema_smoke tests.test_v140_planetary_worlds_smoke -v` | Exit 0, 24 harness tests, 0.515s |
| `python -B scripts/validate_v1plus_planning.py` | Planning-only 11-version validation; not G0–G9 approval |
| `python -B scripts/validate_bootstrap_provenance.py --require-approved-review` | Accepted historical bootstrap provenance remains digest-bound: two components, 11 imported targets, two local assets |
| `python -B scripts/manage_v030_generated_manifest.py verify` | Unchanged seven-file historical DataGen inventory |
| `git diff --check`, `git diff --cached --check`, `git diff --exit-code` | Exit 0 after staging the intentional implementation/evidence changes; the excluded untracked bundle is not part of this claim |
| Evidence finalizer / index check | 127 local file links, all ZIP CRC/member hashes, 16 evidence members plus checksum file, and staged evidence bytes verified |

Compiler, failed XML, successful XML and GameTest logs are retained in the root
archive; the failed world is a separate archive. Expected migration fault-
injection ERROR logs are not counted as new failures. Final artifact IDs and
generated-resource bytes are recorded separately, not inferred from log text.

## Packaged upgrade and restart

The unchanged bounded runner executed:

```text
python -B scripts/run_v140_planetary_worlds_smoke.py <fresh libraries-only server>
  --baseline-jar <pre-planet v1.4 JAR> --host-jar <frozen SKY JAR>
  --evidence-dir <fresh disjoint native directory>
  --java C:/Program Files/Java/jdk-17.0.7/bin/java.exe --accept-eula
```

The precise paths, arguments, artifact hashes and exit receipts are in native
`summary.json` and each process's `launch.json`/`observations.json`. Baseline
SHA256 is `9fc582f1b83599526627b065d72b9995fbb7a69e04065a135dce7aa8697b1acf`.
All three local offline operator-driven JVMs exited 0. One rocket followed
Earth → Mars → Venus → Earth, with exact fuel **2000 → 1552 → 1019 → 504**;
the final world was cleanly restarted. This is a packaged compatibility check,
not a player/menu/network/audio/sky-render test. No SSH or remote host was used.
The self-owned listen port 59345 was closed after completion.

## Independent verification

The contract, source, incremental state-guard and failure/fixture reviews are
retained with their original findings and corrections. Independent final
artifact inspection confirms unique CRC-valid entries, exact current-version
resource bytes in main/sources, the two old-type effects-only changes, unchanged
API bytes (all 27 classes also match main) and no production flight-code changes.

- The eight exact SKY-focused JUnit classes ran with `--rerun-tasks --offline
  --no-daemon --no-build-cache`: exit 0, **25 tests / 8 suites**, no skipped,
  failed or errored cases, 47.798 seconds.
- Independent `runData runGameTestServer` passed in 132.892 seconds: zero
  writes among 33 resources, all **234 required tests** in the reused world,
  and four matched 25-block fixture install/restore pairs. One 2140-ms /
  42-tick-behind warning is retained, not hidden or presented as a load result.
- All **1187** source/build/generated input hashes and the three artifact
  hashes were unchanged across the independent commands.
- Raw native readback verifies the **138-member** manifest, three clean exits,
  rocket/cargo authority and ledgers: **17 diamonds + 64 iron ingots**, station
  counts **1 → 3 → 3**, binding counts **3 → 6 → 6**, and identical final rocket,
  station and transfer authority across clean restart. Native stdout retains
  **25 / 10 / 10** warnings, with no ERROR/FATAL. This audit launches no additional
  server and does not claim whole-world byte identity.

The supplemental audit initially referenced `bounds` instead of the actual
saved `bounding_box`; its KeyError and helper snapshot are retained. Correcting
the reader's field name preserves the conservation predicates and captured
world bytes. This was an audit-helper error, not a new runtime failure.

Exact commands/receipts and original helper diagnostics are retained in the
independent archive. Source and command reviews do not grant release approval.

## Evidence inventory

- [Root results](root-checks.json), [raw logs and XML](root-checks.zip) and
  [member hashes](root-checks-files.json).
- [Failed native GameTest world](failed-world.zip) and
  [member hashes](failed-world-files.json).
- [Packaged native restart](native-restart.zip), [summary](native-summary.json)
  and [member hashes](native-restart-files.json).
- [Original independent reports](independent-review.zip) and
  [member hashes](independent-review-files.json).
- [Artifact identities](development-artifacts.json),
  [33 generated resources](generated-resources.json),
  [changed source identities](source-identity.json), [local link checks](links.json)
  and [complete evidence checksums](SHA256SUMS.txt).

## Remaining boundaries

[Manual cases](MANUAL-CASES.md) are **NOT EXECUTED**: actual GPU rendering, audio
listening, shader/mod compatibility, resource reload visual continuity, two real
clients and frame-time measurements. Per-frame state-query overhead is not
measured. Generic Space has no station-specific parent planet; profiles are
stylized, not an ephemeris. Failed or overridden profiles/types can retain
platform fallback. Conservative production landing still refuses vegetation-
occupied candidates; this fixture correction does not promise new terrain clearing.

Full S2/forced-stop, long-load, gameplay parity and G0–G9 acceptance remain
deferred/open under ADR-018; all original machinery/dimensions are not yet
complete. No remote deployment, tag, push or human release approval is implied.
The next current-version implementation is discovery/research, then remaining
migration/recovery work.
