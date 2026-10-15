package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternJsonCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerManager;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessProgress;
import io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpContent;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineManager;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * W68 (W62 S5): a v1.2 kernel multiblock (rolling machine or precision assembler) runs {@value #BATCHES} batches through
 * its typed ports. Each item input port has a hopper and a dropper on two faces, water comes from a mod pump, the mod's
 * combustion generator powers the energy port, and a hopper under each output port plus capability calls from two
 * other faces take the products. A test-owned manager (as PrecisionAssemblerMigrationSaveFailureGameTests does) gives
 * the controller extra process ticks; the controller and every port are saved and loaded once mid-run.
 */
final class KernelAutomationRun {
    static final int BATCHES = 20;
    private static final int DROPPER_PERIOD = 16, PIPE_PERIOD = 48, SETTLE_SUB_TICKS = 40;
    private record Feed(BlockPos port, Item item, int perBatch, HopperBlockEntity hopper, DispenserBlockEntity dropper) { }
    private record Output(BlockPos port, Item item, int perBatch, HopperBlockEntity hopper) { }
    private final AutomationRig rig;
    private final ServerLevel level;
    private final String name;
    private final BlockEntity controller;
    private final List<BlockPos> ports = new ArrayList<>();
    private final List<Feed> feeds = new ArrayList<>();
    private final List<Output> outputs = new ArrayList<>();
    private final Map<Item, Integer> collected = new LinkedHashMap<>();
    private final BooleanSupplier formed;
    private final Supplier<Optional<ProcessProgress>> progress;
    private final Runnable processTick;
    private final int subTicksPerTick, energyPerBatch;
    private BlockPos energyPort, fluidPort;
    private GeneratorFeed power;
    private PumpBlockEntity pump;
    private int waterPerBatch, reservoir, waterSupplied, subTick, settle = -1;
    private boolean reloaded;

    private KernelAutomationRun(AutomationRig rig, String name, BlockEntity controller, BooleanSupplier formed,
                                Supplier<Optional<ProcessProgress>> progress, Runnable processTick, int subTicksPerTick, int energyPerBatch) {
        this.rig = rig; level = rig.level; this.name = name; this.controller = controller; this.formed = formed;
        this.progress = progress; this.processTick = processTick; this.subTicksPerTick = subTicksPerTick; this.energyPerBatch = energyPerBatch;
    }

    /** Rolling: 2 iron ingots, 100 mB water and 20 FE/t for 100 ticks make 8 iron bars ({@code rolling_iron_bars}). */
    static KernelAutomationRun rolling(GameTestHelper helper) {
        var rig = new AutomationRig(helper);
        try {
            BlockPos base = helper.absolutePos(new BlockPos(3, 2, 3)), center = base.offset(2, 0, 0);
            rig.room(base.offset(-2, -2, -2), base.offset(6, 4, 3));
            for (int x = 0; x < 5; x++) { for (int y = 0; y < 3; y++) { for (int z = 0; z < 2; z++) {
                rig.place(base.offset(x, y, z), ModBlocks.MACHINE_CASING.get().defaultBlockState());
            } } }
            BlockPos itemIn = base, fluidIn = base.offset(1, 0, 0), energy = base.offset(3, 0, 0), itemOut = base.offset(4, 0, 0);
            rig.place(itemIn, ModBlocks.ROLLING_MACHINE_ITEM_INPUT_PORT.get().defaultBlockState());
            rig.place(fluidIn, ModBlocks.ROLLING_MACHINE_FLUID_INPUT_PORT.get().defaultBlockState());
            rig.place(energy, ModBlocks.ROLLING_MACHINE_ENERGY_INPUT_PORT.get().defaultBlockState());
            rig.place(itemOut, ModBlocks.ROLLING_MACHINE_ITEM_OUTPUT_PORT.get().defaultBlockState());
            rig.place(center, ModBlocks.ROLLING_MACHINE.get().defaultBlockState().setValue(RollingMachineBlock.FACING, Direction.NORTH));
            var controller = (RollingMachineBlockEntity) rig.level.getBlockEntity(center);
            var manager = new RollingMachineManager(patterns(rig.level, "rolling_machine"));
            manager.observeController(rig.level, controller);
            var run = new KernelAutomationRun(rig, "Rolling machine", controller,
                    () -> controller.formationState() == MultiblockFormationState.FORMED, controller::processProgress,
                    () -> { manager.markProcessReady(rig.level, center); manager.tick(rig.level.getServer()); }, 50, 2_000);
            rig.beforeClose(() -> manager.removeController(rig.level, controller));
            run.ports.addAll(List.of(itemIn, fluidIn, energy, itemOut));
            run.feed(itemIn, Items.IRON_INGOT, 2, Direction.NORTH, Direction.WEST);
            run.water(fluidIn, Direction.DOWN, 100);
            run.power(energy, Direction.NORTH);
            run.output(itemOut, Items.IRON_BARS, 8);
            return run.formedNow(helper);
        } catch (RuntimeException | Error failure) { rig.close(); throw failure; }
    }

    /** Precision: 2 iron ingots and 2 control boards at 40 FE/t for 20 ticks make 1 advanced circuit and 2 redstone torches. */
    static KernelAutomationRun precision(GameTestHelper helper) {
        var rig = new AutomationRig(helper);
        try {
            BlockPos center = helper.absolutePos(new BlockPos(4, 2, 3));
            rig.room(center.offset(-3, -2, -2), center.offset(3, 4, 5));
            for (int x = -1; x <= 1; x++) { for (int y = 0; y < 3; y++) { for (int z = 0; z < 4; z++) {
                rig.place(center.offset(x, y, z), ModBlocks.MACHINE_CASING.get().defaultBlockState());
            } } }
            // PrecisionAssemblerPortLayout: inputs 0 to 4, outputs 0 and 1, then energy, controller anchor (1, 0, 0).
            List<BlockPos> inputs = List.of(center.offset(-1, 0, 0), center.offset(1, 0, 0), center.offset(-1, 0, 1),
                    center.offset(1, 0, 1), center.offset(-1, 0, 2));
            List<BlockPos> outs = List.of(center.offset(1, 0, 2), center.offset(-1, 0, 3));
            BlockPos energy = center.offset(1, 0, 3);
            inputs.forEach(position -> rig.place(position, ModBlocks.PRECISION_ASSEMBLER_ITEM_INPUT_PORT.get().defaultBlockState()));
            outs.forEach(position -> rig.place(position, ModBlocks.PRECISION_ASSEMBLER_ITEM_OUTPUT_PORT.get().defaultBlockState()));
            rig.place(energy, ModBlocks.PRECISION_ASSEMBLER_ENERGY_INPUT_PORT.get().defaultBlockState());
            rig.place(center, ModBlocks.PRECISION_ASSEMBLER.get().defaultBlockState().setValue(PrecisionAssemblerBlock.FACING, Direction.NORTH));
            var controller = (PrecisionAssemblerBlockEntity) rig.level.getBlockEntity(center);
            var manager = new PrecisionAssemblerManager(patterns(rig.level, "precision_assembler"));
            manager.observeController(rig.level, controller);
            var run = new KernelAutomationRun(rig, "Precision assembler", controller,
                    () -> controller.formationState() == MultiblockFormationState.FORMED, controller::processProgress,
                    () -> { manager.markProcessReady(rig.level, center); manager.tick(rig.level.getServer()); }, 25, 800);
            rig.beforeClose(() -> manager.removeController(rig.level, controller));
            run.ports.addAll(inputs); run.ports.addAll(outs); run.ports.add(energy);
            run.feed(inputs.get(0), Items.IRON_INGOT, 2, Direction.NORTH, Direction.WEST);
            run.feed(inputs.get(1), ModItems.CONTROL_BOARD.get(), 2, Direction.NORTH, Direction.EAST);
            run.power(energy, Direction.EAST);
            run.output(outs.get(0), ModItems.ADVANCED_CIRCUIT.get(), 1);
            run.output(outs.get(1), Items.REDSTONE_TORCH, 2);
            return run.formedNow(helper);
        } catch (RuntimeException | Error failure) { rig.close(); throw failure; }
    }

    private static MultiblockPatternCatalogManager patterns(ServerLevel level, String id) {
        try (var input = level.getServer().getResourceManager().getResourceOrThrow(ModIdentity.id("machine_patterns/" + id + ".json")).open()) {
            var patterns = new MultiblockPatternCatalogManager();
            patterns.accept(MultiblockPatternCatalog.create(List.of(MultiblockPatternJsonCodec.decode(
                    input.readNBytes(MultiblockPatternDefinition.MAX_DEFINITION_BYTES + 1)))));
            return patterns;
        } catch (IOException missing) { throw new IllegalStateException("W68 cannot read the " + id + " pattern", missing); }
    }
    /** Three quarters of the inputs go into a hopper on {@code hopperSide}, the rest into a dropper on {@code dropperSide}. */
    private void feed(BlockPos port, Item item, int perBatch, Direction hopperSide, Direction dropperSide) {
        int total = BATCHES * perBatch, second = total / 4;
        var hopper = rig.hopper(port.relative(hopperSide), hopperSide.getOpposite(), List.of(new ItemStack(item, total - second)));
        var dropper = rig.dropper(port.relative(dropperSide), dropperSide.getOpposite(), List.of(new ItemStack(item, second)));
        feeds.add(new Feed(port, item, perBatch, hopper, dropper));
    }
    private void water(BlockPos port, Direction side, int perBatch) {
        rig.place(port.relative(side), PumpContent.BLOCK.get().defaultBlockState());
        pump = (PumpBlockEntity) level.getBlockEntity(port.relative(side)); fluidPort = port;
        waterPerBatch = perBatch; reservoir = BATCHES * perBatch;
    }
    /** One coal item: 1,600 burn ticks at 40 FE, more than the 20 batches need. */
    private void power(BlockPos port, Direction side) { energyPort = port; power = new GeneratorFeed(rig, port.relative(side), Items.COAL, 1); }
    private void output(BlockPos port, Item item, int perBatch) {
        outputs.add(new Output(port, item, perBatch, rig.hopper(port.below(), Direction.DOWN, List.of())));
    }
    private KernelAutomationRun formedNow(GameTestHelper helper) {
        for (int attempt = 0; attempt < 4 && !formed.getAsBoolean(); attempt++) { processTick.run(); }
        helper.assertTrue(formed.getAsBoolean(), name + " did not form");
        return this;
    }

    void start(int timeoutTicks) { rig.drive(timeoutTicks, this::gameTick, this::verify); }

    private boolean gameTick() {
        if (pump != null && reservoir > 0) {
            int filled = pump.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(() -> new IllegalStateException("Pump"))
                    .fill(new FluidStack(Fluids.WATER, reservoir), IFluidHandler.FluidAction.EXECUTE);
            reservoir -= filled; waterSupplied += filled;
        }
        for (int i = 0; i < subTicksPerTick; i++) {
            subTick++;
            power.tick(); rig.tickHoppers();
            if (subTick % DROPPER_PERIOD == 0 && completed() > 0) {
                for (var feed : feeds) { if (!AutomationRig.empty(feed.dropper())) { rig.pulse(feed.dropper()); } }
            }
            processTick.run();
            if (subTick % PIPE_PERIOD == 0) { pipeOutputs(subTick / PIPE_PERIOD % 2 == 0 ? Direction.EAST : Direction.NORTH); }
            if (!reloaded && completed() >= BATCHES / 2 && progress.get().isPresent()) { refusalsThenReload(); }
            if (settle < 0 && reloaded && completed() >= BATCHES && progress.get().isEmpty()) { settle = 0; }
            if (settle >= 0 && ++settle > SETTLE_SUB_TICKS) { pipeOutputs(Direction.SOUTH); return true; }
        }
        return false;
    }
    private IItemHandler items(BlockPos port, Direction side) {
        return level.getBlockEntity(port).getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
    }
    private void pipeOutputs(Direction side) {
        for (var output : outputs) {
            IItemHandler handler = items(output.port(), side);
            if (handler == null) { continue; }
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack taken = handler.extractItem(slot, 64, false);
                if (!taken.isEmpty()) { collected.merge(taken.getItem(), taken.getCount(), Integer::sum); }
            }
        }
    }
    private int produced(Output output) {
        int total = collected.getOrDefault(output.item(), 0) + AutomationRig.count(output.hopper(), output.item());
        IItemHandler handler = items(output.port(), null);
        for (int slot = 0; handler != null && slot < handler.getSlots(); slot++) {
            if (handler.getStackInSlot(slot).is(output.item())) { total += handler.getStackInSlot(slot).getCount(); }
        }
        return total;
    }
    private int completed() { var first = outputs.get(0); return produced(first) / first.perBatch(); }

    private void refusalsThenReload() {
        reloaded = true; var helper = rig.helper;
        for (Direction side : new Direction[]{null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            for (var feed : feeds) {
                IItemHandler handler = items(feed.port(), side);
                helper.assertTrue(handler != null, name + " input port hid its handler on side " + side);
                ItemStack before = handler.getStackInSlot(0);
                helper.assertTrue(handler.extractItem(0, 64, false).isEmpty() && ItemStack.matches(before, handler.getStackInSlot(0)),
                        name + " input port released an input from side " + side);
            }
            for (var output : outputs) {
                IItemHandler handler = items(output.port(), side);
                helper.assertTrue(handler != null && handler.insertItem(0, new ItemStack(Items.DIRT, 3), false).getCount() == 3,
                        name + " output port accepted an insertion from side " + side);
            }
            IEnergyStorage port = level.getBlockEntity(energyPort).getCapability(ForgeCapabilities.ENERGY, side).orElse(null);
            helper.assertTrue(port != null && port.extractEnergy(10_000, false) == 0, name + " energy port released FE from side " + side);
            if (fluidPort != null) {
                IFluidHandler fluid = level.getBlockEntity(fluidPort).getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElse(null);
                helper.assertTrue(fluid != null && fluid.drain(4_000, IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                        name + " fluid port released water from side " + side);
            }
        }
        var before = progress.get().orElseThrow(); int stored = storedEnergy();
        CompoundTag saved = controller.saveWithFullMetadata(); var portData = new ArrayList<CompoundTag>();
        ports.forEach(position -> portData.add(level.getBlockEntity(position).saveWithFullMetadata()));
        controller.load(saved);
        for (int index = 0; index < ports.size(); index++) { level.getBlockEntity(ports.get(index)).load(portData.get(index)); }
        power.reload();
        helper.assertTrue(progress.get().equals(Optional.of(before)) && storedEnergy() == stored,
                name + " save and load changed the active job or the stored FE");
    }
    private int storedEnergy() {
        return level.getBlockEntity(energyPort).getCapability(ForgeCapabilities.ENERGY).map(IEnergyStorage::getEnergyStored).orElse(-1);
    }

    private void verify() {
        var helper = rig.helper;
        helper.assertTrue(formed.getAsBoolean() && progress.get().isEmpty(), name + " did not finish formed and idle");
        for (var feed : feeds) {
            int left = AutomationRig.count(feed.hopper(), feed.item()) + AutomationRig.count(feed.dropper(), feed.item());
            IItemHandler handler = items(feed.port(), null);
            for (int slot = 0; handler != null && slot < handler.getSlots(); slot++) {
                if (handler.getStackInSlot(slot).is(feed.item())) { left += handler.getStackInSlot(slot).getCount(); }
            }
            helper.assertTrue(left == 0, name + " left " + left + " " + feed.item() + " of " + BATCHES * feed.perBatch() + " unused");
        }
        for (var output : outputs) {
            int produced = produced(output);
            helper.assertTrue(produced == BATCHES * output.perBatch(), name + " made " + produced + " " + output.item()
                    + ", stated " + BATCHES * output.perBatch());
        }
        if (pump != null) {
            int left = reservoir + pump.fluid().getAmount() + level.getBlockEntity(fluidPort).getCapability(ForgeCapabilities.FLUID_HANDLER)
                    .map(handler -> handler.getFluidInTank(0).getAmount()).orElse(-1);
            helper.assertTrue(waterSupplied == BATCHES * waterPerBatch && left == 0,
                    name + " water: supplied " + waterSupplied + ", unused " + left + ", stated use " + BATCHES * waterPerBatch);
        }
        long generated = power.generated(), accounted = (long) power.buffered() + storedEnergy() + (long) BATCHES * energyPerBatch;
        helper.assertTrue(generated == accounted, name + " FE: generator made " + generated + ", buffer " + power.buffered()
                + " + port " + storedEnergy() + " + stated use " + (long) BATCHES * energyPerBatch);
        helper.assertTrue(rig.looseStacks(controller.getBlockPos(), 8) == 0, name + " spilled items into the world");
    }
}
