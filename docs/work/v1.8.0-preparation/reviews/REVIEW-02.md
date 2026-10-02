# C14 contract review, round 2: v1.8.0 Classic Content contracts and ADR-063

```yaml
reviewer: independent contract reviewer (read-only)
review_round: C14R2
date: 2026-10-02
repository: D:/GitHub/AdvancedRocketry-Community
branch: codex/v1.8.0-classic-content
commit_reviewed: 90f1e9eeea8604b978a48642713576b7c561a532
previous_round: C14R1 at f05eec21 (commits f05eec2..90f1e9e answer it)
verdicts:
  ADR-060: ACCEPT
  ADR-061: ACCEPT WITH REQUIRED CHANGES   # C14R2-H1 blocks acceptance
  ADR-062: ACCEPT WITH REQUIRED CHANGES
  ADR-063: ACCEPT WITH REQUIRED CHANGES
finding_counts: {Critical: 0, High: 1, Medium: 6, Low: 7, Info: 5}
round_1_status: {fixed: 18, partially_fixed_or_reopened: 3 (H1, M6, L1), info_answered: 6}
```

## 1. Scope and method

Reviewed revision 2 of ADR-060, ADR-061 and ADR-062, the new ADR-063 and every
file changed in `f05eec21..90f1e9ee` (25 commits; `review-01-dispositions.md`).
The repository was read only (`git archive`, `git log`, `git diff`); the
untracked documentation bundle was not read; no Gradle.

| # | Command / probe (all under `round-2/`) | Result |
|---|---|---|
| 1 | `git archive 90f1e9ee` → `t/`; pinned upstream zip downloaded → `u/ar` | zip SHA-256 `40eb5d43…cca01d` (as round 1) |
| 2 | vanilla JARs: 1.12.2 (`8ada07da…`, SHA-1 `0f275bc1547d01fa5f56ba34bdc87d981ee12daf` = Mojang's 1.12.2 client) and the local 1.20.1 `client-extra.jar` (`f43d1ac0…`; its `client.jar` SHA-1 `0c3ec587…` = Mojang's 1.20.1 client) | used read only |
| 3 | `python tools/audit/vanilla_derivation.py --upstream u/ar --vanilla 1.12.2=… --vanilla 1.20.1=… --check` | `up to date` (5 min 12 s) — `logs/derivation_check.log` |
| 4 | `python tools/audit/inventory_v180_content.py --upstream u/ar --check` | `up to date` |
| 5 | `python scripts/validate_v180_content_ledger.py` | PASS: 652 units; 52/133/292/93/25/57; assets 275 IMPORT, 80 REVIEW, 430 REGENERATE, 103 EXCLUDE, 10 IMPORTED — `logs/validator.log` |
| 6 | same with `--closure` | exit 1 (ADR-061/062 PROPOSED, 292 PLANNED) as intended |
| 7 | `python -m unittest tests.test_validate_v180_content_ledger` | 37 tests OK |
| 8 | `p/lowlevel.py`: the tool's own `decode_png`/`Image`/`compare`/`verdict` on pixel-identical copies of vanilla images | 1,565 of 3,837 vanilla PNGs have < 8 luminance levels; all **1,330** of them with ≥ 32 opaque pixels score `CLEAR` against themselves — `logs/lowlevel.log` |
| 9 | `p/ungated.py`: the tool's measures without the two luminance-level gates, on every IMPORT/REVIEW PNG | 21 verdicts rise; 1 HIT and 1 SUSPECT on IMPORT files are real (below) — `logs/ungated.log` |
| 10 | `p/crops.py`: sub-image and flip/rotate search of IMPORT/REVIEW PNGs ≤ 64 px in every vanilla PNG ≤ 512 px | `gui/buttons/tabtemplate.png` is a 92–94 % pixel-exact crop of the vanilla creative tab — `logs/crops.log` |
| 11 | `p/frames.py`, `p/lava.py`: first-frame and all-frame comparison of animated strips | oxygen flow ≈ vanilla water flow; enriched-lava sheets are not vanilla lava (rank ≤ 0.09) |
| 12 | `p/decodefail.py` | all 3,837 vanilla PNGs decode (no silent skips) |
| 13 | `p/mutate2.py`: 22 mutations of copies (ledger, asset plan, allowlist + ADR-062 digest, origin findings, derivation JSON) through the validator | 12 caught, 10 survive — `logs/mutations2.log` |
| 14 | `javap -c` of `net.minecraft.world.level.levelgen.WorldDimensions` in the local mapped Forge 47.4.10 JAR | `bake()` resolves each stem as `datapackStems.getOptional(key).or(() -> saved)`: data-pack dimensions win over `level.dat`, so ADR-063's generator change does reach existing worlds — `logs/worlddimensions_javap*.log` |
| 15 | Reading upstream `RecipeHandler`, `OreGenerator`, `MapGenCrater/Volcano`, `BlockSmallPlatePress`, `BlockElectricMushroom`, `EntityRocket` and the LibVulpes material table (`LibVulpes.java` 349–359 at `c2ca79d`); modern `RocketLandingPadSelector`, `SafeCelestialTravel`, rolling recipe codec, build.gradle resource roots, generated Moon/Mars/Venus/Tau Ceti data and asteroid tables; GitHub API for the 16x pull requests | see findings |

