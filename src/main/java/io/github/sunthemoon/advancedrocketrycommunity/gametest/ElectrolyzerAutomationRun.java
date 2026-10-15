package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpContent;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * W68 (W62 S5): the electrolyzer's frozen side matrix driven only by vanilla automation for {@value #BATCHES} batches.
 * A hopper on top supplies empty canisters, a hopper and a dropper on two sides supply redstone (its own power source,
 * 2,000 FE each), a mod pump on a third side pushes water, and a hopper underneath takes the filled canisters; the
 * unsided face is the second extraction path. Its block entity is saved and loaded once mid-run.
 */
final class ElectrolyzerAutomationRun {
    static final int BATCHES = 20;
    private static final int SUB_TICKS = 40, DROPPER_PERIOD = 16, PIPE_PERIOD = 48, SETTLE_SUB_TICKS = 40;
    private static final int CANISTERS = 2 * BATCHES, REDSTONE = BATCHES, WATER = 1_000 * BATCHES, FE_PER_BATCH = 2_000;
    private final AutomationRig rig;
    private final ServerLevel level;
    private final BlockPos position;
    private final ElectrolyzerBlockEntity machine;
    private final HopperBlockEntity canisters, redstone, outputs;
    private final DispenserBlockEntity dropper;
    private final PumpBlockEntity pump;
    private final Map<Item, Integer> collected = new LinkedHashMap<>();
    private int reservoir = WATER, supplied, subTick, settle = -1;
    private boolean reloaded;

    ElectrolyzerAutomationRun(GameTestHelper helper) {
        rig = new AutomationRig(helper); level = rig.level; position = helper.absolutePos(new BlockPos(3, 2, 3));
        try {
            rig.room(position.offset(-2, -2, -2), position.offset(2, 2, 2));
            rig.place(position, ModBlocks.ELECTROLYZER.get().defaultBlockState().setValue(ElectrolyzerBlock.FACING, Direction.NORTH));
            machine = (ElectrolyzerBlockEntity) level.getBlockEntity(position);
            canisters = rig.hopper(position.above(), Direction.DOWN, List.of(new ItemStack(ModItems.EMPTY_CANISTER.get(), 16),
                    new ItemStack(ModItems.EMPTY_CANISTER.get(), 16), new ItemStack(ModItems.EMPTY_CANISTER.get(), CANISTERS - 32)));
            redstone = rig.hopper(position.north(), Direction.SOUTH, List.of(new ItemStack(Items.REDSTONE, REDSTONE - 5)));
            dropper = rig.dropper(position.west(), Direction.EAST, List.of(new ItemStack(Items.REDSTONE, 5)));
            rig.place(position.east(), PumpContent.BLOCK.get().defaultBlockState());
            pump = (PumpBlockEntity) level.getBlockEntity(position.east());
            outputs = rig.hopper(position.below(), Direction.DOWN, List.of());
        } catch (RuntimeException | Error failure) { rig.close(); throw failure; }
    }

    void start(int timeoutTicks) { rig.drive(timeoutTicks, this::gameTick, this::verify); }

    private boolean gameTick() {
        if (reservoir > 0) {
            int filled = pump.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(() -> new IllegalStateException("Pump"))
                    .fill(new FluidStack(Fluids.WATER, Math.min(reservoir, 4_000)), IFluidHandler.FluidAction.EXECUTE);
            reservoir -= filled; supplied += filled;
        }
        for (int i = 0; i < SUB_TICKS; i++) {
            subTick++;
            rig.tickHoppers();
            if (subTick % DROPPER_PERIOD == 0 && !AutomationRig.empty(dropper)) { rig.pulse(dropper); }
            ElectrolyzerBlockEntity.serverTick(level, position, machine.getBlockState(), machine);
            if (subTick % PIPE_PERIOD == 0) { pipeOutputs(); }
            if (!reloaded && completed() >= BATCHES / 2 && machine.progress() > 0) { refusalsThenReload(); }
            if (settle < 0 && reloaded && completed() >= BATCHES && machine.progress() == 0) { settle = 0; }
            if (settle >= 0 && ++settle > SETTLE_SUB_TICKS) { pipeOutputs(); return true; }
        }
        return false;
    }
    /** The unsided face is the second extraction path beside the hopper under the machine. */
    private void pipeOutputs() {
        IItemHandler unsided = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElseThrow(() -> new IllegalStateException("Unsided"));
        for (int slot = ElectrolyzerBlockEntity.SLOT_HYDROGEN; slot <= ElectrolyzerBlockEntity.SLOT_OXYGEN; slot++) {
            ItemStack taken = unsided.extractItem(slot, 64, false);
            if (!taken.isEmpty()) { collected.merge(taken.getItem(), taken.getCount(), Integer::sum); }
        }
    }
    private int produced(Item item) {
        int total = collected.getOrDefault(item, 0) + AutomationRig.count(outputs, item);
        IItemHandler unsided = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElseThrow(() -> new IllegalStateException("Unsided"));
        for (int slot = 0; slot < unsided.getSlots(); slot++) { if (unsided.getStackInSlot(slot).is(item)) { total += unsided.getStackInSlot(slot).getCount(); } }
        return total;
    }
    private int completed() { return produced(ModItems.HYDROGEN_CANISTER.get()); }

