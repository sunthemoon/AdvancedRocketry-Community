# C18b-JACKHAMMER-01 - ordinary single-block tool

Date: 2026-10-09 (Asia/Taipei). Owner/implementer/integrator: Root.
Base: `30dd002841a6d0dbabad7c72e1774d5f3cb17330`, normally pushed.
Status: ADOPTED for bounded Root implementation after fresh independent review.
See [ADOPTION-01](ADOPTION-01.md). No source, registration, generated output or
runtime result exists at adoption; the source checkout starts afterward.

## Outcome and authority

Implement the missing ordinary jackhammer behavior under accepted ADR-066
section 5.3, without waiting for equipment-module or physical-hatch authority.
This is a native single-block pickaxe, not a separate mining transaction.
Numeric proposals below require independent leaf review before adoption.
No full content/asset ledger unit or Required Gate is delivered by preparation.

Proposed stable ID: `advancedrocketrycommunity:jackhammer`. Keep the actual
`Tiers.DIAMOND`, native pickaxe actions/enchantability/combat defaults (constructor
attack bonus 1, attack speed -2.8), maximum damage 1,024 and stack size one.
Mining speed is 50 only on `minecraft:mineable/pickaxe`, otherwise native speed
one. Tier-qualified loot remains the inherited Forge TierSortingRegistry rule;
speed does not grant drops above the diamond tier. No new tier identity.

Repair uses the existing `forge:rods/titanium` item tag, not default diamond
repair. Ordinary same-item combination, enchantments, Damage and unrelated
vanilla item data retain native semantics. No project-owned NBT root/schema.
The ID is additive and permanent from registration; future rename/removal needs
the existing ADR-061 migration process. No 1.12 inventory remapping is added.

## Native disable and mining boundaries

Add only the already accepted `equipment.classicEnabled` COMMON switch,
default true, using the existing server-switch accessor/registered test override
mechanism. It does not change existing suit/reserve behavior or implement the
unadopted equipment-module draft.

Disabled jackhammer refuses native block breaking at the item's post-BreakEvent
`onBlockStartBreak` hook and refuses native melee at `onLeftClickEntity`.
Predicted mining speed/tool actions may also be disabled, but are not final
server enforcement. Registration, recipes, inventory/save data and ordinary
safe anvil repair/withdrawal remain available. Do not rely solely on mining
speed, a cancelable listener that another listener can clear, or client state.

Use the existing ordinary player action/progress and destroy/loot paths, Forge
BreakEvent and station BUILD protection. No blanket canHarvest=true, scan/AOE,
alternate block removal, fake-player breaking, extra tick service, position
packet, tile-NBT copy, or native interception. A canceled native break must
preserve the target and tool. Native mineBlock can damage the tool before a
later removeBlock refuses; no stronger removal/durability atomicity is promised.
Direct destroyBlock calls are downstream evidence, not hardness/range/timing
admission; those checks must exercise actual native action/progress.

## Acquisition and assets

Read-only upstream recipe SHA-256 is
`a2d717f1afe921977dfced20123272c8ceca26b457b2d52d37d23b9f6e249a45` at
MIT commit `c5cd5af62fc07cd4e0d24f06a16033f181c47c04`; exact input is registered
in the pre-authoring provenance record before any transformation.
Proposed modern shaped recipe retains rows `" pt"`, `"imp"`, `"di "`:
diamond gem, titanium rod, two iron rods, two aluminum plates and one motor.
Use `forge:gems/diamond`, `forge:rods/titanium`, `forge:rods/iron`,
`forge:plates/aluminum`, `advancedrocketrycommunity:motors`; result one tool.
Unlock on obtaining a motor; retain normal recipe-unlocked alternate criterion.
The modern recipe is a recorded MIT-derived conversion, not NEW-only data.

Titanium's initial rutile conversion still needs the missing C16b arc furnace;
steel/motor progression is also unfinished. Do not add a cheap bootstrap route,
claim survival reachability from supplied test ingredients, or mark acquisition
complete. New original code-generated item art may be used as an unapproved
development visual; it does not import/deliver the legacy IMPORT candidate.
Only lawful resource parents are referenced, never copied official assets.

