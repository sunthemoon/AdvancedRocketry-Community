package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerManager;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real player/explosion entry points around interrupted legacy ownership. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PrecisionAssemblerRemovalGameTests {
    private static final BlockPos CASING = new BlockPos(2, 2, 1);

    private PrecisionAssemblerRemovalGameTests() {
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 30)
    public static void pendingMigrationRejectsSurvivalAndCreativeAtEveryMarkerCut(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            var beforeDrops = dropSnapshot(helper);
            prepare(helper);
            FakePlayer player = player(helper);
            for (int count = 0; count <= 7; count++) {
                markPorts(helper, count);
                List<CompoundTag> before = snapshots(helper);
                for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
                    player.setGameMode(mode);
                    for (BlockPos position : protectedPositions()) {
                        helper.assertTrue(!player.gameMode.destroyBlock(helper.absolutePos(position)),
                                "Player removed a pending block at marker cut " + count + " in " + mode);
                    }
                }
                helper.assertTrue(before.equals(snapshots(helper)), "Refused player removal changed saved data");
            }
            assertNoDrops(helper, beforeDrops);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 30)
    public static void pendingMigrationRejectsExplosionLootAndEntityDestruction(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            var beforeDrops = dropSnapshot(helper);
            prepare(helper);
            WitherBoss wither = new WitherBoss(EntityType.WITHER, helper.getLevel());
            for (int count = 0; count <= 7; count++) {
                markPorts(helper, count);
                List<CompoundTag> before = snapshots(helper);
                for (BlockPos position : protectedPositions()) {
                    BlockPos absolute = helper.absolutePos(position);
                    helper.assertTrue(!helper.getBlockState(position).canEntityDestroy(
                            helper.getLevel(), absolute, wither), "Pending block allowed entity destruction");
                }
                explode(helper, protectedPositions());
                helper.assertTrue(before.equals(snapshots(helper)), "Explosion changed pending saved data");
                assertNoDrops(helper, beforeDrops);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 45)
    public static void damagedPendingStructureRetainsBindingsThenMigratesAndDropsOnce(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            prepare(helper);
            markPorts(helper, 3);
            helper.setBlock(CASING, Blocks.AIR);
            PrecisionAssemblerRuntime.markDirty(helper.getLevel(), helper.absolutePos(CASING));
        });
        helper.runAtTickTime(14, () -> {
            assertRetainedBindings(helper);
            PrecisionAssemblerBlockEntity controller = PrecisionAssemblerGameTests.controller(helper);
            CompoundTag saved = controller.saveWithFullMetadata();
            controller.load(saved.copy());
            helper.assertTrue(saved.equals(controller.saveWithFullMetadata()),
                    "Retained migration ownership failed NBT round-trip");
            FakePlayer player = player(helper);
            for (BlockPos position : protectedPositions()) {
                helper.assertTrue(!player.gameMode.destroyBlock(helper.absolutePos(position)),
                        "Damaged pending structure allowed removal");
            }
            helper.setBlock(CASING, ModBlocks.MACHINE_CASING.get());
            PrecisionAssemblerRuntime.markDirty(helper.getLevel(), helper.absolutePos(CASING));
        });
        helper.runAtTickTime(28, () -> {
            assertActive(helper);
            var beforeDrops = dropSnapshot(helper);
            FakePlayer player = player(helper);
            for (BlockPos position : protectedPositions()) {
                helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(position)),
                        "Completed migration or its inert leftover port remained locked");
            }
            assertResourceDrops(helper, beforeDrops);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 40)
    public static void missingPatternRetainsPendingBindingsUntilDefinitionReturns(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            prepare(helper);
            markPorts(helper, 2);
            PrecisionAssemblerManager withoutDefinition = new PrecisionAssemblerManager(
                    new MultiblockPatternCatalogManager());
            try {
                withoutDefinition.observeController(helper.getLevel(), PrecisionAssemblerGameTests.controller(helper));
                withoutDefinition.tick(helper.getLevel().getServer());
                assertRetainedBindings(helper);
            } finally {
                withoutDefinition.clear();
            }
            PrecisionAssemblerRuntime.markDirty(helper.getLevel(),
                    helper.absolutePos(PrecisionAssemblerGameTests.CONTROLLER));
        });
        helper.runAtTickTime(24, () -> {
            assertActive(helper);
            var beforeDrops = dropSnapshot(helper);
            BlockPos controller = helper.absolutePos(PrecisionAssemblerGameTests.CONTROLLER);
            helper.assertTrue(helper.getBlockState(PrecisionAssemblerGameTests.CONTROLLER).canEntityDestroy(
                    helper.getLevel(), controller, new WitherBoss(EntityType.WITHER, helper.getLevel())),
                    "Active controller kept its entity-destruction restriction");
            explode(helper, protectedPositions());
            var added = assertResourceDrops(helper, beforeDrops);
            helper.assertTrue(added.values().stream().map(ItemStack::of)
                    .filter(stack -> stack.is(ModBlocks.PRECISION_ASSEMBLER.get().asItem()))
                    .mapToInt(ItemStack::getCount).sum() == 1, "Active explosion lost or duplicated its controller block");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 30)
    public static void unavailableOwnerRejectsPortRemovalWithoutLoadingItsChunk(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            var beforeDrops = dropSnapshot(helper);
            var port = PrecisionAssemblerGameTests.port(helper, PrecisionAssemblerGameTests.INPUT_0);
            CompoundTag original = port.saveWithFullMetadata();
            BlockPos unavailable = new BlockPos(20_000_000, 64, 20_000_000);
            helper.assertTrue(!helper.getLevel().hasChunkAt(unavailable), "Owner fixture chunk was already loaded");
            CompoundTag changed = original.copy();
            CompoundTag position = changed.getCompound("arce_part_binding").getCompound("controller");
            position.putInt("x", unavailable.getX());
            position.putInt("y", unavailable.getY());
            position.putInt("z", unavailable.getZ());
            port.load(changed);
            try {
                helper.assertTrue(!player(helper).gameMode.destroyBlock(
                        helper.absolutePos(PrecisionAssemblerGameTests.INPUT_0)), "Unloaded owner allowed removal");
                explode(helper, List.of(PrecisionAssemblerGameTests.INPUT_0));
                helper.assertTrue(changed.equals(port.saveWithFullMetadata()), "Unloaded-owner guard changed port data");
                helper.assertTrue(!helper.getLevel().hasChunkAt(unavailable), "Removal forced the owner chunk to load");
                assertNoDrops(helper, beforeDrops);
            } finally {
                port.load(original);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 30)
    public static void unmigratedLegacyControllerAndPortsRemainRemovable(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            var beforeDrops = dropSnapshot(helper);
            PrecisionAssemblerGameTests.insertInputs(helper);
            PrecisionAssemblerMigrationGameTests.stageLegacySave(helper, false);
            FakePlayer player = player(helper);
            for (BlockPos position : protectedPositions()) {
                helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(position)),
                        "Untouched legacy block was unnecessarily locked");
            }
            assertResourceDrops(helper, beforeDrops);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 30)
    public static void futureResourcesRemainIntactWhenRemovalIsRequested(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            var beforeDrops = dropSnapshot(helper);
            PrecisionAssemblerGameTests.insertInputs(helper);
            var controller = PrecisionAssemblerGameTests.controller(helper);
            CompoundTag saved = controller.saveWithFullMetadata();
            saved.getCompound("arce_precision_resources").putInt("schema_version", 2);
            controller.load(saved);
            List<CompoundTag> before = snapshots(helper);
            for (BlockPos position : protectedPositions()) {
                helper.assertTrue(!player(helper).gameMode.destroyBlock(helper.absolutePos(position)),
                        "Future ownership data allowed removal");
            }
            explode(helper, protectedPositions());
            helper.assertTrue(before.equals(snapshots(helper)), "Future ownership data was changed by removal");
            assertNoDrops(helper, beforeDrops);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 30)
    public static void futureControllerIdentityAlsoProtectsItsOldBoundPorts(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            var beforeDrops = dropSnapshot(helper);
            prepare(helper);
            var controller = PrecisionAssemblerGameTests.controller(helper);
            CompoundTag saved = controller.saveWithFullMetadata();
            saved.getCompound("arce_multiblock").putInt("schema_version", 2);
            controller.load(saved);
            List<CompoundTag> before = snapshots(helper);
            FakePlayer player = player(helper);
            for (BlockPos position : protectedPositions()) {
                helper.assertTrue(!player.gameMode.destroyBlock(helper.absolutePos(position)),
                        "Future controller identity allowed removal of its original ports");
            }
            explode(helper, protectedPositions());
            helper.assertTrue(before.equals(snapshots(helper)), "Future identity protection changed saved roots");
            assertNoDrops(helper, beforeDrops);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 40)
    public static void preparedSnapshotRepairsMissingAndOlderSameOwnerBindings(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            var beforeDrops = dropSnapshot(helper);
            prepare(helper);
            markPorts(helper, 1);
            var controller = PrecisionAssemblerGameTests.controller(helper);
            CompoundTag saved = controller.saveWithFullMetadata();
            saved.getCompound("arce_multiblock").putLong("generation", controller.generation() + 1);
            controller.load(saved);
            controller.setChanged();
            for (int index : List.of(0, 1)) {
                var port = PrecisionAssemblerGameTests.port(helper, PrecisionAssemblerMigrationGameTests.ITEM_PORTS[index]);
                CompoundTag portSaved = port.saveWithFullMetadata();
                portSaved.remove("arce_part_binding");
                port.load(portSaved);
                port.setChanged();
            }
            FakePlayer player = player(helper);
            for (int index : List.of(0, 1)) {
                BlockPos position = PrecisionAssemblerMigrationGameTests.ITEM_PORTS[index];
                helper.assertTrue(!player.gameMode.destroyBlock(helper.absolutePos(position)),
                        "Port with a missing saved binding was removable before owner recovery");
                explode(helper, List.of(position));
                helper.assertTrue(helper.getBlockEntity(position) != null,
                        "Explosion removed an unbound pending legacy port");
            }
            assertNoDrops(helper, beforeDrops);
            PrecisionAssemblerRuntime.markDirty(helper.getLevel(),
                    helper.absolutePos(PrecisionAssemblerGameTests.CONTROLLER));
        });
        helper.runAtTickTime(24, () -> {
            assertActive(helper);
            var controller = PrecisionAssemblerGameTests.controller(helper);
            helper.assertTrue(controller.generation() == 2, "Binding repair advanced the prepared generation");
            for (BlockPos position : protectedPositions().subList(1, 9)) {
                var binding = PrecisionAssemblerGameTests.port(helper, position).multiblockBinding().orElseThrow();
                helper.assertTrue(binding.generation() == 2
                        && binding.machineInstanceId().equals(controller.controllerState().machineInstanceId()),
                        "Saved binding cut did not recover the exact prepared owner");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 30)
    public static void bindingRepairRejectsForeignNewerAndMismatchedPortSnapshots(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            prepare(helper);
            markPorts(helper, 3);
            List<CompoundTag> baseline = snapshots(helper);
            for (int fault = 0; fault < 4; fault++) {
                List<BlockPos> positions = protectedPositions();
                for (int index = 0; index < positions.size(); index++) {
                    helper.getBlockEntity(positions.get(index)).load(baseline.get(index).copy());
                }
                var first = PrecisionAssemblerGameTests.port(helper, PrecisionAssemblerGameTests.INPUT_0);
                CompoundTag missing = first.saveWithFullMetadata();
                missing.remove("arce_part_binding");
                first.load(missing);
                var second = PrecisionAssemblerGameTests.port(helper, PrecisionAssemblerGameTests.INPUT_1);
                CompoundTag conflicting = second.saveWithFullMetadata();
                switch (fault) {
                    case 0 -> conflicting.getCompound("arce_part_binding")
                            .putString("machine_instance_id", UUID.randomUUID().toString());
                    case 1 -> conflicting.getCompound("arce_part_binding").putLong("generation", 2);
                    case 2 -> conflicting.getCompound("arce_precision_port").put("item",
                            new net.minecraft.world.item.ItemStack(Items.GOLD_INGOT).save(new CompoundTag()));
                    case 3 -> conflicting.getCompound("arce_precision_port_migration")
                            .putString("machine_id", UUID.randomUUID().toString());
                    default -> throw new AssertionError("Unknown fixture fault");
                }
                second.load(conflicting);
                List<CompoundTag> before = snapshots(helper);
                PrecisionAssemblerManager manager = PrecisionAssemblerMigrationSaveFailureGameTests.fixtureManager(helper.getLevel());
                try {
                    manager.observeController(helper.getLevel(), PrecisionAssemblerGameTests.controller(helper));
                    manager.tick(helper.getLevel().getServer());
                    helper.assertTrue(before.subList(1, 9).equals(snapshots(helper).subList(1, 9)),
                            "Conflicting preflight partly repaired or overwrote port data");
                    helper.assertTrue(before.get(0).get("arce_precision_resources").equals(
                            snapshots(helper).get(0).get("arce_precision_resources")),
                            "Conflicting binding repair changed prepared Items");
                    helper.assertTrue(first.multiblockBinding().isEmpty(), "Failed preflight changed an earlier binding");
                } finally {
                    manager.clear();
                }
            }
            helper.succeed();
        });
    }

    private static void prepare(GameTestHelper helper) {
        PrecisionAssemblerGameTests.insertInputs(helper);
        PrecisionAssemblerMigrationGameTests.stageLegacySave(helper, true);
    }

    private static void markPorts(GameTestHelper helper, int count) {
        String machine = PrecisionAssemblerGameTests.controller(helper).controllerState().machineInstanceId().toString();
        for (int index = 0; index < count; index++) {
            var port = PrecisionAssemblerGameTests.port(helper, PrecisionAssemblerMigrationGameTests.ITEM_PORTS[index]);
            CompoundTag saved = port.saveWithFullMetadata();
            CompoundTag marker = new CompoundTag();
            marker.putInt("schema_version", 1);
            marker.putString("machine_id", machine);
            marker.putString("channel", index < 5 ? "item_input_" + index : "item_output_" + (index - 5));
            saved.put("arce_precision_port_migration", marker);
            port.load(saved);
            port.setChanged();
        }
    }

    private static List<BlockPos> protectedPositions() {
        List<BlockPos> positions = new ArrayList<>();
        positions.add(PrecisionAssemblerGameTests.CONTROLLER);
        positions.addAll(List.of(PrecisionAssemblerMigrationGameTests.ITEM_PORTS));
        positions.add(PrecisionAssemblerGameTests.ENERGY);
        return List.copyOf(positions);
    }

    private static List<CompoundTag> snapshots(GameTestHelper helper) {
        return protectedPositions().stream().map(pos -> helper.getBlockEntity(pos).saveWithFullMetadata()).toList();
    }

    private static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PrecisionRemoval"));
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static void explode(GameTestHelper helper, List<BlockPos> positions) {
        BlockPos origin = helper.absolutePos(PrecisionAssemblerGameTests.CONTROLLER);
        Explosion explosion = new Explosion(helper.getLevel(), null,
                origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5,
                1.0F, false, Explosion.BlockInteraction.DESTROY,
                positions.stream().map(helper::absolutePos).toList());
        explosion.finalizeExplosion(false);
    }

    private static void assertRetainedBindings(GameTestHelper helper) {
        var controller = PrecisionAssemblerGameTests.controller(helper);
        helper.assertTrue(controller.formationState() == MultiblockFormationState.BINDING_CONFLICT,
                "Pending structural invalidation did not retain a schema-valid ownership state");
        helper.assertTrue(controller.controllerState().partPositions().size() == 8, "Pending part positions were lost");
        for (BlockPos position : protectedPositions().subList(1, 9)) {
            var port = PrecisionAssemblerGameTests.port(helper, position);
            var binding = port.multiblockBinding().orElseThrow();
            helper.assertTrue(binding.machineInstanceId().equals(controller.controllerState().machineInstanceId())
                    && binding.generation() == controller.generation(), "Pending port lost its original owner");
            helper.assertTrue(!port.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                    "Invalid pending structure exposed Items");
        }
    }

    private static void assertActive(GameTestHelper helper) {
        var controller = PrecisionAssemblerGameTests.controller(helper);
        helper.assertTrue("active".equals(controller.saveWithFullMetadata()
                .getCompound("arce_precision_resources").getString("phase")), "Repaired migration did not activate");
        PrecisionAssemblerGameTests.assertInputs(helper, 2, 2);
    }

    private static Map<UUID, CompoundTag> dropSnapshot(GameTestHelper helper) {
        Map<UUID, CompoundTag> snapshot = new LinkedHashMap<>();
        for (ItemEntity entity : drops(helper)) {
            snapshot.put(entity.getUUID(), entity.getItem().save(new CompoundTag()));
        }
        return snapshot;
    }

    private static void assertNoDrops(GameTestHelper helper, Map<UUID, CompoundTag> before) {
        // Test templates share an entity region. Compare the complete Item
        // entity/stack ledger around the synchronous action, not world emptiness.
        helper.assertTrue(before.equals(dropSnapshot(helper)),
                "Protected removal spawned or changed resource/block-item drops");
        if (!before.isEmpty()) {
            AdvancedRocketryCommunity.LOGGER.info(
                    "Precision removal fixture retained {} pre-existing Item entities unchanged at {}",
                    before.size(), helper.absolutePos(PrecisionAssemblerGameTests.CONTROLLER));
        }
    }

    private static Map<UUID, CompoundTag> assertResourceDrops(GameTestHelper helper, Map<UUID, CompoundTag> before) {
        Map<UUID, CompoundTag> added = dropSnapshot(helper);
        before.forEach((id, stack) -> helper.assertTrue(stack.equals(added.remove(id)),
                "Successful removal changed a pre-existing Item entity"));
        helper.assertTrue(added.values().stream().map(ItemStack::of).filter(stack -> stack.is(Items.IRON_INGOT))
                .mapToInt(ItemStack::getCount).sum() == 2, "Removal lost or duplicated iron");
        helper.assertTrue(added.values().stream().map(ItemStack::of).filter(stack -> stack.is(Items.REDSTONE))
                .mapToInt(ItemStack::getCount).sum() == 2, "Removal lost or duplicated redstone");
        return added;
    }

    private static List<ItemEntity> drops(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(PrecisionAssemblerGameTests.CONTROLLER)).inflate(4.0));
    }
}
