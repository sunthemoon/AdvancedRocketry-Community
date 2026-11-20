# Final campaign phase 1 inputs manifest

**DRAFT, 2026-11-20.** W398, for [TASK-W398](../TASK-W398-CAMPAIGN-INPUTS-MANIFEST.md),
Root decision R532. Read-only research on `codex/v1.8.0-w398`, starting at
`059e4a4b40c3f3b7feee061973cec49e21eef971`. The write scope is this record alone.
This is an inputs inventory, not a runnable campaign manifest, a fixture delivery,
a candidate test result or Gate approval. No checklist or historical evidence is changed.

## 1. Reading the inventory

The scope is P1/P2/P3 of [FINAL-CAMPAIGN-PLAN section 5](FINAL-CAMPAIGN-PLAN.md).
Section 5 below accounts for **all 68 open-question bullets** in checklists A through J
(6, 5, 6, 9, 10, 4, 5, 4, 6, 13 respectively), retaining their inventory row IDs.
An answer can be partial: **needs Root** names what is still missing, not a request
to relax a test. O4/O5/O7/O8/O9 require the owner arrangements in
[the final packet section 6](OWNER-FINAL-PACKET-DRAFT.md); Root cannot invent accounts,
equipment or signatures.

Paths in code spans are observed external locations, not Markdown links or proposed
archive names. All relative Markdown links point to files tracked at the starting
shared-branch commit. `E` below means `D:/GitHub/ARCE-Task-Evidence/`; a relative
external location such as `E/root-queue/W69/native-04/` expands under that directory.
`Hxx` identifies a hash in section 3.4; `FIxx` identifies a fixture, not a test result.
SHA-256 is used unless explicitly identified otherwise. A `level.dat` hash identifies
only that file, **not a whole world**. A historical JAR is not C4.

Searches covered the ten checklists, their preparation sections, the plan, relevant
version/release/worker records, tracked scripts and fixture sources, and E's directory
tree. The external filename walk excluded libraries/caches, generated reports,
region/entity/data interiors and similar bulk directories; it searched for `level.dat`
and named world/fixture/API/sources/JEI archives. A separate walk checked Forge launcher
arguments at library roots. Historical ZIPs were inspected with Python `zipfile`,
not booted. Thus **not located** means no usable item was identified by these searches,
not proof that no unindexed backup exists anywhere on the machine. Temporary review
copies belonging to other workers are not campaign inputs.

## 2. P1: candidate C4

The task's C4 identity is confirmed by the downloaded evidence. The plan's description
of C4 as not yet built is older than these inputs; it must not be used to choose a JAR.

| Field | Value and authority |
|---|---|
| Candidate source | `659bbcb6a745885565c07d98f5d695d43beef3f2`. The evidence's `_temp/arce-v180-regression/tested-commit.txt` and `runtime-identity.json` agree. |
| Hosted regression | Run `44630684725`, attempt `1`, as assigned by TASK-W398. This research did not query GitHub or re-evaluate its verdict. |
| Governance | Run `44630684714`, as assigned by TASK-W398; no new governance run here. |
| Installed release artifact | `E/root-queue/C4/candidate/unpacked/advancedrocketry-community-1.20.1-1.8.0-dev.jar`; **15,944,222 bytes**, freshly hashed `c048743e61da4f939ce0492ca3ec73b7d69ef1634577bebc3279c91cf506cea1`. |
| Build ZIP | `E/root-queue/C4/candidate/v180-build-659bbcb6a745885565c07d98f5d695d43beef3f2-1.zip`; 14,418,320 bytes; `40b2032c411724a998af3d840c2b757689ecf8a024ca60d6259ac58bb7f492de`. Its sole member is the release JAR above. |
| Evidence ZIP | `E/root-queue/C4/candidate/v180-build-evidence-659bbcb6a745885565c07d98f5d695d43beef3f2-1.zip`; 4,209,844 bytes; `aedaad8d6175c5f236fa33e06f07cf71ff3191f7780755520080376c9e32cde7`. Copied to an owned Temp folder and extracted there, not in C4's directory. |
| Build identity | `_temp/arce-v180-regression/build-identity.json`: schema 1, build `1.20.1-1.8.0-dev`, Minecraft `1.20.1`, artifact `advancedrocketry-community`; properties hash `4331c961cc2af42c9b764cf588393a59158e938003eece905de51fe4fc5b56cb`. |
| Release audit | `artifact-sha256.txt`, `artifact-audit.log` and `jar-content-manifest.json` agree with the measured release hash; audit reports 6,993 JAR entries. `java-version.txt` records hosted Temurin `17.0.21+9`, distinct from the local Java `17.0.7`. |
| Sources JAR | **Not in these downloaded artifacts; no sources SHA-256 in their identity files.** `build-identity.json` names `build/libs/advancedrocketry-community-1.20.1-1.8.0-dev-sources.jar` but does not hash it. A filename is not an artifact receipt. |
| API JAR | **Not in these downloaded artifacts; no API classifier SHA-256 in their identity files.** Needs Root to retain/build the C4 classifier from the exact source and record its identity before the independent consumer build. |
| Other runtime hashes | `runtime-identity.json` lists `adapterTest.jar`, `ciGameTest.jar`, `main.jar`, `release.jar`. The adapter source-set runtime hash is not a separately published API classifier or a packaged independent compatibility mod. |
| Network pin | `git show 659bbcb6a:src/test/resources/network-protocols.txt` gives `rocket_flight=10`, `celestial_snapshot=4`, `classic_controls=3`, `suit_motion=2`, and `endgame`, `life_support_status`, `rocket_visual`, `satellite` each `1`. See the [tracked pin](../../../../src/test/resources/network-protocols.txt). Neither old navigation protocol 7 nor discovery protocol 8 is the C4 flight pin. |

The C4 native campaign was running during this research. Its existing
`E/root-queue/C4/campaign/plan.json` has 78 families, `partial=false`, no omitted
families and no unresolved argument requirements; H09 identifies that immutable
plan snapshot. `campaign-attempt1.json`, logs and raw cut images are **in-progress
outputs**, not accepted immutable P2 baselines. No conclusion about the completed
campaign, X01, X02 or downgrade acceptance is drawn here.

## 3. P2: fixtures

### 3.1 Historical and general worlds

Existence and readiness are separate. A runner that creates a world is useful even
when its previous temporary server was correctly removed. Rebuilding on an old JAR
can establish a new historical-format test fixture, but cannot establish that a
world was actually played historically. Root must retain that distinction for
historical in-flight, pre-sky and earned-progress rows.

**Preparation estimates** below are W398's rough engineering estimates, not measured
durations or acceptance budgets. They exclude lock wait, campaign execution, downloading
uncached dependencies and product fixes; overlapping scene work must not be added twice.

