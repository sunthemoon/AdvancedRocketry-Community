# C15c M3 independent follow-up review

Date: 2026-10-03. Reviewer: delegated independent read-only reviewer `/root/review_c15c`. Base HEAD: `cd63c5ff53e3daa0e6c3be92f17f16c061ddc5a6`. Root supplied the owner's choice to enlarge the landing ground while retaining existing rocket constraints. This review assesses the resulting dirty implementation snapshot; it does not infer approval of the entire ADR revision, visual assets, or version gates.

## Scope and status

Re-reviewed actual source/test/doc/generated-resource changes for the radius expansion, inspected the unchanged selector/rocket bounds/feature-reach/floor/filter contracts, and independently executed a geometry probe and targeted checks in the prior `git archive` export. No repository writes, user-bundle reads, or child agents occurred. Temporary artifacts are under this fresh follow-up directory.

**Source/geometry disposition: C15cR1-M3 is remedied for newly generated chunks.** No additional implementation finding was identified in this scope. Targeted final runtime result is recorded below; no whole-version Gate PASS is assigned.

## Geometry and preserved contracts

- [ExoplanetWorldgen.java](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/celestial/exoplanet/worldgen/ExoplanetWorldgen.java), lines 24–31, sets radius 224 and correctly describes rectangular footprints and diagonal feature reach.
- [LandingGroundTest.java](../../../../src/test/java/io/github/sunthemoon/advancedrocketrycommunity/celestial/exoplanet/worldgen/LandingGroundTest.java), lines 34–72, enumerates widths/depths 1–256 over the eight actual pads, rejects rectangles exceeding 16 chunks, and uses the selector-equivalent integer centre `pad - (size - 1) / 2`. It includes the actual admissible 255×4×1 RocketBounds regression.
- The production selector's centring is `pad - floorDiv(minimum + maximum, 2)`. For any integer relative minimum and width this normalizes to the tested formula; bounds overflows still fail admission in production. The unchanged [RocketLandingPadSelector.java](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/transfer/RocketLandingPadSelector.java), lines 33–43 and 231–270, provides these eight pads and arithmetic. No RocketBounds/flight/transfer limit was changed.
- The independently compiled probe constructs **actual captured RocketBounds** objects, derives radius 224 / budget 16 / reach 12 / pad offsets from captured production source, and replays the inspected selector arithmetic. It is not claimed to execute the Forge selector itself. Any admissible footprint has each axis at most 16 chunks ×16 blocks, so the finite rectangle sweep covers all admitted horizontal extents; the probe also enforces the existing bounding-volume limit.
- It observed **67,736 admissible rectangles**, with **0 chunk-admission mismatches** between final test and selector formulas. Largest corner radius: **201.75727991822254** (pad 64,64; rectangle 2×254; x 64..65, z −62..191). Adding the conservative diagonal feature bound `hypot(12,12)` yields **218.72784266669967 <224**, margin **5.27215733330033**. A tighter axiswise corner envelope is 217.1128738697915. Actual 255×4×1 bounds remain accepted, volume 1,020.
- The earlier intermediate test used `pad - size / 2` and stale codec/filter boundary literals. Review identified those and root corrected them before the final snapshot. Initial geometry observation and inputs remain in `initial-snapshot/`, `initial-snapshot.json`, and `initial-geometry.log`; no stale calculation is treated as the final proof.

## Generated data, test expectations, and seams

- The eight generated JSON files contain exactly **10 semantic scalar changes**, all 160→224: three floor `inner_radius` values in Tau Ceti f's noise router and one radius in each of the seven placed-feature filters. No other semantic changes were found. See `generated-semantic-diff.json` and `final-generated-check.json`.
- [V180ExoplanetResourcesTest.java](../../../../src/test/java/io/github/sunthemoon/advancedrocketrycommunity/celestial/exoplanet/V180ExoplanetResourcesTest.java), lines 148 and 222, retains exact radius assertions, now 224. LandingGroundTest retains exact codec expectations and meaningful diagonal inside/outside checks at (158,158)/(159,159). These changes align tests with the owner-authorized expanded reserve; they do not weaken bounds, remove tests, relax budgets/timeouts, or suppress errors.
- [ADR-063](../../../../docs/decisions/ADR-063-MATERIALS-ORES-AND-PLANETARY-SURFACES.md), lines 479–502, and [CHANGELOG.md](../../../../CHANGELOG.md), lines 79 and 91–94, disclose the rectangular envelope and **new-generation-only** expansion. Existing chunks retain their earlier terrain/plants, including a prior 160-block reserve. The fix does not retroactively clear or rewrite them.
- Final snapshot also restores the v1.8 client DataGen writer: earlier version providers do not run during v1.8 generation, and earlier outputs are immutable. V180ExoplanetData and fallback both obtain their Tau Ceti profiles from canonical SkyProfiles. This supersedes the earlier draft's proposed single-provider integration while retaining one profile-value authoring source. Root's runData was reported successful; this reviewer independently checks its resulting JSONs, not a separate DataGen execution.