## 2. Status of round-1 findings

| Finding | Status | Evidence |
|---|---|---|
| H1 vanilla derivation by name | **Fixed for the named files; control reopened as C14R2-H1 and C14R2-M1** | earth_phases, the three turf files, log tops, plate press faces, crucible, carbon brick, GUI sheets and the space leggings are HIT/EXCLUDE; space suit icons REVIEW. The measuring tool misses low-palette copies and crops (R2-H1) and its results are not bound (R2-M1) |
| H2 LibVulpes scope | Fixed | ADR-061 §4.1 approves no LibVulpes file; `machinegeneric.png` and `models/item/models/motor.obj` verified byte-identical to LibVulpes blobs `f09c2614…`, `c3a05bbf…` and REVIEW |
| M1 configuration surfaces | Fixed | 135 config units (132 names, three read in two categories) + 14 XML files; my round-1 probe's 19 keys are all present |
| M2 coremod rules | Fixed | three live `asm_rule`s (comment-stripped `INVOKESTATIC` scan); underwater breathing and blaze items in the ledger notes |
| M3 fan, early power | Fixed | fan/gem/rod products; curated coal-generator unit → C16a combustion generator |
| M4 wrong mechanisms | Fixed (residual wording in C14R2-L3) | config rows, holographic selector, discovery chance, ore scanner corrected |
| M5 REDESIGNED v1.8.0 | Fixed (evidence binding weak, C14R2-L1) | no row has plan `v1.8.0`; 37 rows PLANNED with batches |
| M6 validator gaps | Partly fixed; residue in C14R2-L1, C14R2-M1 | tag literals, family checks, ADR-062 for DEFERRED/REJECTED, allowlist, closure all catch; 10 of 22 new mutations survive |
| M7 batch dependencies, model textures | Fixed in data (not enforced, C14R2-L1) | dependency column follows the recipes; model textures share their model's batch |
| M8 matrix / ADR-046 waiver | Fixed | 提案 markers; waiver assigned to C17b in ADR-062 §5 |
| M9 ADR-018 trigger | Fixed (owner decision in ADR-062 §8) | — |
| M10 placeholders | Fixed | ADR-061 §1.3 lists the fourteen components and the C19 upgrade check |
| M11 edited pixels as NEW | Fixed | ADR-061 §4.4 / §4.8 |
| M12 recipe-graph access | Fixed in the contract | energy, Level access, research; ADR-063 then breaks it twice (C14R2-M3, M4) |
| L1 factual slips | Partly fixed (C14R2-L3) | audit §3.1 still says the press works "when a piston pushes it"; water "counts as solid" |
| L2 asset-plan inconsistencies | Fixed | per-model rules; drill/tubes excluded; motor REVIEW |
| L3 advancements | Fixed | new triggers named |
| L4 coverage | Fixed | ADR-018-deferred evidence wording; guide, switches, deferred migration |
| L5 seams | Fixed in ADR-061 §6 (ADR-063's seam text has its own gap, C14R2-M5) | — |
| L6 generator robustness | Fixed | method-scoped commands, registered packets, conditional block entities, parser tests |
| L7 mis-merge / citations | Fixed | — |
| I1–I6 | Answered | I4 now has an open consequence (C14R2-M2) |

## 3. Findings

### High

#### C14R2-H1 — The derivation check cannot see low-palette copies or crops; three IMPORT files are vanilla-derived

`tools/audit/vanilla_derivation.py` computes `overlap` only when the **vanilla**
image has ≥ 8 luminance levels (line 216) and `rank` only when the **candidate**
has ≥ 8 levels (line 218); `iou` alone never yields a verdict. It pairs images
only at equal size or exact ×2/×4 scale, with no sub-image, flip or rotation
search.

- Probe 8: run through the tool's own functions, a pixel-identical copy (new
  file bytes) of any of 1,330 vanilla images — most 1.12.2 item icons
  (`iron_leggings`, `diamond_helmet`, `gold_chestplate`, `sign`, `egg`, …) —
  scores `CLEAR`. Only byte-identical files are caught (hash).
