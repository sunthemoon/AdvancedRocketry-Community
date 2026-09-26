package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.PrecisionAssemblerMigrationSaveFailureGameTests;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/** Deterministic production-manager notifications, not physical chunk eviction. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PrecisionAssemblerUnloadGameTests {
    private static final TicketType<ChunkPos> FIXTURE_TICKET = TicketType.create(
            "arce_precision_unload_test", Comparator.comparingLong(ChunkPos::toLong), 60);

    private PrecisionAssemblerUnloadGameTests() {
    }

    @GameTest(template = "empty", batch = "precision_unload_active", timeoutTicks = 40)
    public static void activeUnloadRetiresViewsAndPreservesProcessing(GameTestHelper helper) {
        run(helper, fixture -> {
            fixture.seed();
            for (int tick = 0; tick < 3; tick++) {
                fixture.controller.tickProcess(helper.getLevel());
            }
            fixture.pause();
            helper.assertTrue(fixture.controller.processProgress().orElseThrow().progressTicks() == 3,
                    "Unload fixture did not preserve a nonzero processing checkpoint");
            Views old = fixture.views();
            Snapshot before = fixture.snapshot();
            fixture.unload();
            fixture.assertUnavailable(old);
            fixture.assertStable(before, "active", false);
            fixture.reload();
            fixture.assertStable(before, "active", false);
            fixture.assertRetired(old);
            fixture.assertFreshViews(old);

            helper.getLevel().setBlockAndUpdate(fixture.controller.getBlockPos().north(),
                    Blocks.AIR.defaultBlockState());
            for (int tick = 3; tick < 20; tick++) {
                fixture.controller.tickProcess(helper.getLevel());
            }
            helper.assertTrue(fixture.controller.processProgress().isEmpty()
                            && fixture.controller.storedItemCopy(0).isEmpty()
                            && fixture.controller.storedItemCopy(1).isEmpty()
                            && ItemStack.matches(fixture.controller.storedItemCopy(5),
                                    new ItemStack(ModItems.ADVANCED_CIRCUIT.get()))
                            && ItemStack.matches(fixture.controller.storedItemCopy(6),
                                    new ItemStack(Items.REDSTONE_TORCH, 2))
                            && fixture.ports.energy().storedEnergy() == 200,
                    "Resumed process did not finish exactly one batch with the remaining Energy");
            fixture.assertRetired(old);
            IItemHandler output = fixture.views().output().resolve().orElseThrow();
            helper.assertTrue(output.extractItem(0, 1, false).is(ModItems.ADVANCED_CIRCUIT.get())
                            && fixture.controller.storedItemCopy(5).isEmpty(),
                    "Fresh output capability did not operate after resumed completion");
        });
    }

    @GameTest(template = "empty", batch = "precision_unload_prepared", timeoutTicks = 40)
    public static void preparedUnloadRetainsBindingsAndMigratesOnce(GameTestHelper helper) {
        run(helper, fixture -> {
            fixture.seed();
            fixture.pause();
            Views old = fixture.views();
            for (int index = 0; index < fixture.itemPorts.size(); index++) {
                var port = fixture.itemPorts.get(index);
                CompoundTag saved = port.saveWithFullMetadata();
                ItemStack stack = fixture.controller.storedItemCopy(index);
                saved.getCompound("arce_precision_port").put("item",
                        stack.isEmpty() ? new CompoundTag() : stack.save(new CompoundTag()));
                port.load(saved);
                port.setChanged();
                if (index < 3) {
                    port.markLegacyMigrated(fixture.controller.controllerState().machineInstanceId(),
                            PrecisionAssemblerChannels.input(index));
                }
            }
            CompoundTag prepared = fixture.controller.saveWithFullMetadata();
            prepared.getCompound("arce_precision_resources").putString("phase", "preparing");
            fixture.controller.load(prepared);
            fixture.controller.setChanged();
            helper.assertTrue(fixture.itemPorts.stream().filter(port -> port.migrationMarker().isPresent()).count() == 3,
                    "Preparing unload fixture did not contain exactly three migration markers");
            Snapshot before = fixture.snapshot();
            fixture.unload();
            fixture.assertUnavailable(old);
            fixture.assertStable(before, "preparing", false);
            fixture.reload();
            fixture.assertStable(before, "active", true);
            fixture.assertRetired(old);
            fixture.assertFreshViews(old);
            for (var port : fixture.itemPorts) {
                var marker = port.migrationMarker().orElseThrow();
                helper.assertTrue(marker.machineId().equals(fixture.controller.controllerState().machineInstanceId())
                                && marker.channel().equals(port.assignedChannel().orElseThrow()),
                        "Resumed migration wrote another owner or channel marker");
            }

            IItemHandler fresh = fixture.views().input().resolve().orElseThrow();
            helper.assertTrue(fresh.insertItem(0, new ItemStack(Items.IRON_INGOT), false).isEmpty(),
                    "Completed migration did not expose a writable fresh input");
            helper.assertTrue(fixture.controller.storedItemCopy(0).getCount() == 3
                            && fixture.itemPorts.get(0).legacyStoredItemCopy().getCount() == 2,
                    "Fresh insertion did not separate central ownership from the inert shadow");
            Snapshot changed = fixture.snapshot();
            Views retired = fixture.views();
            fixture.unload();
            fixture.assertUnavailable(retired);
            fixture.reload();
            fixture.assertStable(changed, "active", false);
            fixture.assertRetired(retired);
            helper.assertTrue(fixture.controller.storedItemCopy(0).getCount() == 3,
                    "Repeated load notification reimported the old Item shadow");
        });
    }

    private static void run(GameTestHelper helper, Consumer<Fixture> exercise) {
        BlockPos template = helper.absolutePos(BlockPos.ZERO);
        BlockPos position = new BlockPos(880_000 + (template.getX() & ~15) + 15,
                template.getY() + 80, 880_000 + (template.getZ() & ~15) + 13);
        ChunkPos origin = new ChunkPos(position);
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                ChunkPos chunk = new ChunkPos(origin.x + x, origin.z + z);
                helper.getLevel().getChunkSource().addRegionTicket(FIXTURE_TICKET, chunk, 2, chunk);
            }
        }
        PrecisionAssemblerMigrationSaveFailureGameTests.placeStructure(helper.getLevel(), position);
        helper.getLevel().setBlockAndUpdate(position.north(), Blocks.AIR.defaultBlockState());
        helper.runAtTickTime(8, () -> {
            var manager = PrecisionAssemblerMigrationSaveFailureGameTests.fixtureManager(helper.getLevel());
            try {
                var controller = (PrecisionAssemblerBlockEntity) helper.getLevel().getBlockEntity(position);
                helper.assertTrue(controller != null && controller.formationState() == MultiblockFormationState.FORMED,
                        "Cross-chunk unload fixture did not form");
                manager.observeController(helper.getLevel(), controller);
                manager.tick(helper.getLevel().getServer());
                helper.assertTrue(manager.pendingValidationCount() == 0 && manager.pendingProcessCount() == 0,
                        "Fixture manager did not drain its initial validation/process work");
                var ports = PrecisionAssemblerPortSet.resolve(helper.getLevel(), controller).orElseThrow();
                ChunkPos remote = new ChunkPos(ports.inputs().get(1).getBlockPos());
                helper.assertTrue(!remote.equals(origin), "Unload notification did not target a separate part chunk");
                exercise.accept(new Fixture(helper, controller, ports, manager, remote));
            } finally {
                manager.clear();
            }
            helper.succeed();
        });
    }

    private static final class Fixture {
        private final GameTestHelper helper;
        private final PrecisionAssemblerBlockEntity controller;
        private final PrecisionAssemblerPortSet ports;
        private final PrecisionAssemblerManager manager;
        private final ChunkPos remote;
        private final List<PrecisionAssemblerPortBlockEntity> itemPorts;

        private Fixture(GameTestHelper helper, PrecisionAssemblerBlockEntity controller,
                        PrecisionAssemblerPortSet ports, PrecisionAssemblerManager manager, ChunkPos remote) {
            this.helper = helper;
            this.controller = controller;
            this.ports = ports;
            this.manager = manager;
            this.remote = remote;
            itemPorts = new ArrayList<>(ports.inputs());
            itemPorts.addAll(ports.outputs());
        }

        void seed() {
            var input = views().input().resolve().orElseThrow();
            var second = ports.inputs().get(1).getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
            helper.assertTrue(input.insertItem(0, new ItemStack(Items.IRON_INGOT, 2), false).isEmpty()
                            && second.insertItem(0, new ItemStack(Items.REDSTONE, 2), false).isEmpty()
                            && views().energy().resolve().orElseThrow().receiveEnergy(1_000, false) == 1_000,
                    "Unload fixture resources were rejected");
        }

        void pause() {
            helper.getLevel().setBlockAndUpdate(controller.getBlockPos().north(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            controller.tickProcess(helper.getLevel());
        }

        void unload() {
            manager.onChunkUnloading(helper.getLevel(), remote.x, remote.z);
            helper.assertTrue(controller.formationState() == MultiblockFormationState.WAITING_UNLOADED,
                    "Part-chunk unload notification did not put the controller into WAITING");
            // Exercise a queued process while waiting, without a synthetic
            // load notification or another global server tick interleaving.
            manager.markProcessReady(helper.getLevel(), controller.getBlockPos());
            manager.tick(helper.getLevel().getServer());
            helper.assertTrue(controller.formationState() == MultiblockFormationState.WAITING_UNLOADED,
                    "Queued work resumed a machine before its load notification");
        }

        void reload() {
            manager.onChunkChanged(helper.getLevel(), remote.x, remote.z);
            helper.assertTrue(manager.pendingValidationCount() > 0, "Load notification did not queue revalidation");
            manager.tick(helper.getLevel().getServer());
            helper.assertTrue(controller.formationState() == MultiblockFormationState.FORMED,
                    "Loaded structure did not re-form");
        }

        Snapshot snapshot() {
            List<CompoundTag> savedPorts = new ArrayList<>();
            itemPorts.forEach(port -> savedPorts.add(port.saveWithFullMetadata()));
            savedPorts.add(ports.energy().saveWithFullMetadata());
            return new Snapshot(controller.saveWithFullMetadata(), savedPorts);
        }

        void assertStable(Snapshot before, String phase, boolean newMarkers) {
            Snapshot after = snapshot();
            CompoundTag expected = before.controller().copy();
            expected.getCompound("arce_multiblock").putString("formation_state", controller.formationState().name());
            expected.getCompound("arce_precision_resources").putString("phase", phase);
            helper.assertTrue(expected.equals(after.controller()),
                    "Lifecycle changed controller identity, binding generation, Items, process or journal");
            for (int index = 0; index < before.ports().size(); index++) {
                CompoundTag oldPort = before.ports().get(index).copy();
                CompoundTag newPort = after.ports().get(index).copy();
                if (newMarkers && index < 7) {
                    if (oldPort.contains("arce_precision_port_migration")) {
                        helper.assertTrue(oldPort.get("arce_precision_port_migration")
                                        .equals(newPort.get("arce_precision_port_migration")),
                                "Migration rewrote an existing marker");
                    }
                    oldPort.remove("arce_precision_port_migration");
                    newPort.remove("arce_precision_port_migration");
                }
                helper.assertTrue(oldPort.equals(newPort), "Lifecycle changed a port binding, shadow or Energy root");
            }
        }

        Views views() {
            return new Views(ports.inputs().get(0).getCapability(ForgeCapabilities.ITEM_HANDLER),
                    ports.outputs().get(0).getCapability(ForgeCapabilities.ITEM_HANDLER),
                    ports.energy().getCapability(ForgeCapabilities.ENERGY));
        }

        void assertUnavailable(Views old) {
            itemPorts.forEach(port -> helper.assertTrue(!port.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                    "Waiting structure exposed an Item capability"));
            helper.assertTrue(!ports.energy().getCapability(ForgeCapabilities.ENERGY).isPresent(),
                    "Waiting structure exposed Energy capability");
            assertRetired(old);
        }

        void assertRetired(Views old) {
            helper.assertTrue(!old.input().isPresent() && !old.output().isPresent() && !old.energy().isPresent(),
                    "Lifecycle retained a previously published LazyOptional");
            helper.assertTrue(old.inputView.getStackInSlot(0).isEmpty()
                            && old.outputView.getStackInSlot(0).isEmpty()
                            && old.inputView.insertItem(0, new ItemStack(Items.IRON_INGOT), false).getCount() == 1
                            && old.outputView.extractItem(0, 1, false).isEmpty()
                            && old.energyView.getEnergyStored() == 0
                            && old.energyView.receiveEnergy(1, false) == 0,
                    "Retired capability reference read or changed resources");
        }

        void assertFreshViews(Views old) {
            Views fresh = views();
            helper.assertTrue(fresh.inputView != old.inputView && fresh.energyView != old.energyView
                            && ItemStack.matches(fresh.inputView.getStackInSlot(0), controller.storedItemCopy(0))
                            && fresh.energyView.getEnergyStored() == ports.energy().storedEnergy()
                            && fresh.energyView.receiveEnergy(1, true) == 1,
                    "Fresh capabilities did not expose the retained resources");
        }
    }

    private record Snapshot(CompoundTag controller, List<CompoundTag> ports) {
    }

    private static final class Views {
        private final LazyOptional<IItemHandler> input;
        private final LazyOptional<IItemHandler> output;
        private final LazyOptional<IEnergyStorage> energy;
        private final IItemHandler inputView;
        private final IItemHandler outputView;
        private final IEnergyStorage energyView;

        private Views(LazyOptional<IItemHandler> input, LazyOptional<IItemHandler> output,
                      LazyOptional<IEnergyStorage> energy) {
            this.input = input;
            this.output = output;
            this.energy = energy;
            inputView = input.resolve().orElseThrow();
            outputView = output.resolve().orElseThrow();
            energyView = energy.resolve().orElseThrow();
        }

        LazyOptional<IItemHandler> input() { return input; }
        LazyOptional<IItemHandler> output() { return output; }
        LazyOptional<IEnergyStorage> energy() { return energy; }
    }
}
