# C15b independent implementation review, round 2

- Reviewed HEAD: `cd63c5ff53e3daa0e6c3be92f17f16c061ddc5a6`.
- Baseline for the review delta: C15b round 1 delivery `7be92263`.
- Repository: `D:/GitHub/AdvancedRocketry-Community`.
- Isolation: repository read only; tracked HEAD exported with `git archive` to this directory's `tree/`. The untracked user development-document bundle was not read. No repository files were written.
- Scope: C15b answers to round 1; surface production code and tests; surface DataGen; C15b dispositions and packets; C15c native seam evidence answering C15b findings. Not a review of all C15c functionality or of ADR-064.

## Findings

No new actionable Critical, High, Medium or Low findings identified in this scope.

Round 1 required changes have been implemented and independently checked:

- H1: the geode start uses the lowest ground of its bounded 5 x 5 grid; every placed column independently clamps its roof to ground minus COVER. The real Venus coverage test generates 576 chunks and inspects cover, with at least three hollow starts. Both this test and the manually lowered-ground placement test passed independently.
- M1: Moon turf is the fallback for foreign biomes; the resource test pins lowlands to dark turf and other biomes to highland turf. The ADR and CHANGELOG disclose the hybrid upgrade band and missing new ores/starts. The C15c harness force-loads the explored square/ring and asserts actual hybrid Moon columns rather than merely accepting an old-surface-only sample.
- M2: the placement helper now snapshots the box plus a 16-block margin before/after and rejects unrecorded changes. Pool rise is shared with the arithmetic test and checked against the rim. Saved-piece tests compare actual loaded numbers, not only tags. The crater fixture deliberately exercises the floor clamp. All selected surface tests passed. The unsupported rim-cap testing claim is explicitly withdrawn.
- M3: the GameTest seed is written before startup. The independent export's generated `server.properties` contains `level-seed=0`.
- M4: worldgen and press tests use in-memory overrides; selected switch/press tests passed. The earlier unexplained press failure remains disclosed rather than called proven fixed by its hypothesis.
- M5: the fixed Venus layout rationale now states the actual technical choice and acknowledges that legacy biome layers were seeded. The per-world layout remains an owner decision.
- L1-L5: bounded volcano relief sampling, non-flammability checks, full placed-feature set, cave/biome/tree difference disclosures and failure-safe elevator batch cleanup are present. Applicable JUnit/GameTests were independently executed.
- L6: the archived C15c native check forces all explored chunks and performs an Overworld warm-up before the comparison. Archive integrity and reported receipts were independently audited; the native server harness itself was not rerun in this review.

## Independent execution

Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8 offline.

| Command | Exit | Evidence/result |
|---|---|---|
| `git archive --format=tar -o <this directory>/head.tar cd63c5ff`; `tar -xf <this directory>/head.tar -C <this directory>/tree` | 0 / 0 | Fresh tracked export |
| `gradlew.bat --offline test --tests '*V180PlanetResourcesTest' --tests '*SurfaceShapesTest' --tests '*V180SurfaceArtTest' --tests '*SwitchOverridesTest' --tests '*V180MaterialResourcesTest' --tests '*CommonConfigTest' --no-daemon` | 0 | `junit.log`, `junit-results/`: 37 tests, 6 suites, 0 failures/errors/skips; 35 seconds |
| `python -B <this directory>/select_gametests.py` | 0 | `annotation-selection.json` plus `annotation-backup/`: only non-selected holder annotations changed |
| `gradlew.bat --offline runGameTestServer --no-daemon` | 0 | `gametest.log`, `gametest-latest.log`: all 62 required tests passed, 1 minute 33 seconds |
| `python -B <this directory>/audit_packet.py` | 0 | `packet-audit.log`, `packet-audit.json` |
| `python -B scripts/validate_v180_content_ledger.py --require-accepted` in repository | 0 | PASS; 653 units, 200 still PLANNED |
| `python -B scripts/check_client_imports.py` in repository | 0 | No client imports outside client package |
| `python -B scripts/validate_v090_resources.py` in repository | 0 | 1,154 files, 325 JSON, 774 bilingual keys, 348 references |
| `git diff --check 7be92263..cd63c5ff` | 0 | No whitespace errors |

The annotation-selection script retains the original annotations in five classes: `PlanetSurfaceGameTests`, `PlanetFeatureGameTests`, `VenusStructureGameTests`, `MaterialGameTests`, `MaterialRecipeGameTests`. It changes 84 other holders to a namespace disabled by the existing Gradle configuration. Production code, selected tests, assertions and timeouts are unchanged. Forge registers batch callbacks independently of the namespace filtering; the selected run therefore retains their hooks. This is targeted execution, not an immutable full-suite or release-build claim.

Packet checks: C15b 10 SHA256 entries, 483 root-check archive members, 122 independent-review archive members and 690 source identities at `7be92263`; C15c 7 SHA256 entries, 384 root-check archive members and 180 source identities at `b4b81e9f`. Every checked size/hash matched. Archived C15c full JUnit XML totals reproduce 1,441 tests / 271 suites / 0 failures/errors/skips. Native summary reports 300 explored chunks unchanged and 76 hybrid plus 128 old-surface sampled columns per body's upgrade band; these are archived observations, not independent native rerun results.

The first invocation of the reviewer-written packet audit exited 1 because it initially treated the independent-review manifest object as a list. The script was corrected for that format and the full audit rerun successfully; this was a reviewer script error, not a repository defect.

## Remaining boundaries / recommendation

C15b round 1 required implementation changes are satisfied; the reviewed implementation scope can be marked independently verified. This does not accept ADRs on the maintainer's behalf and does not establish v1.8 release readiness.

ADR-063 revisions 4, 5 and 6 and ADR-061 revision 7 remain proposed. The iron-plate route and seeded Venus layout are owner decisions. Human visual/provenance acceptance for the generated surfaces and actual GPU client verification remain open. No client, long exploration, worldgen reload or CI execution was performed here. The archived packaged-server seam check was inspected/audited but not independently rerun. C16-C19 and the v1.8 Required Gate set remain outside this review's completed scope.

Repository modifications: none. New repository tests: none. All reviewer artifacts are under this one fresh Temp directory. No version approval or Gate PASS is claimed.
