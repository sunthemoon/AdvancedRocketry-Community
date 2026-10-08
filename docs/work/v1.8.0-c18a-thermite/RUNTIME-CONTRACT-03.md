# Thermite material and ordinary light: proposed runtime contract

Root proposal, 2026-10-08. Production baseline
9c816af94bee0798d3c22d179fb40652c7648246. This complete narrow contract
supersedes the author's draft 01 and correction 02 for this leaf only, after
independent review and a separate Root adoption record. Neither source/pixels
nor feature, ledger, risk or Gate approval follows from proposal publication.
The original sealed author records and historical deviations remain unchanged.

## 1. Scope and dependencies

Deliver one plain thermite material plus ordinary standing/wall vacuum light,
its recipes, common tag, registration, NEW data/art and automated checks.
Consume existing C15a aluminum/iron dust/tag and small-plate-press acquisition.
Do not consume steel fan, carbon/UI components, fluid/vent migration, station
queries, equipment or shared-hatch/save machinery. This is the precise C18a
subleaf under ADR-066 section 8, not a deferral of those other C18a obligations.
Unlit conversion, combustion tags/catalog, torches queues, fire interception,
pipe seals, world-change adapters, heat/explosions, machines and persistence
transactions remain out. No new BlockEntity, SavedData, NBT root, network,
atmosphere lookup, scanning algorithm or client-only common dependency.

## 2. Stable identities and material recipes

Under advancedrocketrycommunity: thermite is an ordinary Item; thermite_torch
is a standing block and the single paired item; thermite_wall_torch is a wall
block with no independently registered item. Keep these names stable. Existing
worlds gain normal registry content; no scan or old-ID replacement. Subsequent
removal would need the normal missing-mapping policy.

Root proposes adoption of ADR-066 section 3.2 numbers: light 14, shapeless one
forge:dusts/aluminum plus one forge:dusts/iron to one thermite; one vanilla
stick plus one forge:dusts/thermite to four thermite_torch. Material ingredients
use tags; result.item remains a literal output ID. Missing/wrong/additional
ingredients reject under vanilla shapeless rules. Two recipe IDs equal the
output IDs. Unlock thermite on either input dust tag; unlock torches on the
thermite tag; vanilla recipe-unlocked criterion is OR-ed with input criteria
and rewards only that recipe. Neither matching nor assembly mutates inputs.

NEW tag data/forge/tags/items/dusts/thermite.json, replace false, contains only
our thermite; other packs/mods may add their members. Root adds its tag to the
existing forge:dusts umbrella through the sole V180MaterialData.Items writer,
not a competing provider. No MaterialCatalog family or shared API change.
Existing press/cooking/machine recipes remain byte-identical.

## 3. Acquisition and configuration

The supported survival route crafts the existing press (piston over three
tagged iron ingots), places it above an aluminum/iron ore block with obsidian
two cells below the press, then applies an off-to-on redstone edge. The input
is a world ore block, not a raw item. Both cells must be loaded, input has no
BlockEntity/nonnegative hardness, exactly one matching press recipe and an
uncanceled piston event. One operation consumes one ore and creates two dust,
leaving press/obsidian. Natural ore may be left in place with obsidian installed
below; Silk Touch is an alternative, not mandatory for that route. A second
edge on air creates nothing. One operation per ore supplies two thermite and
eight torches with two sticks; this arithmetic needs actual native validation.

classic.smallPlatePress=false prevents new pressing, not crafting with existing
or externally supplied dust. No alternate dust recipe is invented. Turning off
new Overworld ores affects newly generated ore only, not existing world blocks.
For this passive light/material leaf, lifeSupport.classicDevicesEnabled does
not gate vanilla crafting, placement or emitted light. It owns no active device
work/debit/output and follows the registered passive station-light pattern;
this exact scope is a Root adoption decision, not an accidental ignored switch.
The active-device disable rule remains for other C18a leaves. Reloading tags,
recipes and unlocks never rewrites placed torch BlockState or held items.

## 4. Native behavior and implementation boundary

