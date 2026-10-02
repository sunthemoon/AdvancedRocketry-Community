# C14 contract review, round 1: v1.8.0 Classic Content contracts and audit

```yaml
reviewer: independent contract reviewer (read-only)
review_round: C14R1
date: 2026-10-02
repository: D:/GitHub/AdvancedRocketry-Community
branch: codex/v1.8.0-classic-content
commit_reviewed: f05eec21  # docs(content): propose the v1.8 classic content contracts (ADR-060..062)
diff_base: 55da6a58        # v1.7 handoff
upstream: Advanced-Rocketry/AdvancedRocketry @ c5cd5af62fc07cd4e0d24f06a16033f181c47c04
libvulpes: Advanced-Rocketry/libVulpes @ c2ca79dc18625c9e63a191a795f1f07d078f29f0
verdicts:
  ADR-060: ACCEPT WITH REQUIRED CHANGES
  ADR-061: ACCEPT WITH REQUIRED CHANGES   # H1, H2 block acceptance until fixed
  ADR-062: ACCEPT WITH REQUIRED CHANGES
finding_counts: {Critical: 0, High: 2, Medium: 12, Low: 7, Info: 6}
```

## 1. Scope

Reviewed: ADR-060, ADR-061, ADR-062; `docs/work/v1.8.0-content-audit.md`,
`v1.8.0-legacy-inventory.json` (609 units), `v1.8.0-content-ledger.csv`,
`v1.8.0-asset-plan.csv` (182 rules / 898 assets), `v1.8.0-contract-coverage.md`,
`v1.8.0-implementation-log.md`; `tools/audit/inventory_v180_content.py`,
`scripts/validate_v180_content_ledger.py`, `tests/test_validate_v180_content_ledger.py`;
the `docs/PORTING_MATRIX.md` rows, `docs/provenance/v1.8.0-development-metadata.md`
and the CI step in `.github/workflows/repository-docs.yml`. I read the context
documents listed in the brief (AGENTS.md, the v1.8 version document, docs/16 §5,
docs/17, docs/08, UPSTREAM.md, NOTICE.md, THIRD-PARTY-NOTICES.md) and the ADRs
that ADR-062 revisits (046, 049, 051, 055, 056, 057, 058, 059), plus ADR-053,
ADR-018, ADR-031, ADR-033, ADR-037 and ADR-041 where a claim depended on them.
I did not read `AdvancedRocketry-Community-v1plus-Development-Docs/`, did not run
Gradle, and wrote nothing in the repository.

## 2. Method and commands (all in my own directory)

| # | Command / probe | Result |
|---|---|---|
| 1 | `git archive f05eec21 \| tar -x -C tree`; `git diff --stat 55da6a58 f05eec21` | 15 files, +8291/-17, as briefed |
| 2 | `curl` the pinned upstream zip; `sha256sum ar.zip` | `40eb5d43…cca01d`, equal to the audit's `archive_sha256` |
| 3 | unzip to `up/ar`; `probes/verify_manifest.py` (every `legacy-manifest` path vs. SHA-256) | 1,408/1,408 equal, 0 missing; 21 extra files outside the manifest (build files, LICENSE, README, `Template.xml`, `XML_CONFIG_README.txt`, `mcmod.info`, AT cfg) — `logs/verify_manifest.log` |
| 4 | `python tools/audit/inventory_v180_content.py --upstream up/ar --check` | `inventory: up to date`, exit 0 (`logs/inventory_check.log`) |
| 5 | same, after editing one display name in my copy of the inventory | exit 1 (differs) — restored, exit 0 again |
| 6 | same, after appending a byte to my copy of `AudioRegistry.java` | exit 2, hash mismatch named — restored |
| 7 | `python scripts/validate_v180_content_ledger.py` | PASS; 61/157/224/91/24/52; assets 350/30/430/78/10; batches as in ADR-062 (`logs/validator.log`) |
| 8 | `python -m unittest tests.test_validate_v180_content_ledger -v` and `python -m unittest discover -s tests -p "test_validate_v180*"` | 18 tests OK both ways (`logs/unittest.log`) |
| 9 | `probes/config_keys.py` (every `config.get*` in all upstream Java) | 132 distinct keys, 113 inventoried, **19 missing** (`logs/config_keys.log`) |
| 10 | `probes/events.py` (`@SubscribeEvent` per class vs. inventory) | all 41 rules of the 12 chosen classes found; excluded classes are registration/GC/test only (`logs/events.log`) |
| 11 | `probes/oredict.py` (ore-dictionary names in recipes and Java) | `fanSteel` ×10 recipe files, `gemDilithium`, `rodIron` not represented (`logs/oredict.log`) |
| 12 | `probes/vanilla_names.py` vs. the local Forge Gradle cache `minecraft_repo/versions/1.20.1/client-extra.jar` (read only, no download) | name collisions incl. 3 IMPORT files not marked REVIEW (`logs/vanilla_names.log`) |
| 13 | `probes/derivation_scan.py`, `pair_detail.py`, `lum_fit.py`, `recolor_scan.py`, `ar_silhouette.py` (pure-Python PNG decoder `probes/pngdecode.py`: exact-pixel overlap, alpha-mask IoU, luminance correlation and pairwise order agreement) | confirmed vanilla recolours planned for IMPORT (H1) — `logs/derivation_scan.log`, `logs/pair_detail.log`, `logs/lum_fit.log`, `logs/recolor_scan_ar.log`, `logs/ar_silhouette.log`; visual side-by-sides were inspected and then deleted |
| 14 | `probes/sound_check.py` (Vorbis vendor/comments, duration, SHA-1 vs. the 1.20.1 asset index) | no comments in any OGG; no OGG equals a vanilla sound object; placeholder = 0.27 s mono (`logs/sound_check.log`) |
| 15 | LibVulpes via raw.githubusercontent / api.github.com: `LICENSE` at `c2ca79d`, commit metadata, `LICENSE` history, tree, 72 textures (git-blob verified 72/72), per-file history, contributors | LICENSE SHA-256 `1f9978a4…55be3` confirmed; added only by `95c6b85` (zmaster587, 2017-10-01, "Update build number, more permissive licence"); `c2ca79d` = 2024-02-01 "working on getting it to build" (tip of `1.12`); findings H2 (`logs/lv_*.log`) |
| 16 | `probes/mutate.py` — 20 targeted mutations of copies of the ledger, asset plan, inventory and ADR statuses run through the validator with `--root` | 15 of 19 mutations survive; 4 caught (`logs/mutations.log`) |
| 17 | Reading of upstream code for every DEFERRED/REJECTED group and sampled REDESIGNED/PLANNED rows; modern registry, config and gravity code | see findings |