The NEW development icon proposal is a top cross-handle, compact body and
straight narrow bit, tint `0xBDD0DE`, using only V180MaterialArt.png's existing
digit palette and transparent dots. Exact proposed rows (not imported pixels):

```text
................
..222222222222..
..299222222992..
..222267762222..
.....269962.....
.....268862.....
.....267762.....
.....266662.....
......2772......
......2992......
......2992......
......2882......
.......99.......
.......77.......
.......55.......
................
```

Use the lawful `minecraft:item/handheld` parent and the new texture's ID.
Add `minecraft:pickaxes` membership. Bilingual names are Jackhammer / 钻锤;
tooltips describe pickaxe-block mining, titanium-rod repair and disabled state.
No placeholder or internal review instructions enter player-facing text.

## Write scope and ownership

Root's source checkout will be a NEW isolated worktree
`D:/GitHub/arce-v180-jackhammer-20261009`, branch
`codex/v1.8.0-jackhammer`, created only from published leaf adoption.
Root owns all central bindings; no delegated implementation or Claude.

- NEW `equipment/tool/JackhammerItem.java` beneath the existing common package.
- NEW `datagen/V180JackhammerData.java` and `datagen/V180JackhammerLanguage.java`.
- NEW matching `equipment/tool/JackhammerGameTests.java`, optional small owned
  fixture class in that package, and `src/test/.../equipment/tool/` tests.
- Narrow bindings in `registry/ModItems.java`, `registry/ModCreativeTabs.java`,
  `config/CommonConfig.java`, `datagen/BootstrapDataGenerators.java`,
  `datagen/V180LanguageProvider.java`.
- Only the tool's generated model/texture/recipe/unlock and item tags, plus
  additive v1.8 en_us/zh_cn language keys. Earlier generated roots are immutable.
- This task, leaf adoption/verification/review evidence, pre-authoring/final
  provenance records, and narrow canonical plan/status/implementation log edits.

Root NEW evidence leaf:
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/jackhammer-root-20261009-01`.
Independent contract/source reviewers use separate NEW leaves/private checkouts.
User AGENTS.md, inherited untracked work, other worktrees and all sealed evidence
are excluded. Check status/worktrees/active agents before writes and HEAD moves.

## Verification and non-goals

Publish task/provenance/plan/log before authoring; independently inspect pinned
native hooks and all proposed behavior/data, then adopt only reviewed scope.
Commit the candidate before executing or independently reviewing its actual diff.
Cover tagged/non-tagged speed, actual diamond-qualified loot, single-target
survival and creative wear, canceled/station-denied breaks, negative-hardness
native admission, finite breakage, tag-based/native anvil repair, ordinary
enchantments/data preservation, disabled native mining/melee and re-enable.
Attribute any missing above-tier/mod interaction test rather than invent proof.

Run Java 17 forced uncached clean build and explicit test, twice runData followed
by separate empty git diff --exit-code, unfiltered runGameTestServer, ledger,
provenance, strict repository and whitespace checks. Inspect generated JSON,
PNG/originality and artifacts. Short packaged save/restart continuation remains
required; real-client V1/V2 is a distinct obligation, not replaced by embedded
actors or static art checks. Preserve actual argv/times/exits/XML and every
failure/error, not just task scheduling or cached results.

Check C/D free space >=10 GiB before sustained work; owned D TEMP/TMP and Java
tmp only. Remove only owned ended disposable outputs after absolute containment,
reparse and process checks, using one native PowerShell attempt. No denied-target
retry, shell/policy bypass, other-agent cleanup or input-world deletion. Retain
compact relative manifests; <=50 MiB/file and <=100 MiB/slice evidence.

Non-goals: arc furnace/steel progression, other tools/gear/modules, hatch/writer,
O1/O2/O3, oxygen or charge transactions, sleep/respawn/dimension policies, packets,
public API, ledger delivery, asset approval, tag/release approval or any G0-G9.
Acceptance stays v1.0 under ADR-060; v1.8 stays IN_PROGRESS / IMPLEMENTING.