- On the committed plan:
  - `textures/blocks/plank_blue.png` — IMPORT #124 (C15c, and ADR-063 §6
    relies on it) — is vanilla 1.12.2 `blocks/planks_oak.png` recoloured:
    Spearman 1.000, Pearson 0.994 over 256 px (7 levels each), committed
    verdict `CLEAR` (probe 9, `logs/pairs.log`; checked visually).
  - `textures/gui/buttons/tabtemplate.png` (24×24) — IMPORT #211 (C16a) —
    equals the vanilla creative-inventory tab on 92 % of opaque pixels at
    (157,34) of 1.20.1 `creative_inventory/tabs.png`, 94 % flipped in five more
    vanilla sheets (probe 10).
  - `textures/blocks/fluid/oxygen_flow.png` — IMPORT #118 (C16a) — ranks 0.88
    against vanilla `water_flow.png` (candidate has 6 levels, so the tool
    reports rank 0); its sibling `oxygen_still.png` is SUSPECT at 0.83.
- The tool has no unit tests and no calibration set, so neither the
  thresholds nor the false-negative rate are known (ADR-061 §4.8 publishes
  thresholds only).

The validator accepts all three IMPORT rows, so the next batches would ship
Mojang-derived art, which AGENTS.md §3.2 and docs/08 §7 forbid. False negatives
matter more here than false positives.

Required change:
1. Remove the luminance-level gates or replace them with a minimum count of
   informative pixels. Compute `overlap` on every vanilla image, without the
   dominant-colour exclusion when the image has few colours. Compute `rank` when
   both images have ≥ 3 levels and ≥ 32 shared pixels.
2. Add a sub-image search (candidate inside larger vanilla sheets), the eight
   flips/rotations, and per-frame comparison of animated strips.
3. Add a committed calibration set and unit tests covering exact copies,
   recolours, crops, flips, ×2/×4 rescales and low-palette icons. The tests
   assert the verdicts and record the false-negative rate.
4. Regenerate the results. Move `plank_blue.png` to EXCLUDE and
   `tabtemplate.png` and `oxygen_flow.png` (with its `.mcmeta`) to REVIEW at
   most. Revise the allowlist and its digest (ADR-062).

### Medium

#### C14R2-M1 — The derivation results are not bound to the tool, CI or an ADR

The validator trusts `docs/work/v1.8.0-vanilla-derivation.json`, but nothing
recomputes it: CI runs only the inventory check and the validator. No digest is
pinned, and the file's `vanilla` hashes and `thresholds` are not checked. These
mutations survive:
- flipping a `HIT` (earth_phases) to `CLEAR`, adding it to the allowlist and
  re-pinning the digest;
- flipping a `SUSPECT` (space_boots) the same way;
- deleting the JAR hashes and thresholds.

So the H1 control holds only as long as nobody edits a JSON file.

Required change:
- Pin the results file's SHA-256 (and the two JAR SHA-256 values) in ADR-061,
  next to the allowlist digest in ADR-062.
- Make the validator check the pinned digests and the thresholds against the
  tool's constants.
- Run `vanilla_derivation.py --check` in CI. Fetch the two clients by pinned
  SHA-1 from Mojang's version manifest, as ForgeGradle already does for 1.20.1,
  or state who runs it locally for each batch and record the output in the
  batch evidence.

