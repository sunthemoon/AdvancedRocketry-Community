# C18a-SEAL-01: read-only Seal Detector (proposed technical leaf)

2026-10-06. Source basis `35a146fbbe1f2de94160f82307d041d2cd26e472`.
Status: PROPOSED, not adopted or assigned. Unit `item:sealDetector`; stable ID
`advancedrocketrycommunity:seal_detector`. Implement one obtainable handheld
instrument, not room certification, reserve installation or a new query API.

## 1. Prerequisites that must be adopted before source assignment

ADR-066:188-191 accepts the measurement, but its section 9:939-942 states batch
dependencies without a detector exception. A task alone cannot waive that text.
Propose this additive scoped clarification to ADR-066 section 9:

> For C18a-SEAL-01 only, the read-only seal detector depends on the existing
> ADR-024 compiled boundary catalog, loaded-cell adapter and lifecycle-owned
> atmosphere manager, plus its reviewed ordinary recipe/resources. It neither
> depends on nor completes steel-fan/carbon-brick acquisition, fluid conversion,
> vent/scrubber migration, station mutation/access authority or equipment/finder
> integration. Those prerequisites remain mandatory for their dependent C18
> interactions; this exception delivers none of those units.

There is also a real interaction dependency, not just absent registration.
`AtmosphereServerEvents:49-58` currently dirties doors/registered boundaries on
every right-click. ADR-024:80 requires registered-block interaction invalidation.
Native Forge 47.4.10 `ServerPlayerGameMode.useItemOn` posts RightClickBlock at
bytecode 41, may call block.use at 259 and item.useOn at 349. Thus a new useOn
alone can dirty a scan or activate the door before measurement.

Propose an ADR-024:80 qualification: the exact detector-only interaction, with
block activation denied and no block mutation, is excluded from preemptive
right-click invalidation; all real block/state/neighbor mutations retain the
existing synchronous invalidation. Root must adopt/review both clarifications
and the leaf choices below, under its actual contract authority, before code.
No waiver of full tests, risk rules or Required Gates is proposed.

| Existing batch dependency | This leaf's actual use / boundary |
|---|---|
| C15a steel fan; C16d UI/carbon brick | No machine, component or recipe edge; not implemented here. |
| C16 fluid/tag conversion | Read existing boundary precedence/tags/fluid state only; no conversion. |
| Vent migration / D5 | Read the existing elected ACTIVE, resource-valid provider; no debit, bank or schema change. |
| Station access/protection | No station lookup/owner action or mutation; ordinary player/block-use admission retained. |
| D1 reserve / C18b finder summary | No equipment provider, armor root, HUD/API or installation route. |

## 2. Exact use, admission and query order

Unstackable, no durability/capability/owned NBT. Both real hands and creative
mode use the same rules. Existing `lifeSupport.classicDevicesEnabled` applies;
no new config or wire. Use vanilla `UseOnContext`, not a new target packet/ray.
Boundary target = immutable clicked BlockPos; supply target = that position
plus exactly `getClickedFace()` (one of six Direction values), not eye/feet or
opposite face. No air-use query, ray trace, neighbor search or volume request.

Item routing listener, both logical sides: if the event's actual held item is
this registered detector, set `useBlock=DENY`; never set useItem=ALLOW or clear a
foreign cancellation. Existing item-use denial/cancellation remains respected.
Root's atmosphere event adapter receives a private exact-item predicate and
skips only this detector's preemptive RightClickBlock dirty mark, before any
Level/state read. No other item or invalidation path changes. Foreign same-JVM
mods can override events/shapes; this is not a sandbox or a promise about them.

Server use validates actual hand/stack identity/count=1, ServerPlayer/ServerLevel,
owning thread/server/Level, and existing `AtmosphereAnalyzerService.admitted`
(alive, connected, current PlayerList object, non-FakePlayer/nonspectator).
Invalid admission: FAIL, no reader/message/cooldown. Client predicts interaction
only, never measurement. Check vanilla item cooldown before reader/message.
Two server ticks, shared by both hands; add after an admitted response including
DISABLED/UNAVAILABLE/PENDING. This is the existing analyzer cadence, not gas balance.
Disabled: one fixed localized message, zero reader/chunk/block/catalog/manager
queries; the preceding actor/host identity checks are not falsely counted as zero.