The implementation log's Gradle claims (1,376 JUnit, 337 GameTests, byte-identical
JARs) were not re-run (out of brief); no finding depends on them.

## 3. What holds

- The pinned archive, the 1,408 manifest files and the 685 inventory inputs are
  hash-exact; the generator is deterministic, refuses tampered inputs and detects
  a tampered inventory. The CI step should work on Linux: it runs after the
  existing exact-commit clone step, uses repository-relative paths and needs no
  extra modules.
- Registry-derived units are complete for blocks (101), items (35), block
  entities (61), entities (10), biomes (12), satellites (9), enchantment (1) and
  advancements (17); commented registrations (three pipes, two tile entities) are
  handled correctly; the `@SubscribeEvent` coverage of the twelve chosen classes
  is complete.
- Counts agree across ADR-062 §1, the audit §1–§2, the implementation log, the
  matrix row and the validator (609 units; 61/157/224/91/24/52; 898 assets
  350/30/430/78/10; 18 tests; 36 distinct LibVulpes resource locations, 79/40/11
  occurrences, 124/529 Java references; 43 OBJ/20 MTL; 17 OGG; 12 `*leo.jpg`;
  9 language files; `orientablebottom.json` byte-identical to vanilla;
  placeholder hash `f33cbdac…`; lathe = rolling machine OGG; `AdvancedRocketry.java`
  897–898; `coilCopper` 36 in Java, `plateSteel` 20 in recipes).
- Verified upstream behaviour: force-field placement without events (TileForceFieldProjector 16, 47–48);
  50-block laser reach and 1-point damage; deployed-rocket force loading
  (EntityStationDeployedRocket 166–170); vitrified sand has no producer;
  `MapGenSpaceStation`, `BiomeGenPumpkin/Watermelon`, `SatelliteSpyTelescope` are
  never registered or referenced; the space-return rule picks the *farthest*
  station (PlanetEventHandler 186–200); sleep, torch, spawn, ore-suppression and
  fall rules are as described.
- Modern IDs named as `advancedrocketrycommunity:` targets exist; the audit's
  "30 blocks and 57 items" is correct.
- ADR-060 follows ADR-053's template, stays PROPOSED, keeps
  `runtime_implementation_allowed: false`, authorizes no release, tag or import,
  and correctly keeps the first three prerequisites open.

## 4. Findings

### High

#### C14R1-H1 — The asset plan imports Minecraft-derived textures; the vanilla check is name-only

Evidence (local 1.20.1 vanilla jar; probes 12–13):