Use public TorchBlock(Properties, ParticleOptions), WallTorchBlock with the
same public signature, and StandingAndWallBlockItem(standing, wall, properties,
Direction.DOWN). No subclass is needed. Properties: no collision, instant
break, wood sound, lightLevel 14, push reaction DESTROY; wall dropsLike standing.
Reference ParticleTypes.FLAME, inheriting native visual flame/smoke behavior
without heat, ignition, damage or explosions. No official art bytes are copied.
Standing support/floor, wall horizontal facing/support, neighbour removal and
ordered placement-direction fallback are native semantics, not clicked-face
dispatch. A ceiling-only target with all fallback supports absent rejects;
a ceiling click with available floor/wall fallback is not promised to reject.
Actual block/item placement and support-loss tests must distinguish those cases.

Each block loot table returns the same one thermite_torch item with native
survives_explosion condition. Normal support loss/removal produces one, native
creative removal produces none. Wall block asItem/clone is mapped to the paired
item by StandingAndWallBlockItem; no wall-torch item ID is created. Standing
blockstate has one variant; wall has four horizontal facing variants. Models
reference minecraft:block/template_torch/template_torch_wall by identifier and
the NEW torch texture through their torch slot. Item torch is generated from
that NEW texture; thermite item is generated from its NEW item texture.

API signatures/fallback were observed in the installed baseline mapped JAR by
the separately registered read-only platform task and its javap extension;
they are binary/API facts, not an actual placement result. The final report,
artifact SHA and any remaining model-slot/loot/support fact qualifications must
be attached before adoption. No latest-lane API inference is substituted.

## 5. Exact resources and authoring boundary

NEW outputs in src/generated/v1.8/resources: two blockstates, two block models,
two item models, two block loot tables, two shapeless recipes, two advancements,
one thermite dust tag and two PNG textures: fifteen leaf-owned files total.
Existing dust-umbrella and two language JSON files are central merge outputs,
not additional competing leaf writers. Bilingual keys are item.thermite,
block.thermite_torch and block.thermite_wall_torch under this namespace;
English Thermite/Thermite Torch/Thermite Wall Torch and Chinese
铝热剂/铝热火把/壁挂铝热火把. Existing names remain unchanged.

Declare exact planned paths as NEW MIT before any pixels. Author independent
16x16 transparent RGBA dust-heap and torch grids without reading/tracing legacy,
vanilla or third-party pixels. Reuse only existing V180MaterialArt.png encoder,
not existing grids. Fixed literal grids/palette produce deterministic bounded
PNGs, separately originality-reviewed. Old IMPORT candidates 226/229 remain
unchanged and unresolved; NEW does not clear or close them. Final per-file
hashes, packaging and resource/provenance validation remain required.

## 6. Verification, ownership and admission

Root owns existing central registry, tabs, language/provider wiring, umbrella,
adapter-fixture wiring and generated outputs. Claude implements only separately
registered new provider/language/JUnit/report files. Use actual literal registry
IDs in native checks; do not test only an unregistered local block.

A0: exact JSON path/ingredient/result/tag/unlock/model/loot contracts, independent
wall rotations, fifteen resource partition, fresh results, PNG structure/alpha/
CRC/pixel geometry, deterministic bytes and all four client/server provider
flag combinations. T12 forbids exact thermite only among ingredient selectors,
never the required result.item. Compare old press resources unchanged.

A1: registered identities/pairing, floor/four-wall item placement and direction
fallback, isolated ceiling/unsupported refusal with unchanged count, removal
drops, no BlockEntity, constant light and loaded native-light publication,
ordinary recipes/tag substitution without input mutation, press conservation
and disabled behavior. A separate adapter-only foreign-item fixture contributes
foreign aluminum/iron/thermite tag members and tests the actual RecipeManager.
It must never be packaged in production. The adapter's existing ready-event
EnvironmentQueryFixture provides the actual installed service: query actual
Moon Level as vacuum, then test loaded torch light there with a bounded separate
fixture and restoration, never a local AtmosphereLevelService stand-in. Native
fixture loading is finite setup, not product chunk-loading behavior.

S1 survival acquisition, reload, normal save/stop/restart and upgrade placement;
real-GPU V1 particle/model/light checks and any applicable V2 remain unrun until
actual evidence. No crash-atomic or full-graph result is inferred from A0/A1.
Java17 build/test, twice DataGen with no second difference, GameTests and strict
repository/provenance/resource checks are required. Preserve original failures
and unrun Gates. No R-021 acceptance, ledger delivery or release tag is granted.
