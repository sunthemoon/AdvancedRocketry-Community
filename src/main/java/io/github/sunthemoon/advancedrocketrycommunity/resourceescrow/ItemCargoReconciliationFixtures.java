package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Test-gated native item fixtures with partial, typed-NBT deltas and unchanged propulsion. */
final class ItemCargoReconciliationFixtures {
    private ItemCargoReconciliationFixtures() { }
    static CompoundTag stack(int count) {
        var result = new ItemStack(Items.STONE, count).save(new CompoundTag()); var tag = new CompoundTag();
        tag.putByte("b", (byte) 7); tag.putShort("s", (short) 7); tag.putInt("i", 7); tag.putLong("l", 7);
        tag.putByteArray("ba", new byte[] {1, 2}); tag.putIntArray("ia", new int[] {1, 2});
        tag.putLongArray("la", new long[] {1, 2}); result.put("tag", tag); return result;
    }
    static EscrowItemBuffer buffer(boolean load) {
        return new EscrowItemBuffer(List.of(stack(load ? 10 : 60), new CompoundTag(), new CompoundTag(), new CompoundTag()));
    }
    static RocketStructureSnapshot snapshot(ServerLevel level, BlockPos origin, boolean load) {
        EscrowTestEndpoints.requireEnabled(level.getServer());
        var base = EscrowCargoPreparationGameTests.snapshot(level.dimension().location(), origin, false, List.of());
        var entry = stack(load ? 60 : 10); entry.putByte("Slot", (byte) 0);
        var entries = new ListTag(); entries.add(entry); var data = new CompoundTag(); data.put("Items", entries);
        var block = new RocketBlock(new RocketPosition(0, 0, 0),
                new RocketBlockState(ResourceLocation.tryParse("minecraft:chest"),
                        Map.of("facing", "north", "type", "single", "waterlogged", "false")),
                new RocketBlockEntityPayload(VanillaContainerRocketAdapter.ID, data));
        return RocketStructureSnapshot.create(UUID.randomUUID(), base.sourceDimension(), base.sourceOrigin(), List.of(block),
                List.of(block.position()), new RocketStats(1, 100, 1000, 2000, 1, 1, 1, 1), 25, base.propulsion().orElseThrow());
    }
    static RocketEntity create(ServerLevel level, BlockPos origin, boolean load) {
        var rocket = java.util.Objects.requireNonNull(ModEntities.ROCKET.get().create(level));
        rocket.initialize(snapshot(level, origin, load), UUID.randomUUID(), UUID.randomUUID());
        var flight = rocket.flightData().orElseThrow();
        rocket.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false,
                ResourceLocation.tryParse("minecraft:water"), null, 1000), 25));
        EscrowTags.require(level.addFreshEntity(rocket), "Item fixture publication failed"); return rocket;
    }
    static int amount(EscrowItemBuffer buffer) {
        return buffer.slots().stream().mapToInt(saved -> {
            if (saved.isEmpty()) { return 0; }
            EscrowTags.require(saved.equals(stack(saved.getByte("Count"))), "Item device changed typed NBT");
            return saved.getByte("Count");
        }).sum();
    }
    static int amount(RocketEntity rocket) {
        return rocket.snapshot().orElseThrow().blocks().stream().mapToInt(block -> block.blockEntityPayload().map(payload -> {
            int total = 0; var items = payload.data().getList("Items", 10);
            for (int i = 0; i < items.size(); i++) {
                var item = items.getCompound(i); var expected = stack(item.getByte("Count")); expected.putByte("Slot", item.getByte("Slot"));
                EscrowTags.require(expected.equals(item), "Item fixture changed typed NBT"); total += item.getByte("Count");
            }
            return total;
        }).orElse(0)).sum();
    }
}