| Legacy file | Plan | Vanilla source | Measure |
|---|---|---|---|
| `textures/env/earth_phases.png` | IMPORT #178 ("… earth phases") | `textures/environment/moon_phases.png` | same 128×64, identical alpha mask, **7,817/8,192 pixels byte-equal** over 43 distinct colours; visually the vanilla phase sheet with the moon squares repainted |
| `textures/blocks/moon_turf.png` | IMPORT #53 | `textures/block/grass_block_top.png` | luminance r = 0.996, pairwise order agreement 1.000 (256 px, 66 levels) — a linear recolour |
| `textures/blocks/moon_turf_dark.png` | IMPORT #53 | same | r = 0.994, order 1.000 |
| `textures/blocks/hotdry_turf.png` | IMPORT #54 | same | r = 0.997, order 1.000 |
| `textures/blocks/log_charcoal_top.png` | IMPORT #57 | `block/jungle_log_top.png` | r = 0.946, order 0.939 (probable) |
| `textures/items/space_{helmet,chestplate,leggings,boots}.png` (and `space*.png` duplicates) | IMPORT #126 | `item/iron_*`, `item/chainmail_boots` | identical or near-identical silhouettes, 23–57 opaque pixels byte-equal in the vanilla armour palette (probable) |
| GUI sheets `gui/lasertile`, `cuttingmachine`, `crystallizer`, `precisionassembler`, `blastfurnace`, `genericneibackground` | IMPORT #151–157, #168 | vanilla container GUIs | 43–62 % of non-background pixels byte-equal in positions of the vanilla frame and inventory slots (needs review) |

ADR-061 §4.2 defines `REVIEW` for "files sharing a vanilla name" and the audit
(§3.9) relies on names and one byte comparison. That misses recolours (above)
and also mis-handles name matches: `textures/blocks/forcefield.png`,
`textures/models/beacon.png` and `textures/planets/lava.png` share vanilla names
but are IMPORT, while `textures/gui/eyecandy/earth.png` (a 256×256 rendered
cube-earth; vanilla `painting/earth.png` is 32×32) and `env/sun.png` (128×128
grey+alpha vs. 32×32) are EXCLUDE as "presumed vanilla-derived" without evidence.
docs/08 §7 forbids "从游戏 JAR 提取后略改的资产"; AGENTS.md §3.2 forbids
copying Mojang art. Accepting the plan as is would schedule four confirmed
derivatives for import (the turf files are the core of C15b).

Required change:
1. Move `earth_phases.png`, `moon_turf.png`, `moon_turf_dark.png`,
   `hotdry_turf.png` to `EXCLUDE` (reason: recolour of a named vanilla file,
   with the measure); move `log_charcoal_top.png`, the space-suit item icons and
   the GUI sheets to `REVIEW`; treat every vanilla-name collision as `REVIEW`
   (as ADR-061 §4.2 says) rather than IMPORT or blind EXCLUDE.
2. Add to ADR-061 §4 a mandatory, reproducible vanilla-derivation check for every
   IMPORT candidate (AR and LibVulpes) against the vanilla assets of **both**
   1.12.2 and 1.20.1: hash equality, same-size exact-pixel overlap, alpha-mask IoU,
   and luminance correlation/rank agreement with stated thresholds. The importer's
   `generate`/`verify` must record the check result per entry, and a hit forces
   `EXCLUDE` or a recorded human `REVIEW` decision.
3. Correct audit §3.9 and the asset-plan reasons accordingly.

#### C14R1-H2 — The LibVulpes source and scope cannot be approved as drafted

Evidence:
- ADR-061 §4.1 approves LibVulpes `c2ca79d` by a prose scope ("material product
  textures, machine casing and structure textures, hatches, coils and the
  dilithium ore") and says acceptance of the ADR *is* the approval. There is no
  pinned LibVulpes file manifest (path + hash), the asset plan covers only the 898
  AR files, and §4.7 checks LibVulpes hashes only after import. AGENTS.md §3.2
  requires the license, source commit **and allowed scope** to be recorded in
  `docs/provenance/` before copying; the development-metadata record states only
  the license facts.
- Authorship: LibVulpes commit `984d6747` (Silfryi, 2020-05-04) "16x textures from
  the unmerged Cl1ff PR" last changed `blocks/machinegeneric.png` (the casing AR
  references 79 times), `inputhatch`, `outputhatch`, `fluidinput`, `fluidoutput`,
  `batteryrf`, `batterycreative`, `ic2plug`. These are third-party works that
  reached the repository through someone else's unmerged pull request; the MIT
  `LICENSE` added in 2017 does not by itself establish their terms.
- Vanilla resemblance: `items/ingot.png` agrees with the vanilla ingot silhouette
  on 98.8 % of pixels with luminance r = 0.81 (vs. `gold_ingot`), `items/nugget.png`
  98.8 %, r = 0.80 (vs. `gold_nugget`) — the "material product textures" are in
  the approved scope.
