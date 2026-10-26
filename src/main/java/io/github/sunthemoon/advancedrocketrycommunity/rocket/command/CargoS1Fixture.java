package io.github.sunthemoon.advancedrocketrycommunity.rocket.command;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.command.ResourceRestartReleaseFixture.require;

import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankSave;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockTags;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowPrefix;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/** Small fixed, opt-in S1 fixtures. No escrow admission or writer acknowledgements are synthesized. */
final class CargoS1Fixture {
    static final BlockPos ORIGIN = new BlockPos(72, 100, 72);
    static final UUID OWNER = new UUID(136, 1);
    static final UUID LEDGER = new UUID(136, 2);
    static final UUID OPERATION = new UUID(136, 3);
    static final List<String> VARIANTS = List.of("future", "invalid", "oversized", "marked", "missing");

    static void tags() {
        for (var block : List.of(ModBlocks.PRESSURIZED_TANK.get(), Blocks.CHEST, Blocks.BARREL)) {
            require(block.defaultBlockState().is(ModBlockTags.ROCKET_MOVABLE), "Production movable tag missing " + block);
        }
        require(!Blocks.TRAPPED_CHEST.defaultBlockState().is(ModBlockTags.ROCKET_MOVABLE), "Unproved trapped chest joined production tags");
    }

    static CompoundTag typedTag() {
        var tag = new CompoundTag();
        tag.putByte("b", (byte) 7); tag.putShort("s", (short) 7); tag.putInt("i", 7); tag.putLong("l", 7);
        tag.putByteArray("ba", new byte[] {1, 2}); tag.putIntArray("ia", new int[] {1, 2});
        tag.putLongArray("la", new long[] {1, 2});
        return tag;
    }

    static CompoundTag stack(int count) {
        var stack = new CompoundTag(); stack.putString("id", "minecraft:stone"); stack.putByte("Count", (byte) count);
        stack.put("tag", typedTag()); return stack;
    }

    static FluidStack fluid(int amount) {
        var fluid = new FluidStack(Fluids.WATER, amount); fluid.setTag(typedTag()); return fluid;
    }

    static RocketEntity seed(ServerLevel level, boolean items) {
        level.setBlockAndUpdate(ORIGIN, ModBlocks.ROCKET_MOTOR.get().defaultBlockState());
        level.setBlockAndUpdate(ORIGIN.above(), ModBlocks.ROCKET_SEAT.get().defaultBlockState());
        level.setBlockAndUpdate(ORIGIN.above(2), ModBlocks.GUIDANCE_COMPUTER.get().defaultBlockState());
        level.setBlockAndUpdate(ORIGIN.west(), ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState());
        var positions = List.of(ORIGIN.north(), ORIGIN.south(), ORIGIN.east(2));
        var blocks = List.of(Blocks.CHEST, Blocks.BARREL, Blocks.CHEST);
        for (int i = 0; i < positions.size(); i++) {
            var at = positions.get(i); level.setBlockAndUpdate(at, blocks.get(i).defaultBlockState());
            var root = new CompoundTag(); var inventory = new ListTag(); var entry = stack(17 + i);
            entry.putByte("Slot", (byte) (i * 13)); inventory.add(entry); root.put("Items", inventory);
            root.putString("CustomName", "{\"text\":\"S1 retained cargo\"}"); root.putString("Lock", "S1 lock");
            level.getBlockEntity(at).load(root);
        }
        level.setBlockAndUpdate(ORIGIN.east(), items ? Blocks.IRON_BLOCK.defaultBlockState()
                : ModBlocks.PRESSURIZED_TANK.get().defaultBlockState());
        if (!items) {
            var outer = new CompoundTag(); outer.put(TankSave.ROOT, TankSave.encode(fluid(3456)));
            level.getBlockEntity(ORIGIN.east()).load(outer);
        }
        var adapters = RocketBlockEntityAdapters.defaults();
        var task = new RocketStructureScanTask(new ServerLevelRocketScanWorld(level, adapters), level.dimension().location(),
                new RocketPosition(72, 100, 72), UUID.randomUUID(), level.getGameTime());
        var result = task.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        for (int n = 0; n < 64 && result.status() == RocketScanResult.Status.RUNNING; n++) {
            result = task.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        }
        require(result.status() == RocketScanResult.Status.SUCCESS, "S1 production-tag scan failed: " + result.issues());
        var snapshot = result.snapshot().orElseThrow();
        require(snapshot.blocks().size() == 8 && snapshot.stats().blockEntityCount() == (items ? 3 : 4), "S1 capture count");
        var assembled = new RocketAssemblyTransaction(new ServerLevelRocketTransactionWorld(level, adapters, OWNER),
                new RocketRegionLockManager(), new RocketOperationLedger(), RocketTransactionJournal.NO_OP)
                .execute(UUID.randomUUID(), snapshot);
        require(assembled.success(), "S1 assembly failed: " + assembled.code());
        var rocket = (RocketEntity) level.getEntity(assembled.rocketEntityId().orElseThrow());
        for (var role : RocketCargoRole.values()) {
            place(level, devicePosition(role), items, role, root(items, role, 10 + role.ordinal(), binding(rocket), "valid"));
            for (int i = 0; i < VARIANTS.size(); i++) {
                place(level, protectedPosition(role, i), items, role, root(items, role, 20 + 5 * role.ordinal() + i, null, VARIANTS.get(i)));
            }
        }
        return rocket;
    }

