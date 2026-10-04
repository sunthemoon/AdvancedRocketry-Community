# C16a-04 classic fluids — development verification

Date: 2026-10-03. Branch `codex/v1.8.0-classic-content`; HEAD
`cd63c5ff53e3daa0e6c3be92f17f16c061ddc5a6`. This is an uncommitted development
snapshot, not a release candidate. Contract: accepted ADR-064 revision 2.
Version remains **IN_PROGRESS / IMPLEMENTING**; no Required Gate is passed.

## Delivered scope

Ten ledger units are delivered, leaving **189 PLANNED rows /156 REVIEW assets**.
Container-only gas block redesigns deliberately do not register world blocks
or buckets; the old upstream block concepts become the following Forge fluids.

| Legacy unit | Disposition | Stable modern identity |
|---|---|---|
| `block:enrichedLavaFluid` | IMPLEMENTED | `advancedrocketrycommunity:enriched_lava` |
| `block:hydrogenFluid` | REDESIGNED | `advancedrocketrycommunity:hydrogen` |
| `block:nitrogenFluid` | REDESIGNED | `advancedrocketrycommunity:nitrogen` |
| `block:oxygenFluid` | REDESIGNED | `advancedrocketrycommunity:oxygen` |
| `block:rocketFuel` | IMPLEMENTED | `advancedrocketrycommunity:rocket_fuel` |
| `fluid:enrichedLava` | IMPLEMENTED | `advancedrocketrycommunity:enriched_lava` |
| `fluid:hydrogen` | IMPLEMENTED | `advancedrocketrycommunity:hydrogen` |
| `fluid:nitrogen` | IMPLEMENTED | `advancedrocketrycommunity:nitrogen` |
| `fluid:oxygen` | IMPLEMENTED | `advancedrocketrycommunity:oxygen` |
| `fluid:rocketFuel` | IMPLEMENTED | `advancedrocketrycommunity:rocket_fuel` |

Five FluidTypes retain the audited physical/tint facts. Rocket fuel and
enriched lava have source/flowing identities, LiquidBlocks and the original
`advancedrocketrycommunity:rocket_fuel_bucket` /
`advancedrocketrycommunity:enriched_lava_bucket` sprites. Enriched lava uses
the entity lava-heat action. New Venus volcano cores/pools use enriched lava;
explored blocks are not migrated. Exact generator assertions require the new
fluid, while general terrain samples also permit old vanilla lava.

Stable `advancedrocketrycommunity:empty_canister`,
`advancedrocketrycommunity:hydrogen_canister`,
`advancedrocketrycommunity:oxygen_canister` and new
`advancedrocketrycommunity:nitrogen_canister` retain their 16-stack limit.
Detached count-one Forge handlers swap exactly 1,000 mB; direct multi-stack
fill/drain refuses simulation and execution. Partial/tagged fluid transfers,
oversized item metadata and foreign serializable item capabilities refuse
instead of discarding unknown data. Bounded metadata survives an accepted
swap. Suit and vent behavior remains ID-compatible.

The v1.8 gas-giant table retains H8 and adds selectable N8 per 1,000 ticks,
without changing mission eligibility, duration or selected-product rules.
Only its historical v1.6 copy is excluded from main/source packaging; that
file remains unchanged. Both JARs contain exactly one current gas table.
Thirteen newly drawn PNGs, models, bilingual labels and creative entries are
repository MIT work; no upstream bitmap/code is copied.

Not delivered: recipe tag/signature migration, classic shared hatches,
motors/casing alias, tanks/pumps, C16b–d, C17–C19, full progression, release
approval or real-client visual/multiplayer acceptance.

## Actual checks

Windows, Java 17.0.7, Forge 47.4.10, Minecraft 1.20.1. Heavy Gradle/native
jobs are serialized. The isolated implementation worker and reviewer do not
write central files. The user's untracked development-document bundle is
excluded from source snapshots and archives.

