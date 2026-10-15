package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.PipeSealBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpBudget;
import io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpContent;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.PressurizedTankBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import io.github.sunthemoon.advancedrocketrycommunity.material.press.SmallPlatePressRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * W68 (W62 S5) fluid devices, the small plate press and the pipe seal. Each moves twenty units (sources, buckets'
 * worth of fluid, presses or items) through its real automation surface and is counted on both sides.
 */
final class DeviceAutomationRuns {
    static final int ROUNDS = 20;
    private static final UUID OWNER = new UUID(0x5736_3850_554D_50L, 68); // Offline, as PumpGameTests' owner.

    /**
     * A walled water pool under the pump; one bamboo (2,000 FE) in the mod's generator pays for exactly twenty drains at
     * 100 FE each. Water sources refill, so the drain count is read from the FE ledger and must match the mB moved:
     * a source is 1,000 mB. (W68 first used a lava pool; see the record for why a reloaded pump stopped on it.)
     */
    static void pump(GameTestHelper helper, int timeoutTicks) {
        var rig = new AutomationRig(helper);
        try {
            BlockPos base = helper.absolutePos(new BlockPos(2, 2, 2));
            rig.room(base, base.offset(6, 5, 5));
            for (int x = 0; x <= 6; x++) { for (int z = 0; z <= 5; z++) {
                rig.place(base.offset(x, 0, z), Blocks.STONE.defaultBlockState());
                boolean inside = x >= 1 && x <= 5 && z >= 1 && z <= 4;
                rig.place(base.offset(x, 1, z), inside ? Blocks.WATER.defaultBlockState() : Blocks.STONE.defaultBlockState());
            } }
            BlockPos at = base.offset(3, 3, 2);
            rig.place(at, PumpContent.BLOCK.get().defaultBlockState());
            var pump = (PumpBlockEntity) rig.level.getBlockEntity(at); pump.placed(OWNER, null);
            rig.place(at.east(), ModBlocks.PRESSURIZED_TANK.get().defaultBlockState());
            var tank = (PressurizedTankBlockEntity) rig.level.getBlockEntity(at.east());
            var power = new GeneratorFeed(rig, at.west(), Items.BAMBOO, 1);
            var state = new Object() {
                long collected; int ticks, settle = -1; boolean reloaded;
                long paid() { return power.generated() - power.buffered() - pump.energy(); }
                long moved() { return collected + tank.fluidState().getAmount() + pump.fluid().getAmount(); }
            };
            rig.drive(timeoutTicks, () -> {
                state.ticks++;
                for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH}) {
                    state.collected += fluids(tank, side).drain(150, IFluidHandler.FluidAction.EXECUTE).getAmount();
                }
                if (state.ticks % 10 == 0) { state.collected += fluids(pump, Direction.UP).drain(200, IFluidHandler.FluidAction.EXECUTE).getAmount(); }
                // Each drain debits 100 FE and adds 1,000 mB in one publication; the two ledgers move together.
                helper.assertTrue(state.paid() % PumpBudget.COST == 0 && state.moved() == state.paid() / PumpBudget.COST * PumpBudget.SOURCE,
                        "Pump ledgers diverged: paid " + state.paid() + " FE, moved " + state.moved() + " mB");
                if (!state.reloaded && state.paid() >= ROUNDS / 2 * PumpBudget.COST) {
                    state.reloaded = true;
                    for (Direction side : Direction.values()) {
                        helper.assertTrue(pump.getCapability(ForgeCapabilities.ENERGY, side).orElseThrow(() -> new IllegalStateException("Pump power"))
                                .extractEnergy(10_000, false) == 0, "The pump released FE to side " + side);
                    }
                    if (!tank.fluidState().isEmpty()) {
                        helper.assertTrue(fluids(tank, Direction.UP).fill(new FluidStack(Fluids.LAVA, 1_000), IFluidHandler.FluidAction.EXECUTE) == 0,
                                "The water tank accepted lava");
                    }
                    int energy = pump.energy(), inPump = pump.fluid().getAmount(), inTank = tank.fluidState().getAmount();
                    pump.load(pump.saveWithFullMetadata()); tank.load(tank.saveWithFullMetadata()); power.reload();
                    helper.assertTrue(pump.energy() == energy && pump.fluid().getAmount() == inPump && tank.fluidState().getAmount() == inTank
                            && OWNER.equals(pump.owner()), "Pump or tank save and load changed FE, fluid or owner");
                }
                if (state.settle < 0 && state.reloaded && power.spent() && pump.energy() < PumpBudget.COST && pump.fluid().isEmpty()) { state.settle = 0; }
                return state.settle >= 0 && ++state.settle > 3;
            }, () -> {
                state.collected += fluids(tank, Direction.WEST).drain(64_000, IFluidHandler.FluidAction.EXECUTE).getAmount();
                helper.assertTrue(power.generated() == (long) power.burnTicks() * 40 && state.paid() == (long) ROUNDS * PumpBudget.COST,
                        "Pump FE: generator made " + power.generated() + ", buffer " + power.buffered() + ", pump " + pump.energy());
                helper.assertTrue(state.moved() == (long) ROUNDS * PumpBudget.SOURCE && tank.fluidState().isEmpty() && pump.fluid().isEmpty(),
                        "Pump moved " + state.moved() + " mB for " + ROUNDS + " paid drains");
            }, () -> "pump " + pump.status() + " FE " + pump.energy() + " cooldown " + pump.cooldown() + " fluid " + pump.fluid().getAmount()
                    + ", tank " + tank.fluidState().getAmount() + ", collected " + state.collected + ", paid " + state.paid()
                    + ", fuel left " + power.unburnt() + ", generated " + power.generated() + ", reloaded " + state.reloaded);
        } catch (RuntimeException | Error failure) { rig.close(); throw failure; }
    }

    /** Two stacked tanks: a mod pump fills the top one, the bottom one pulls from it, and two faces drain the bottom. */
    static void tank(GameTestHelper helper, int timeoutTicks) {
        var rig = new AutomationRig(helper);
        try {
            BlockPos bottom = helper.absolutePos(new BlockPos(3, 2, 3)), top = bottom.above();
            rig.room(bottom.offset(-2, -2, -2), bottom.offset(2, 3, 2));
            rig.place(bottom, ModBlocks.PRESSURIZED_TANK.get().defaultBlockState());
            rig.place(top, ModBlocks.PRESSURIZED_TANK.get().defaultBlockState());
            rig.place(top.east(), PumpContent.BLOCK.get().defaultBlockState());
            var lower = (PressurizedTankBlockEntity) rig.level.getBlockEntity(bottom);
            var upper = (PressurizedTankBlockEntity) rig.level.getBlockEntity(top);
            var pump = (PumpBlockEntity) rig.level.getBlockEntity(top.east());
            var state = new Object() { int reservoir = ROUNDS * 1_000, supplied; long collected; int settle = -1; boolean reloaded; };
            rig.drive(timeoutTicks, () -> {
                if (state.reservoir > 0) {
                    int filled = fluids(pump, Direction.EAST).fill(new FluidStack(Fluids.WATER, Math.min(state.reservoir, 2_000)), IFluidHandler.FluidAction.EXECUTE);
                    state.reservoir -= filled; state.supplied += filled;
                }
                for (Direction side : new Direction[]{Direction.WEST, Direction.SOUTH}) {
                    state.collected += fluids(lower, side).drain(400, IFluidHandler.FluidAction.EXECUTE).getAmount();
                }
                if (!state.reloaded && state.collected >= ROUNDS * 500L) {
                    state.reloaded = true;
                    for (var entity : List.of(upper, lower)) {
                        if (entity.fluidState().isEmpty()) { continue; }
                        for (Direction side : Direction.values()) {
                            helper.assertTrue(fluids(entity, side).fill(new FluidStack(Fluids.LAVA, 1_000), IFluidHandler.FluidAction.EXECUTE) == 0
                                    && fluids(entity, side).drain(new FluidStack(Fluids.LAVA, 1_000), IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                                    "A water tank mixed in or released lava on side " + side);
                        }
                    }
                    int up = upper.fluidState().getAmount(), down = lower.fluidState().getAmount();
                    upper.load(upper.saveWithFullMetadata()); lower.load(lower.saveWithFullMetadata());
                    helper.assertTrue(upper.fluidState().getAmount() == up && lower.fluidState().getAmount() == down, "Tank save and load changed its fluid");
                }
                if (state.settle < 0 && state.reloaded && state.collected == ROUNDS * 1_000L) { state.settle = 0; }
                return state.settle >= 0 && ++state.settle > 3;
            }, () -> {
                long inside = (long) upper.fluidState().getAmount() + lower.fluidState().getAmount() + pump.fluid().getAmount();
                helper.assertTrue(state.supplied == ROUNDS * 1_000 && state.collected + inside == state.supplied && inside == 0,
                        "Tanks: supplied " + state.supplied + " mB, drained " + state.collected + ", left " + inside);
            });
        } catch (RuntimeException | Error failure) { rig.close(); throw failure; }
    }

    /**
     * The press has no block entity or capability (ADR-061 section 6): twenty rising edges each press the iron block a
     * block placer set below it into the recipe's plates, collected as the dropped items they are. Extra edges with no
     * input, and a block placed under a press that stays powered, press nothing.
     */
    static void smallPlatePress(GameTestHelper helper) {
        var rig = new AutomationRig(helper);
        try {
            BlockPos anvil = helper.absolutePos(new BlockPos(3, 2, 3)), target = anvil.above(), press = target.above(), signal = press.east();
            rig.room(anvil.offset(-2, -1, -2), anvil.offset(2, 4, 2));
            rig.place(anvil, Blocks.OBSIDIAN.defaultBlockState());
            rig.place(press, MaterialContent.SMALL_PLATE_PRESS.get().defaultBlockState());
            rig.place(target, Blocks.AIR.defaultBlockState()); rig.place(signal, Blocks.AIR.defaultBlockState());
            var selection = SmallPlatePressRecipe.select(rig.level.getRecipeManager().getAllRecipesFor(MaterialContent.SMALL_PLATE_PRESS_TYPE.get()),
                    new ItemStack(Items.IRON_BLOCK));
            ItemStack stated = selection.recipe().orElseThrow().assemble(new SimpleContainer(new ItemStack(Items.IRON_BLOCK)), rig.level.registryAccess());
            int plates = 0, others = 0;
            for (int round = 0; round < ROUNDS; round++) {
                rig.level.setBlockAndUpdate(target, Blocks.IRON_BLOCK.defaultBlockState());
                rig.level.setBlockAndUpdate(signal, Blocks.REDSTONE_BLOCK.defaultBlockState());
                helper.assertTrue(rig.level.getBlockState(target).isAir(), "Round " + round + " did not consume its iron block");
                for (var entity : rig.level.getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(1))) {
                    if (entity.getItem().is(stated.getItem())) { plates += entity.getItem().getCount(); } else { others += entity.getItem().getCount(); }
                    entity.discard();
                }
                rig.level.setBlockAndUpdate(signal, Blocks.AIR.defaultBlockState());
                if (round == ROUNDS / 2) {
                    rig.level.setBlockAndUpdate(signal, Blocks.REDSTONE_BLOCK.defaultBlockState());
                    rig.level.setBlockAndUpdate(target, Blocks.IRON_BLOCK.defaultBlockState());
                    helper.assertTrue(rig.level.getBlockState(target).is(Blocks.IRON_BLOCK), "A press that stayed powered pressed again");
                    rig.level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState()); rig.level.setBlockAndUpdate(signal, Blocks.AIR.defaultBlockState());
                    rig.level.setBlockAndUpdate(signal, Blocks.REDSTONE_BLOCK.defaultBlockState());
                    rig.level.setBlockAndUpdate(signal, Blocks.AIR.defaultBlockState());
                }
            }
            others += rig.looseStacks(target, 3);
            helper.assertTrue(plates == ROUNDS * stated.getCount() && others == 0,
                    "Press: " + ROUNDS + " iron blocks made " + plates + " " + stated.getItem() + " and " + others + " other items");
        } finally { rig.close(); }
        helper.succeed();
    }

    /**
     * Three pipe seals forward items (a hopper and a dropper on two faces into two containers), water (a mod pump into
     * a tank) and FE (the mod's generator into an electrolyzer). A seal keeps nothing; one facing another seal refuses.
     */
    static void pipeSeal(GameTestHelper helper, int timeoutTicks) {
        var rig = new AutomationRig(helper);
        try {
            BlockPos items = helper.absolutePos(new BlockPos(2, 3, 2)), fluid = items.offset(0, 0, 4), energy = items.offset(4, 0, 4);
            rig.room(items.offset(-2, -2, -2), energy.offset(2, 2, 2));
            rig.place(items, ModBlocks.PIPE_SEAL.get().defaultBlockState());
            rig.place(items.below(), Blocks.CHEST.defaultBlockState()); rig.place(items.east(), Blocks.BARREL.defaultBlockState());
            var hopper = rig.hopper(items.above(), Direction.DOWN, List.of(new ItemStack(Items.IRON_INGOT, 15)));
            var dropper = rig.dropper(items.west(), Direction.EAST, List.of(new ItemStack(Items.IRON_INGOT, ROUNDS - 15)));
            rig.place(items.north(), ModBlocks.PIPE_SEAL.get().defaultBlockState());
            rig.place(fluid, ModBlocks.PIPE_SEAL.get().defaultBlockState());
            rig.place(fluid.north(), PumpContent.BLOCK.get().defaultBlockState());
            rig.place(fluid.south(), ModBlocks.PRESSURIZED_TANK.get().defaultBlockState());
            rig.place(energy, ModBlocks.PIPE_SEAL.get().defaultBlockState());
            var power = new GeneratorFeed(rig, energy.west(), Items.BAMBOO, 2);
            rig.place(energy.east(), ModBlocks.ELECTROLYZER.get().defaultBlockState().setValue(ElectrolyzerBlock.FACING, Direction.NORTH));
            var seal = (PipeSealBlockEntity) rig.level.getBlockEntity(items);
            var pump = (PumpBlockEntity) rig.level.getBlockEntity(fluid.north());
            var tank = (PressurizedTankBlockEntity) rig.level.getBlockEntity(fluid.south());
            var sink = (ElectrolyzerBlockEntity) rig.level.getBlockEntity(energy.east());
            var chest = (Container) rig.level.getBlockEntity(items.below());
            var barrel = (Container) rig.level.getBlockEntity(items.east());
            var state = new Object() { int reservoir = ROUNDS * 1_000, supplied, subTick, settle = -1; boolean reloaded; };
            rig.drive(timeoutTicks, () -> {
                if (state.reservoir > 0) {
                    int filled = fluids(pump, Direction.UP).fill(new FluidStack(Fluids.WATER, Math.min(state.reservoir, 4_000)), IFluidHandler.FluidAction.EXECUTE);
                    state.reservoir -= filled; state.supplied += filled;
                }
                for (int i = 0; i < 16; i++) {
                    state.subTick++; rig.tickHoppers(); power.tick();
                    if (state.subTick % 16 == 0 && !AutomationRig.empty(dropper)) { rig.pulse(dropper); }
                }
                int moved = AutomationRig.count(chest, Items.IRON_INGOT) + AutomationRig.count(barrel, Items.IRON_INGOT);
                if (!state.reloaded && moved >= ROUNDS / 2) {
                    state.reloaded = true;
                    IItemHandler toSeal = seal.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.SOUTH).orElseThrow(() -> new IllegalStateException("Seal"));
                    helper.assertTrue(toSeal.getSlots() == 0 && toSeal.insertItem(0, new ItemStack(Items.IRON_INGOT), false).getCount() == 1,
                            "A seal forwarded into another seal");
                    for (BlockPos position : List.of(items, fluid, energy)) {
                        BlockEntity entity = rig.level.getBlockEntity(position); entity.load(entity.saveWithFullMetadata());
                    }
                }
                if (state.settle < 0 && state.reloaded && moved == ROUNDS && state.reservoir == 0 && pump.fluid().isEmpty() && power.spent()) { state.settle = 0; }
                return state.settle >= 0 && ++state.settle > 3;
            }, () -> {
                int moved = AutomationRig.count(chest, Items.IRON_INGOT) + AutomationRig.count(barrel, Items.IRON_INGOT);
                helper.assertTrue(moved == ROUNDS && AutomationRig.empty(hopper) && AutomationRig.empty(dropper)
                        && AutomationRig.count(barrel, Items.IRON_INGOT) > 0 && AutomationRig.count(chest, Items.IRON_INGOT) > 0,
                        "Seal items: " + moved + " of " + ROUNDS + " arrived through both faces");
                helper.assertTrue(state.supplied == ROUNDS * 1_000 && tank.fluidState().getAmount() == state.supplied,
                        "Seal water: supplied " + state.supplied + " mB, tank holds " + tank.fluidState().getAmount());
                long generated = power.generated(), accounted = (long) power.buffered() + sink.energyStored();
                helper.assertTrue(generated == accounted && generated == 2L * power.burnTicks() * 40,
                        "Seal FE: generator made " + generated + ", buffer " + power.buffered() + " + received " + sink.energyStored());
                helper.assertTrue(rig.looseStacks(items, 4) == 0, "Seal automation spilled items into the world");
            });
        } catch (RuntimeException | Error failure) { rig.close(); throw failure; }
    }

    private static IFluidHandler fluids(BlockEntity entity, Direction side) {
        return entity.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElseThrow(() -> new IllegalStateException("Fluid port"));
    }

    private DeviceAutomationRuns() { }
}
