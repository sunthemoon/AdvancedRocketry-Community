package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.PrecisionAssemblerMigrationSaveFailureGameTests;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real saved-binding cuts in an Energy-only chunk, using disposable test worlds. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PrecisionAssemblerMigrationBindingGameTests {
    private static final TicketType<ChunkPos> FIXTURE_TICKET = TicketType.create(
            "arce_precision_binding_test", Comparator.comparingLong(ChunkPos::toLong), 60);
    private static final long GENERATION = 2L;

    private PrecisionAssemblerMigrationBindingGameTests() {
    }

    @GameTest(template = "empty", batch = "precision_energy_binding_retry", timeoutTicks = 40)
    public static void energyOnlyChunkBindingRetriesBeforeActivation(GameTestHelper helper) {
        exerciseSavedBinding(helper, 1);
    }

    @GameTest(template = "empty", batch = "precision_energy_binding_gate", timeoutTicks = 40)
    public static void unsavedEnergyBindingBlocksActivationUntilExplicitRetry(GameTestHelper helper) {
        exerciseSavedBinding(helper, 2);
    }

    private static void exerciseSavedBinding(GameTestHelper helper, int failures) {
        ServerLevel level = helper.getLevel();
        BlockPos template = helper.absolutePos(BlockPos.ZERO);
        BlockPos position = new BlockPos(640_000 + (template.getX() & ~15) + 15,
                template.getY() + 80, 640_000 + (template.getZ() & ~15) + 13);
        ChunkPos origin = new ChunkPos(position);
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                ChunkPos chunk = new ChunkPos(origin.x + x, origin.z + z);
                level.getChunkSource().addRegionTicket(FIXTURE_TICKET, chunk, 2, chunk);
            }
        }
        List<BlockPos> items = Stream.concat(PrecisionAssemblerPortLayout.INPUT_CELLS.stream(),
                PrecisionAssemblerPortLayout.OUTPUT_CELLS.stream())
                .map(cell -> worldPosition(position, cell)).toList();
        BlockPos energyPosition = worldPosition(position, PrecisionAssemblerPortLayout.ENERGY_CELL);
        ChunkPos energyChunk = new ChunkPos(energyPosition);
        helper.assertTrue(!energyChunk.equals(origin)
                        && items.stream().noneMatch(item -> new ChunkPos(item).equals(energyChunk)),
                "Energy binding fixture must have no controller or Item ports in its chunk");
        PrecisionAssemblerMigrationSaveFailureGameTests.placeStructure(level, position);
        level.setBlockAndUpdate(position.north(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAtTickTime(8, () -> {
            var controller = (PrecisionAssemblerBlockEntity) level.getBlockEntity(position);
            helper.assertTrue(controller != null && controller.formationState() == MultiblockFormationState.FORMED,
                    "Energy binding fixture did not form");
            var energy = (PrecisionAssemblerPortBlockEntity) level.getBlockEntity(energyPosition);
            CompoundTag prepared = controller.saveWithFullMetadata();
            prepared.getCompound("arce_multiblock").putLong("generation", GENERATION);
            ListTag inventory = new ListTag();
            for (int index = 0; index < items.size(); index++) {
                ItemStack stack = index == 0 ? new ItemStack(Items.IRON_INGOT, 2)
                        : index == 1 ? new ItemStack(Items.REDSTONE, 2) : ItemStack.EMPTY;
                CompoundTag item = stack.isEmpty() ? new CompoundTag() : stack.save(new CompoundTag());
                inventory.add(item.copy());
                var port = (PrecisionAssemblerPortBlockEntity) level.getBlockEntity(items.get(index));
                CompoundTag saved = port.saveWithFullMetadata();
                saved.getCompound("arce_part_binding").putLong("generation", GENERATION);
                saved.getCompound("arce_precision_port").put("item", item);
                port.load(saved);
                port.setChanged();
            }
            CompoundTag resources = prepared.getCompound("arce_precision_resources");
            resources.putString("phase", "preparing");
            resources.put("items", inventory);
            controller.load(prepared);
            controller.setChanged();
            CompoundTag oldEnergy = energy.saveWithFullMetadata();
            oldEnergy.getCompound("arce_part_binding").putLong("generation", GENERATION - 1);
            oldEnergy.getCompound("arce_precision_port").putInt("energy", 777);
            energy.load(oldEnergy);
            energy.setChanged();
            level.getChunkSource().save(true);
            helper.assertTrue(savedEntity(level, energyPosition).equals(energy.saveWithFullMetadata()),
                    "Initial older Energy binding was not actually saved");

            BindingSaveFailure fault = new BindingSaveFailure(level, energyPosition, failures);
            PrecisionAssemblerManager manager = PrecisionAssemblerMigrationSaveFailureGameTests.fixtureManager(level);
            manager.observeController(level, controller);
            MinecraftForge.EVENT_BUS.addListener(fault);
            try {
                // Synchronous real-manager work prevents a later world tick from
                // hiding the cleared dirty flag or repairing the stale disk root.
                manager.tick(level.getServer());
                helper.assertTrue(fault.fired >= 1, "Energy binding save fault was not exercised");
                if (failures == 2) {
                    helper.assertTrue(fault.fired == 2, "Energy binding was not retried at the second barrier");
                    helper.assertTrue("preparing".equals(controller.saveWithFullMetadata()
                                    .getCompound("arce_precision_resources").getString("phase")),
                            "An unsaved Energy binding activated migration");
                    helper.assertTrue(!((PrecisionAssemblerPortBlockEntity) level.getBlockEntity(items.get(0)))
                                    .getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                            "Unconfirmed migration exposed Item automation");
                    helper.assertTrue(savedEntity(level, energyPosition).getCompound("arce_part_binding")
                                    .getLong("generation") == GENERATION - 1,
                            "Both save failures did not retain the old Energy disk binding");
                    MinecraftForge.EVENT_BUS.unregister(fault);
                    manager.markProcessReady(level, position);
                    manager.tick(level.getServer());
                }
                helper.assertTrue("active".equals(controller.saveWithFullMetadata()
                                .getCompound("arce_precision_resources").getString("phase")),
                        "Confirmed Energy binding did not activate migration");
                helper.assertTrue(savedEntity(level, energyPosition).getCompound("arce_part_binding")
                                .getLong("generation") == GENERATION,
                        "Migration activated with an older Energy binding still on disk");
                helper.assertTrue(fault.fired == failures, "Fixture did not reach its exact failure count");
                MinecraftForge.EVENT_BUS.unregister(fault);
                level.getChunkSource().save(true);
                CompoundTag savedController = savedEntity(level, position);
                CompoundTag savedEnergy = savedEntity(level, energyPosition);
                controller.load(savedController.copy());
                energy.load(savedEnergy.copy());
                helper.assertTrue(savedController.equals(controller.saveWithFullMetadata())
                                && savedEnergy.equals(energy.saveWithFullMetadata()),
                        "Saved controller/Energy roots changed during reload");
                manager.clear();
                manager.observeController(level, controller);
                manager.tick(level.getServer());
                helper.assertTrue(controller.formationState() == MultiblockFormationState.FORMED
                                && energy.multiblockBinding().orElseThrow().generation() == GENERATION,
                        "Reloaded Energy binding stranded the migrated structure");
                helper.assertTrue(controller.saveWithFullMetadata().getCompound("arce_precision_resources")
                                .getList("items", Tag.TAG_COMPOUND).equals(inventory)
                                && energy.storedEnergy() == 777,
                        "Binding recovery changed central Items or physical Energy");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(fault);
                manager.clear();
            }
            helper.succeed();
        });
    }

    private static BlockPos worldPosition(BlockPos controller, PatternPosition cell) {
        return controller.offset(cell.x() - 1, cell.y(), cell.z());
    }

    private static CompoundTag savedEntity(ServerLevel level, BlockPos position) {
        Path world = level.getServer().getWorldPath(LevelResource.ROOT);
        Path region = DimensionType.getStorageFolder(level.dimension(), world).resolve("region");
        try {
            CompoundTag saved = PrecisionAssemblerPersistedChunkReader.readBlockEntity(region, position)
                    .orElseThrow();
            if (saved.getBoolean("keepPacked")) {
                throw new IllegalStateException("Fixture BlockEntity must be fully serialized");
            }
            // ChunkSerializer adds this envelope flag; it is not emitted by
            // BlockEntity.saveWithFullMetadata. Compare all actual BE fields.
            saved.remove("keepPacked");
            return saved;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read saved Energy binding fixture", exception);
        }
    }

    private static final class BindingSaveFailure implements Consumer<ChunkDataEvent.Save> {
        private final ServerLevel level;
        private final BlockPos position;
        private final int limit;
        private int fired;

        private BindingSaveFailure(ServerLevel level, BlockPos position, int limit) {
            this.level = level;
            this.position = position;
            this.limit = limit;
        }

        @Override
        public void accept(ChunkDataEvent.Save event) {
            if (fired >= limit || event.getLevel() != level
                    || !event.getChunk().getPos().equals(new ChunkPos(position))) {
                return;
            }
            for (Tag entry : event.getData().getList("block_entities", Tag.TAG_COMPOUND)) {
                CompoundTag entity = (CompoundTag) entry;
                if (entity.getInt("x") == position.getX() && entity.getInt("y") == position.getY()
                        && entity.getInt("z") == position.getZ()
                        && entity.getCompound("arce_part_binding").getLong("generation") == GENERATION) {
                    fired++;
                    throw new IllegalStateException("Intentional Energy-only migration binding save failure");
                }
            }
        }
    }
}