| Fixture | What exists, location, creator and hash | Coordinates/settings retained; missing work and rough preparation |
|---|---|---|
| FI01: v0.9 Beta archive (A03) | **Exists and hash matches.** [Original Beta ZIP](../../v1.0.0-beta-world-upgrade/verification/populated/original-beta-world.zip), H01; [report](../../v1.0.0-beta-world-upgrade/VERIFICATION.md), [summary](../../v1.0.0-beta-world-upgrade/verification/populated/summary.json), [creator](../../../../scripts/v100_beta_world_fixture.py). 59 world files, 8,403,030 bytes. | Accepted Beta JAR `fbddf66938000cba369a83d4a22ff36b5ff1c9c635a0abd14f672b454e3946ad`; Forge 47.4.10, JEI absent. Paused machine progress 40/water 1,000/FE 0; running progress 25/water 4,000/FE 19,500. Earth rocket `(384,101,384)`, fuel 1,000; Moon `(8,80,8)`, fuel 628; each chest slots 0/26 = 17 diamonds/64 iron. Moon vent `(64,100,64)`, room x/z 63..65, y 100..102. Station `1d6c1f30-103c-41fe-b53c-6441fdbf2371`, region `[-256,-256,255,255]`, transferred owner ending `1002`, prior owner ending `1001` member. Source summary retains all mission/entity/logical IDs. Copy/manifest and qualify current reader before C4: about 1-2 h. |
| FI02: v1.0 world (B01) | **A retained v1.0-development-era legacy passenger world exists**, [58-file ZIP](../../v1.0.0-legacy-passenger/verification/legacy-world.zip), H02, [record](../../v1.0.0-legacy-passenger/VERIFICATION.md). This is specifically a pre-world-owned-save reconciliation fixture, not a representative accepted final-v1.0 base. | Owner RootVehicle, two assigned seats, fuel 618, 17 diamonds. [Passenger persistence captures](../../v1.0.0-passenger-persistence/VERIFICATION.md) retain player/entity NBT projections but not a complete source world. **Needs Root:** select whether H02 covers a separate legacy branch and build/retain a representative normal v1.0 world with machine/station/target projections. About 2-4 h. |
| FI03: historical v1.0 in-flight variant (B04) | **No complete in-flight migration source located.** FI02 is not asserted to be in flight. [Flight cut runner](../../../../scripts/run_v100_flight_forced_stop.py) creates ASSEMBLED/FUELED/COUNTDOWN/ASCENT/TRANSIT_PREPARED/DESTINATION_SPAWNED/PASSENGERS_TRANSFERRED/SOURCE_REMOVED/DESCENT/LANDED cuts; these are a construction route, not proof of historical play. | **Needs Root:** pin historical JAR, exact stop timing, schema/TravelTarget projection and retain a source before upgrade. About 2-4 h after selecting the source; do not substitute a new C4 flight. |
| FI04: representative v1.3 world (D/E10) | **Retained source bytes exist inside** [v1.4 migration archive](../../v1.4.0-mig-worlds/native-migration.zip), H03: `native-final/original-world/` has 34 files, including `level.dat`, regions and satellite/celestial roots; `native-final/legacy-copy/world-backup/` has 53 files after the v1.3 setup. [Report](../../v1.4.0-mig-worlds/VERIFICATION.md) distinguishes the original five historically archived files from the later inventory. | Setup report has six-block rocket origin `(520,101,520)`, fuel/capacity 2,000; native setup adds state that was not in the original capture. Full original/backup selection and archived member inventory must be checked before restoration. About 1-2 h to extract into a new copy and bind it to C4. The [v1.3 flight archive](../../v1.3.0-external-flight/corrected-runtime.zip), H04, instead holds observations/captures; its report explicitly excludes full worlds/libraries. |
| FI05: representative v1.4 world (E10) | **Reports and projections exist; a complete final v1.4 world was not located.** [Migration archive/report](../../v1.4.0-mig-worlds/VERIFICATION.md), H03: `native-final/restored-restart/inputs/world/` contains only `level.dat`, not the final world. [Migration runner](../../../../scripts/run_v140_migration_smoke.py) is the nearest retained reconstruction procedure. | Source evidence contains researched Mars/Venus, the rocket above and station requests; it is not an undiscovered variant. Recreate from FI04 on the pinned historical v1.4 host with intact paired catalogs/roots, or have Root locate the complete historical world. About 3-5 h. |
| FI06: representative v1.5 station/warp world (F) | **Archived native procedure, IDs and NBT exist, no complete world in the package.** [Migration report](../../v1.5.0-mig-native/VERIFICATION.md) and [archive](../../v1.5.0-mig-native/root-checks.zip), H05, final `attempt-10/native/summary.json` and `scripts/native_mig_c3_check.py`; also [station](../../v1.5.0-station-native/VERIFICATION.md) and [warp](../../v1.5.0-warp-native/VERIFICATION.md) reports. | Build from the authentic prior-world copy, not an empty station catalog. Native cuts retain root-4 station migration, permissions and dock observations; their absolute Temp world names are historical, not available E paths. **Needs Root:** restore/recreate complete world, charged cores, owner/member IDs, routes and observation windows. About 3-5 h, overlapping FI05. |
| FI07: representative v1.6 mission world (G) | **Archived procedure/captures exist, no complete world.** [Closure report](../../v1.6.0-c9-closure/VERIFICATION.md), [archive](../../v1.6.0-c9-closure/root-checks.zip), H06, `attempt-04/scripts/native_v160_check.py` and `attempt-04/native/summary.json`. | Procedure uses the prior v1.5 world, native scheduler/claim/removal phases and exact rewards; reports retain a baseline of four forced chunks. **Needs Root:** complete source, ordinary-player terminal controls, multi-hour deadlines and expiry manifest. About 3-5 h, overlapping FI06. Logical instances do not need fabricated world regions. |
| FI08: representative v1.7 endgame world (H/E10) | **Archived native/refload scripts, summaries and captures exist, not a complete server world.** [Closure report](../../v1.7.0-c13-closure/VERIFICATION.md) and [archive](../../v1.7.0-c13-closure/root-checks.zip), H07; final `native/summary.json`, `refload/summary.json` and archived `scripts/native_v170_check.py`/`native_v170_refload.py`. | Prior-world upgrade, endpoint/transfer/restart and reference-load development evidence are retained. Full worlds/JFRs are explicitly excluded. Reconstruct from FI07 or locate the original; bind all target/owner/device inventories on C4. About 3-5 h, overlapping FI07/FI25. Historical Windows refload numbers are not REF acceptance. |
| FI09: all-v1.7-ID/placeholder world (I03) | **Generator and evidence exist, source world intentionally not retained.** [W69](W69.md), [creator](../../../../scripts/v170_world_fixture.py), [upgrade runner](../../../../scripts/run_v180_world_upgrade.py); `E/root-queue/W69/native-04/layout.json` H08 and `source-world-files.json` retain the native inventory. | Historical source `55da6a58842762c382edce0a5a842d06bb76ff6e`; 30 blocks, 57 items, 19 BE types at 24 positions; six managed roots. Block i at `(128+4*(i%12),100,128+4*(i//12))`; item i in chest `(128+4*(i//27),100,240)`, slot `i%27`, count 1. Owner `00690000-0000-4000-8000-000000000001`, rider ending `0002`. Regenerate with that source, retain pre-upgrade bytes and requalify migrations against C4, rather than reuse W69's old candidate oracle blindly: about 2-4 h. |
| FI10: many-machine layout (C/I02/I06/I08) | **A1 layout implementation exists, no retained packaged all-content world located.** [W70](W70.md) and [ClassicScaleGameTests](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/classic/runtime/ClassicScaleGameTests.java) create 16 each of nine classic kinds = 144 controllers over 36 remote chunks, 100 idle ticks, half then active. | Kinds: lathe, arc furnace, cutting, crystallizer, chemical reactor, etcher, centrifuge, observatory, processor. This does not inventory every v1.8 device or the v1.2 three-machine/100-idle scene. **Needs Root:** packaged builder, all delivered kinds/ports, declared active mix/recipes, orientation/unload/damage locations and scaling factor. About 1-2 days, reusing the test's patterns/resource ledgers and the external gen scripts, not exporting a GameTest world as final evidence. |
| FI11: API fixture mod against C4 (D01-D12) | **Source/build project exists; matching independent C4 binary not located.** [compat-test-mod README](../../../../compat-test-mod/README.md), [fixture metadata](../../../../src/adapterTest/resources/META-INF/mods.toml). The default publication is still v1.3; P1's `adapterTest.jar` is not this build. | Publish C4 host/API to a new local Maven directory, select `-ParceVersion=1.20.1-1.8.0-dev` and its `-ParceRepository`, build the independent reobfuscated `arce-adapter-compat-test-1.0.0.jar`, retain boundary reports/hashes. Metadata requires host/Forge/Minecraft on BOTH; it has no display-test exemption. A real client installation/omission handshake still needs measurement. About 1-2 h plus D scenes. |
| FI12: legal 64-block rocket (A12/I09) | **No hashed ready-made 64-block campaign world located.** [Visual budgets](../../../17-V1PLUS-QUALITY-BUDGETS.md) specify the size, not a placement manifest. | Adapt the normal assembler/rocket scan fixtures to an exactly counted, legal fueled structure with seated/observer views and a multi-rocket copy; record origin/seats/snapshot. About 1-2 h shared with FI13/FI14. |
| FI13: legal 512-block rocket (A12/I09) | **No hashed ready-made 512-block campaign world located.** Same source requirement as FI12. | Construct/validate/count exactly 512 blocks through the normal assembly route, with travel and disassembly space; retain coordinates and first-use state. About 1-2 h shared with the other sizes. |
| FI14: legal 2,048-block rocket (A10/A12/I09/J08) | **Boundary benchmark construction exists, not the complete visual fixture.** [Cargo native benchmark](../../../../scripts/run_v180_cargo_native_benchmark.py) checks 2,048 blocks/128 payloads; [W206](W206.md)/[W207](W207.md) retain governed-flight observations. | Preserve the separate 128-payload/1 MiB boundary scene and the normal render/travel scene; do not assume a synthetic snapshot is a buildable player rocket. **Needs Root:** chosen legal builder/coordinate manifest and first-use/relaunch variants. About 2-4 h. |
| FI15: docs/17 reference workload (A08/A09/A11/B07/I10) | **Specification exists, no complete combined world/schedule located.** [Quality budgets section 4](../../../17-V1PLUS-QUALITY-BUDGETS.md), [version section 13](../../../versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md). No prior isolated system load substitutes. | Minimum two real/verified equivalent actors, 16 oxygen devices/rooms, 10 station records, 100 missions/tasks, two active rockets, one 2,048 assembly/disassembly, dimension transfer, periodic saves/restarts, Xmx at most 4 GiB. **Needs Root:** combined ordinary-world builder, room sizes/supply, time schedule, sampling/queue/ticket observers, machine/endgame overlays and O7. About 1-2 days after component fixtures; four-hour soak is additional execution, not preparation. |
| FI16: ordinary core/new-world route (A02/A06/A13/A17/I01) | **Historical scene builders/worlds exist**, `E/v18-client-20261010/scripts/` and `fixtures/v18-test-world-after-r03/`; `E/v18-sp-20261104/scripts/gen.py` through `gen8.py`, `batches*` and `fixtures/r01-world-final` through `r08-world-final`. H10/H11 identify selected level files; H12/H13 the reports. | Historical client defaults are seed 20261006, 1280x720/scale 2; duo A 1600x900/scale 3 and B Chinese/scale 2. These are not supported minimum dimensions or C4 pointer locations. **Needs Root:** C4 continuous witness route, actual recipe/count/device selection, core scene/controller inventory and O4 hand-played subset. About 1-2 days overlapping FI10/FI12-FI15. |

### 3.2 Specialized variants, packs and controls

Each named fixture family below needs its own copied-world/pack inventory; a citation
to a close A1 or native fixture does not declare that it is already runnable with real players.