- The LibVulpes tree also bundles `libs/gregtech-1.12.2-1.0.117-stripped.jar`
  (third-party, not MIT), so the root LICENSE is not a per-file guarantee.
- The scope is also ambiguous against the batch plan: rutile/tin/aluminum/iridium
  ore textures (C15a), motors and the advanced structure block (C16a) and the
  linker (C17a) are needed but not clearly in or out.

Required change: before acceptance, (a) add a LibVulpes manifest under
`legacy-manifest/` or `docs/provenance/` listing each in-scope file with path,
git blob SHA and SHA-256 at `c2ca79d`, last author/commit and handling; (b) extend
the asset plan (or a sibling plan) to those files with the same rules and
validator checks; (c) mark the Cl1ff/`984d6747` files `REVIEW` pending an
authorship finding; (d) run H1's vanilla check on them; (e) state explicitly
which ore/motor/linker/advanced-casing files are in scope and which need `NEW`
art; (f) record license, commit and the exact file scope in `docs/provenance/`
as AGENTS.md §3.2 requires; and (g) say in the ADR that the maintainer's
acceptance approves that file list, not a category.

### Medium

#### C14R1-M1 — Configuration surfaces outside `ARConfiguration` are not inventoried

The audit claims 113 keys "(both `config.get` signatures)"; the generator reads
only `api/ARConfiguration.java`. Missing (probe 9):
`AdvancedRocketry.java` 949–951 `Planet.BlacklistedBiomes`, `HighPressureBiomes`,
`SingleBiomes`; `client/ClientProxy.java` 426–444 sixteen HUD layout keys
(`suitPanelX/Y/ModeX/ModeY`, `oxygenBar*`, `hydrogenBar*`, `atmBar*`). Also not
inventoried: `config/advRocketry/oreConfig.xml` (AdvancedRocketry.java 1187–1224,
ore tables per atmosphere and temperature via `OreGenProperties`) and the
per-machine XML recipe overrides (`RecipeHandler.registerXMLRecipes`, 11 machines,
called at AdvancedRocketry.java 1112). (`planetDefs.xml` is covered by the
"XML 行星" matrix row; `asteroidConfig.xml` by the v1.6 audit/ADR-052.)
Required change: scan every Java file for `config.get*` (both signatures, string
or identifier categories), add the 19 keys and the two XML surfaces as units
with dispositions (e.g. REJECTED with ADR-031 for random-planet lists;
PLANNED/REDESIGNED for HUD layout; REDESIGNED to data packs for XML recipes and
ore tables), and correct the audit text.

#### C14R1-M2 — Gameplay rules delivered outside `@SubscribeEvent` are missing

- Planetary gravity on **non-player entities**: the coremod injects
  `GravityHandler.applyGravity` into `Entity.onUpdate` (ClassTransformer 756;
  GravityHandler 48–96) so items, mobs, arrows, throwables, boats, minecarts,
  falling blocks and TNT all follow planet gravity. The modern
  `CelestialGravityController` acts on `ServerPlayer` only. ADR-062 §3 defers
  only ADR-058 *area fields* on non-players; planetary gravity on non-players
  has no unit and no disposition. (The matrix "ASM/coremod REJECTED" row rejects
  the mechanism, not its gameplay.)
- Suit underwater breathing: `PlanetEventHandler.playerTick` 173–175 refills air
  for entities immune to low oxygen; the ledger row for `playerTick` mentions
  only the space return and the Moon advancement.
- Rocket inventory access bypass (`RocketInventoryHelper.allowAccess`, ASM hooks).
- `blockRightClicked` also blocks blaze powder and blaze rods (audit says flint and
  steel and fire charges only).
Required change: add an `asm_rule` (or `rule`) kind fed from the live
`ClassTransformer` hooks and from multi-rule handlers, with dispositions; give
non-player planetary gravity PLANNED (C18a) or DEFERRED/REJECTED with a player
impact statement in ADR-062.

#### C14R1-M3 — LibVulpes gameplay content that AR does not name is missing (fans, early power)

- `fanSteel` is an ingredient in 10 upstream recipe files (blockpump, centrifuge,
  fuelingstation, oxygencharger, oxygendetection, oxygenscrubber, oxygenvent,
  railgun, sealdetector, spacechestplate); the generator's product prefixes omit
  `fan` (and `gem`, `rod`), so `material:Steel` lists no fan, ADR-061 §2.1 names no
  fan tag and no batch delivers it.
- The LibVulpes coal generator (blockstate, recipe and GUI at `c2ca79d`) was the
  early FE source of an AR+LibVulpes install. The modern project has no early
  generator (only the black-hole generator and the satellite microwave receiver);
  ADR-062 places the solar generator in C17c, after the C16 machines that need FE.