#### C14R2-M2 — ADR-061 §4.9 is not applied to AR's own 16x texture set

ADR-061 §4.9 puts a file under REVIEW when its history "brings in material from
an issue, an unmerged pull request or another project". LibVulpes `984d6747`
(Silfryi, 2020-05-04) says its 16x textures came "from the unmerged Cl1ff PR".
Two days later the same contributor (`voidsong-dragonfly`, commit author
"Silfryi Kalsandryn") merged AR pull requests #1809 and #1811 ("16x textures …").
#1889 followed.

The plan applies §4.9 only to `machinegeneric.png` (via byte identity). It
leaves **29 IMPORT files from #1811 and 7 from #1889** as IMPORT: for example
`atmospheredetector`, `controlpanel`, `cuttingmachine`, `pumpside`,
`satellitebay`, `solar`, `warpcore`, `bioniclegs`, `landingboots`,
`pressuretank0–3`, `sawbladeiron` and four planet icons (`logs/pr16x.log`).
The PR bodies credit no one, and a search for Cl1ff issues in both repositories
returns nothing (the account may be renamed). The audit notes the chain only
"for the maintainer's G0 review".

Required change: apply §4.9 uniformly. Either make the #1809/#1811/#1889 files
REVIEW pending an authorship finding, or record in the origin-findings file a
reasoned finding that the AR set is the PR author's own work. Note the same
question for the three v0.1.0 imports from #1811 (C14R2-I3).

#### C14R2-M3 — ADR-063 gives iridium no source; the titanium-iridium engines become unreachable

ADR-063 §1/§4 registers `iridium_ore` and `raw_iridium` but places them nowhere
("asteroid and laser drill tables may list it"). Legacy players got iridium from
the "Iridium Enriched asteroid" (`asteroidConfig.xml`, AdvancedRocketry.java
1159–1161: `libvulpes:ore0 10`). The v1.6 tables replaced it with `rich_asteroid`
(gold, diamond, emerald), and no generated table, laser-drill table or recipe
mentions iridium.

The C17a advanced bipropellant and nuclear engines need
`ingotTitaniumIridium` (recipes `advbipropellantengine`, `nuclearengine`). The
recipe-graph rule of ADR-061 §5.3 would fail at C16d/C17a.

Required change: commit to an iridium source in ADR-063 (or the C17a contract).
For example, restore an iridium-rich asteroid type by an ADR-052 table revision,
or add Moon/Mars placement. Make the iridium configuration rows (now PLANNED
"ore placed features") say which.

#### C14R2-M4 — ADR-063's Tau Ceti worlds have no defined access path

The six exotic biomes, lightwood, electric mushrooms and the crystals move to
`tau_ceti_f`/`tau_ceti_g`, which are reachable only after an interstellar
station warp (ADR-044). ADR-063 §6 defines the bodies and Levels but none of
the following:
- the `travel_routes` (Mars and Venus each have `<body>_surface_orbit`; nothing
  is planned for Tau Ceti f/g);
- how their required discovery (ADR-037) is earned in another star system;
- that no classic progression item depends on their content.

Without routes the Levels cannot be visited, and ADR-061 §5.3's Level-access
rule has nothing to evaluate.

Required change: add the routes (station orbit ↔ surface; any Earth route
would be interstellar), the discovery path and the warp prerequisite to
ADR-063 §6. State that the C15c content is optional for progression, or list
what depends on it (legacy `crystal@3` fed only the rejected-in-form vacuum
laser recipe).

#### C14R2-M5 — The Moon terrain change conflicts with the Moon landing rule, and the seam is understated

- The modern Moon is flat: bedrock at y 0 and end stone at y 1–3, so the surface
  is y 4 (`dimension/moon.json`). ADR-063 §5 gives it the legacy relief
  (Regolith Highlands base height 1.0, variation 0.2; Lowlands 0.5), which puts
  the new surface near y 75–100. Every explored Moon area then sits in a pit
  behind a wall of roughly 70–95 blocks, not just "a visible step".