For enabled reads: bound service and host guards first; finite eye and native hit
coordinates; eye X/Z in [-30,000,000,30,000,000), eye Y within build height;
clicked cell in spawn/build/world-border bounds; `level.mayInteract(player,target)`
must hold. All checks are non-loading; use ordinary vanilla interaction gates,
not operator/claim bypass. Required reach is squared Euclidean eye-to-hit <=36
(six blocks, inclusive), additionally squared minimum eye-to-clicked-unit-AABB
distance <=36 to reject an inconsistent near hit carrying a far target. No LOS
scan or special permission is inferred; normal native caller gates still apply.
Face-adjacent cell must independently satisfy spawn/build/border bounds.

Then use loaded-only getChunkNow for clicked chunk and, when different, adjacent
chunk: at most two preflights; never getChunk/getChunkAt/ticket/ensureLoaded.
Unloaded target => whole UNAVAILABLE without state/manager queries. Loaded
target => exactly one existing `ServerLevelVolumeWorldView(level,false,boundaries)`
observation, retaining tags -> door state -> compiled rule -> fluid/air/shape.
Out-of-build targets are rejected before the adapter's legacy OPEN fallback.
Adjacent invalid/unloaded => retain boundary result but supply UNAVAILABLE,
without adjacent atmosphere calls. Otherwise call existing manager
`breathabilityAt(level,adjacent)` once; only BREATHABLE calls `controlledAt` once.
No tick, scan request, observeVent, explicit markDirty, resource debit or save.
Manager queries can create/update its own ephemeral per-Level service and revoke
obsolete environment state. They are not allocation-free, persistent writers
or evidence that ordinary host ticks stopped (`AtmosphereManager:118-132`).
The manager's internal breathability query can itself call controlledAt; thus
worst case two indexed provider validations, not a false two-total-world-read
claim. Loaded provider lookup is existing behavior, not another selected cell.
Custom collision shapes remain the existing same-JVM trust boundary.

Capture immutable targets/face; after queries recheck the same live binding,
player/Level and actual held identity/count. A closed/replaced handle or stale
actor returns UNAVAILABLE, never mixed ownership. Do not retain context/player/
stack/Level/VolumeId in readings. Do not copy/deserialize held NBT or call suit APIs.

## 3. Exact internal signatures, lifecycle and feedback

All signatures below are proposed internal implementation, NOT existing APIs
or API-classifier additions. Use the existing atmosphere.instrument package.

- package-private `record SealDetectorReading(Outcome outcome, Boundary boundary,
  Supply supply)`; Outcome READING/DISABLED/UNAVAILABLE; Boundary SEALED/OPEN/
  UNAVAILABLE; Supply SUPPLIED/NOT_KNOWN_SUPPLIED/PENDING/UNAVAILABLE.
  DISABLED/whole UNAVAILABLE require both fields UNAVAILABLE.
- package-private `SealDetectorService(MinecraftServer,AtmosphereManager,
  AtmosphereBoundaryCatalog)`; `SealDetectorReading read(UseOnContext)`;
  `boolean owns(MinecraftServer)`; idempotent terminal `close()` clears binding.
  Only the admitted Item path calls it. Independently recheck context hand/stack
  identity against the player's current hand and the final SealDetectorItem type;
  do not turn this package-private callback into arbitrary-position authority.
- package-private `SealDetectorRuntime.install(service)`, `remove(service)` and
  `read(UseOnContext)`; one inactive volatile service reference, no world/player
  collection. Missing handle gives UNAVAILABLE; removal matches exact service.
- public concrete `SealDetectorLifecycle(Supplier<AtmosphereManager>,
  Supplier<AtmosphereBoundaryCatalog>)`, started/stopping/stopped event methods.
  Start once at ServerStarted after manager init; missing dependency leaves
  inactive; close/drop before manager clear at HIGHEST stopping priority and at
  stopped fallback. Old handle never rebinds; foreign server close does nothing.