Required change: add the fan component (and its tag) and an explicit
"early power" unit; decide in ADR-062 whether the classic chain ships an early
generator (and in which batch, before C16) or depends on other mods' FE, with the
player impact; extend the generator's LibVulpes scan to ore-dictionary-only
components.

#### C14R1-M4 — Several dispositions name mechanisms that do not exist or misstate legacy behaviour

- IMPLEMENTED configuration rows with no modern setting: `CLIENT.overworldSkyOverride`,
  `PlanetSkyOverride`, `StationSkyOverride` (the mod has no client config at all;
  `CommonConfig` is the only `ForgeConfigSpec`); `OXYGEN.EnableAtmosphericEffects`
  ("life support switch" — none); `OXYGEN.vacuumDamage` (damage is fixed);
  `STATION.warpTravelTime` (`StationLimits.WARP_COUNTDOWN_TICKS = 200`, constant);
  `ENERGY.MicrowaveRecieverMultiplier` (no setting); `STATION.allowZeroGSpacestations`
  (modern stations default to 0 g, ADR-041; no switch); `PLANET.planetsMustBeDiscovered`
  (per-body `discovery_required`, ADR-037 — a redesign).
- `block:planetHoloSelector` → "flight star map screen (ADR-035)": the legacy block
  sets the **station's warp destination** (TileHolographicPlanetSelector 193–194);
  the equivalent is the ADR-044/046 warp command and the C17b warp screen.
- `config:PLANET.planetDiscoveryChance` REJECTED as "random planet generation": it
  is the warp-controller discovery chance (ARConfiguration 472); ADR-037 discovery
  is the redesign.
- Landing float (ADR-062 §3, audit §3.4): legacy rockets auto-place iron floats
  under the landing footprint when the surface is a **non-water liquid** and treat
  water as safe (EntityRocket 884–909); the block has no recipe. "Rockets cannot
  land on water" misstates the player impact (modern: no landing on unsupported
  liquid, notably lava).
- `item:oreScanner` is REDESIGNED (no item) while asset rule #115 imports its
  texture for a C18a "survey readout item".
Required change: correct these rows (IMPLEMENTED → REDESIGNED/REJECTED with the
real mechanism, or PLANNED where a setting will be added), fix the ADR-062 §3
landing-float text and player impact, and align rule #115 with the ledger.

#### C14R1-M5 — REDESIGNED is used for 37 undelivered v1.8 items, which escape closure

37 rows are `REDESIGNED` with plan `v1.8.0` and targets such as "ore placed
features (C15a)", "planet feature data (C15b)", "common item tags in recipes (C16)",
"torch tag (C18a)", copper products and four carvers. ADR-062 §1 defines
REDESIGNED as "delivered by a different modern mechanism", these rows carry no
batch, never move to IMPLEMENTED, and C19's "zero PLANNED" check ignores them
(mutation: moving `block:centrifuge` to REDESIGNED/v1.8.0 with free text passes).
Required change: keep them `PLANNED` with a batch and record the redesign in
`target`/`notes` (or add a distinct open state with a batch); make the validator
reject REDESIGNED/IMPLEMENTED rows with plan `v1.8.0` unless the delivering batch is
recorded as complete.

#### C14R1-M6 — Validator enforcement gaps (surviving mutations, `logs/mutations.log`)

Survived and representing real gaps:
1. IMPLEMENTED target `advancedrocketrycommunity:machine_casings` (a tag literal)
   or an unrelated registered ID (`machine_casing` for the lathe) — `modern_ids()`
   accepts any lower-case string literal in `registry/*.java` (`vacuum`, `main`,
   `planetary_`, tag names).
2. IMPLEMENTED needs a modern ID only for block/item/item_variant/entity, not for
   block_variant, fluid, biome, sound_event, enchantment, keybinding.
3. DEFERRED/REJECTED rows may cite any accepted ADR (ADR-001/ADR-016 pass); 14
   current REJECTED rows cite only ADR-031/055/059 and not the ADR-062 player-impact
   tables (`block:lightSource`, four `command:planet/*`, six `config:PLANET.*`,
   `config:STATION.spaceStationId`, `entity:laserNode`, `item:elevatorChip`).
4. Asset-plan loosening passes (EXCLUDE→IMPORT for `env/sun.png`, REVIEW→IMPORT
   for `*leo.jpg`, deleting the hovercraft-OBJ exclusion so it falls into
   `models/*.obj` IMPORT, IMPORT for the rejected transceiver), although ADR-061
   §4.2 requires an ADR-062 revision; there is no link between asset handlings and
   unit dispositions.
5. `--require-accepted` does not enforce ADR-061 §5.4's "zero PLANNED" (all ADRs
   flipped to ACCEPTED with 224 PLANNED rows: exit 0).
