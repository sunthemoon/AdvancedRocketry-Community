# C18a-AIRLOCK-01: powered two-block airlock door

Date: 2026-10-07. Author/integrator: Root. Status: PROPOSED; no implementation
or Gate acceptance. Base source: `a7a3cf03388165f900f4b3804420b003f6d3ec2e`.
Parent: accepted ADR-066 revision 3, section 3.2. The user's conditional
authorization requires a different-agent review without unresolved
Critical/High/Medium before this technical leaf is adopted. Major semantics,
native interception, hatch writers and risk exceptions are not selected here.

## Observable outcome and scope

Register `advancedrocketrycommunity:airlock_door` as one DoorBlock-derived
block and its ordinary BlockItem. It is an iron-type, two-block powered door:
vanilla placement, support, hinge/facing, redstone, interaction and removal
remain authoritative. No automatic cycling, machine inventory, BlockEntity,
custom NBT, packet, ownership or native/bytecode interception is introduced.
The existing COMMON device switch does not erase or open a passive door.
This is an additive new ID with no alias or replacement of existing doors;
existing saves are unchanged. Vanilla BlockState/BlockItem persistence is used,
not a new owned save format or direct 1.12.2 conversion. Removing the content
after use requires the ordinary missing-content handling, not silent remapping.

Modern leaf balance: grey metal, strength 5, correct pickaxe drops, no occlusion
and vanilla door push/removal behavior. Six exact vanilla iron ingots, in
`II/II/II`, craft three untagged doors; no unavailable titanium or C16 machine
is required. The ordinary recipe book unlock uses iron ingot OR recipe-known,
and grants only that recipe. Ordinary lower-half loot yields one door with
explosion decay; upper-half loot yields none. Creative removal must not duplicate
the item. These are new modern choices, not an attribution to owner-selected
numbers or imported legacy recipe bodies.

## Seal and invalidation contract

The loaded-world boundary adapter recognizes this registered door before
generic sealing/permeable tags or state-only external boundaries. It reads at
most the local cell and the vertically adjacent counterpart, with loaded/build
height checks before the counterpart read and no chunk request or ticket.
Both halves seal only if they are the same airlock block, have opposite HALF,
matching FACING/HINGE/POWERED and both OPEN=false. A missing, wrong or mismatched
counterpart is OPEN; an unavailable counterpart is UNLOADED. A valid open pair
is TRAVERSABLE. Other vanilla/modded doors keep their existing classification.

Airlock mutation callbacks revoke existing indexed supplied air synchronously
through the existing AtmosphereManager.markDirty mechanism, before a powered
or explicit open transition/removal can leave old-scan air authoritative.
Placement and neighbour/support changes also invalidate the affected halves.
Callbacks delegate normal door behavior once, do not supply air or run a scan,
and preserve bounded manager queues and its existing lifecycle. Simulation or
read-only boundary queries never mutate the manager. Exact callback signatures
were checked against the pinned Forge baseline in the Root primary inspection
below; this establishes signatures, not a runtime pass.

The new block extends DoorBlock using BlockSetType.IRON. Before its ordinary
`setOpen(Entity, Level, BlockState, BlockPos, boolean)` delegate, invalidate only
when the supplied airlock state changes OPEN. Before each ordinary
`neighborChanged(BlockState, Level, BlockPos, Block, BlockPos, boolean)`,
`onPlace(BlockState, Level, BlockPos, BlockState, boolean)`,
`onRemove(BlockState, Level, BlockPos, BlockState, boolean)` and
`playerWillDestroy(Level, BlockPos, BlockState, Player)` delegate, invalidate
the local affected cell. Its manager already includes all six neighbours,
including the counterpart. Delegate once without swallowing exceptions.
Do not override the query-like updateShape, placement-state or collision methods
to mutate the manager. Invalidation is logical-server/main-thread only; the
runtime bridge stores no new owner/cache and uses the installed lifecycle-bound
manager. Tests must exercise these actual registered callbacks and native shape
changes, not substitute direct markDirty calls for them.

Root inspection `cf85e5`, Python -B, exit 0, stream-hashed mapped Forge JAR
`95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e`.
Exactly three named members were bounded to 512 KiB each, in memory only:
DoorBlock (18,941 bytes, `98200abbfca09a1149ec33dd2ec5b9e1ce3f831eeffb82ce46c1122a323337ee`),
BlockBehaviour (24,017 bytes, `33a73cccc684a0d41bb0f075c13b2cad5721b3d9e6402c85f3e965d83890a288`)
and BlockBehaviour$Properties (17,949 bytes, `7b379ccb00fa99762c6961f9e94d2258c994c12c59a46aa4789ee5851ed3eb52`).
The constructor and the five delegate signatures above are public native
methods; no Code body, archive member enumeration, class export or JVM ran.
Evidence is the original tool output, not a separately exported command log.
This read did not set a new child TEMP directory and made no output/script file;
future checks retain the task-local D TEMP rule. A web documentation probe
failed; unpinned third-party search results are not a source for this contract.

## Implementation ownership and verification

Root alone owns existing registration, creative tab, loaded-world adapter,
runtime bridge, DataGen hook/language/tag additions, generated v1.8 output,
provenance and status/log files. A separately assigned worker may own only new
`atmosphere/content/AirlockDoorBlock.java`, `datagen/V180AirlockData.java`,
`gametest/AirlockDoorGameTests.java`, related new JUnit tests and this task's
source progress record, in a dedicated worktree. No write assignment exists yet.
New art/model/data declarations precede authoring; use independently drawn
community art and refer to native door templates by ID, never copy their bodies.

Verify actual registration/recipe and nonmutation, placement/redstone/support,
both-half seal/mismatch/unavailable cases, synchronous supplied-air revocation
and bounded recovery without direct test markDirty, ordinary survival/creative
removal/drop counts, loot explosion behavior and BlockState roundtrip. Add
deterministic resource/PNG/path and bilingual JUnit coverage. Independently
review the actual diff; run exact-source no-cache clean build, twice runData
with clean diff/status checks, full GameTests and package/client-boundary audits.
Local C is below 10 GB, so no local Java/Gradle/native execution. Scratch stays
under `D:/GitHub/ARCE-Task-Evidence/v1.8.0`, with child-local TEMP/TMP.
Native restart, real survival clients and V1/V2 remain unverified until executed.
Do not alter Tau deadlines/readiness, inherited tests, ledger or G0-G9 to qualify
this independent door. Required failures remain recorded as failures.
