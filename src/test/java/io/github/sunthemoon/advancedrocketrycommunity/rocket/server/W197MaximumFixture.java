package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** W197 benchmark input, derived from W164's retained R156 draft; never shipped in the normal JAR. */
final class W197MaximumFixture {
    static final int QUEUE = 64;
    private W197MaximumFixture() { }

    static RocketStructureSnapshot maximumSnapshot(RocketStructureSnapshot base) {
        var baseline = snapshot(base, 256, 0);
        int remaining = RocketLimits.MAX_TOTAL_NBT_BYTES - RocketNbtSize.uncompressedBytes(RocketSnapshotNbtCodec.encode(baseline));
        var maximum = snapshot(base, 256 + remaining / 128, remaining % 128);
        if (RocketNbtSize.uncompressedBytes(RocketSnapshotNbtCodec.encode(maximum)) != RocketLimits.MAX_TOTAL_NBT_BYTES) {
            throw new IllegalStateException("Maximum snapshot fixture differs");
        }
        return maximum;
    }
    private static RocketStructureSnapshot snapshot(RocketStructureSnapshot base, int bytes, int bonus) {
        return RocketStructureSnapshot.create(base.snapshotId(), base.sourceDimension(), base.sourceOrigin(), blocks(bytes, bonus),
                base.passengerAnchors(), new RocketStats(2048, 204800, 204800, 1000, 1, 1, 1, 128),
                base.createdAtGameTime(), base.propulsion().orElseThrow());
    }
    static List<RocketBlock> blocks(int bytes, int bonus) {
        var blocks = new ArrayList<RocketBlock>();
        var item = new ItemStack(Items.IRON_INGOT); var payload = new CompoundTag();
        // Fill the accepted snapshot byte ceiling, not the larger sum of separate BE allowances.
        var itemTag = new CompoundTag(); itemTag.putByteArray("w197_test_padding", new byte[1]); item.setTag(itemTag);
        var entry = item.save(new CompoundTag()); entry.putByte("Slot", (byte) 0);
        var items = new ListTag(); items.add(entry); payload.put("Items", items);
        int padding = bytes - RocketNbtSize.uncompressedBytes(payload) + 1;
        entry.getCompound("tag").putByteArray("w197_test_padding", new byte[padding]);
        var be = new RocketBlockEntityPayload(VanillaContainerRocketAdapter.ID, payload);
        entry.getCompound("tag").putByteArray("w197_test_padding", new byte[padding + 1]);
        var larger = new RocketBlockEntityPayload(VanillaContainerRocketAdapter.ID, payload);
        if (be.uncompressedBytes() != bytes || larger.uncompressedBytes() != bytes + 1) {
            throw new IllegalStateException("Payload fixture differs");
        }
        for (int i = 0; i < 2048; i++) {
            var at = i >= 2017 ? new RocketPosition(31, i - 2016, 31) : new RocketPosition(i % 32, i / 1024, i / 32 % 32);
            RocketBlockEntityPayload blockPayload = null;
            if (i < 128) {
                var copied = (i < bonus ? larger : be).data();
                var noise = new byte[padding + (i < bonus ? 1 : 0)];
                new Random(164L + i).nextBytes(noise);
                copied.getList("Items", 10).getCompound(0).getCompound("tag").putByteArray("w197_test_padding", noise);
                blockPayload = new RocketBlockEntityPayload(VanillaContainerRocketAdapter.ID, copied);
            }
            blocks.add(new RocketBlock(at, RocketBlockStateAdapter.capture((i < 128 ? Blocks.BARREL : Blocks.IRON_BLOCK).defaultBlockState()),
                    blockPayload));
        }
        return blocks;
    }

}