6. Inventory content (lines, display names, deleted or fabricated non-registry
   units) is checked only by the generator `--check` (verified: it catches them),
   not by the validator whose docstring claims agreement with legacy-manifest.
Required change: check IMPLEMENTED targets against the actual `register(...)`
names per registry (blocks, items, fluids, entities, sounds, biomes via data);
require ADR-062 (or a named ADR with a player-impact table) for DEFERRED/REJECTED;
pin the EXCLUDE/REVIEW asset sets (e.g. a committed list plus hash) so loosening
fails without a matching ADR-062 revision; map assets to owning units and fail
IMPORT for assets of DEFERRED/REJECTED units; add a `--closure` (or make
`--require-accepted`) fail on PLANNED rows; add unit tests for each.

#### C14R1-M7 — Batch dependencies understate the legacy recipe graph; model textures land after their models

Legacy recipes (probe `logs/recipe_ingr.log`): rocket loaders need `ic@1` tracking
circuits (C16d); the advanced and nuclear engines need TitaniumAluminide /
TitaniumIridium (arc furnace, C16b) and dilithium gems (crystallizer, C16c); the
warp monitor needs `ic@2`, `ic@3`, `misc@0` (C16d); the beacon needs `ic@1`, `ic@3`
(C16d); the gas charge pad and CO2 scrubber need `misc@0`, `ingotCarbon` (C16d)
and `fanSteel`; the space-breathing enchantment is applied by chemical-reactor
special recipes (C16c; TileChemicalReactor 140–180, AdvancedRocketry 1133).
ADR-062 §6 lists C17a/C18a → C16a only, C17b → an ADR-046 revision only, C17c →
C17a, C18b → C18a. The asset plan also imports OBJ/MTL in C16b (sawblade), C17a
(engines, tanks) and C18b (jetpack, laser gun) whose `map_Kd`/bound textures
(`models/combustion`, `tank`, `nuclearengine`, `cuttingmachine2`, `basicLaserGun`,
`jetpack.png`) are only in C18d (rule #177) — contrary to ADR-061 §4.6/§5.2.
Required change: correct the "Depends on" column (or state which recipes are
rebalanced so the dependency disappears) and move each model texture into the
batch of its model.

#### C14R1-M8 — Governance: proposed dispositions written into the matrix as decided; an expiring waiver is unassigned

- `docs/PORTING_MATRIX.md` now states ADR-062's PROPOSED decisions as final:
  laser line/spiral modes, spy telescope, physical asteroid fields, the legacy
  gravity API → `REJECTED`/"never unless ADR"; satellite overlay, cross-system
  cargo, non-player fields, terraforming → "post-v2.0.0 DEFERRED"; several
  accepted-ADR deferrals turned into PLANNED. The v1.7 precedent marks proposed
  rows ("（ADR-054，提案）"). Rejecting what accepted ADRs (049, 055, 058) deferred
  is a maintainer decision.
- ADR-046 UI-02's known issue "messages are literal English … Expires no later
  than v1.8.0" (also v1.5 KNOWN-ISSUES) is neither resolved in ADR-062 §5 nor
  assigned to a batch.
Required change: mark the changed matrix rows as proposed until ADR-062 is
accepted (or defer the matrix edit), and add the ADR-046 language waiver (and its
notice gaps) to ADR-062 §5 with a batch (C17b).

#### C14R1-M9 — ADR-062 makes ADR-018's campaign trigger unsatisfiable without saying so

ADR-018 (ACCEPTED) triggers the full acceptance campaign when "every original
machine and dimension … has a completed implementation" and says "Closing an
inventory row by deferring or rejecting it is not completion for this trigger."
ADR-062 defers or rejects original machines (terraformer, biome scanner,
unmanned vehicle assembler, wireless transceiver) and dimensions (asteroid
dimensions, cave planets, world types). Read literally, the trigger can never
fire, yet ADR-060 says "ADR-018 continues to schedule the full campaign".
Required change: list ADR-018 in ADR-062's `revisits`, and propose (for the
maintainer) how the trigger is evaluated once accepted DEFERRED/REJECTED rows
exist — or keep the trigger and say the campaign cannot start until those rows
are implemented.

#### C14R1-M10 — "No placeholder IDs exist" is not accurate

The coverage table maps version-document §8 "旧 placeholder item/block 迁移到正式 ID"
and §10 "升级世界中的 placeholder/旧 ID" to "no placeholder IDs exist". Fourteen
items are `DevelopmentComponentItem`s whose tooltip says "Development component —
no machine behavior in v0.1.0" (silicon_wafer, basic_circuit, advanced_circuit,
data_storage_unit, the satellite chassis/modules/batteries/cargo hold), and the
v1.2 block models are "community-authored placeholder models"
(V120MachineBlockStateProvider). These are the placeholders §8/§10 mean.
Required change: list them in ADR-061 §1.3 with their formalisation (IDs kept,
tooltip and models replaced, recipes rebalanced), and cover them in the C19
world-upgrade check.

