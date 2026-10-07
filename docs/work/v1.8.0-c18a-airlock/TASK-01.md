# C18a-AIRLOCK-01: airlock source implementation

Date: 2026-10-07. Integrator/contract owner: Root. Version: v1.8.0.
Status: ready; implementation has not started. Required Gates remain open.

## Narrow technical adoption

Root adopts [LEAF-SPEC-02](LEAF-SPEC-02.md), SHA-256
`e8e25fbd65e240363b208073dd80e0be79b985855607a28a52cecd6fe88a002f`,
under accepted ADR-066 revision 3 section 3.2 and the user's conversation reply:

> 授权无未解决 Critical/High/Medium 的审核定稿继续实现；重大语义调整仍另行确认

This records the existing reply, not a new owner response on this date. Root's
modern recipe/material/config mapping is a scoped technical choice, not literal
owner-selected legacy numbers. No pending native interception, hatch writer,
oxygen/typed/first-event semantic change or risk exemption is accepted here.

The [original review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-airlock-contract-independent-review-20261007-01/REVIEW-01.md)
retains two Medium findings; the separate
[successor review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-airlock-contract-independent-review-20261007-01/REVIEW-02.md),
SHA-256 `fa85b2ed74d2f20d5a86a32b54574831e066a78d98143d2a8164cae6ece88135`,
reports them addressed at specification level with no new C/H/M. Root reads the
complete review (`2e4de9`, exit 0). The pre-assignment primary prerequisite is
provided by the separately scoped
[placement report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-airlock-placement-native-facts-20261007-01/REPORT-01.md),
SHA-256 `d2a83138f5e1bf969e26bbcd2d61776b9404b4d21c97b093d499adc588f12e2b`.
Root reads and hash-checks its final 7,845 bytes (`9c1573`, exit 0). It proves the
public placement-state signature and native BlockItem null-to-FAIL branch before
its write/callback/debit tail, not outer-use/foreign callback rollback or actual
server conservation. Those behavioral tests remain required.

## Outcome, interface binding and non-goals

An obtainable ordinary powered two-half airlock with paired fail-open observation
and synchronous invalidation of air beside both halves. Retain the exact leaf
recipe, vanilla semantics, disable mapping and all tests/deadlines.

Internal source bindings, not a new public extension API:

- `AirlockDoorBlock(Properties, BooleanSupplier)` extends DoorBlock using IRON.
- `observeBoundary(LevelReader, BlockPos, BlockState): CellObservation` is a
  bounded read-only instance query; the adapter calls it only for the exact
  registered airlock ID, before tags/providers, leaving other block rules intact.
- Mutation delegates call the Root-owned
  `AtmosphereRuntime.invalidateDoorBoundary(ServerLevel, BlockPos, BlockPos)`
  before native delegation, with local and state-designated counterpart.
  Root forwards both bounded marks only on the owning live server thread and
  loaded/build-height-valid cells, with no read repair, tickets or new cache.
  Stop cleanup must disconnect the installed runtime manager before it can
  recreate cleared authority; Root owns that bridge/central lifecycle change.
- `V180AirlockData` follows existing small DataProvider conventions, with owned
  client/server JSON, original grid PNGs and bilingual names. Original resource
  declaration is [pre-authoring provenance](../../provenance/v1.8.0-c18a-airlock-new-resources.md).

No BlockEntity, inventory, fluid/FE transaction, new schema/network/config key,
automatic two-door cycle, public boundary callback, native hook or other-door
priority change. No Tau fix or Gate acceptance is part of this source assignment.

## Ownership and isolated setup

Source worktree planned: `D:/GitHub/arce-v180-airlock-20261007`; branch planned:
`codex/v1.8.0-airlock-source`. Root creates it after this adoption/provenance
checkpoint is committed. The actual base SHA and setup receipt are recorded
before the worker starts; the existing Java basis is unchanged from tested 8b3.
No shared-checkout worker write permission is granted.

Worker owns only these NEW files in that isolated worktree:

1. `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/atmosphere/content/AirlockDoorBlock.java`
2. `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/datagen/V180AirlockData.java`
3. `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/AirlockDoorGameTests.java`
4. `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/atmosphere/content/AirlockDoorBlockTest.java`
5. `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/datagen/V180AirlockDataTest.java`
6. This task's `SOURCE-01.md`.

Root alone owns existing ModBlocks/ModItems/ModCreativeTabs, the loaded-world
adapter, AtmosphereRuntime, AdvancedRocketryCommunity lifecycle, BootstrapDataGenerators,
V180MaterialData and V180LanguageProvider; any central bridge tests, generated
v1.8 output, provenance, status/ledger/log, Git commits and normal pushes.
The worker must not modify those files or AGENTS.md, run Git writes, add native
facts/helpers or broaden scope. Source compilation depends on Root's bridge,
registration and resources; an isolated incomplete checkpoint is not delivery.

## Verification and handoff

Implement actual registered placement/redstone/support/removal and installed
atmosphere witnesses, not direct test markDirty substitutes. Include the original
opposite-half-only chamber counterexample and its mirror, cached/in-flight/
completed/backlog revocation before another manager tick, mismatch/unavailable
guards, disabled placement/resource counts and safe existing operation, ordinary
survival/creative/explosion drops and actual crafting. Unit/resource checks and
native save/restart/client requirements remain those of the accepted leaf.

Different-agent review of actual source precedes Root integration. Fresh hosted
no-cache clean build, twice runData with tracked/untracked cleanliness, complete
GameTests and artifact/client boundaries are required; no inherited assertion,
timeout or budget may be relaxed. Local C is below 10 GB: no local Java/JVM,
Gradle or native server/client. Worker uses only PowerShell/Git reads/Python -B,
apply_patch, and task-owned D TEMP/TMP/TMPDIR; no network/install/process cleanup.
Root records actual command exits and failed runs. All G0-G9, real player/GPU,
packaged restart and full v1.8 delivery remain open.