- "Rocket landing uses the surface height (ADR-033), so arrivals are
  unaffected" is not true for the Moon. ADR-033 keeps the Moon's fixed pads and
  "legacy minimum landing Y=80" without the ground-support check, and
  `RocketLandingPadSelector` uses `max(heightmap, 80)` around (8, 80, 8) (lines
  58, 290–292).
  - On new terrain, rockets land floating over craters or lowlands below y 80.
  - `SafeCelestialTravel.prepareFixedPlatform` builds a 5×5 floor at y 79 with
    only three blocks of clearance, which can sit inside a hill.
- (Verified: data-pack dimension definitions override `level.dat`, so existing
  worlds do get the new generator for new chunks.)

Required change:
- Decide the Moon landing rule for relief terrain: adopt the ADR-033
  new-surface support check, or keep a flat reserved pad area around the fixed
  pads in the noise settings.
- Handle the developer platform (clear upward to the sky, or place it on the
  heightmap).
- Quantify the seam (expected step height) in ADR-063 §5 and the release notes.
- Add A1 tests: landing over a crater, and platform travel into a hill.

#### C14R2-M6 — Some C15b world-generation bounds are not implementable as written

- Volcanoes "cone radius ≤ 32" (64 blocks across) as a placed feature: in
  1.20.1 a feature may write only within the 3×3 chunks around its origin chunk,
  that is ≤ 16 blocks from a random origin and ≤ 24 from the chunk centre.
  Larger writes are refused or logged as far-chunk writes. (Legacy
  `MapGenVolcano` was a multi-chunk `MapGenBase`, size 64.)
- Craters "as a carver": carvers only remove blocks, but legacy craters raise
  a rim (`MapGenCrater` 119–126).

Required change: specify the mechanism per feature:
- volcanoes as a structure (jigsaw or single piece with a bounding box), or a
  radius ≤ 16;
- craters as a carver plus a rim feature within the feature write radius, or as
  a structure.

Keep the per-chunk bounds and server switches.

### Low

#### C14R2-L1 — Validator residue (surviving mutations)

- An IMPLEMENTED or REDESIGNED row with plan `v1.8.0` accepts any existing
  `docs/work/v1.8.0-*` file as evidence, so `block:lathe` → `machine_casing`
  with the audit as evidence passes (R1-2, R2-3b).
- Owning units on asset rules can be any ledger unit (R2-h).
- Model-texture batch alignment (round-1 M7) is not checked (R2-g).
- A `CLEARED` origin finding accepts any reviewer string (R2-f).
- A unit can move to REJECTED citing ADR-062 although §4 never mentions it
  (R2-m).

Required change:
- Evidence must be the batch's evidence file and must list the unit ID.
- For IMPORT rules, owners must share the rule's batch or an earlier batch,
  and a model's textures must not land later than the model.
- `CLEARED` needs the owner or a named independent reviewer.
- Optionally, a group key linking DEFERRED/REJECTED rows to ADR-062 §3/§4
  rows.

#### C14R2-L2 — Stale or inconsistent counts

- ADR-062 Context and audit §1 say 132 configuration keys, but the inventory has
  135 config units, so the kind table sums to 649, not 652.
- ADR-062 Consequences say "about 220 planned units" (now 292).
- Implementation log line 66 says "18 unit tests" (now 37).
- ADR-061 Context says "350 legacy files as import candidates" (now 275 IMPORT,
  80 REVIEW).

#### C14R2-L3 — Residual factual slips (from round-1 L1/M4)

- Audit §3.1 still says the plate press works "when a piston pushes it".
- Audit §3.4 and ADR-062 §3 say water "counts as solid": the legacy rocket only
  treats water as a safe landing (no float) and settles under it.
- The ledger note for `block:landingfloat` and the matrix row still say
  "water landing".

#### C14R2-L4 — ADR-063's material table drops legacy products silently; the ledger disagrees

Legacy LibVulpes flags (LibVulpes.java 349–359) and AR 897–898 also define:
- silicon nugget and dust (the legacy crystallizer boule recipe is silicon ingot
  plus silicon nugget, RecipeHandler BOULE branch);
- gold coil, copper sheet, titanium coil and iridium coil;
- the titanium aluminide block;
- the titanium iridium sheet, gear and block.

The ledger's `material:Gold` target says "plate, dust and coil"; ADR-063 says
dust and plate. Either add these products or list them as dropped with a reason.
Keep the silicon nugget unless C16c changes the boule recipe.

