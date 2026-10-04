# C15c independent implementation review

Reviewed baseline: `cd63c5ff` on `codex/v1.8.0-classic-content`, C15c delivery `b4b81e9f`, implementation `28d24b8193731c8f68764b8bf76990361df60a74`; related C15b fixes are present. Date: 2026-10-03. Reviewer: independent delegated Codex review session `/root/review_c15c`.

Scope: celestial/exoplanet, client/exoplanet and relevant sky integration, V180Exoplanet DataGen, Exoplanet/TauCeti tests, generated resources and the C15c evidence packet. Read the required project/version/testing/coordination/provenance documents. No repository writes, child agents, or reads of the untracked user development-documents bundle. All executable checks used a fresh absolute Temp directory and `git archive` export.

## Findings at original HEAD, ordered by severity

### C15cR1-M1 — Electric-mushroom flashes do not reach the custom planetary sky

References: [ClientExoplanetEffects.java](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/exoplanet/ClientExoplanetEffects.java), original lines 59–75; [PlanetaryDimensionEffects.java](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/sky/PlanetaryDimensionEffects.java), original lines 31–38; [PlanetarySkyRenderer.java](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/sky/PlanetarySkyRenderer.java), original lines 54–59.

The mushroom callback calls `ClientLevel.setSkyFlashTime(2)`, but the custom sky render path accepts only the selected profile, matrices and sun angle. Its sphere/horizon colours depend on profile/daylight, never the flash time. Tau Ceti g uses this custom effect, so its advertised sky flash has no colour input; the thunder and vanilla lightmap may still respond. This is a confirmed static data-flow defect, not a GPU observation.

Remedy: pass bounded client flash strength to the renderer, apply a pure tested colour blend, respect Minecraft's hide-lightning-flash preference, and preserve client-only effects with no entity damage/fire/network mutation.

Root applied that remedy during this review; independent amended-source checks are recorded below. V1 remains unverified.

### C15cR1-M2 — The flash cooldown survives into another world with a different clock

References: [ClientExoplanetEffects.java](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/exoplanet/ClientExoplanetEffects.java), original lines 52–72; [ElectricMushroomBlock.java](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/celestial/exoplanet/ElectricMushroomBlock.java), lines 29–40.

Client setup installs one `Flashes` object in a static callback; its `nextFlash` uses the first world's game time and has no logout/world-change reset. After a flash at game time 1,000,000, joining a fresh world suppresses the effect until time 1,000,100 (about 13.9 hours), not five seconds. The same mechanism can suppress after a clock rewind.

Reproduction: `FlashProbe.java` compiles the exact original private `Flashes` implementation with minimal recording external-API stubs. Outputs: old world flashes 1; fresh world with retained callback flashes 0 at times 0 and 100; independently new callback in fresh world flashes 1; retained next flash 1,000,100. `javac` and `java` both exited 0. This is an isolated behaviour probe, not a real client session.

Remedy: world-identity-scoped cooldown without retaining an unloaded ClientLevel, or explicit lifecycle resets; handle clock rewind/overflow and test same-world rate limiting. Root added a weak-identity `FlashCooldown` and regression tests. Callback installation itself is a volatile assignment and does not require `enqueueWork` solely for that assignment.

### C15cR1-M3 — Landing-ground coverage assumes a square footprint that existing rocket constraints do not require

References: [ExoplanetWorldgen.java](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/celestial/exoplanet/worldgen/ExoplanetWorldgen.java), lines 24–30; [LandingGroundTest.java](../../../../src/test/java/io/github/sunthemoon/advancedrocketrycommunity/celestial/exoplanet/worldgen/LandingGroundTest.java), lines 31–39; [TauCetiPathGameTests.java](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/TauCetiPathGameTests.java), lines 40–43 and 144–176. Related existing contracts: [RocketBounds.java](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/model/RocketBounds.java), lines 9–28, and [RocketLandingPadSelector.java](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/transfer/RocketLandingPadSelector.java), lines 231–259 and 310–323.

