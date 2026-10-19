package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.PressurizedTankBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankSave;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockTags;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoMutation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** C-P host and pure mutation fixtures, not cargo transaction or crash-safe disassembly evidence. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketCargoHostGameTests {
    @GameTest(template = "rocket_test", batch = "cargo_host", timeoutTicks = 40)
    public static void productionTagsAssembleTankChestAndBarrel(GameTestHelper helper) {
        var origin = new BlockPos(3, 2, 3);
        helper.setBlock(origin, ModBlocks.ROCKET_MOTOR.get());
        helper.setBlock(origin.above(), ModBlocks.ROCKET_SEAT.get());
        helper.setBlock(origin.above(2), ModBlocks.GUIDANCE_COMPUTER.get());
        helper.setBlock(origin.east(), ModBlocks.PRESSURIZED_TANK.get());
        helper.setBlock(origin.west(), Blocks.CHEST); helper.setBlock(origin.south(), Blocks.BARREL);
        helper.setBlock(origin.north(), ModBlocks.ROCKET_FUEL_TANK.get());
        var tank = (PressurizedTankBlockEntity) helper.getBlockEntity(origin.east());
        load(tank, new FluidStack(Fluids.WATER, 749));
        for (var position : List.of(origin.east(), origin.west(), origin.south())) {
            helper.assertTrue(helper.getBlockState(position).is(ModBlockTags.ROCKET_MOVABLE), "Missing production movable entry");
        }
        EntitySections.whenLoaded(helper, () -> assemble(helper, origin), origin, origin.east(), origin.west(), origin.south());
    }

    private static void assemble(GameTestHelper helper, BlockPos origin) {
        var absolute = helper.absolutePos(origin); var level = helper.getLevel(); var adapters = RocketBlockEntityAdapters.defaults();
        var task = new RocketStructureScanTask(new ServerLevelRocketScanWorld(level, adapters), level.dimension().location(),
                new RocketPosition(absolute.getX(), absolute.getY(), absolute.getZ()), UUID.randomUUID(), level.getGameTime());
        var result = task.step(256);
        for (int i = 0; i < 64 && result.status() == RocketScanResult.Status.RUNNING; i++) { result = task.step(256); }
        helper.assertTrue(result.status() == RocketScanResult.Status.SUCCESS, "Production tag scan failed: " + result.issues());
        var snapshot = result.snapshot().orElseThrow();
        helper.assertTrue(snapshot.stats().blockEntityCount() == 3, "Tank/chest/barrel capture count changed");
        helper.assertTrue(snapshot.stats().fuelCapacity() == 1000, "Cargo tank was counted as propulsion capacity");
        var payload = snapshot.blocks().stream().flatMap(b -> b.blockEntityPayload().stream())
                .filter(p -> p.adapterId().equals(PressurizedTankRocketAdapter.ID)).findFirst().orElseThrow();
        helper.assertTrue(PressurizedTankRocketAdapter.fluid(payload.data()).getAmount() == 749, "Tank cargo was not captured");
        var assembled = new RocketAssemblyTransaction(new ServerLevelRocketTransactionWorld(level, adapters, UUID.randomUUID()),
                new RocketRegionLockManager(), new RocketOperationLedger(), RocketTransactionJournal.NO_OP).execute(UUID.randomUUID(), snapshot);
        helper.assertTrue(assembled.success(), "Tank assembly refused: " + assembled.code());
        var entity = level.getEntity(assembled.rocketEntityId().orElseThrow());
        helper.assertTrue(entity != null && helper.getBlockState(origin.east()).isAir(), "Assembly did not move the tank");
        entity.discard();
    }

    @GameTest(template = "rocket_test", batch = "cargo_host", timeoutTicks = 40)
    public static void afterPayloadRestoresExactlyAndCargoIsNotPropulsion(GameTestHelper helper) {
        var pos = new BlockPos(2, 2, 2); helper.setBlock(pos, ModBlocks.PRESSURIZED_TANK.get());
        var tank = (PressurizedTankBlockEntity) helper.getBlockEntity(pos); var adapters = RocketBlockEntityAdapters.defaults();
        load(tank, new FluidStack(Fluids.WATER, 2000));
        var payload = adapters.capture(tank).payload();
        var propulsion = new RocketPropulsion(RocketPropulsion.Mode.MONO, 5000, 0, 1, 1,
                RocketPropulsion.Settings.DEFAULTS.withAllowedFluids(Map.of(RocketPropulsion.Role.MONO, List.of("minecraft:water"),
                        RocketPropulsion.Role.BIPROPELLANT_FUEL, List.of(), RocketPropulsion.Role.OXIDIZER, List.of(),
                        RocketPropulsion.Role.WORKING_FLUID, List.of())));
        var before = RocketStructureSnapshot.create(UUID.randomUUID(), helper.getLevel().dimension().location(), new RocketPosition(0, 64, 0),
                List.of(new RocketBlock(new RocketPosition(0, 0, 0), RocketBlockStateAdapter.capture(tank.getBlockState()), payload)),
                List.of(), new RocketStats(1, 1, 1, 5000, 1, 0, 1, 1), 10, propulsion);
        var fuel = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState.empty(propulsion)
                .withFluidBank(false, net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), null, 3000);
        String fuelSignature = fuel.resourceSignature();
        for (int amount : new int[] {1, 749, 1000}) {
            for (boolean loading : new boolean[] {true, false}) {
                var delta = new EscrowCargoPayload(new EscrowCargoPayload.Cursor(before.snapshotId(), 0, 0, 0,
                        PressurizedTankRocketAdapter.ID.toString()), new FluidStack(Fluids.WATER, amount).writeToNBT(new CompoundTag()));
                var after = RocketCargoMutation.apply(before, delta, loading);
                var afterPayload = after.blocks().get(0).blockEntityPayload().orElseThrow();
                helper.assertTrue(adapters.restore(tank, afterPayload) && afterPayload.equals(adapters.capture(tank).payload()), "After payload did not restore exactly");
                helper.assertTrue(tank.fluidState().getAmount() == 2000 + (loading ? amount : -amount), "Wrong cargo balance");
                helper.assertTrue(fuelSignature.equals(fuel.resourceSignature()) && fuel.totalAmount() == 3000
                        && before.propulsion().equals(after.propulsion()), "Cargo was mistaken for same-Fluid propulsion");
            }
        }
        var overCapacity = PressurizedTankRocketAdapter.payload(new FluidStack(Fluids.WATER, 300_000));
        helper.assertTrue(adapters.restore(tank, overCapacity) && tank.fluidState().getAmount() == 300_000,
                "Restore must not truncate a balance above the configured capacity");
        var retained = new CompoundTag(); var future = TankSave.encode(new FluidStack(Fluids.WATER, 4)); future.putInt("schema", 2);
        retained.put(TankSave.ROOT, future); tank.load(retained);
        helper.assertTrue(tank.repairRequired() && !adapters.capture(tank).supported(), "Retained tank was captured");
        helper.succeed();
    }
    private static void load(PressurizedTankBlockEntity tank, FluidStack fluid) {
        var outer = new CompoundTag(); outer.put(TankSave.ROOT, TankSave.encode(fluid)); tank.load(outer);
    }
}