    static BlockPos devicePosition(RocketCargoRole role) { return new BlockPos(72 + role.ordinal() * 2, 100, 76); }
    static BlockPos protectedPosition(RocketCargoRole role, int index) { return new BlockPos(64 + role.ordinal() * 5 + index, 100, 79); }
    static String key(boolean items) { return items ? RocketItemCargoRoot.KEY : RocketFluidCargoRoot.KEY; }
    static LoaderBinding binding(RocketEntity rocket) {
        return new LoaderBinding(rocket.assemblyTransactionId().orElseThrow(), rocket.level().dimension().location(),
                rocket.getUUID(), rocket.blockPosition(), null);
    }

    static CompoundTag root(boolean items, RocketCargoRole role, int identity, LoaderBinding binding, String variant) {
        var id = new UUID(136, identity);
        var root = items ? RocketItemCargoRoot.placed(OWNER, id, role).withSlot(0, stack(23)).withBinding(binding).encode()
                : RocketFluidCargoRoot.placed(OWNER, id, role, LoaderTank.of(fluid(2345))).withBinding(binding).encode();
        switch (variant) {
            case "future" -> { root.putInt("schema_version", 2); root.put("extension", typedTag()); }
            case "invalid" -> {
                if (items) { root.getCompound("buffer").getList("slots", 10).getCompound(0).putByte("Count", (byte) -1); }
                else { root.getCompound("tank").putInt("Amount", -1); }
            }
            case "oversized" -> root.putString("extension", "x".repeat(33_000));
            case "marked" -> root.put("resource_escrow", new RocketEscrowSection(LEDGER, 1,
                    List.of(new EscrowPrefix.Marker(OPERATION, 1, role.markerRole(), 1))).encode());
            case "missing" -> {
                if (items) { root.getCompound("buffer").getList("slots", 10).getCompound(0).putString("id", "s1missing:item"); }
                else { root.getCompound("tank").putString("FluidName", "s1missing:fluid"); }
            }
            case "valid" -> { }
            default -> throw new IllegalArgumentException(variant);
        }
        return root;
    }

    static BlockEntity place(ServerLevel level, BlockPos at, boolean items, RocketCargoRole role, CompoundTag root) {
        var block = items ? (role == RocketCargoRole.LOAD ? ModBlocks.ROCKET_ITEM_LOADER : ModBlocks.ROCKET_ITEM_UNLOADER)
                : (role == RocketCargoRole.LOAD ? ModBlocks.ROCKET_FLUID_LOADER : ModBlocks.ROCKET_FLUID_UNLOADER);
        level.setBlockAndUpdate(at, block.get().defaultBlockState());
        var entity = level.getBlockEntity(at); var outer = new CompoundTag(); outer.put(key(items), root);
        entity.load(outer); entity.setChanged(); return entity;
    }

    static RocketCargoStatus status(BlockEntity device) {
        return device instanceof RocketItemCargoBlockEntity item ? item.displayStatus()
                : ((RocketFluidCargoBlockEntity) device).displayStatus();
    }

    static void checkDevices(ServerLevel level, boolean items, RocketEntity rocket) {
        for (var role : RocketCargoRole.values()) {
            var device = level.getBlockEntity(devicePosition(role));
            require(device != null && device.onlyOpCanSetNbt() && status(device) == RocketCargoStatus.ADMISSION_DISABLED,
                    "Valid device missing, writable or admission enabled");
            require(root(items, role, 10 + role.ordinal(), binding(rocket), "valid")
                    .equals(device.saveWithFullMetadata().get(key(items))), "Valid root changed");
            for (int i = 0; i < VARIANTS.size(); i++) {
                var protectedDevice = level.getBlockEntity(protectedPosition(role, i));
                require(protectedDevice != null, "Protected device missing");
                boolean protectedRoot = items ? ((RocketItemCargoBlockEntity) protectedDevice).protectedFromRemoval()
                        : ((RocketFluidCargoBlockEntity) protectedDevice).protectedFromRemoval();
                require(protectedRoot && root(items, role, 20 + 5 * role.ordinal() + i, null, VARIANTS.get(i))
                        .equals(protectedDevice.saveWithFullMetadata().get(key(items))), "Protected root changed: " + role + "/" + VARIANTS.get(i));
            }
        }
    }

    /** Exercise the production restore adapter, checking every state and full typed payload before removing the copies. */
    static void restorable(ServerLevel level, RocketEntity rocket) {
        var snapshot = rocket.snapshot().orElseThrow();
        var world = new ServerLevelRocketTransactionWorld(level, RocketBlockEntityAdapters.defaults(), rocket.ownerId().orElseThrow());
        require(world.canRestoreSnapshot(snapshot), "Snapshot is no longer restorable");
        for (var block : snapshot.blocks()) {
            var at = new RocketPosition(104, 100, 72).add(block.position());
            var expected = new RocketWorldBlock(block.state(), block.blockEntityPayload().orElse(null));
            require(world.placeBlockIfEmpty(at, expected), "Payload restoration refused");
            require(world.readBlock(at).filter(expected::equals).isPresent(), "Restored state or typed payload differs");
            require(world.removeBlockNoDrops(at, expected), "Restored temporary block removal failed");
        }
    }

    private CargoS1Fixture() { }
}