#### C14R1-M11 — Edited upstream files would be recorded as `NEW`

ADR-061 §4.4: "Pixel edits, recolouring and upscaling produce `NEW` files with the
upstream file named as inspiration, not `UPSTREAM_AR_MIT` files." A pixel-edited
copy is a derivative: it keeps the upstream licence obligations and inherits any
vanilla derivation (H1: a recolour of `moon_turf.png` is still a recolour of
`grass_block_top.png`). Recording it as community-original `NEW` hides the chain
from the G0 licence review.
Required change: record derivatives as `UPSTREAM_AR_MIT` (or `THIRD_PARTY_APPROVED`)
with the source hash and a `pixel edit`/`recolour`/`upscale` transformation; a
derivative of an `EXCLUDE`/`REVIEW` file inherits that handling; `NEW` only for
work not derived from any file.

#### C14R1-M12 — The recipe-graph contract cannot detect location or progression hard locks

ADR-061 §5.3 proves obtainability from recipes, loot, world generation and
missions, with cycles allowed when each member has an acyclic route. It does not
model access: an ore that generates only on the Moon (dilithium, `DilithiumPerChunkLuna`)
is obtainable only after a rocket that can reach the Moon, which may itself need
that ore; research/discovery gates (ADR-037) and the energy source (M3) are
further prerequisites. The version document requires "no unreachable node or
hard-locking cycle" (§5) and a "complete, playable" chain (§14).
Required change: add Level-access nodes (route + required rocket tier/fuel),
discovery/research gates and an energy prerequisite for powered machines to the
graph model; require an explicit, reviewed exemption list (force-field block,
unlit torch, fluid blocks, technical blocks) instead of silent exclusions.

### Low

#### C14R1-L1 — Factual inaccuracies in the audit, ADR-061 and the asset plan

- Audit §3.1: ore generation is driven by **20** keys, not 21 (5 metals × 3 +
  Luna dilithium + master switch).
- ADR-061 Context, audit §3.8 and rule #23: **five** sound events share the
  silent placeholder; `dummy.ogg` is a file, not an event. Also, `AudioRegistry`
  registers only `ElectricShockSmall` in the Forge registry (AudioRegistry 31–36);
  the other 14 are unregistered `SoundEvent` objects (and `buttonBlipA` exists
  only in `sounds.json`).
- "Photographic-looking" LEO images: `moonleo.jpg`, `earthlikeleo.jpg` are painted
  or procedural tiles; keep `REVIEW` but fix the reason. `atmosphereleo.png`
  (same 2017 add and 2021 downsize commits as the `*leo.jpg` set) is IMPORT via
  #179.
- `textures/font.png` does not share a vanilla file name (vanilla `font/ascii.png`).
- Pump: the BFS runs when its cache is empty, not "for every refill"
  (TilePump 129–168); add the unbounded downward air scan (TilePump 136) to §4.
- Laser gun: the per-player map is an instance `WeakHashMap` on the singleton
  item (ItemBasicLaserGun 34, 42), not static.
- Small plate press: it is itself the redstone-powered piston that crushes the
  block beneath it, on obsidian (BlockSmallPlatePress 95–150).
- ADR-062 §4 "three deprecated pipe block entities … not registered": the tile
  entities are registered (AdvancedRocketry 389–391); their blocks are not.
- `packet:PacketMoveRocketInSpace` is never registered (no discriminator,
  AdvancedRocketry 309–324) — dead code; `PacketItemModifcation` is registered
  and used by AR items but not inventoried.
- `event:PlanetEventHandler.onCrafting` is a no-op (body commented out, 92–111)
  yet is PLANNED as "advancement crafting triggers".

Required change: correct the texts and rows.

#### C14R1-L2 — Asset-plan internal inconsistencies

`models/block/models/sawblade.*` falls under #169 (C17a "rocket engine and tank")
while the matrix says saw blade C16b; `models/item/models/motor.*` is C17a
although motors are C16a (and its MTL points at a texture AR does not ship);
`textures/models/drill.png` is IMPORT while `models/drill.obj` is EXCLUDE;
ADR-061 §4.2 says loosening `REVIEW` needs an ADR-062 revision while §4.5 lets
`REVIEW` assets enter after an origin finding.
Required change: add specific rules, drop textures with no consumer, and state
in §4.2 that a recorded positive origin finding (not an ADR revision) moves a
REVIEW file to IMPORT.

#### C14R1-L3 — Two advancements depend on content that is not delivered

