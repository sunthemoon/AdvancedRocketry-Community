package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/** Included only by w144NativeJar; no JUnit or test bootstrap dependencies in the dedicated server. */
final class CargoNativeFixtures {
    static final int MIB = 1_048_576;
    private CargoNativeFixtures() { }

    static RocketStructureSnapshot base(ServerLevel level, BlockPos origin, boolean item, boolean load) {
        return item ? ItemCargoReconciliationFixtures.snapshot(level, origin, load)
                : EscrowCargoPreparationGameTests.snapshot(level.dimension().location(), origin, false,
                        List.of(load ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, 2000)));
    }

    static RocketStructureSnapshot expanded(RocketStructureSnapshot base, int aggregate) {
        var blocks = new ArrayList<RocketBlock>(); var target = base.blocks().get(0); blocks.add(target);
        int remaining = aggregate - target.blockEntityPayload().orElseThrow().uncompressedBytes();
        for (int i = 1; i < 2048; i++) {
            var at = new RocketPosition(i % 16, i / 256, i / 16 % 16);
            if (i < 128) {
                int bytes = remaining / (128 - i); var payload = chest(bytes); remaining -= bytes;
                blocks.add(new RocketBlock(at, new RocketBlockState(ResourceLocation.tryParse("minecraft:chest"),
                        Map.of("facing", "north", "type", "single", "waterlogged", "false")), payload));
            } else { blocks.add(new RocketBlock(at, new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of()))); }
        }
        var result = RocketStructureSnapshot.create(base.snapshotId(), base.sourceDimension(), base.sourceOrigin(), blocks,
                base.passengerAnchors(), new io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats(
                        2048, 100, 1000, base.stats().fuelCapacity(), 1, base.passengerAnchors().size(), 1, 128),
                base.createdAtGameTime(), base.propulsion().orElseThrow());
        EscrowTags.require(aggregate(result) == aggregate, "Native aggregate fixture differs"); return result;
    }
    private static RocketBlockEntityPayload chest(int bytes) {
        int overhead = chestBlobs(0).uncompressedBytes();
        EscrowTags.require(bytes >= overhead, "Native chest size below framing");
        return chestBlobs(bytes - overhead);
    }
    private static RocketBlockEntityPayload chestBlobs(int bytes) {
        var entries = new ListTag();
        for (int i = 0; i < 3; i++) {
            var stack = new ItemStack(Items.DIRT, 1).save(new CompoundTag()); var tag = new CompoundTag();
            tag.putByteArray("blob", new byte[bytes / 3 + (i < bytes % 3 ? 1 : 0)]); stack.put("tag", tag);
            EscrowItemStackCodec.validateNative(stack); stack.putByte("Slot", (byte) (i + 1)); entries.add(stack);
        }
        var data = new CompoundTag(); data.put("Items", entries);
        return new RocketBlockEntityPayload(VanillaContainerRocketAdapter.ID, data);
    }
    static RocketStructureSnapshot after(RocketStructureSnapshot before, boolean item, boolean load) {
        var cursor = new EscrowCargoPayload.Cursor(before.snapshotId(), 0, 0, 0,
                before.blocks().get(0).blockEntityPayload().orElseThrow().adapterId().toString());
        return item ? RocketItemCargoMutation.apply(before, new EscrowItemCargoPayload(cursor,
                ItemCargoReconciliationFixtures.stack(4), 0, 0), load)
                : RocketCargoMutation.apply(before, new EscrowCargoPayload(cursor,
                        new FluidStack(Fluids.WATER, 1000).writeToNBT(new CompoundTag())), load);
    }
    static int encoded(RocketStructureSnapshot snapshot) { return RocketNbtSize.uncompressedBytes(RocketSnapshotNbtCodec.encode(snapshot)); }
    static int aggregate(RocketStructureSnapshot snapshot) {
        return snapshot.blocks().stream().flatMap(block -> block.blockEntityPayload().stream())
                .mapToInt(RocketBlockEntityPayload::uncompressedBytes).sum();
    }
    static boolean fits(RocketStructureSnapshot base, int aggregate, boolean item, boolean load) {
        try {
            var before = expanded(base, aggregate); encoded(before); encoded(after(before, item, load)); return true;
        } catch (RocketSnapshotException failure) {
            EscrowTags.require(failure.code() == io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode.SNAPSHOT_DATA_TOO_LARGE,
                    "Unexpected native fixture construction failure: " + failure); return false;
        }
    }
    static RocketStructureSnapshot largest(RocketStructureSnapshot base, boolean item, boolean load) {
        int low = 65_536, high = MIB;
        EscrowTags.require(fits(base, low, item, load) && !fits(base, high, item, load), "Native boundary is not bracketed");
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (fits(base, middle, item, load)) { low = middle; } else { high = middle - 1; }
        }
        EscrowTags.require(!fits(base, low + 1, item, load), "Missing native adjacent refusal");
        return expanded(base, low);
    }
}