| Command/check | Observed result |
|---|---|
| `gradlew.bat compileJava compileTestJava` | Exit 0 |
| `gradlew.bat runData test --tests '*fluid*'` | Exit 0; 17 JUnit cases |
| `gradlew.bat clean build runData runGameTestServer` (`full-01`) | Exit 1; JUnit passed, one of 409 required GT failed on the former one-product view assertion; retained |
| Same command (`full-02`) | Exit 0, 6m34s; **1,487 JUnit /280 suites**, zero failures/errors/skips; **410 required GameTests**, seed 0 |
| Frozen build/source comparison after DataGen | Java/build/generated inputs unchanged; later harness-only corrections listed separately |
| `python -B -m unittest tests.test_run_v180_fluid_smoke -v` | Exit 0; 6 cases, including duplicate slots and captured-chunk coordinate bounds |
| Original-art screen against vanilla 1.12.2 and 1.20.1 | Exit 0; **49 CLEAR**, no SUSPECT/HIT; 13 additional original fluid textures |
| Independent `--offline clean test --tests '*fluid.*' build runData runGameTestServer` | Exit 0; **17 JUnit /4 suites**, all **410 required GT**, zero failures/skips |
| Independent source and artifact verification | 2,592 runtime entries verified; DataGen equality; main/API JAR SHA exactly reproduced |
| Independent pure planner probe | 922 boundary/capacity decisions passed |
| Native packaged-server `run_v180_fluid_smoke.py` (`native-02`) | Exit 0; four phases, legacy v1.7 seed then v1.8 and two same-world restarts |
| Independent native/art packet review | Actual files and NBT decoded/audited; **not independently replayed** |
| `validate_repository.py --require-approved-identity` | Exit 0; 45 passed, no warnings/pending/failures |
| `validate_v1plus_planning.py`, `validate_bootstrap_provenance.py` | Exit 0 each |
| `validate_v180_content_ledger.py --require-accepted` | Exit 0; 653 units; 189 PLANNED /156 REVIEW remain |
| Same ledger check with `--closure` | Exit 1 on actual undelivered content/assets; not version-complete |
| `validate_v120_machine_resources.py`, `check_client_imports.py`, `validate_build_artifact.py --expected-version 1.20.1-1.8.0-dev <main-jar>` | Exit 0 each |
| `git diff --check`; `git diff --exit-code` | Exit 0 /1; intentional uncommitted development differences |

The new automated coverage is 17 JUnit cases and nine GT cases: eight real
Forge capability/container/suit/vent/liquid/heat checks plus the nitrogen
mission-selection/claim check. Existing hydrogen reward remains 576 over
72,000 ticks in its mission fixture; the view now has two products. No
timeout, production budget or resource assertion was relaxed.

## Native persistence and retained corrections

The harness operates on a fresh copy of the historical fixture world and
installs hash-pinned v1.7/current/adapter-test JARs. It does not modify the
source world or input JARs. The fixed FULL-loaded chunk holds original
16-stack empty/H/O canisters with metadata, new N16 and two count-one liquid
buckets. Live read-only capability reports require exact fluids/amounts and
stacked refusal; native saved chest NBT remains identical over two restarts.
Exact source states at (130/136/140,80,132) preserve old vanilla lava and new
rocket fuel/enriched lava. Server phases exit 0 with no unexpected findings.

Two Low independent harness findings were fixed: duplicate slot records could
be collapsed by a dictionary, and the first old-lava fixture was outside the
captured chunk. Duplicate slots now refuse with a regression; seed/query
coordinates and their complete basin remain in chunk 8,8. The first native
attempt and its failed oracle are retained; `native-02` passes unchanged
resource expectations. Native verification now occurs before a phase is
marked PASS. The v1.6 adapter fixture is copied for historical-world registry
compatibility. These harness changes do not alter tested Java/resources.

## Evidence identity and residual gates

Main JAR: **5,064,574 bytes**, SHA-256
`a1f65ddfff1f638e45690a52041e9f1cc58a93d5a5f6a963601da116e5d7d674`.
Sources JAR SHA-256:
`90148def87c4272f0b78e2ecafedd105b46d10e85de1f13684e3be5b22f6d3f1`.
API JAR SHA-256 remains
`252463ff81bdfc1d48c9e6c3ea397e7ca1bb00f4ebf9e5b804405bd27fe6b2eb`.
The unchanged wrapper files were omitted by the first baseline capture and
added only after confirming their git diff was empty; license packaging inputs
are separately hashed. Prior generator/C15 evidence is not overwritten.

The [task archive](root-evidence.zip), [source manifest](source-identity.json),
[checksums](checksums.txt), [independent report](reviews/REVIEW-FINAL.md),
[independent packet](reviews/review-evidence.zip) and final validator receipts
in this directory bind the exact uncommitted snapshot. Root native/art checks
are independently audited, while Java/build/automated checks are independently
rerun; all three resulting JARs are byte-identical, sources included.
`git diff --exit-code` is intentionally 1 for the dirty development tree; it
is not clean-candidate or G2 evidence. Ledger closure must still fail on the
actual remaining rows/assets. V1/V2 and all inherited G0–G9 requirements remain
open; this slice does not make the whole version development-complete.
