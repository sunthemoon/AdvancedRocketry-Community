# Ordinary thermite material and light: runtime contract proposal 04

Root proposal, 2026-10-08. Production baseline9c816af9. Replaces proposed
contract03 only after independent review and explicit ADOPTION-04. Original
commits/sealed evidence remain unchanged. No source/pixels/asset/unit/Gate
adoption follows from this proposal. The first contract03 reviewer returned
2 Medium/5 Low and omitted mandatory live-main reads; its failed runner and
original report remain unwaived. This successor needs a fresh complete review.

## 1. Scope and dependencies

One plain thermite item and ordinary standing/wall vacuum-compatible light;
registration, recipes/tags/unlocks, NEW resources and automated checks. Consume
existing C15a aluminum/iron dust/tags and world-block press acquisition. No steel
fan, C16d UI/carbon, fluid/vent migration, station query, equipment or shared
hatch/save consumption. Freeze this subleaf under ADR-066 section8 and record
this dependency disposition under section9; other C18a obligations stay open.

No unlit conversion, combustion tags/catalog/queues, fire interception, pipe
seal, new world-change adapter, heat/explosion, machines, BlockEntity, SavedData,
new NBT schema, network, atmosphere query, scan or client-only common dependency.
Existing worlds gain normal registry content without migration/scan; later ID
removal needs the normal missing-mapping policy.

## 2. Stable IDs, numbers and recipes

Namespace advancedrocketrycommunity. Plain Item thermite; standing block and
paired item thermite_torch; wall block thermite_wall_torch, no own item.
Root holder symbols: ModItems.THERMITE/THERMITE_TORCH and
ModBlocks.THERMITE_TORCH/THERMITE_WALL_TORCH.

Root proposes ADR-066 section3.2 numbers: light14; two vanilla shapeless recipes,
IDs thermite/thermite_torch. One forge:dusts/aluminum plus one forge:dusts/iron
produces one thermite. One minecraft:stick plus one forge:dusts/thermite produces
four thermite_torch. Material ingredients are tags; result.item is a literal ID.
Wrong/missing/extra ingredients reject by vanilla matching rules. Match/assemble
do not mutate input stacks. Thermite unlock is either input dust tag; torch
unlock is the thermite tag. Include recipe-unlocked OR criterion and only the
corresponding recipe reward. Existing press/machine/cooking recipes unchanged.

Root's single V180MaterialData.Items writer emits the NEW thermite tag and adds
its required reference to forge:dusts in the same TagsProvider. The NEW file
data/forge/tags/items/dusts/thermite.json is exactly
{"values":["advancedrocketrycommunity:thermite"]}; omitted replace defaults to
false/merge. Other mods/packs can append members. No optional-reference shortcut,
cross-provider tag tracking, second writer or MaterialCatalog family/API change.

## 3. Survival route and switches

Craft the existing press from piston over three tagged iron ingots. Install it
above a world ore block, with obsidian below that ore. Loaded cells, no input
BlockEntity, nonnegative hardness, one unambiguous recipe, off-to-on redstone
edge and uncanceled piston event remain required. One ore becomes two matching
dust, leaving press/obsidian; a second edge on air creates nothing. Natural ore
can be left in place with obsidian installed below; Silk Touch/placed ore is an
alternative. Raw item entities are not press inputs. One aluminum-ore operation
and one iron-ore operation supply two thermite/eight torches with two sticks;
that arithmetic is not native survival evidence.

classic.smallPlatePress=false stops new pressing, not crafting existing or
external dust. No alternate dust recipe. Disabling new Overworld ores affects
new chunks, not already present ore. Proposed lifeSupport.classicDevicesEnabled
interpretation: this passive material/light remains craftable/placeable/lit,
with no active device work/debit/output. C17b station light does not establish
the C18a switch exemption; ADOPTION-04 must explicitly record this narrow
interpretation of ADR-066's active-device clause. Other devices retain their
disable rule. No whole-ADR exception. Reload never rewrites placed states/items.

## 4. Native block/item/model behavior

Direct public TorchBlock(Properties, ParticleOptions), WallTorchBlock with
same signature, one StandingAndWallBlockItem(standing, wall, properties,
Direction.DOWN). No subclass needed. Properties: no collision, instant break,
wood sound, constant light14, push reaction DESTROY. Each block keeps its own
default registered loot ID; no dropsLike/lootFrom sharing. Each distinct loot
table returns one paired thermite_torch with native survives_explosion.
Normal support loss/removal drops one; creative removal drops none. Paired item
maps both blocks; no wall-item registry ID. Wall naming delegates through item.

Native standing floor/center support, horizontal wall support/facing, neighbour
removal and nearest-direction fallback apply. This is not clicked-face dispatch.
Ceiling-only/no-fallback support rejects; an available floor/wall fallback may
succeed after ceiling click. Test those fixtures separately. Reference
ParticleTypes.FLAME, inherit native visual flame/smoke, no heat/fire/damage.
No official bitmap or implementation copied into product.

Standing blockstate one variant; wall four facing variants. Reference template
parents minecraft:block/template_torch and template_torch_wall, torch texture
slot, explicit render_type minecraft:cutout. Wall yaw east0/south90/west180/
north270. Item models use minecraft:item/generated with layer0 pointing at the
NEW item/torch texture. Reference identifiers only, not official geometry/UVs.

