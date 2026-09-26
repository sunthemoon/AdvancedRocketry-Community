package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternJsonCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerManager;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerPortBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerPortLayout;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.io.IOException;
import java.util.List;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Cross-chunk retry fixtures for a disposable GameTest world, one failure per save barrier. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PrecisionAssemblerMigrationSaveFailureGameTests {
    private static final TicketType<ChunkPos> FIXTURE_TICKET = TicketType.create(
            "arce_precision_migration_test", Comparator.comparingLong(ChunkPos::toLong), 60);

    private PrecisionAssemblerMigrationSaveFailureGameTests() {
    }

    @GameTest(template = "empty", batch = "precision_migration_controller_failure", timeoutTicks = 40)
    public static void controllerSaveFailureCanRetryWithoutUnrelatedChunkChanges(GameTestHelper helper) {
        exerciseRetry(helper, false);
    }

    @GameTest(template = "empty", batch = "precision_migration_port_failure", timeoutTicks = 40)
    public static void portMarkerSaveFailureCanRetryWithoutUnrelatedChunkChanges(GameTestHelper helper) {
        exerciseRetry(helper, true);
    }

    private static void exerciseRetry(GameTestHelper helper, boolean markerBarrier) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        // Keep both blocks and chunk dirty flags separate from the framework's
        // template/sign placement, which can overlap the following test batch.
        BlockPos controllerPosition = new BlockPos(
                512_000 + (origin.getX() & ~15) + 15, origin.getY() + (markerBarrier ? 96 : 80),
                512_000 + (origin.getZ() & ~15) + 15);
        ChunkPos controllerChunk = new ChunkPos(controllerPosition);
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                ChunkPos chunk = new ChunkPos(controllerChunk.x + x, controllerChunk.z + z);
                // A fixed four-chunk fixture; tickets expire even if this test fails.
                level.getChunkSource().addRegionTicket(FIXTURE_TICKET, chunk, 2, chunk);
            }
        }
        List<BlockPos> items = Stream.concat(PrecisionAssemblerPortLayout.INPUT_CELLS.stream(),
                PrecisionAssemblerPortLayout.OUTPUT_CELLS.stream())
                .map(cell -> worldPosition(controllerPosition, cell)).toList();
        BlockPos failurePosition = markerBarrier ? items.get(1) : controllerPosition;
        helper.assertTrue(!new ChunkPos(controllerPosition).equals(new ChunkPos(items.get(1))),
                "Save-failure fixture must put the marked input in a different chunk");
        placeStructure(level, controllerPosition);
        OneShotSaveFailure fault = new OneShotSaveFailure(level, failurePosition, markerBarrier);

        helper.runAtTickTime(8, () -> {
            PrecisionAssemblerBlockEntity controller = controller(level, controllerPosition);
            helper.assertTrue(controller.formationState() == MultiblockFormationState.FORMED,
                    "Cross-chunk save-failure fixture did not form");
            for (int index = 0; index < items.size(); index++) {
                PrecisionAssemblerPortBlockEntity port = port(level, items.get(index));
                CompoundTag saved = port.saveWithFullMetadata();
                ItemStack stack = index == 0 ? new ItemStack(Items.IRON_INGOT, 2)
                        : index == 1 ? new ItemStack(Items.REDSTONE, 2) : ItemStack.EMPTY;
                saved.getCompound("arce_precision_port").put("item",
                        stack.isEmpty() ? new CompoundTag() : stack.save(new CompoundTag()));
                port.load(saved);
                port.setChanged();
            }
            CompoundTag saved = controller.saveWithFullMetadata();
            saved.remove("arce_precision_resources");
            controller.load(saved);
            controller.setChanged();
            level.getChunkSource().save(true);
            PrecisionAssemblerManager manager = fixtureManager(level);
            manager.observeController(level, controller);
            MinecraftForge.EVENT_BUS.addListener(fault);
            try {
                // Drive the real manager synchronously so another test's tick/save
                // cannot repair the failed chunk before the explicit retry.
                manager.tick(level.getServer());
                helper.assertTrue(fault.fired, "The one-shot migration save failure was not exercised");
                helper.assertTrue(fault.savesAfterFailure == 0,
                        "An unrelated save repaired the target chunk before explicit retry");
                helper.assertTrue("preparing".equals(controller.saveWithFullMetadata()
                                .getCompound("arce_precision_resources").getString("phase")),
                        "A failed save activated migrated Item resources");
                helper.assertTrue(!level.getChunkAt(failurePosition).isUnsaved(),
                        "Retry fixture must retain the save failure's cleared dirty flag");
                helper.assertTrue(!port(level, items.get(0)).getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                        "A failed save exposed Item automation");
                MinecraftForge.EVENT_BUS.unregister(fault);
                manager.markProcessReady(level, controllerPosition);
                manager.tick(level.getServer());
                CompoundTag resources = controller.saveWithFullMetadata().getCompound("arce_precision_resources");
                helper.assertTrue("active".equals(resources.getString("phase")),
                        "Explicit retry did not recover after the transient save failure");
                for (int index = 0; index < items.size(); index++) {
                    var handler = port(level, items.get(index)).getCapability(ForgeCapabilities.ITEM_HANDLER)
                            .resolve().orElseThrow();
                    ItemStack actual = handler.getStackInSlot(0);
                    ItemStack expected = index == 0 ? new ItemStack(Items.IRON_INGOT, 2)
                            : index == 1 ? new ItemStack(Items.REDSTONE, 2) : ItemStack.EMPTY;
                    helper.assertTrue(ItemStack.matches(expected, actual),
                            "Save-failure retry changed Item slot " + index);
                    helper.assertTrue(port(level, items.get(index)).saveWithFullMetadata()
                                    .contains("arce_precision_port_migration", Tag.TAG_COMPOUND),
                            "Save-failure retry lost port marker " + index);
                }
            } finally {
                MinecraftForge.EVENT_BUS.unregister(fault);
                manager.clear();
            }
            helper.succeed();
        });
    }

    public static PrecisionAssemblerManager fixtureManager(ServerLevel level) {
        ResourceLocation resource = new ResourceLocation(AdvancedRocketryCommunity.MOD_ID,
                "machine_patterns/precision_assembler.json");
        try (var input = level.getServer().getResourceManager().getResourceOrThrow(resource).open()) {
            var patterns = new MultiblockPatternCatalogManager();
            patterns.accept(MultiblockPatternCatalog.create(List.of(MultiblockPatternJsonCodec.decode(
                    input.readNBytes(MultiblockPatternDefinition.MAX_DEFINITION_BYTES + 1)))));
            return new PrecisionAssemblerManager(patterns);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read the Precision migration fixture pattern", exception);
        }
    }

    public static void placeStructure(ServerLevel level, BlockPos controller) {
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 4; z++) {
                    level.setBlockAndUpdate(controller.offset(x - 1, y, z),
                            ModBlocks.MACHINE_CASING.get().defaultBlockState());
                }
            }
        }
        PrecisionAssemblerPortLayout.INPUT_CELLS.forEach(cell -> level.setBlockAndUpdate(
                worldPosition(controller, cell),
                ModBlocks.PRECISION_ASSEMBLER_ITEM_INPUT_PORT.get().defaultBlockState()));
        PrecisionAssemblerPortLayout.OUTPUT_CELLS.forEach(cell -> level.setBlockAndUpdate(
                worldPosition(controller, cell),
                ModBlocks.PRECISION_ASSEMBLER_ITEM_OUTPUT_PORT.get().defaultBlockState()));
        level.setBlockAndUpdate(worldPosition(controller, PrecisionAssemblerPortLayout.ENERGY_CELL),
                ModBlocks.PRECISION_ASSEMBLER_ENERGY_INPUT_PORT.get().defaultBlockState());
        level.setBlockAndUpdate(controller, ModBlocks.PRECISION_ASSEMBLER.get().defaultBlockState()
                .setValue(PrecisionAssemblerBlock.FACING, Direction.NORTH));
    }

    private static BlockPos worldPosition(BlockPos controller, PatternPosition cell) {
        return controller.offset(cell.x() - 1, cell.y(), cell.z());
    }

    private static PrecisionAssemblerBlockEntity controller(ServerLevel level, BlockPos position) {
        return (PrecisionAssemblerBlockEntity) level.getBlockEntity(position);
    }

    private static PrecisionAssemblerPortBlockEntity port(ServerLevel level, BlockPos position) {
        return (PrecisionAssemblerPortBlockEntity) level.getBlockEntity(position);
    }

    private static final class OneShotSaveFailure implements Consumer<ChunkDataEvent.Save> {
        private final ServerLevel level;
        private final BlockPos position;
        private final boolean markerBarrier;
        private boolean fired;
        private int savesAfterFailure;

        private OneShotSaveFailure(ServerLevel level, BlockPos position, boolean markerBarrier) {
            this.level = level;
            this.position = position;
            this.markerBarrier = markerBarrier;
        }

        @Override
        public void accept(ChunkDataEvent.Save event) {
            if (event.getLevel() != level || !event.getChunk().getPos().equals(new ChunkPos(position))) {
                return;
            }
            if (fired) {
                savesAfterFailure++;
                return;
            }
            var entities = event.getData().getList("block_entities", Tag.TAG_COMPOUND);
            for (int index = 0; index < entities.size(); index++) {
                CompoundTag entity = entities.getCompound(index);
                if (entity.getInt("x") != position.getX() || entity.getInt("y") != position.getY()
                        || entity.getInt("z") != position.getZ()) {
                    continue;
                }
                boolean matches = markerBarrier
                        ? entity.contains("arce_precision_port_migration", Tag.TAG_COMPOUND)
                        : "preparing".equals(entity.getCompound("arce_precision_resources").getString("phase"));
                if (matches) {
                    fired = true;
                    throw new IllegalStateException("Intentional one-shot Precision migration save failure");
                }
            }
        }
    }
}