## Input identity

`final-snapshot.json` records bytes/SHA-256 for **27 inputs: 17 Java files, 2 documents and 8 generated JSONs**. Captured byte-for-byte inputs are retained under `final-snapshot/`; pre-sync export inputs under `before-final-sync/`. Key hashes:

Input-manifest SHA-256: `6bdcacd1bbfea8feac1e0e4b6ac30d3434e52e26d032c8668fa590e2e3544ef8`.

| Input | SHA-256 |
|---|---|
| ExoplanetWorldgen.java | `b2b470d20616fe305140f3a0e5b6d4516477f4a8d5a1f1a02250c306478409a8` |
| LandingGroundTest.java | `46fc976e69fc942d06f7612e7b77344e8b287525b8f27aac39b105aab65d7de9` |
| V180ExoplanetResourcesTest.java | `02557505fe6c375782e3a5881c3abd41bb37934c695cbff4f94e076f702ee273` |
| tau_ceti_f noise_settings JSON | `4c98713cd12d959146cc36ee17fa1e846df388b24bcc0770990a93d25d5a6c4b` |

`post-test-doc-supplement.json` separately records two later documentation-only inputs, not rerun bytes: LandingGroundFilter's Javadoc example (SHA-256 `79b07ffd1f9538c89531e3113dbb978a813147bf4992f684ba40a26405b334fd`) and CELESTIAL-DATA-GUIDE (SHA-256 `5bf98ce1d1e4d254a3c85b933e3904ce9884c1f952fb91d7fe672dbf4dad56e8`). An exact byte replacement check confirms the Java change only replaces the Javadoc example160 with224. The [guide](../../../../docs/CELESTIAL-DATA-GUIDE.md), lines 96–104, now describes feature origins outside the ground, permitted landing footprints clear, existing-chunk seams, and unchanged rocket size limits; it does not claim that all cells near the reserve's outer boundary are free of feature writes. Supplement capture exited0; no additional heavy rerun was necessary for these comments/docs.

## Actual commands and results

All commands run from the absolute Temp follow-up/export directories, with Java 17.0.7; nothing writes to the repository.

| Check | Actual result | Evidence |
|---|---|---|
| `python -B prepare_m3_probe.py`; `javac` captured RocketBounds/RocketPosition/RocketSnapshotException/RocketValidationCode/RocketLimits plus `LandingEnvelopeProbe.java`; `java -cp classes LandingEnvelopeProbe` | 0 /0 /0; final geometry values above | `prepare_m3_probe.py`, `LandingEnvelopeProbe.java`, `snapshot.json`, `javac.log`, `javac.exit`, `geometry.log`, `geometry.exit` |
| `python -B sync_final_m3.py`; `python -B check_generated_diff.py` | 0 /0; 27 captured inputs, 8 JSONs /10 radius scalar changes | `final-snapshot.json`, `generated-semantic-diff.json` |
| First targeted Gradle command below | **1**, 45s; 38 JUnit /2 failures (resource assertions still expected160); GameTests not reached | `failed-first-run/gradle-m3-final.log`, `.exit`, `junit-xml/`, input manifest and original resource test |
| Same command after root updates two exact resource assertions | **0**, 1m35s; **38 JUnit /9 suites, 0 failures/errors/skips; 42 required GameTests passed** | `gradle-m3-final.log`, `gradle-m3-final.exit`, `final-test-results.json`, `final-junit-xml/`, `final-gametest-latest.log` |

Targeted command:

```text
gradlew.bat --offline --no-daemon --max-workers=2 test --tests '*Exoplanet*' --tests '*LandingGroundTest' --tests '*FlashCooldownTest' --tests '*SkyMathTest' --tests '*SkyProfileTest' --tests '*SkyProfileReloadTest' --tests '*SkyResourcesTest' runGameTestServer
```

The export-only GameTest namespace selector remains unchanged: selected 12 C15c tests and 29 adapter tests, plus the separately retained one water-depth observation probe. Production assertions are unchanged; other tests are filtered by holder namespace, not deleted. Batch hooks still register. GameTests use seed0, not a multi-seed campaign.

## Limits and recommendation

This is geometry/JUnit/generated-resource verification plus existing selected GameTests. It is not an actual full-size 255×4×1 rocket flight or a newly added runtime scan of every rectangle/seed. No independent client/GPU/V1, existing-chunk migration campaign, packaged server restart, performance soak, human artwork/ADR approval, or complete v1.8 progression closure is claimed.

The unchanged 200 PLANNED content rows and remaining version gates remain outside this remediation. Keep v1.8 IN PROGRESS. The targeted final run passes; integrate the C15c fixes/evidence and continue only the remaining v1.8 slices and required gates.