Exact platform report is
D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18a-thermite-platform-qualification-20261008-01/reviewer-01/REPORT-01.md,
SHA e170d21f313804ba25eff15cb3b1185a946e1fb0dea836a5ce4abf2729c33a72.
Root ROTATION-01.json separately records the one baseline blockstate interface;
ADOPTION-04 must pin its SHA and independent review, not represent it as the
technician's observation. API/reference facts do not prove native/visual results.

## 5. Exact resource writer and source interface

V180ThermiteData implements DataProvider. Public constructor
(PackOutput output, boolean client, boolean server); public static
Map<String, JsonObject> clientFiles()/serverFiles(), String[] dustGrid()/torchGrid(),
byte[] dustTexture()/torchTexture(). Fresh maps/JSON/arrays/bytes per call.
Literal IDs/raw JSON like V180StationLightData, no registry-holder references
or BlockLootSubProvider. run(CachedOutput) writes only enabled partitions;
getName() stable. No ExistingFileHelper parameter or competing tag writer.

Fourteen leaf-owned outputs under src/generated/v1.8/resources:
client eight: two blockstates, two block models, two item models, two PNGs;
server six: two recipes, two advancements, two distinct block loot tables.
Root separately owns the NEW thermite tag, existing dust-umbrella and bilingual
language merge outputs. V180ThermiteLanguage.translations(boolean chinese)
returns immutable three-key map: item.advancedrocketrycommunity.thermite,
block.advancedrocketrycommunity.thermite_torch and block...thermite_wall_torch;
English Thermite/Thermite Torch/Thermite Wall Torch; Chinese
铝热剂/铝热火把/壁挂铝热火把. Existing keys untouched.

Root wires provider/language and tracks both NEW PNG texture identifiers via
ExistingFileHelper.trackGenerated with its PNG texture ResourceType before
provider/model use. Raw JSON author does not guess registry/helper integration.
Root owns registry/tabs/tags/generated output/adapter/native-test wiring.
Claude's separate TASK-03 grants only new provider/language/A0/report files.

## 6. Original art and prior asset boundary

Pre-authoring NEW MIT declaration lists textures/item/thermite.png and
textures/block/thermite_torch.png, original16x16 dust heap/narrow shaft/capped
bright tip. No legacy/vanilla/third-party pixel/grid read, tracing or transform.
Reuse only V180MaterialArt.png encoder: one RGB tint multiplied by its ten grey
levels per digit, '.' alpha0, other pixels alpha255. Literal grids/tint produce
bounded deterministic RGBA PNGs, no multi-hue palette or replacement encoder.
References to vanilla parents/particles are not imports. Originality and final
bytes/hashes need independent review. Old IMPORT226/229 remain unresolved and
unchanged; NEW does not close those dispositions, assets or release Gates.

## 7. Verification and admission conditions

A0: exact fourteen-file partition, recipes/tag-selector/result/unlock/model/
loot contracts, independently verified wall rotations, fresh results, PNG CRC/
alpha/grid geometry/determinism and all four provider flag combinations. Inline
ingredient-only oracle: no exact thermite ingredient; required result.item stays.
Central tag/umbrella separately tested; old press resources byte-identical.

A1: literal registered identities/pairing/no BE; native ItemStack.useOn with
survival-configured player (Forge snapshots/cancellation), floor/four-wall
placement/fallback, isolated ceiling/no-support count-preserving refusal, normal/
creative/support-loss drops. Dark loaded fixture emission14 and propagated
light14/neighbor13 within unchanged40ticks; return to measured baseline after
removal. Both disabled switches get their distinct expected outcomes from §3.
Actual RecipeManager matching/assembly/tag substitution and input immutability;
press conservation/no-input/disabled behavior, not only recipe arithmetic.

Root's adapter-only fixture registers three ordinary items under arce_adapter_test:
thermite_aluminum_dust, thermite_iron_dust, thermite_dust. Three adapter resource
files append them to the respective common tags. Check loaded membership before
matching foreign ingredients. No live tag mutation or production foreign member.
Preserve exact production-generated tag checks; inspect loaded-tag cardinality
assumptions and run full regression instead of weakening assertions.

Configured Moon vacuum comes from EnvironmentQueryFixture's actual ready-event
handle, not effective room air. Main native test also uses actual installed
AtmosphereAnalyzerRuntime.read(player) in bounded loaded Moon fixture: ambient
must be present/pressure0, unsupplied nonbreathable eye cell, unchanged after
placement/neighbor invalidation, torch still emits/publishes14. Missing ambient
fails. Restore owned cells/player/chunk force; do not replace runtime or use a
local AtmosphereLevelService oracle. Finite chunk setup is test-only loading.

S1 survival crafting/pickup, reload, save/stop/restart, prior-world placement;
real-GPU V1 and applicable V2 remain actual-evidence obligations. A0/A1 do not
prove crash atomicity or whole graph. Java17 build/test, twice DataGen/no second
diff, GameTests, strict repository/provenance/resource checks; preserve failures,
unrun conditions and inherited Gates. No R-021, ledger, whole C18 or release
acceptance. ADOPTION-04 must pin exact reviewed commit/reports/rotation and
explicitly adopt numbers/passive interpretation/C15a-only dependencies before
source or pixel dispatch.