    private void refusalsThenReload() {
        reloaded = true; var helper = rig.helper; var before = machine.saveWithFullMetadata();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            IItemHandler charge = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElseThrow(() -> new IllegalStateException("Side items"));
            helper.assertTrue(charge.extractItem(0, 64, false).isEmpty(), "The electrolyzer released its charge slot to side " + side);
            helper.assertTrue(charge.insertItem(0, new ItemStack(ModItems.EMPTY_CANISTER.get()), false).getCount() == 1,
                    "The electrolyzer took a canister into its charge slot from side " + side);
            IFluidHandler water = machine.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElseThrow(() -> new IllegalStateException("Side water"));
            helper.assertTrue(water.drain(4_000, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "The electrolyzer released water to side " + side);
        }
        IItemHandler bottom = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElseThrow(() -> new IllegalStateException("Bottom"));
        for (int slot = 0; slot < bottom.getSlots(); slot++) {
            helper.assertTrue(bottom.insertItem(slot, new ItemStack(ModItems.HYDROGEN_CANISTER.get()), false).getCount() == 1,
                    "The electrolyzer output face accepted an insertion");
        }
        for (Direction side : new Direction[]{null, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            IEnergyStorage energy = machine.getCapability(ForgeCapabilities.ENERGY, side).orElseThrow(() -> new IllegalStateException("Energy"));
            helper.assertTrue(energy.extractEnergy(20_000, false) == 0, "The electrolyzer released FE to side " + side);
        }
        helper.assertTrue(before.equals(machine.saveWithFullMetadata()), "A refused automation call changed the electrolyzer");
        int progress = machine.progress(), energy = machine.energyStored(), water = machine.waterAmount();
        machine.load(before.copy());
        helper.assertTrue(machine.progress() == progress && machine.energyStored() == energy && machine.waterAmount() == water,
                "Electrolyzer save and load changed its job, FE or water");
    }

    private void verify() {
        var helper = rig.helper;
        IItemHandler unsided = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElseThrow(() -> new IllegalStateException("Unsided"));
        int canistersLeft = AutomationRig.count(canisters, ModItems.EMPTY_CANISTER.get()) + unsided.getStackInSlot(ElectrolyzerBlockEntity.SLOT_INPUT).getCount();
        int redstoneLeft = AutomationRig.count(redstone, Items.REDSTONE) + AutomationRig.count(dropper, Items.REDSTONE)
                + unsided.getStackInSlot(ElectrolyzerBlockEntity.SLOT_CHARGE).getCount();
        helper.assertTrue(canistersLeft == 0, "Electrolyzer left " + canistersLeft + " of " + CANISTERS + " empty canisters");
        // FE ledger: every consumed redstone is 2,000 FE; all of it is the stated use of 20 batches or still stored.
        long charged = (long) ElectrolyzerBlockEntity.REDSTONE_ENERGY * (REDSTONE - redstoneLeft);
        helper.assertTrue(redstoneLeft == 0 && charged == (long) BATCHES * FE_PER_BATCH + machine.energyStored() && machine.energyStored() == 0,
                "Electrolyzer FE: charged " + charged + " from " + (REDSTONE - redstoneLeft) + " redstone, stated use "
                        + BATCHES * FE_PER_BATCH + ", left " + machine.energyStored());
        for (Item item : List.of(ModItems.HYDROGEN_CANISTER.get(), ModItems.OXYGEN_CANISTER.get())) {
            int produced = produced(item);
            helper.assertTrue(produced == BATCHES, "Electrolyzer made " + produced + " " + item + ", stated " + BATCHES);
        }
        int waterLeft = reservoir + pump.fluid().getAmount() + machine.waterAmount();
        helper.assertTrue(supplied == WATER && waterLeft == 0, "Electrolyzer water: supplied " + supplied + ", unused " + waterLeft);
        helper.assertTrue(machine.progress() == 0, "Electrolyzer did not finish idle");
        helper.assertTrue(rig.looseStacks(position, 8) == 0, "Electrolyzer automation spilled items into the world");
    }
}
