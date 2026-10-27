package io.github.sunthemoon.advancedrocketrycommunity.rocket.command;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.command.ResourceRestartReleaseFixture.require;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

/** Fixed W176 native fixtures. These never enable admission or synthesize a durable acknowledgement. */
final class FuelLoaderS1Fixture {
    static final UUID OWNER = new UUID(176, 1);
    static final BlockPos ORIGIN = new BlockPos(72, 100, 72);
    static final BlockPos DEVICE = new BlockPos(72, 100, 76);
    static final BlockPos OLD_LOADER = new BlockPos(260, 101, 256);
    static final List<String> VARIANTS = List.of("future", "invalid", "oversized", "work-limit", "missing");

    static RocketEntity seed(ServerLevel level) {
        level.setBlockAndUpdate(ORIGIN, ModBlocks.ROCKET_MOTOR.get().defaultBlockState());
        level.setBlockAndUpdate(ORIGIN.above(), ModBlocks.ROCKET_SEAT.get().defaultBlockState());
        level.setBlockAndUpdate(ORIGIN.above(2), ModBlocks.GUIDANCE_COMPUTER.get().defaultBlockState());
        level.setBlockAndUpdate(ORIGIN.west(), ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState());
        var adapters = RocketBlockEntityAdapters.defaults();
        var scan = new RocketStructureScanTask(new ServerLevelRocketScanWorld(level, adapters), level.dimension().location(),
                new RocketPosition(72, 100, 72), UUID.randomUUID(), level.getGameTime());
        var result = scan.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        for (int i = 0; i < 64 && result.status() == RocketScanResult.Status.RUNNING; i++) {
            result = scan.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        }
        require(result.status() == RocketScanResult.Status.SUCCESS, "Loader S1 scan: " + result.issues());
        var assembled = new RocketAssemblyTransaction(new ServerLevelRocketTransactionWorld(level, adapters, OWNER),
                new RocketRegionLockManager(), new RocketOperationLedger(), RocketTransactionJournal.NO_OP)
                .execute(UUID.randomUUID(), result.snapshot().orElseThrow());
        require(assembled.success(), "Loader S1 assembly: " + assembled.code());
        var rocket = (RocketEntity) level.getEntity(assembled.rocketEntityId().orElseThrow());
        place(level, DEVICE, root(10, binding(rocket), "valid"));
        for (int i = 0; i < VARIANTS.size(); i++) { place(level, protectedAt(i), root(20 + i, null, VARIANTS.get(i))); }
        return rocket;
    }

    static LoaderBinding binding(RocketEntity rocket) {
        return new LoaderBinding(rocket.assemblyTransactionId().orElseThrow(), rocket.level().dimension().location(),
                rocket.getUUID(), rocket.blockPosition(), null);
    }

    static BlockPos protectedAt(int index) { return new BlockPos(64 + index, 100, 79); }

    static CompoundTag root(int identity, LoaderBinding binding, String variant) {
        var tank = LoaderTank.of(ModIdentity.id("rocket_fuel"), CargoS1Fixture.typedTag(), 2345);
        var root = new LoaderRoot(OWNER, new UUID(176, identity), binding, tank, LoaderEscrowSection.neutral()).encode();
        switch (variant) {
            case "valid" -> { }
            case "future" -> { root.putInt("schema_version", 2); root.put("extension", CargoS1Fixture.typedTag()); }
            case "invalid" -> root.getCompound("tank").putInt("Amount", -1);
            case "oversized" -> root.putString("extension", "x".repeat(33_000));
            case "work-limit" -> root.putByteArray("opaque", new byte[2_097_153]);
            case "missing" -> root.getCompound("tank").putString("FluidName", "s1missing:fluid");
            default -> throw new IllegalArgumentException(variant);
        }
        return root;
    }

    static FluidFuelLoaderBlockEntity place(ServerLevel level, BlockPos at, CompoundTag root) {
        level.setBlockAndUpdate(at, ModBlocks.FLUID_FUEL_LOADER.get().defaultBlockState());
        var device = (FluidFuelLoaderBlockEntity) level.getBlockEntity(at);
        var outer = new CompoundTag(); outer.put(LoaderRoot.KEY, root); device.load(outer); device.setChanged(); return device;
    }

    static void checkDevices(ServerLevel level, RocketEntity rocket) {
        var device = (FluidFuelLoaderBlockEntity) level.getBlockEntity(DEVICE);
        require(device != null && device.onlyOpCanSetNbt() && device.attempt() == FluidFuelLoaderStatus.ADMISSION_DISABLED,
                "Valid loader missing, writable or admission not disabled");
        var expected = root(10, binding(rocket), "valid");
        var port = device.getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElseThrow();
        for (Direction side : Direction.values()) {
            require(device.getCapability(ForgeCapabilities.FLUID_HANDLER, side).resolve().orElseThrow() == port, "Different sided port");
        }
        require(port.drain(749, FluidAction.SIMULATE).getAmount() == 749 && port.fill(device.tank().fluidStack(), FluidAction.SIMULATE) == 2345,
                "Valid loader port simulation refused");
        require(expected.equals(device.saveWithoutMetadata().get(LoaderRoot.KEY)), "Valid root changed");
        for (int i = 0; i < VARIANTS.size(); i++) {
            var protectedDevice = (FluidFuelLoaderBlockEntity) level.getBlockEntity(protectedAt(i));
            require(protectedDevice != null && protectedDevice.protectedFromRemoval() && !protectedDevice.unkeepable()
                    && !protectedDevice.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent()
                    && root(20 + i, null, VARIANTS.get(i)).equals(protectedDevice.saveWithoutMetadata().get(LoaderRoot.KEY)),
                    "Protected root changed or acquired native denial: " + VARIANTS.get(i));
        }
    }

    static CompoundTag oldRoot() {
        var item = new CompoundTag(); item.putString("id", "minecraft:bucket"); item.putByte("Count", (byte) 1);
        item.put("tag", CargoS1Fixture.typedTag());
        return FuelLoaderStorage.encode(new FuelLoaderData(FuelLoaderData.Role.OUTPUT, item, 0, OWNER, null, null));
    }

    static void checkOld(ServerLevel level) {
        var device = level.getBlockEntity(OLD_LOADER);
        require(device instanceof FuelLoaderBlockEntity && device.onlyOpCanSetNbt(), "Old item loader absent or writable");
        var port = device.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
        require(port.getSlots() == 1 && port.extractItem(0, 1, true).getCount() == 1, "Old loader output simulation changed");
        require(oldRoot().equals(device.saveWithoutMetadata().get(FuelLoaderStorage.DATA_KEY)), "Old item loader root changed");
    }
    private FuelLoaderS1Fixture() { }
}