The new test computes the largest footprint as `sqrt(MAX_LANDING_CHUNKS) * 16` and checks only 64×64. The existing model limits total bounding volume and total landing chunks, not equal side lengths. `LandingFootprintProbe.java`, compiled against actual unchanged pure RocketBounds/Position/Limits sources, accepts bounds `(0,0,0)..(254,3,0)` (volume 1,020). With the selector's actual centring at pad `(64,64)`, this spans x −63..191, z 64, exactly 16 chunks, and reaches radius 201.437 — outside the radius-160 dry/clear reserve. Thus the claimed coverage of all admissible footprints/fallback pads is not established; a valid long/thin footprint can encounter water/vegetation outside the reserve.

Remedy: retain existing rocket constraints and derive the reserve from all permitted rectangular chunk extents plus diagonal feature reach, with non-square regression cases; alternatively explicitly narrow the new guarantee through an approved decision. Do not silently restrict existing rocket dimensions. Owner decision is pending; this finding is unresolved in the amended snapshot reviewed here.

### C15cR1-L1 — Tau Ceti profiles are not in the initial built-in fallback

References: [SkyProfiles.java](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/celestial/visual/SkyProfiles.java), original lines 14–26; [SkyProfileReloadListener.java](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/sky/SkyProfileReloadListener.java), lines 24–25 and 71–77; ADR-063 revision-6 sky paragraph.

The new profiles are generated by ExoplanetContent/V180ExoplanetData, but the initial fallback contains only Moon/space/Mars/Venus. An invalid initial resource reload retains a map without either Tau Ceti profile, and SkySelection therefore cannot select their custom skies. Normal valid resource loading is unaffected. The revision-6 statement that both are new built-ins is inaccurate at original HEAD.

Remedy: one canonical six-profile authoring map used both by initial fallback and the DataGen writer, with initial-failure regression coverage. Root implemented that remedy during review; independent amended-source checks are recorded below.

## Checks and non-findings

- C15c bodies, routes, discovery satellite, Level IDs, generated block models/loot/tags/translations, feature shapes and generation switches are covered by the inspected code and independently executed targeted tests.
- The speculation that `giant_swamp_tree` could not place under shallow water was tested and **not confirmed**: the production configured feature placed over dry ground and water depths 1/2/3, with oak logs at all four origins. It is not a finding; no implementation change was requested. The probe runs on real Forge registries/tags, unlike a tag-less bootstrap assumption.
- No new common-side `net.minecraft.client` references were found. No new C2S channel or saved-data schema was introduced by this slice.
- C15c evidence archive SHA-256, all 384 member size/hash records, member completeness and CRC pass. All 165 `tested_implementation_files` hashes match the original HEAD export. Evidence validity does not grant a Required Gate or human approval.
- The existing seed-0 tests pass, but this is not a multi-seed runtime campaign, nor independent reproduction of the retained packaged S1 run.

## Independently executed commands and results

Evidence root: `C:/Users/Administrator/AppData/Local/Temp/arce-c15c-review-455ade503d994cc5ac8c90ffd376a0a6/`.