`holographic` triggers on `libvulpes:holoProjector` in the inventory (REDESIGNED,
no item); `wenttothemoon` triggers within about 22 blocks of the lander at
(2347, 80, 67) on Luna (PlanetEventHandler 177–181; MapGenLander 18–20), and the
lander is DEFERRED. "Rebuilt with modern triggers" should name the new triggers.

#### C14R1-L4 — Coverage table gaps

§10/§11/§13/§15 items (modpack matrix, progression video, soak, performance
report) are assigned to slice "v1.9"; they are v1.8 evidence deferred by ADR-018
— say so and keep v1.8 `IN_PROGRESS` until they exist. The version document's
tutorial/player documentation (§1, §6.7) and §17 (temporarily disabling an
unstable machine; migration strategy for DEFERRED) have no contract or slice.

#### C14R1-L5 — Terrain changes to existing Levels lack ADR-033's seam disclosure

C15b replaces the Moon/Mars/Venus surfaces in Levels that existing worlds already
generated. ADR-033 requires "later generator changes must preserve IDs and
disclose terrain seams". ADR-061 §6 says "new chunks only" but neither ADR states
the seams and their player impact. C15a also places Moon ores before C15b changes
the Moon's terrain.

#### C14R1-L6 — Generator robustness

Hard-coded `number < 912` splits command groups; `PRODUCT_PREFIXES` lacks
`fan`/`gem`/`rod`; every `network/` file counts as a packet whether registered or
not; `conditional` is set for blocks and items only (`ARGravityMachine` is
conditional, AdvancedRocketry 419–420); the LibVulpes scan misses ore-dictionary
components; only one parsing unit test exists. Add fixtures for config, command,
packet registration and conditional parsing.

#### C14R1-L7 — Minor semantic mis-merges

`packet:PacketFluidParticle` is MERGED into `fluid:rocketFuel` (it is the pump and
fueling particle; merge into `block:blockPump`); several REDESIGNED rows cite only
ADR-062 instead of the ADR that delivered the mechanism (e.g.
`keybinding:openRocketUI`, `block:launchpad`).

### Info

- **C14R1-I1** — Keep both CI commands: the validator relies on the generator
  `--check` for inventory content; document this in the validator docstring.
- **C14R1-I2** — Map the ledger vocabulary to docs/16 §5 (IMPLEMENTED/REDESIGNED
  are not `PASSED_EQUIVALENT`/`PASSED_REDESIGNED` until behaviour acceptance).
- **C14R1-I3** — Deferring terraforming and the hovercraft past v2.0 changes the
  v2.0 parity target; present it to the maintainer as an explicit product choice
  (PRODUCT.md lists terraforming among later-restored features).
- **C14R1-I4** — The v0.1.0 imports `machinevent.png`, `machinewarning.png`,
  `datastorageunit.png` were last changed by AR PR #1811 (`276e4e44`, Silfryi
  Kalsandryn, 2020); LibVulpes `984d6747` attributes the same 16x set to an
  "unmerged Cl1ff PR". Record that chain in the maintainer's review (no change
  to the accepted v0.1.0 record is implied).
- **C14R1-I5** — Legacy low oxygen needed only a helmet (AtmosphereLowOxygen
  24–38); folding it into "no oxygen" changes the requirement to a full suit —
  worth a sentence in ADR-062 §2.
- **C14R1-I6** — ADR-060 could carry ADR-053's explicit outcomes "no forced or
  persistent chunk loading" and "every system can be disabled by the server
  without breaking world load" into v1.8 (monitoring-station remote launch,
  beacons, projector, pump).

## 5. Verdicts

| ADR | Verdict | Conditions |
|---|---|---|
| ADR-060 | ACCEPT WITH REQUIRED CHANGES | M9 (ADR-018 interplay); the fourth prerequisite is met only after the inventory fixes of M1–M3 and the ADR-061/062 acceptances |
| ADR-061 | ACCEPT WITH REQUIRED CHANGES | H1, H2, M6, M10, M11, M12 before acceptance; L1, L2 |
| ADR-062 | ACCEPT WITH REQUIRED CHANGES | M1–M5, M7–M9 before acceptance; L1, L3–L5, L7 |

No Critical findings. The two High findings concern assets and sources that ADR
acceptance would approve; both are fixable in a revision 2 without changing the
overall structure, which is sound: per-unit ledger, ordered asset rules,
batch-level contracts, pinned sources, and a validator in CI.

## 6. Artifacts kept

`probes/` (all scripts, including `pngdecode.py`, `mutate.py`), `logs/` (every log
named above, including `asset_plan_resolved.txt`, `recipe_ingr.log`,
`lv_history.log`). Deleted after the review: the exported tree, the upstream zip
and unpacked copy, the downloaded LibVulpes textures and JSON responses, and every
extracted or rendered vanilla image.