- public concrete `SealDetectorItem(Item.Properties,BooleanSupplier)`;
  `useOn(UseOnContext)`; `onRightClickBlock(RightClickBlock)` routing method.
  Package-private three-argument constructor adds `Function<UseOnContext,
  SealDetectorReading>` for tests only, defaulting to Runtime::read.
- package-private `SealDetectorFeedback.message(SealDetectorReading): Component`.
  One fixed localized system-chat Component to the actor, never hazard action
  bar; no position/volume IDs, arbitrary error/NBT text or messages to bystanders.

Reuse the SAME frozen catalog passed to manager in common setup; Root retains
that value for startup injection, not a second registration event/empty default.
No SavedData acquisition, new server truth store, context resolver or scanner.

| Observation | Display |
|---|---|
| SEALED | Boundary SEALED |
| OPEN or TRAVERSABLE | Boundary OPEN |
| UNLOADED / invalid target | UNAVAILABLE; never OPEN by inference |
| Neighbor BREATHABLE + controlledAt true | SUPPLIED (known current scanned-volume provider) |
| Neighbor BREATHABLE + controlledAt false, or VACUUM | NOT_KNOWN_SUPPLIED, NOT "no oxygen"/room certification |
| Neighbor PENDING | PENDING, no positive supply |
| Neighbor invalid/unloaded / missing service | UNAVAILABLE, not false/zero |

Exactly 12 NEW bilingual keys: `item.advancedrocketrycommunity.seal_detector`;
`tooltip.advancedrocketrycommunity.seal_detector.measurement_only`; and prefix
`message.advancedrocketrycommunity.seal_detector.` followed by `reading`,
`disabled`, `unavailable`, `boundary.{sealed,open,unavailable}` and
`supply.{supplied,not_known_supplied,pending,unavailable}`. Reading has two enum
arguments only. EN "Seal detector | Boundary: %s | Adjacent supply: %s";
ZH "密封检测器 | 边界：%s | 相邻供气：%s". Tooltip: one boundary does not certify
a room / 单个边界不能证明整个房间密封. Existing keys/values unchanged.

## 4. Obtainability and original resources (proposal; no art produced)

NEW ordinary shapeless recipe: one minecraft:iron_ingot + one minecraft:redstone
+ one minecraft:glass_pane -> one seal_detector; unlock on inventory_changed
with minecraft:redstone, plus ordinary recipe_unlocked alternate criterion and
recipe reward. Registered vanilla inputs have no new mod acquisition cycle.
This is a reviewed NEW route, not legacy-parity or complete survival-tech proof.
Item remains registered/craftable when devices are disabled, matching config.

Exactly four new generated paths: assets/advancedrocketrycommunity/{textures/
item/seal_detector.png,models/item/seal_detector.json}, data/advancedrocketrycommunity/
{recipes/seal_detector.json,advancements/recipes/misc/seal_detector.json}.
Ordinary minecraft:item/generated parent referenced by ID, not copied.
Root must publish NEW/MIT provenance BEFORE any art/provider authoring. No old
detector bitmap, other instrument grid, game/Forge or external art is read/traced.
Reuse only `V180MaterialArt.png` encoder, with this newly proposed two-prong
probe/compact display/stem silhouette, 16x16, tint 0xE0BD72:

```text
................
..22........22..
..29........92..
..299......992..
...299....992...
....22222222....
....26777762....
....26799762....
....26733762....
....26777762....
.....266662.....
......2442......
......2442......
......2442......
......2222......
................
```

`.` = transparent; digits use existing greys [20,44,72,93,117,141,169,191,210,238],
each channel floor(grey*tintChannel/255), alpha255. This exact proposed palette
uses 2/3/4/6/7/9, no dynamic color/status texture. Independent originality screen,
generated bytes/hash and real-client inspection remain obligations, not done.

## 5. Disjoint implementation and verification scope