| Fixture | Existing input and scope | What is still needed; rough preparation |
|---|---|---|
| FI17: W254 dedicated campaign world (J01/J02/J04) | **Procedure exists, no ready two-authenticated-client source world located.** [W254-V2-CHECKLIST sections 0-3](W254-V2-CHECKLIST.md), [bay exit runner](../../../../scripts/run_v180_bay_exit_cuts.py), [W269 design](W269-DESIGN.md). Historical native cut worlds and current C4 images are one-seat/facility fixtures, not the completed V2 world. | Two-seat hand-fuelled Earth-Moon bay rocket, data bay and research-locked solar bay, INTENT hold +48 blocks, initially empty origin column. Safe case: 3x3 pad one below origin; fall case: floor about ten below B's seat, not within one block of origin height. Set `advancedrocketrycommunity.releaseTestHooks=true` and `advancedrocketrycommunity.bayV2CampaignPlayers=<A-UUID>,<B-UUID>` before Forge args. Marker `.arce-bay-v2-campaign` is exactly 32 ASCII bytes `ARCE_BAY_V2_DISPOSABLE_WORLD_V1\n`, H14. Online mode, exactly two whitelisted authenticated UUIDs, enforced whitelist, RCON off, restricted access; startup/connect marker lines checked. **Needs Root:** actual UUIDs, copy/hash, origin/seat/pad IDs and cap/status/S3 recipes. About 4-8 h. |
| FI18: LAN held-bay route (J03) | **No approved LAN route located.** W254 explicitly says the dedicated property is ignored by integrated/LAN servers. | Needs Root to supply an approved LAN held fixture/selection method or record NOT_COVERED. Do not extend the dedicated gate by assumption. Design/qualification estimate 1-2 days, not a promise that implementation is allowed by this task. |
| FI19: API cargo/component/item-fuel scenes (D02/D06/D07/D10) | [Flight](../../../../scripts/run_v130_adapter_flight_smoke.py), [component](../../../../scripts/run_v130_component_smoke.py), [fuel](../../../../scripts/run_v130_fuel_smoke.py) and [recovery](../../../../scripts/run_v130_adapter_recovery_smoke.py) runners create bounded native scenes. Component origin `(264,101,264)`, assembler `(264,100,264)`; diamond engine, emerald tank at x263, oak seat y102, gold guidance y103, quartz auxiliary at x265. Native loader positions `(268,101,264)`, `(270,101,270)`, `(268,101,270)` are in the fuel runner. | Initial component capacity 1,500, changed-definition capacity 2,000; large item-fuel frozen batch total 1,573/remainder 73, ordinary cell total 500/remainder 125 in the historical runner. **Needs Root:** real-player owners, partial F, completion-target eligibility, independent removal copies and exact before/after slot/tag inventories on C4. About 3-5 h. Keep item-fuel batch tests separate from FI29 escrow fluid admission. |
| FI20: API Moon room and oxygen suit (D04/D05) | [Atmosphere runner](../../../../scripts/run_v130_atmosphere_boundary_smoke.py) builds Moon room x/z263..265, y220..222, vent `(264,220,264)`, center air y221, custom boundary `(264,222,264)`; `tag_pack` constructs/removes its override. [Suit runner](../../../../scripts/run_v130_suit_equipment_smoke.py) retains leather chestplate provider data and historical oxygen 999/998/998/1,997 across its phases. | Historical actor counts are not a real player's consumption rate. **Needs Root:** full equipment payload, refill amount/interval, authoritative tags after C4 reload, reachable owner controls and HUD/reconnect schedule. About 2-4 h. |
| FI21: API payload/definition/removal variants (D08-D10) | [Satellite payload runner](../../../../scripts/run_v130_satellite_payload_smoke.py) defines `arce_adapter_test:research_payload`, terminal `(264,101,264)`, owner `c6fcdf97-8f10-450e-ad36-67a644d23c2b`, `world/datapacks/satellite_payload/.../research_payload.json`; native mod-uninstalled/reinstalled phases and exact roots exist. | **Needs Root:** build hashed C4 pack bytes and changed-definition variant, account-bound normal GUI recipe/claim path, stale/range control, two removal copies with complete backup inventory. About 2-4 h shared with FI19/FI20; the native actor is not a real client. |
| FI22: valid/invalid routes and data-only test_mars (B03/B06/E01) | **Tracked JSON seeds exist**, [test_mars body](../../../../src/test/resources/data/advancedrocketrycommunity/test_celestial_bodies/test_mars.json) and [test_earth_mars route](../../../../src/test/resources/data/advancedrocketrycommunity/test_travel_routes/test_earth_mars.json). The latter is schema 1, distance 225, bidirectional Earth to test_mars. [v140 migration fixture](../../../../scripts/v140_migration_fixture.py) builds removal/restoration packs from a pinned host. | Not a ready C4 datapack: move seeds into actual loader directories with metadata, audit current schema, valid/invalid pairs, unknown/locked routes and observed quotes. No direct Mars-Venus leg is assumed. About 2-4 h. |
| FI23: planetary research/private-station/binding variants (E02/E03/E08/E09/E10) | FI04/H03 retains real progress/IDs; [migration](../../../../scripts/run_v140_migration_smoke.py), [discovery cuts](../../../../scripts/run_v140_discovery_cut_smoke.py) and [planetary worlds](../../../../scripts/run_v140_planetary_worlds_smoke.py) runners cover close native cases. [Station commands](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/station/command/StationCommands.java) has permission-2 `/arce station admin create <owner-UUID> <orbit-ID> <name>` and transfer/inspect operations. | Separate undiscovered, discovered, private, paid-pending, blocked binding and station-eligible copies, with both SavedData stores intact. **Needs Root:** allowed operators/UUIDs, precise recovery receipts and packet/pending-menu controls. Never erase IDs, reset historical balances or selectively merge backups. About 4-8 h shared with FI05/FI22. |
| FI24: environment/sky/catalog/pre-sky variants (E04-E07/E11) | [PlanetaryExposureGameTests](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/PlanetaryExposureGameTests.java), [sky manual](../../v1.4.0-sky/MANUAL-CASES.md) and [native-restart ZIP](../../v1.4.0-sky/native-restart.zip), H15, are close inputs. ZIP `baseline/inputs/world/` has **only level.dat**, not a complete pre-sky world. | **Needs Root:** isolated/combined hazard coordinates and modifiers, valid/invalid/oversized sky packs, long-ID/48-character names, 100/128-body catalogs, undiscovered surfaces, full-catalog cooldown readback, real paused integrated-client case and second-world transition. Restore/build a historical pre-sky source with an honest identity. About 1 day plus pack/observer implementation; FI05 overlap applies. |
| FI25: two-/ten-/hundred-station and warp route packs (B02/F01-F07) | [Station native](../../v1.5.0-station-native/VERIFICATION.md), [warp native](../../v1.5.0-warp-native/VERIFICATION.md), FI06 and [StationWarpGameTests](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/StationWarpGameTests.java) supply behavior/setup fragments. Ordinary invite/remove/accept/decline and admin create/transfer/delete syntax are in StationCommands. | **Needs Root:** many distinct permitted owners (no silent per-owner-limit bypass), actual region/orbit/core/energy manifest, target-removal and cost-change pack, C4 10/100 driver and sample windows. `/arce station admin delete <station-UUID> confirm` is the actual permission-2 deletion route; [StationManager](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/station/service/StationManager.java) requires literal `confirm`. About 4-8 h plus the benchmark driver if absent. |
| FI26: mission load/expiry/same-tick/recovery packs (G01-G07) | FI07/H06 archived native mission driver; [ADR-051](../../../decisions/ADR-051-RESOURCE-MISSION-INSTANCES-AND-DELIVERY.md) defines logical instances; the checklist retains 32 completions/64 inspections per scheduler tick and separate 500/1,000/synthetic overload cases. | **Needs Root:** >100 active/500/1,000 sets, per-kind deadlines/expiry, online/offline duration, current asynchronous save-epoch cut mapping, same-tick intent receipts and normal GUI controls. No physical instance chunks are promised. About 4-8 h plus same-tick instrumentation if no packaged driver exists. |
| FI27: endgame/reference/authority variants (H01-H07/J16) | [ReferenceLoadReleaseTestCommands](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/endgame/command/ReferenceLoadReleaseTestCommands.java) and [ReferenceLoadFixture](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/endgame/command/ReferenceLoadFixture.java), plus FI08/H07, provide a packaged builder/tick driver: `/arce endgame release-test refload build <x> <z>`, `/arce endgame release-test refload drive on`, `/arce endgame release-test refload status`. These are hook-enabled permission-2 commands. | Builder describes 16 logical/4 physical drills, 32 railguns/16 pairs, 16 black-hole generators, 64 fields/20 fixture players, eight elevator pairs and synthetic root mass. **Needs Root:** chosen x/z/targets, normal owners/screens, verified actor classification and actual throughput against ADR-054 (do not equate driver requests with delivered work), large-range/batch-cut and protected-target variants, REF sampler. About 4-8 h shared with FI08/FI15. |
| FI28: cargo/bay queues, cap, endpoint and save-boundary variants (J01/J04-J10/J17) | [C4 switch report](C4-SWITCH-REPORT.md), [W325 matrix](W325-MATRIX.md), [integrated runner](../../../../scripts/run_v180_integrated_campaign.py), FI14/FI17 and [cargo native benchmark](../../../../scripts/run_v180_cargo_native_benchmark.py) provide exact native/synthetic families and bounds. | **Needs Root:** package real-client status/cap/hold/probe recipes, larger reference queues, isolated wait-23 ticket owners, raw measurements and fourteen first-event debugger save/deferral recipes with real award probes. Facility-only online variants and EXIT cuts remain classified separately from V2. About 1-2 days; current C4 native execution is not repeated by W398. |
| FI29: fluid escrow fuel menu/refusal/hold/seat fixtures (J23-J29) | [W11B3 design](W11B3-DESIGN.md), [fuel admission report](FUEL-ADMISSION-REPORT.md) F18/F26 and [checklist J's fuel prerequisites](CAMPAIGN-CHECKLISTS-J.md) specify the separate gated path and no-hook control. [EscrowLoaderAdmission.SWITCH](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowLoaderAdmission.java) gives the exact additional property `-Dadvancedrocketrycommunity.escrowLoaderAdmission=true`; it has no effect without the full release-test gate. Current loader/accounting runners are included in C4's 78-family plan. | **Needs Root:** exact L1/L2/menu/seat-clear/probe steps, A/B binding/foreign-owner/bank/refusal variants and durable endpoint totals. The fuel property is test selection, not the production switch. A owns the loader menu; B must demonstrate ordinary refusal and permitted synchronized feedback, not bypass ownership. About 4-8 h shared with FI28; B3-V's 2-3 session hours are additional execution and must not be double-counted. |
| FI30: presentation, audio, HUD, asset-fallback and long-text packs (I06-I09/J12-J16) | [W16](W16.md), [W87](W87.md), [W241](W241.md), [W244](W244.md), [W261](W261.md), [W265](W265.md) and external historical client batches/worlds FI16 exist. W241's audited legacy renderer inventory pins original upstream `c5cd5af62fc07cd4e0d24f06a16033f181c47c04`; it is not a captured 1.12 comparison session. | **Needs Root:** complete C4 asset/screen/high-risk inventory and samples, actual producer/preview/config paths, missing-primary-resource and long-font packs, audited visual reference, cache/GPU observers and second-GPU copy. About 1 day after scene fixtures; owner/equipment time additional. Do not copy unaudited upstream art. |
| FI31: sleep/research/ground-survey/landing variants (J18-J20) | [W37B](W37B.md), [W21A design](W21A-DESIGN.md), [W189](W189.md), [W311](W311.md) and FI16 retain design/native/GameTest inputs. | **Needs Root:** qualify every freeze V/B1/R1 control and holder, packs/quorum/portal cases, owner/competitor/rebind/account-full/pause research variants, tall rocket with seat nine above origin and named-pad coordinates. About 4-8 h overlapping FI16/FI23. Ordinary sleep does not implement an undecided planet-respawn extension. |
| FI32: network intent/fault/cache observation controls (B07/C08/D03/D09/D12/E03/E05/E07/I11) | API README scopes rejected/slow callbacks to GameTests; ordinary harness JSON input is mouse/key/server-console control, not a C4 wire-intent generator or cache/allocation observer. Existing tests are design seeds, not packaged real-client controls. | **Needs Root:** bounded per-channel stale/range/permission/late-intent tool, finite rejected/slow-provider fixture, cache invalidation/readback observer and comparable profiling intervals. About 1-2 days after scope review; each unreachable branch stays NOT_COVERED, not PASS. |

### 3.3 Native restart campaign and historical artifacts (J22)

The accepted driver is [run_v180_integrated_campaign.py](../../../../scripts/run_v180_integrated_campaign.py).
Its current C4 invocation already exists in `E/root-queue/C4/run.cmd`; no new invocation
is required to discover its inputs. The exact retained `plan.json` (H09) is the full
family manifest, not an abbreviated `--only` selection:

```text
forced-A, forced-A-retry, released, exit, forced-B, forced-CD, forced-E,
forced-E-retry, forced-F, forced-G, forced-GH-retry, forced-H,
forced-W331-T, forced-W331-W, forced-W331-D, forced-W331-G, forced-W331-R,
forced-W331-L, forced-W331-U,
cargo-C-S1, cargo-C-S2, cargo-C-S3, cargo-C-S4, cargo-C-S4-LOAD, cargo-C-S5,
cargo-D-S1, cargo-D-S2, cargo-D-S2-LOAD, cargo-C-Q,
cargo-B3-S1-REUSE, cargo-B3-S4-REUSE, cargo-B3-S5-REUSE, cargo-B3-S6-REUSE,
cargo-G2-REUSE,
w336-timing-B12_D4, w336-timing-B12_D7, w336-timing-B13_FAILED,
w336-timing-B13_UNKNOWN, w336-timing-B16_MOTION, w336-timing-B10_BLOCKED,
w336-timing-RECONNECT, w336-timing-TURNS-2, w336-timing-TURNS-3,
w336-timing-D6_RETRY,
w336-saturation-bay, w336-saturation-item-load, w336-saturation-item-unload,
w336-saturation-fluid-load, w336-saturation-fluid-unload, w336-saturation-fuel,
w336-downgrade,
r042-N_BAY, r042-L_LEDGER, r042-P_SERVER, r042-P_OWNER, r042-U_BAY,
r042-U_LEDGER, forced-W346-R,
forced-W358-B10-INCOHERENT, forced-W358-B11-REARM,
forced-W358-B11-ESCROW-MAJOR, forced-W358-B11-BAY-MAJOR,
forced-W358-B11-UNKNOWN, forced-W358-CAP-OWNER,
forced-W363-B6-GOVERNED, forced-W363-B17-UNLOADED,
forced-W363-B17-UNLOADED-RESTART,
r042-X_BAY, r042-X_LEDGER, r042-R031_FUTURE, r042-R031_INVALID,
cargo-X03-REPAIR,
accounting-cuts-cargo-load, accounting-cuts-cargo-unload,
accounting-cuts-loader, accounting-outcomes, accounting-root2-upgrade,
accounting-root3-old-jar
```

| Historical role | Observed path and pinned source/hash in C4's launch/plan |
|---|---|
| G2 and downgrade old host | `E/root-queue/W176/old-ba046688.jar`; source `ba046688b0df59ce89181475be8c9d237ea33a26`; `d9bf5f08cd65145090a23ac3ce577da5a0d14d51ba380eb253926fb3b0a038bd`. |
| X03 root-2 host | `E/root-queue/W387/root2-artifact/advancedrocketry-community-1.20.1-1.8.0-dev.jar`; source `78ef8bc86e3db80486eef058bed9ff0692dece92`; `4c47b016f7f48e10d5f44c4a5dbc0462a2437cba72a6a898afbb06cc63a76c37`. |
| Accounting root-2 host, C3 | `E/root-queue/C3/candidate/unpacked/advancedrocketry-community-1.20.1-1.8.0-dev.jar`; source `9a15e1f42` (historical C3); `459669bf802ea7ca0ca88bf5a84902eb69274f4a3313305319843c6d8f81744b`. |
| Native baseline template | `E/v1.8.0/guard-forced-setup-05/native-05/server`, installed Forge 47.4.10 arguments present. Copy only the runner's intended template inputs, not its historical world/mod installation. |

These hashes are the **recorded pins**, also freshly matched against those three
retained files here, not assertions that the current campaign has passed its
artifact verification or all families. [R044 draft](R044-SIGNOFF-DRAFT.md)
and [X01-X03 plan](X01-X03-ISOLATION-PLAN.md) keep failed downgrade verdicts and
separate crash/storage tests visible. **Needs Root:** review the completed C4
outputs, retain their final hashed manifest and classifications, and qualify A04's
older transaction/flight baselines separately. C4 J22 does not automatically cover
the genuine adjacent-chunk/current-seat/native-expiry observations in A04/A05.

### 3.4 Retained identity/hash lookup

H01-H08/H15 are existing tracked or external artifacts freshly rehashed here;
the originating records remain authoritative for their content/coverage. H10/H11
are deliberately file hashes only. H14 is the marker's prescribed hash, verified
against W254's documented byte definition, not a claim that a campaign world was marked.

| ID | Input | SHA-256 |
|---|---|---|
| H01 | FI01 Beta ZIP | `9022b2fdf4537168bc33f1db06f96c8d5ce150c221177cd4e0890b037f9bed1c` |
| H02 | FI02 legacy passenger ZIP | `515fdafe471f542157b51c947037b8fbc31979669b3be27de5b46a01ced99197` |
| H03 | FI04 v1.4 migration ZIP | `83eb1cf2a49a24458aea6f5490b6fb9993882e0b717c99fd068e8a1ad20857ff` |
| H04 | v1.3 external-flight corrected-runtime ZIP | `e175321d8eac0340d06cfe3b18792b87f4f3a34533b984d12a193c281db4a9f4` |
| H05 | v1.5 migration root-checks ZIP | `d549361e86780a9af92b9d80d0829eb3abe1894f87bfc75986e771861a9900f5` |
| H06 | v1.6 closure root-checks ZIP | `e0ca6ac1738a155a4d37f626e8639a884618de560d08b7d0f67ad1fdb468d16e` |
| H07 | v1.7 closure root-checks ZIP | `98276f41409a4eb600cde220a70e3269e542155c98ff88e1eab804abb294ba64` |
| H08 | `E/root-queue/W69/native-04/layout.json` | `f286b68547bdaa683291492230bf6b8f32bf8b62077c4b128d0640f3e583506a` |
| H09 | `E/root-queue/C4/campaign/plan.json` at inspection | `01f4902c49152666f8624b388f797a2a84d955d0f0e1b64359b442651e0ac0af` |
| H10 | `E/v18-client-20261010/fixtures/v18-test-world-after-r03/level.dat` | `419ebae34013fe4d2c1800830a5348b79f937ddebb21653fd296d909b9d508bc` |
| H11 | `E/v18-sp-20261104/fixtures/r08-world-final/level.dat` | `97c4b497c3bad2d008f5d486dfeaf1a311e6d53f657a42ac2f5098b005caa8f6` |
| H12 | `E/v18-client-20261010/REPORT.md` | `6b20e44ccf171de2c9372e788c24475e4227eb6ec26b3390e9f7dbf81c0dc85a` |
| H13 | `E/v18-sp-20261104/REPORT.md` | `c0aefd90b7c44d5c94f94868f18da7041518cbb74d04b8f233dd13e1bb93e2c3` |
| H14 | `.arce-bay-v2-campaign` prescribed bytes | `e67124e2a697a32ccdafc384e672362f81c732e5e01872012a2e22c6e4590f98` |
| H15 | FI24 sky native-restart ZIP | `c076c6a68319171bef05bab3ab58b5bbc1f7456aa2f696d309c7411b12433d34` |
| H16 | `E/v18-client-20261010/SHA256SUMS.txt` (contains per-file fixture hashes) | `b7b906afdbf4f8fae730fe7c4bf4426ee6f3e2eb16fa22a285a6b5137798fa87` |
| H17 | `E/v18-sp-20261104/SHA256SUMS.txt` | `787d5fa4f93281f87fc44ac9224974432efc02b1a2e22fef9fbfaa5f4a1932a0` |

## 4. P3: real-client harness and four cells

### 4.1 Existing driver versus retained runtimes

The original harness is `E/v18-client-20261010/`, with `scripts/live_session.py`,
`scripts/window_control.py`, `PLAN.md`, `REPORT.md` (H12), JSON request batches,
F2 captures and final fixture world FI16. Later scripts are in
`E/v18-sp-20261104/scripts/`; its report is H13. The original hard-coded server
template is `E/pre-v18-client-20261006/runtime/server-template`; its
`libraries/net/minecraftforge/forge/1.20.1-47.4.22/win_args.txt` exists.
The read-only PCL runtime is outside E; its `Beyond Depth 12.7.8` profile is
referenced by code, not a campaign profile delivered in this task. Historical
hardware report: RTX 3070 Laptop GPU, Ryzen 5 5500, 32 GB; later report records
driver 566.36. This is not a fresh hardware/driver measurement.

There is more reusable preparation than the original plan's section 3 lists:
[W188](W188.md) ran [v100_compatibility_runtime.py](../../../../scripts/v100_compatibility_runtime.py)
and [run_v180_compatibility_matrix.py](../../../../scripts/run_v180_compatibility_matrix.py)
on the four requested combinations. However, W188 explicitly deleted its 1.1 GB
prepared runtime after use. Both `E/root-queue/W188/runtime-work-1` and
`E/root-queue/W181/attempt-4/runtime-work-1` are absent. The retained
`E/root-queue/W188/preparation-1/prepared.json` hashes to
`0bb98639c5fbcb4710450aee48bab71d14d2ed8dd7123133db932bd01060548e`;
it records both Forge versions, 3,598 assets, JEI 15.56.0.205 and the JEI hash below.
It is evidence of preparation, not the prepared client/server files themselves.

| Required cell (version section 10) | Server template found now | Client profile found now | Required preparation |
|---|---|---|---|
| 47.4.10, JEI absent | Reusable native libraries/template at `E/v1.8.0/guard-forced-setup-05/native-05/server`; not an authenticated C4 campaign server configuration. | No retained isolated matching campaign profile located; W188's runtime removed. | Recreate official runtime/client and fresh campaign server config; install hash-checked C4, no JEI. |
| 47.4.10, JEI 15.56.0.205 | Same baseline template; a distinct server JEI installation is not required by the **client-state** matrix. Existing dedicated template is not evidence of this cell. | No retained ready JEI campaign profile located. The pinned JEI binary does exist at `E/root-queue/W188/jei-inspect/jei-1.20.1-forge-15.56.0.205.jar`, SHA-256 `6b251e60a4719c874da89d750af97aa6498f6f4475e3ae79cb1288c7bf0cb65c`. | Separate client game directory/options/mod inventory with that JEI and C4; share only read-only platform files, not saves/configs. |
| 47.4.23, JEI absent | No retained 47.4.23 server `win_args.txt` located in the searched E runtime roots; W188's was deleted. | No retained ready matching campaign profile located. | Recreate 47.4.23 official server/client with the existing preparer; independent config and C4 copy. |
| 47.4.23, JEI 15.56.0.205 | Same missing compatibility-lane template as preceding row. | No retained ready JEI campaign profile located; JEI binary above is reusable. | Same 47.4.23 runtime with an isolated JEI client directory, not the old 47.4.22 PCL profile. |

The existing preparer accepts `root`, `--java`, `--asset-cache`; it prepares
`servers/<forge>` and the client version/library/assets data. W181's asset cache
exists at `E/root-queue/W181/attempt-4/asset-cache`. It should be revalidated by
the preparer rather than treated as inherently correct. W188's recorded preparation
took about seven minutes with that cache; allow roughly 1-2 h for reconstruction,
configuration, C4 retargeting and verification, **not** just those seven minutes.
No preparation or download was executed here.

### 4.2 Attach, measurement and remaining harness work

| Capability | What actually exists | What must be built/confirmed |
|---|---|---|
| Owner-window attach | `window_control.py` has `Window(pid)`, enumerates the PID's owned GLFW window and rechecks ownership before input. Later `shot_pid.py`/`close_pid.py` are utilities, not an authenticated session manager. All inspected `live_session*.py` variants start their own clients; no owner-window attach CLI/session mode was found. | Build a PID/window allowlist attach mode that does not launch clients, read launcher credentials/tokens, assume offline UUIDs or close an owner process it did not start. Owner starts both signed-in windows. Bind each PID to the supplied account, language, game directory and viewport; enforce bounded requests and session evidence. About 1-2 days including qualification. |
| Online V2 setup | Existing launch helpers use offline names/derived UUIDs and loopback; the compatibility helper substitutes access token `0`/legacy user type. This proves no premium-account authentication. | Fresh online/enforced-whitelist/RCON-off server, exactly two owner-supplied UUIDs, network restriction, five-minute connection check and O5 input/operator arrangement. FI17's marker/property only on the disposable gated copy. |
| Integrated pause/LAN | Later SP variants expose `--singleplayer` and `--sp-world`; no approved LAN bay gate follows from them. | Use an actual pausing integrated client for E07, document the dedicated non-pause contrast, and obtain FI18's approved LAN fixture. |
| Frame times | Harness F3/F2 gives screenshots/FPS, not per-frame samples. No frame-time capture implementation found in either script tree; `PresentMon`, `CapFrameX`, `FrameView` were not on PATH or named in inspected standard installation-directory entries. This limited probe does not prove they are absent everywhere. | **Needs Root/owner O8:** select tool/version on the real GPU, configure bounded per-process capture, first-display stall, average and 1% low calculations, identical baseline/settings and video/time correlation. Rough integration 0.5-1 day after selection; no unapproved tool is installed here. |
| CPU/heap/cache/tickets | Local `C:/Program Files/Java/jdk-17.0.7/bin/jcmd.exe` and `jfr.exe` exist. Native diagnostic/status commands and snapshots exist. Tool existence is not a qualified heap/cache/GPU-buffer observer or a frame-time implementation. | Define JFR/GC/RSS/packet/tick sampling and explicit cache/allocation/release observations for A12/B07/D12/E06/E11/I09; avoid observer subtraction or new invented regression thresholds. About 0.5-1 day, overlapping frame-time work. |
| Client batches | Earlier batches carry historical GUI positions, scene state, languages, muted-audio defaults and old JARs. They are reusable source, not C4 procedures. | Regenerate C4 controls and records at actual viewports, audio-enabled cases, scales 2/3/4, both languages, fullscreen/windowed. Supply separately reviewed stale/late intents FI32. Rough 1-2 days shared with fixtures. |
| Other machines | Historical harness covers one development GPU, not the second category or reference node. | O9 machine/profile and screenshot hashes; P6 normal-user Debian reference-node acceptance; O7 soak accounts/verified actors. These are resource arrangements, not missing localhost directories that W398 can safely fabricate. |

## 5. Every checklist open question and its disposition

The question column is a compact inventory of each source bullet, including its
subparts. FI references point to the concrete inputs/gaps above; citations identify
the answering source. **needs Root** remains literal where preparation, execution,
review or an owner arrangement cannot be supplied by read-only evidence.

### A (six bullets)

Source: [checklist A, Open questions](CAMPAIGN-CHECKLISTS-A.md).

| Rows | Question collected | Answer or remaining input |
|---|---|---|
| A01-A18 | Full C4 identity, P2/P3 manifests; task versus plan identity mismatch. | P1 is supplied in section 2, including the missing classifier hashes. FI01-FI32 and section 4 inventory the known inputs. **needs Root:** runnable per-row copies/configs; the [plan](FINAL-CAMPAIGN-PLAN.md) is not updated by this task. |
| A02/A06/A13/A17 | Core scene, quantities, ownership transfer, exact windows/current controls. | FI01/FI16 retain actual historical scene values; station admin transfer syntax is in [StationCommands](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/station/command/StationCommands.java). **needs Root:** C4 scene/recipe/control manifest, intended dimensions, supported minimum and actual GUI mapping, not old pointer coordinates. |
| A04 | Retargeted transaction/flight baselines, accounting oracles, genuine adjacent-chunk/current-seat/expiry recovery. | Existing [transaction](../../../../scripts/run_v100_transaction_forced_stop.py)/[flight](../../../../scripts/run_v100_flight_forced_stop.py) runners and C4 integrated plan are identified in FI03/section 3.3. **needs Root:** qualify their separate baselines/oracles and genuine arrival/expiry observations; no C4 execution result is asserted here. |
| A08/A11 | Work/save/restart schedule, room footprints, queue observer/budgets, O7 actors. | FI15 preserves [docs/17 minimum workload](../../../17-V1PLUS-QUALITY-BUDGETS.md). Config keys `atmosphere.maxVolumeCells` and `atmosphere.maxInspectionsPerLevelTick` exist in [CommonConfig](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/config/CommonConfig.java). **needs Root:** actual effective values, schedule, 16-room footprints and sampler; owner confirms O7. |
| A12 | Frame tool, cache observer, second GPU/subset. | Section 4.2 establishes the missing frame/attach/cache capability; [packet O8/O9](OWNER-FINAL-PACKET-DRAFT.md) still needs owner choices. **needs Root:** exact tools/version, measured baseline and second-GPU run plan. |
| A18 | Trial count/reset/review rule for first-connect timeout; no allowed rate supplied. | H12/H13 and [plan A18](FINAL-CAMPAIGN-PLAN.md) retain the finding. **needs Root:** predeclared trials/reset and review criteria; no timeout-rate allowance is inferred or added. |

### B (five bullets)

Source: [checklist B, Open questions](CAMPAIGN-CHECKLISTS-B.md).

| Rows | Question collected | Answer or remaining input |
|---|---|---|
| B01/B04 | v1.0 and historical in-flight world identity/schema/target/timing. | FI02/FI03 distinguish the retained [legacy ZIP](../../v1.0.0-legacy-passenger/VERIFICATION.md) from missing normal/in-flight fixtures. **needs Root:** choose source and prepare projections/timing. |
| B02 | Two-station region/owner manifest and supported setup without owner-limit bypass. | FI25 and [StationCommands](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/station/command/StationCommands.java) supply normal/admin operations, not two ready player stations. **needs Root:** distinct eligible owners and resulting region/energy manifest. |
| B03/B06 | Valid/invalid/unknown/locked routes and data-only test_mars. | FI22 identifies [tracked route JSON](../../../../src/test/resources/data/advancedrocketrycommunity/test_travel_routes/test_earth_mars.json) and native pack builder. **needs Root:** C4-valid complete packs, hashes and quote/lock outcomes; built-in Mars is not the data-only case. |
| B05 | O5 concurrency operators/input and overlap criterion. | [Packet O5](OWNER-FINAL-PACKET-DRAFT.md) remains an owner arrangement. **needs Root:** input/receipt mechanism; no numerical overlap tolerance exists in these inputs. |
| B07 | Cache invalidation observer/fixture, matched baseline and degradation review rule. | FI32/section 4.2 identify diagnostics/JFR availability but no qualified cache observer. [Quality budgets](../../../17-V1PLUS-QUALITY-BUDGETS.md) retain absolute limits. **needs Root:** comparable baseline and review rule, not an invented percentage. |

### C (six bullets)

Source: [checklist C, Open questions](CAMPAIGN-CHECKLISTS-C.md).

| Rows | Question collected | Answer or remaining input |
|---|---|---|
| C01 | Active count/mix/recipes/interval/many-machine layout versus 100 idle. | FI10's [scale test](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/classic/runtime/ClassicScaleGameTests.java) supplies 144/half-active/100-tick A1 values, not the requested packaged C4 scene. **needs Root:** explicit v1.2 mix/100-idle plus active workload/measurement interval. |
| C02/C04 | Coordinates, unload proof, safe damage and expected resource protection versus teardown. | FI10 and [W70](W70.md) retain real unload witnesses and per-machine ledgers. **needs Root:** packaged coordinates/damage variants, distinguishing protection refusal from structural teardown. |
| C03 | Coordinated O5 input and ordinary permissions fixture. | FI10/FI32 have no delivered two-account permission/intent scene. **needs Root:** [O5](OWNER-FINAL-PACKET-DRAFT.md), owners and coordinated receipt capture. |
| C05 | Keep NOT_COVERED unless owner names pipe mods. | Answered: [plan O6](FINAL-CAMPAIGN-PLAN.md) adopts D9's NOT_COVERED disposition. No pipe mod is selected and no compatibility PASS is claimed. |
| C06 | Long names/extreme numbers and minimum dimensions. | Historical FI16 dimensions are examples only. **needs Root:** supported window bounds and exact safe long-name/value variants, as [checklist C](CAMPAIGN-CHECKLISTS-C.md) requires. |
| C08 | Permission/distance boundary and bounded actual stale/range server intent. | FI32 names the missing packaged tool. **needs Root:** per-channel policy/actual distance and test intent; [checklist C](CAMPAIGN-CHECKLISTS-C.md) correctly excludes UI closure as proof. |

### D (nine bullets)

Source: [checklist D, Open questions](CAMPAIGN-CHECKLISTS-D.md).

| Rows | Question collected | Answer or remaining input |
|---|---|---|
| D01-D12 | C4 API consumer and actual mandatory-client policy. | FI11 supplies the [build procedure](../../../../compat-test-mod/README.md) and metadata's BOTH-side mandatory host dependency/no display exemption. **needs Root:** C4 classifier/independent binary hashes and real installed/omitted-client handshake; do not misstate metadata as measured policy. |
| D03/D12 | Packaged rejected/slow callback controls versus GameTest-only controls. | [Fixture README](../../../../compat-test-mod/README.md) explicitly names AdapterFaultGameTests; FI32 found no ordinary player enable action. **needs Root:** finite packaged controls or NOT_COVERED for those branches. |
| D04 | Room coordinates and supported tag overrides. | FI20 answers native coordinates/geometry and points to [runner tag_pack](../../../../scripts/run_v130_atmosphere_boundary_smoke.py). **needs Root:** materialized C4 pack/hash and player-bound access, not a guessed override. |
| D05 | Player oxygen payload/refill/interval versus actor suit smoke. | FI20 and [suit runner](../../../../scripts/run_v130_suit_equipment_smoke.py) supply historical provider values only. **needs Root:** real-player payload/refill/time and HUD/reconnect procedure. |
| D06 | Initial partial F, eligible completion layout and normal item loader; not escrow admission. | FI19 gives historical 1,500-capacity component and loader positions/frozen batches from [fuel runner](../../../../scripts/run_v130_fuel_smoke.py). **needs Root:** F and eligible real-player targets; FI29 remains separate. |
| D08/D09 | Valid definition variants and bounded stale/range real-client tools. | FI21 has [payload pack builder/removal phases](../../../../scripts/run_v130_satellite_payload_smoke.py); FI32 lacks qualified wire-intent tooling. **needs Root:** current pack bytes/control route and stale/range probes. |
| D10 | Removal copies and full preserved inventory. | FI21/FI04 identify source data, while [API manual](../../../../compat-test-mod/README.md) warns the fixture is diagnostic. **needs Root:** two fresh backup/copy paths and manifests before removal; never source-world uninstall. |
| D11 | Whether Forge x JEI exhausts declared optional support. | Answered for the declared v1.8 compatibility scope: [version section 10](../../../versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md) names exactly those four cells and defers full modpacks. This does not turn D fixture/API checks or C05/H04 into generic compatibility passes. |
| D12 | Comparable profile windows and CPU/network/cache overhead review threshold. | FI32/section 4.2 identify JFR/diagnostic building blocks. **needs Root:** intervals/baseline/review; [budgets](../../../17-V1PLUS-QUALITY-BUDGETS.md) provide no separate per-integration percentage allowance. |

### E (ten bullets)

Source: [checklist E, Open questions](CAMPAIGN-CHECKLISTS-E.md).

| Rows | Question collected | Answer or remaining input |
|---|---|---|
| E01-E11 | Planetary coordinates/packs/effective recipes/missions/C4 pin; old protocol conflict. | Section 2 supplies C4 flight protocol **10**, not 7/8; FI04-FI08/FI22-FI24 identify existing bytes and gaps. [C4 tracked network pin](../../../../src/test/resources/network-protocols.txt). **needs Root:** actual C4 planetary run manifest/effective values. |
| E01 | Allowed consecutive route legs and quotes; direct Mars-Venus not guaranteed. | FI22's data-only seed is Earth-test_mars only. **needs Root:** enumerate actual C4 [route/catalog](../../../../scripts/v140_migration_fixture.py) paths and server quotes without assuming a direct leg. |
| E02/E03 | Undiscovered/private station variants without balance reset and O5. | FI23 identifies preserved earned progress in [migration report](../../v1.4.0-mig-worlds/VERIFICATION.md). **needs Root:** independent variants, owners and O5; do not edit the earned balances. |
| E03/E05/E07 | Forbidden-surface/cache/late-packet controls and pending-menu timing; no invalid catalog setup. | FI24/FI32 identify missing controls. **needs Root:** bounded actual intent/cache tooling with server receipts; [checklist E](CAMPAIGN-CHECKLISTS-E.md) distinguishes disabled UI from refusal. |
| E04 | Separate/combined environmental hazards with controlled damage modifiers. | FI24 cites [exposure fixtures](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/PlanetaryExposureGameTests.java). **needs Root:** complete player-world coordinates, equipment and effective hazard/modifier manifest. |
| E05 | Long IDs/48-char names, 100/128 bodies, cooldown, pixel bounds. | FI24 has no ready C4 pack set. **needs Root:** materialize/hash catalogs and read actual cooldown/window bounds; [sky manual](../../v1.4.0-sky/MANUAL-CASES.md) is procedure, not a fixture receipt. |
| E06/E11 | O8/O9 tools/equipment and allocation/cache observer. | Section 4.2 inventories available/missing tools. **needs Root/owner:** [O8/O9](OWNER-FINAL-PACKET-DRAFT.md), second GPU and qualified cache/allocation observation, not screenshots alone. |
| E07 | Real pause branch and V2 lifecycle sitting 2. | SP helper `--singleplayer` exists; a DS menu does not pause the server. **needs Root:** actual integrated-paused scene or NOT_COVERED, plus retain [sky manual's](../../v1.4.0-sky/MANUAL-CASES.md) V2 lifecycle in sitting 2. |
| E08 | Paid-pending/blocked history and legitimate recovery with both stores/binding history. | FI23/[migration fixture](../../../../scripts/v140_migration_fixture.py) provide close native migration/removal cases, not all requested progress states. **needs Root:** real earned variants and supported recovery; no selective restore/erased-ID shortcut. |
| E09/E10 | Station-eligible owner, historical identities and pre-sky world. | FI04-FI08 distinguish original/backup bytes from projections; FI24/H15 is level.dat-only pre-sky capture. [StationCommands](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/station/command/StationCommands.java) gives a real admin-create route. **needs Root:** eligible owner/history selection and full historical pre-sky copy. |

### F (four bullets)

Source: [checklist F, Open questions](CAMPAIGN-CHECKLISTS-F.md).

| Rows | Question collected | Answer or remaining input |
|---|---|---|
| F01-F07 | Hashed station source, coordinates/accounts/energy/commands/sample windows. | FI06/FI25 cite [historical station/warp native procedures](../../v1.5.0-warp-native/VERIFICATION.md), but complete source/current manifest is missing. **needs Root:** restored copies and declared drive/sample settings. |
| F01 | Current C4 10/100 benchmark entry point and sampling. | FI25 has no identified ready packaged C4 benchmark for both counts. **needs Root:** accepted driver/observer; [StationWarpGameTests](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/StationWarpGameTests.java) is a construction seed, not REF evidence. |
| F05 | Target-removal/cost-change pack and activation. | FI22/[v140 pack builder](../../../../scripts/v140_migration_fixture.py) supplies a removal pattern. **needs Root:** station-specific C4-valid target/cost pack bytes, hash and pending-warp activation boundary. |
| F06 | Normal deletion and permission syntax. | Partly answered by [StationCommands](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/station/command/StationCommands.java)/[StationManager](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/station/service/StationManager.java): player invite/remove/accept/decline; permission-2 admin delete `<UUID> confirm`. **needs Root:** bind exact station IDs and the non-OP/owner/member actions to FI25. |

### G (five bullets)

Source: [checklist G, Open questions](CAMPAIGN-CHECKLISTS-G.md).

| Rows | Question collected | Answer or remaining input |
|---|---|---|
| G01-G07 | Mission archive/IDs/coordinates, GUI and native invocations. | FI07/FI26 cite [v1.6 closure procedure/archive](../../v1.6.0-c9-closure/VERIFICATION.md). **needs Root:** complete runnable mission copy and C4 command/GUI manifest, not old Temp paths. |
| G01/G02/G05 | Sample windows, multi-hour online/offline duration, expiry and scheduler limits. | FI26 preserves source limits and [checklist G](CAMPAIGN-CHECKLISTS-G.md) cases. **needs Root:** effective C4 limits, deadlines, per-kind expiry and durations; wall-clock downtime is not mission progress. |
| G03 | Same-tick input and receipt observation. | No qualified real-client same-tick controller found in FI32. **needs Root:** deterministic bounded intent/tick observation, not two close manual clicks; [checklist G](CAMPAIGN-CHECKLISTS-G.md). |
| G04 | Map historical cuts to C4 asynchronous snapshot/durable epochs. | FI07's [native scripts](../../v1.6.0-c9-closure/root-checks.zip) retain the historical cuts. **needs Root:** current save-epoch boundary mapping and requalification; no old cut is presumed equivalent. |
| G05/G06 | Resolve inherited physical-instance chunks/visual wording. | [ADR-051](../../../decisions/ADR-051-RESOURCE-MISSION-INSTANCES-AND-DELIVERY.md) defines logical records, not world regions. Answered model boundary: test terminal unload, logical presentation and reclamation. **needs Root:** record disposition of inherited physical wording; do not invent dimensions. |

### H (four bullets)

Source: [checklist H, Open questions](CAMPAIGN-CHECKLISTS-H.md).

| Rows | Question collected | Answer or remaining input |
|---|---|---|
| H01-H07 | Concrete device/target/owner/inventory/screens/native/refload manifest. | FI08/FI27 cite [refload builder/driver](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/endgame/command/ReferenceLoadReleaseTestCommands.java). **needs Root:** x/z, real account roles/ordinary screens, config, C4 native invocation and REF measurements. |
| H03 | Batch-cut seam and large-range variant. | FI27 and [historical native package](../../v1.7.0-c13-closure/root-checks.zip) are seeds. **needs Root:** exact C4 seam, range request and retained cut identity. |
| H04 | Adapter route versus inherited real-protection-mod requirement. | [Plan O6](FINAL-CAMPAIGN-PLAN.md) explicitly leaves H04 NOT_COVERED under D9 absent named mods. Adapter evidence is useful but is not a real-mod PASS. **needs Root:** record that inherited G4 item's disposition, or obtain a specifically named authorized mod lane. |
| H07 | Verified actors and railgun throughput deviation against unchanged ADR-054 load. | FI27's [builder](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/endgame/command/ReferenceLoadFixture.java) has 20 facility players and 16 railgun pairs; requested launches are not achieved throughput. **needs Root:** qualify actors, actual rate/deviation and exclusive REF run against [ADR-054](../../../decisions/ADR-054-ENDGAME-AUTHORITY-PROTECTION-AND-AUDIT.md), without lowering load. |

### I (six bullets)

Source: [checklist I, Open questions](CAMPAIGN-CHECKLISTS-I.md).

| Rows | Question collected | Answer or remaining input |
|---|---|---|
| I01 | O4 hand-played subset, continuous C4 witness route, recipes/counts/endgame choice. | FI16 retains old builders, not a continuous candidate video; [plan O4](FINAL-CAMPAIGN-PLAN.md) recommends scripted route plus subset. **needs Root/owner:** exact subset/device/counts and recorded play. |
| I02/I03/I06/I08 | Archives/hashes/coords, machine/old-ID/screen/asset inventory, high-risk sample and scale. | FI09/H08 supplies the old-ID layout and FI10 supplies a scale seed; [W69](W69.md)/[W70](W70.md) distinguish their evidence. **needs Root:** complete C4 inventory, packaged scenes, classification/sample and reference scaling. |
| I04 | Current shipped counts and category/usage representatives, not W188 totals. | [W188](W188.md) is explicitly historical. **needs Root:** C4 as-shipped category/report receipts and actual JEI page/usage representatives; P1 audit's 6,993 JAR entries are not recipe/category counts. |
| I07 | Audited 1.12 reference, missing-resource packs, final corrections and paired break/repair/tracking. | FI30 cites [W241](W241.md)/[W244](W244.md)/[W261](W261.md)/[W265](W265.md). **needs Root:** approved comparison media/packs and phase-4 paired scene; no old model correction is reverted. |
| I09 | O8 fixed frame/heap/window method; absent extra numerical thresholds. | Section 4.2 and [docs/17](../../../17-V1PLUS-QUALITY-BUDGETS.md) establish the gap and preserved budgets. **needs Root:** chosen tool/procedure/viewport/baseline; report absent thresholds rather than invent FPS floors. |
| I11 | Current holder/policy matrix and safe malicious/stale intent procedure. | FI32 is not a delivered packet driver. **needs Root:** per-menu/channel policy, actual server-received intent and chunk observations; [checklist I](CAMPAIGN-CHECKLISTS-I.md) retains NOT_COVERED for missing instrumentation. |

### J (thirteen bullets)

Source: [checklist J, Open questions](CAMPAIGN-CHECKLISTS-J.md).

| Rows | Question collected | Answer or remaining input |
|---|---|---|
| All J rows | Artifact identity, copied fixtures/coordinates/IDs/scripts/exact setup; REF drivers and owner resources. | Section 2 supplies C4; FI17-FI32/sections 3.3-4 inventory existing inputs and missing setup. **needs Root:** literal executable run manifests and [owner resource arrangements](OWNER-FINAL-PACKET-DRAFT.md), not a claim that all J setup exists. |
| J01 | Current status sequence, cap invocation; avoid claiming foreign-owner cap coverage. | FI17/FI28 and [C4 report](C4-SWITCH-REPORT.md) identify native cap/status sources. **needs Root:** complete current-menu sequence, real-player cap-control recipe and distinct owner's admission evidence. |
| J02/J03 | Dedicated/LAN distinction, seats/pads and W254 3.4 unreachable branch. | FI17 states exact [W254](W254-V2-CHECKLIST.md) gate/marker/setup; FI18 is missing. **needs Root:** actual UUIDs/coordinates and approved LAN route. Retain native X5 plus the explicit real-client not-run reason for 3.4; no bypass. |
| J04 | All ten online/S3 variants and EXIT.0-EXIT.10; facility-only and gate conflict. | [W325](W325-MATRIX.md)/[W269](W269-DESIGN.md) and FI28 distinguish facility-only native cases from the marked-world exception. **needs Root:** C4 real-client control recipes or explicit unreachable/synthetic-only dispositions; W269 selection is not production admission. |
| J05/J08/J09/J10 | Larger REF queues/raw tool, unbounded wait classification, wait-23 isolation. | FI14/FI28 and [C4 section 9](C4-SWITCH-REPORT.md) give benchmark/native seeds. **needs Root:** REF fixtures/raw sampler with other ticket owners isolated; no universal latency maximum or contaminated 400-tick comparison is added. |
| J11 | R245 persistence design/review if measured first-event tick is over budget. | [W207](W207.md) and [checklist J](CAMPAIGN-CHECKLISTS-J.md) retain the condition. **needs Root:** fresh REF first-event measurement; if over budget, a separately designed/reviewed persistence task before release, not a Windows exception. |
| J12-J16 | Machines/accounts/time/second GPU; current audio producers/previews/config/long-font. | FI16/FI30 and section 4.2 identify historical scripts and missing tools. **needs Root:** resources, current [W16](W16.md)/[W87](W87.md) producer/control mapping, audio-enabled profile and long-font pack. |
| J17 | Fourteen C4 debugger save/deferral recipes with real awards/queues; first-event cut authorization. | FI28, [W44 design](W44-DESIGN.md) and [W44I](W44I.md) retain intended seams, not new C4 qualification. **needs Root:** exact debugger/external-stop recipes and award probes; connected-player bay exception does not authorize first-event H cuts, X remains external-stop only. |
| J18 | Freeze V rows/B1/R1, packs/quorum/portals and undecided respawn extension. | FI31/[W37B](W37B.md) identifies the existing matrix. **needs Root:** native/real-client qualification of each condition, controlled scene and explicit extension disposition; ordinary sleep is not the extension. |
| J19 | Current UI/section-7 values and owner/competitor/rebind/full-account/pause scenes. | FI31/[W21A design](W21A-DESIGN.md) supplies design intent only. **needs Root:** C4 implementation readback and copied fixture variants without balance edits or forced arbitrary chunk loads. |
| J21 | Complete finding-to-task-to-batch-to-current-disposition map, including information/round-8 residuals. | FI16/H12/H13 retain both full reports and batches. [Plan section 7.1/J21](FINAL-CAMPAIGN-PLAN.md) names merged W280-W282, W284-W296 and W301 fixes, but does not accept every informational observation. **needs Root:** full per-finding map/current expectations and accepted residual decisions, then C4 recheck. |
| J22 | All historical artifacts/full family selection; keep X01/X02/downgrade/risk results separate. | Answered input identities/78-family selection in section 3.3/H09, based on the [integrated runner](../../../../scripts/run_v180_integrated_campaign.py). **needs Root:** completed C4 verdicts and final hashes, with [R044](R044-SIGNOFF-DRAFT.md) and separate X01/X02 retained; no blanket PASS. |
| J23-J29 | C4 fuel menu/holds/seat-clear/probes/refusals and B observer; no ownership relaxation or admission approval. | FI29 supplies the exact additional `escrowLoaderAdmission` test property; the [fuel admission report](FUEL-ADMISSION-REPORT.md) preserves the separate no-hook comparison. **needs Root:** exact packaged controls and B's authorized observation route; use ordinary refusal plus permitted feedback, never grant B A's owner-only menu. P9/affected reruns remain separate. |

## 6. Delivery, verification and limits

**Completed scope.** One DRAFT record gathers P1 identity, 32 fixture families,
all four P3 cells, the current native family's input selection, hashes and the
68-question disposition inventory. No fixtures/harness/code/tests/assets/ADRs,
registers, status files, queue or other records are changed. Sources remain intact;
no production admission is enabled. No upstream material is copied.

**Design choices.** No ADR decision is made or revised. Conservative research choices
are to distinguish full worlds from level/NBT captures, recorded pins from measured
hashes, source-set adapters from independent API consumers, supported server/client
cells from deleted historical runtime preparation, and native/A1 observations from
V1/V2/REF. Uncertain or unbuilt inputs remain **needs Root**. Preparation estimates
are explicitly estimates with overlapping work, not new budgets.

**Commands actually used.** Read-only `git status --short`, `git branch --show-current`,
`git rev-parse HEAD`, `git worktree list`, `git show`; `Get-CimInstance Win32_Process`;
`Get-Content`, `Get-ChildItem`, `rg`/`rg --files`; bounded inline `python -B -c`/`python -B -`
with pathlib/os.walk/json/zipfile/hashlib; limited `Get-Command` and executable-file
existence checks. They confirmed the clean assigned branch, other workers/C4 left
alone, retained files/ZIP members and hash identities described above. `Copy-Item`
and `Expand-Archive` operated only on W398's owned Temp copy of C4 evidence;
`apply_patch` created this record. No native driver, preparer, Gradle, GameTest,
client, network query, download or subagent ran.

Some broad read outputs exceeded the display limit and were repeated with bounded
selections. Initial guessed filenames (`docs/versions/v1.8.0.md`, a consumer-test
README and render-benchmark path) did not exist; the actual version/compat project
and source requirements were then read. Literal wildcard paths passed to `rg` on
Windows failed and were replaced with directory/`-g` or pathlib searches. One Python
console read hit GBK encoding and was repeated with UTF-8 output. These source-lookup
failures are not product failures or passing evidence. An initial lookup file in
the user's Temp was immediately removed; all subsequent scratch output is in the
owned W398 Temp folder. OpenPet was unreachable; work continued without it.

| Verification command/check | Result |
|---|---|
| `python -B scripts/validate_repository.py` | Exit 0 before staging and on the staged manifest: **45 passed, 0 pending, 0 warnings, 0 failed**. The final record is rechecked before its single delivery commit. |
| Inline question/link/scope audit against `git ls-tree -r --name-only 059e4a4b40c3f3b7feee061973cec49e21eef971` | Exit 0: 68 question bullets in the ten source groups, 32 distinct fixture families, all 180 links resolve to files already tracked at that shared base, no external Markdown links. The first draft audit found two incorrectly named ADR-051 links; both were corrected before staging. A separate byte/hash assertion confirms the 32-byte campaign marker definition. |
| `git diff --check`, `git diff --cached --check`, `git diff --cached --stat`, `git diff --cached --name-only` | Exit 0; only this record is staged. No unrelated work is modified. |
| Owned Temp cleanup | **Tool policy refused execution** of the resolved-path/owned-name-checked PowerShell `Remove-Item -LiteralPath ... -Recurse -Force` command (`rejected: blocked by policy`). A later read confirmed the directory still exists. Per WORKER-RULES, no alternative deletion mechanism was attempted and work continued. |

**Cleanup handoff for Root:** the exact owned leftover is
`C:/Users/Administrator/AppData/Local/Temp/ARCE-W398-c89d920bbc464d24a84c8261e0c2e928/`.
It contains the copied C4 evidence ZIP (same SHA-256 as section 2), its `identity/`
extraction and the external search's `inventory.json`; no source world or other
worker's files. This task's requested final Temp removal remains uncompleted solely
because of that tool refusal. The earlier standalone lookup file was successfully
removed and is not a leftover. No W398 server/JVM was started or needs stopping.

No automated tests are added or modified. Product unit/build/DataGen/GameTest and
native/client runs are **not run by task instruction**, not represented as passed
or generically NOT_APPLICABLE. C4 owns the heavy-JVM work. The evidence path for
this documentation task is this record and its branch commit. It is delivered in
one commit; the resulting SHA is reported in the final handoff and discoverable via
`git log -1 -- docs/work/v1.8.0-root-queue/records/CAMPAIGN-INPUTS-MANIFEST.md`, rather
than inserting a self-referential SHA in the same commit. Nothing is pushed.

**Not completed / risks.** Missing classifier artifacts, complete historical worlds,
packaged scenes, intent/cache/frame observers, authenticated attach, owner resources
and C4 campaign verdicts remain explicit above. Searches are not a full-disk forensic
inventory; unlocated originals may still exist elsewhere, and archives need source-
manifest checks before restoration. Large fixture/observer work and resource choices
can change estimates. Read-only evidence cannot establish current real-player behavior.
This record satisfies **no complete Required Gate set**; v1.8 still requires G0-G9
and owner approval. Root's next work is review/integration of this manifest, locating
or rebuilding only missing v1.8 P1-P3/P5-P9 inputs, then the scheduled C4 campaign;
no later-version implementation is recommended here.