| Command/check | Exit/result | Evidence |
|---|---|---|
| Read-only git status/branch/log/diff; `git archive --format=zip HEAD -o <Temp>/candidate.zip`; Expand-Archive | 0 | `candidate.zip`; original HEAD recorded above |
| `python -B scripts/validate_v180_content_ledger.py` in export | 0, PASS | `python-check-0.log` |
| Same with `--require-accepted` | 0, PASS; validator only checks ADR status fields | `python-check-1.log` |
| Same with `--closure` | 1, expected incomplete: 200 PLANNED units, 156 REVIEW assets | `python-check-2.log` |
| `python -B scripts/validate_v090_resources.py` | 0, 1,094 files / 325 JSON / 774 bilingual keys / 348 references | `python-check-3.log` |
| `python -B scripts/check_client_imports.py` | 0, PASS | `python-check-4.log` |
| `python -B -m unittest tests.test_validate_v180_content_ledger` | 0, 58 tests, 147.164 s | `python-check-5.log` |
| Python evidence ZIP/member/hash/CRC and tested-source-hash probe | 0, all match | `run_checks.py`, `evidence-check.json`, `python-commands.json` |
| `javac` exact extracted Flashes probe; `java FlashProbe` | 0 / 0, stale clock reproduced | `FlashProbe.java`, `flash-probe.log`, `flash-probe-source.json` |
| `javac` actual pure RocketBounds dependencies plus footprint probe; `java LandingFootprintProbe` | 0 / 0, valid 16-chunk footprint outside reserve | `LandingFootprintProbe.java`, `landing-footprint-probe.log` |
| `gradlew.bat --offline --no-daemon --max-workers=2 test --tests '*Exoplanet*' --tests '*LandingGroundTest' runGameTestServer` | 0, 1m55s; 18 JUnit / 4 suites, 0 failures/errors/skips; 41 required GameTests | `gradle-original.log`, `.exit`, `original-test-results.json`, `original-junit-xml/`, `original-gametest-latest.log` |
| `gradlew.bat --offline --no-daemon --max-workers=2 runGameTestServer` with one isolated recording probe added | 0, 1m4s; 42 required GameTests; production swamp-tree placements all true at depths 0–3 | `gradle-water-probe.log`, `.exit`, `probe-sources/ExoplanetReviewProbeGameTests.java` |
| `gradlew.bat --offline --no-daemon --max-workers=2 test --tests '*Exoplanet*' --tests '*LandingGroundTest' --tests '*FlashCooldownTest' --tests '*SkyMathTest' --tests '*SkyProfileTest' --tests '*SkyProfileReloadTest' --tests '*SkyResourcesTest' runGameTestServer` on amended snapshot | 0, 1m31s; 37 JUnit / 9 suites, 0 failures/errors/skips; 42 required GameTests | `gradle-fixes.log`, `gradle-fixes.exit`, `fix-test-results.json`, `fix-junit-xml/`, `fix-gametest-latest.log`, `root-fix-snapshot.json` |

The GameTest selection only changes other classes' `@GameTestHolder` namespace to `review_disabled` in the export; C15c selected assertions and production remain unchanged, and batch hooks still register. Adapter tests remain enabled. Original annotations/backups/hashes are retained in `selection-originals/` and `gametest-selection.json`. The water-depth probe is a separate review-only class; its successful completion proves execution, while the log contains the observed placement values.

Root amended source snapshot: 14 scoped Java source/test files copied byte-for-byte with hashes recorded in `root-fix-snapshot.json`; original inputs are retained in `presync-originals/`. Root repository edits are not reviewer edits and are not claimed to be committed. Static review confirms flash-time forwarding, Minecraft hide-lightning preference handling, bounded pure blending, weak world ownership, and canonical six-profile authoring.

## Retained unverified items and recommendation

No actual client/GPU, V0/V1/V2, human texture originality/visual approval, independent packaged S1/S2 restart/migration run, multi-seed broad exploration, live GitHub CI, performance soak, or complete v1.8 recipe/progression closure was run by this review session. The 200 PLANNED rows remain a concrete version-completion gap. Proposed ADR revisions and human approval must not be inferred from `--require-accepted` passing the ADR's top-level status.

Original verdict: changes requested, 0 Critical, 0 High, 3 Medium and 1 Low. M1/M2/L1 have root remediation independently verified by the amended-source targeted JUnit/GameTest run and static integration review; their GPU presentation remains unverified. M3 awaits owner decision and geometric regression coverage. This does not satisfy all v1.8 Required Gates and cannot mark v1.8 PASSED/RELEASED. After M3 disposition and verification, finish the remaining current-version content batches and gate evidence; do not advance to v1.9 implementation.