Worker NEW files only: six instrument classes named above; datagen/
V180SealDetectorData.java, V180SealDetectorLanguage.java; gametest/
SealDetectorGameTests.java; corresponding Reading/Service/Item/Data unit tests;
own PROGRESS-01.md and HANDOFF-01.md. Keep each class <=500 lines, no framework.
Root exclusively: adopted task/disposition/ADR clarifications/pre-art provenance,
ModItems/ModCreativeTabs, AdvancedRocketryCommunity catalog/lifecycle/event wiring,
AtmosphereServerEvents exact-item constructor predicate (old constructor retains
false default), BootstrapDataGenerators/V180LanguageProvider, four generated
files/two language files, central resource tests and status/ledger/Git.
Existing gameplay, oxygen roots/API/HUD, vent schema, other event cases,
network/build/API artifact and owner decisions must otherwise stay unchanged.

Evidence before delivery: independent actual diff/source review; finite unit
coverage of all enums/invariants, inclusive reach/NaN/Inf/AABB/face mapping,
thread/player/Level/hand/count/config/cooldown/closed-host refusal and call order.
Registered GT uses actual item and connected owned player: full/partial shapes,
fluids/air, open/closed door/trapdoor/gate, tag and registered-rule precedence;
block activation/dirty/scan counters unchanged on detector clicks, ordinary
other-item invalidation retained. Loaded/unloaded cross-chunk neighbor, no new
chunk/ticket; supplied-room publication via ordinary producer, ambient-only,
dirty/PENDING/reload/unload/empty vent; held NBT/resources unchanged, separate
ordinary tick effects. Actual recipe craft/unlock and generated consistency.

Root capacity/serial admission then fixed committed clean build/test/runData,
repeat DataGen/zero old-output differences and unfiltered GameTests, strict
resource/provenance/content checks. Hosted route must retain raw/error outcomes,
not borrow 058/other cohort passes. Later legitimate packaged connected-player
use/restart/closed-host and two-player recipient/isolation, true GPU V1/locales/
tooltip/model/readability and applicable V2. This task creates no native player
fixture/writer/receipt authority; unresolved C18c host admission is not reusable.
No new cross-store crash transaction, but S1/S2/G0-G9 are not closed by static
controls or the instrument. All unrun failures and resource/visual limits remain.

## 6. Root assignment specification (not yet assigned)

The original proposal above is retained as the independently reviewed technical
input. The source checkout basis for a future assignment is the published
`e0c601e4d58cb906d79f744070316c0a2d810c06`; Root must verify that all detector
dependencies still match the original proposal basis. This addendum enumerates
ownership; it does not adopt the proposed ADR qualifications or authorize code.
Root publishes reviewed ADR-067/task adoption and NEW provenance before source
or art authoring. No implementation worktree is assigned by this document yet.

Worker NEW source paths below are relative to
`src/main/java/io/github/sunthemoon/advancedrocketrycommunity/`:

- `atmosphere/instrument/SealDetectorReading.java`;
- `atmosphere/instrument/SealDetectorService.java`;
- `atmosphere/instrument/SealDetectorRuntime.java`;
- `atmosphere/instrument/SealDetectorLifecycle.java`;
- `atmosphere/instrument/SealDetectorItem.java`;
- `atmosphere/instrument/SealDetectorFeedback.java`;
- `datagen/V180SealDetectorData.java`;
- `datagen/V180SealDetectorLanguage.java`;
- `gametest/SealDetectorGameTests.java`.

Worker NEW tests, relative to the corresponding `src/test/java` package:

- `atmosphere/instrument/SealDetectorReadingTest.java`;
- `atmosphere/instrument/SealDetectorServiceTest.java`;
- `atmosphere/instrument/SealDetectorItemTest.java`;
- `datagen/V180SealDetectorDataTest.java`.

Worker may additionally write only its own `PROGRESS-01.md` and `HANDOFF-01.md`
in this task directory of its assigned isolated worktree. Report any additional
write need first. Root retains task/ADR/provenance/disposition, all existing
source, central resource tests, registry/event/bootstrap/language integration,
the four generated outputs/two language files, status/ledger/Git and commits.
No third-party registered fixture, synthetic native admission or production
hook is granted. Finite GameTest setup uses owned connected-player fixtures
and ordinary producers; distinguish setup loads/ticks from measurement work.
Source/data/source-review and unfiltered exact-commit hosted checks remain
pending. Native restart/client and all version Gates remain unverified.