#### C14R2-L5 — ADR-063 does not cover all of its C15 ledger rows

- `config:WORLDGEN.*Copper*` (3 rows) are PLANNED "ore placed features", but
  ADR-063 places no copper (vanilla copper generates): REDESIGNED.
- `config:WORLDGEN.*Iridium*` (3 rows) are PLANNED, but ADR-063 places no
  iridium (C14R2-M3).
- `integration:jei/platePresser` (C15a) is not mentioned. ADR-061 §3.3 asks JEI
  for machines; ADR-063 §8 has no JEI test.

#### C14R2-L6 — ADR-063 does not state its DataGen layout

The Moon dimension lives in `src/generated/v0.3`, and Mars/Venus dimensions and
noise settings in `src/generated/v1.4`. The project supersedes earlier copies
through `processResources` exclusions in `build.gradle` (lines 134–175), which
are integrator-owned, and the v0.3/v1.4 generated-manifest checks run in CI.

Required change: name the C15 output root, the superseded paths and the build
exclusions. State that the old `advancedrocketrycommunity:mars`/`venus` biomes
stay registered, because already generated chunks reference them (ADR-061 §1.4).

#### C14R2-L7 — ADR-063 legacy facts and stated changes

- Legacy craters reach about 91 blocks of radius, not 70 (`getBaseRadius`
  182–192).
- The legacy ore generator ran in every dimension with stone, so the Moon and
  Mars also got copper, tin, rutile and aluminium. The "Luna" dilithium count
  applied to every airless planet (OreGenerator 43–80).
- Electric-mushroom light level 7 is new (legacy 0).
- The plate press removes a block with no owner and no protection check.
  ADR-061 §6 asks world-changing tools to use the ADR-054 §5 chain: decide (for
  example, post a cancellable break event with a fake player, as claim mods
  expect, or document the piston-equivalent exception).

### Info

- **C14R2-I1** — `HIT` has no appeal path. Radial glows score `HIT` against the
  vanilla sun (`stationlight.png`, `hololamp.png` at ×2, rank 0.93), probably
  false positives. Consider a recorded owner override that needs visual evidence.
- **C14R2-I2** — `asm_rule:GravityHandler.applyGravity` is one unit with a
  PLANNED half (living) and a REJECTED half (non-living, ADR-062 §4). Split it
  so the validator sees the rejection.
- **C14R2-I3** — The three v0.1.0 imports last changed by #1811
  (`machinevent`, `machinewarning`, `datastorageunit`) share C14R2-M2's question
  for the G0 review.
- **C14R2-I4** — State rutile's tags (legacy registered it as both `oreRutile`
  and `oreTitanium`) and why `moon_dilithium_ore` is a separate ID.
- **C14R2-I5** — Kernel recipe ingredients resolve a tag to at most 32 variants
  (`BoundedItemIngredientCodec` 58), and a larger tag fails the recipe. Mention
  this limit beside ADR-061 §2.2 ("other mods' titanium works").

## 4. Verdicts

| ADR | Verdict | Conditions |
|---|---|---|
| ADR-060 | ACCEPT | Its revision-2 additions (no forced loading, disable switches, ADR-018 pointer) are sound; it depends on the acceptance of ADR-061/062 as it states |
| ADR-061 | ACCEPT WITH REQUIRED CHANGES | H1 and M1 before acceptance; L1, L2 |
| ADR-062 | ACCEPT WITH REQUIRED CHANGES | M2 and the H1 plan/allowlist changes; L2, L3; the owner's §8 decision at acceptance |
| ADR-063 | ACCEPT WITH REQUIRED CHANGES | M3, M4, M5, M6 before acceptance; L4–L7 |

Round 1's structure held up. The ledger, the allowlist pin, the closure mode,
the family-checked IDs, the LibVulpes decision and the dependency fixes are
real improvements. The remaining High is in the measuring instrument that now
gates every import.

## 5. Artifacts

Kept: `round-2/p/` (probes, including `mutate2.py`, `lowlevel.py`,
`ungated.py`, `crops.py`, `frames.py`, `pair.py`) and `round-2/logs/`. Deleted:
the exported tree, the upstream zip and unpacked copy, the downloaded LibVulpes
source file, the GitHub API responses and every extracted or rendered vanilla
image.
