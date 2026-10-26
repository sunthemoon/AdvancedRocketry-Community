package io.github.sunthemoon.advancedrocketrycommunity.rocket.forge;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotException;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VanillaContainerRocketAdapterTest {
    private static final VanillaContainerRocketAdapter ADAPTER = new VanillaContainerRocketAdapter();
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void captureDoesNotSerializeTheBlockEntityOrUnpackItsLoot() {
        for (boolean chest : new boolean[] {true, false}) {
            RandomizableContainerBlockEntity target = chest ? new GuardedChest() : new GuardedBarrel();
            target.load(inventory(27, 64));
            assertEquals(27, assertDoesNotThrow(() -> ADAPTER.capture(target)).data().getList("Items", 10).size());
            target.setLootTable(ResourceLocation.tryParse("minecraft:chests/simple_dungeon"), 37L);
            var captured = assertDoesNotThrow(() -> ADAPTER.capture(target)).data();
            assertEquals("minecraft:chests/simple_dungeon", captured.getString("LootTable"));
            assertEquals(37L, captured.getLong("LootTableSeed"));
            assertFalse(captured.contains("Items"));
        }
    }

    @Test void everyOccupancySlotAndStackCountMatchesNativeWhitelistedBytes() {
        for (boolean chest : new boolean[] {true, false}) {
            var target = target(chest);
            for (int occupied = 0; occupied <= 27; occupied++) {
                target.load(inventory(occupied, 64));
                identical(target);
            }
            for (int slot = 0; slot < 27; slot++) {
                var data = inventory(1, 1);
                data.getList("Items", 10).getCompound(0).putByte("Slot", (byte) slot);
                target.load(data);
                identical(target);
            }
            for (int count = 1; count <= 64; count++) {
                target.load(inventory(27, count));
                identical(target);
            }
        }
    }

    @Test void lootWithAndWithoutSeedRetainsNativeBytesAndDoesNotRoll() {
        for (boolean chest : new boolean[] {true, false}) {
            for (long seed : new long[] {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
                var target = target(chest);
                target.setLootTable(ResourceLocation.tryParse("minecraft:chests/simple_dungeon"), seed);
                identical(target);
                var data = ADAPTER.capture(target).data();
                assertFalse(data.contains("Items"));
                assertEquals(seed != 0, data.contains("LootTableSeed"));
                assertEquals(seed, data.getLong("LootTableSeed"));
            }
        }
    }

    @Test void customNameLockAndUnapprovedDataKeepExactlyTheOldPayload() {
        for (boolean chest : new boolean[] {true, false}) {
            for (String lock : List.of("", "key", "x".repeat(256))) {
                var target = target(chest);
                var data = inventory(27, 17);
                data.putString("Lock", lock);
                data.putString("CustomName", Component.Serializer.toJson(Component.literal("Cargo \u00e9 \u7bb1")));
                data.putString("ignored", "must not enter payload");
                target.load(data);
                target.getPersistentData().putString("unrelated", "not adapter-owned");
                identical(target);
                assertFalse(ADAPTER.capture(target).data().contains("ForgeData"));
            }
        }
    }

    @Test void exactMaximumPayloadMatchesNativeBytesAndOneMoreByteIsRefused() {
        for (boolean chest : new boolean[] {true, false}) {
            var target = target(chest);
            target.load(blob(0));
            int overhead = legacy(target).uncompressedBytes();
            int size = RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES - overhead;
            target.load(blob(size));
            identical(target);
            assertEquals(RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES, ADAPTER.capture(target).uncompressedBytes());
            target.load(blob(size + 1));
            assertThrows(RocketSnapshotException.class, () -> legacy(target));
            assertThrows(RocketSnapshotException.class, () -> ADAPTER.capture(target));
        }
    }

    @Test void captureIsDefensiveAndTheExistingMetadataBoundsRemain() {
        for (boolean chest : new boolean[] {true, false}) {
            var target = target(chest);
            target.load(inventory(1, 7));
            var captured = ADAPTER.capture(target);
            target.clearContent();
            assertEquals(7, captured.data().getList("Items", 10).getCompound(0).getByte("Count"));
            captured.data().remove("Items");
            assertTrue(captured.data().contains("Items"));
            var tooLong = inventory(0, 1);
            tooLong.putString("Lock", "x".repeat(257));
            target.load(tooLong);
            assertThrows(IllegalArgumentException.class, () -> ADAPTER.capture(target));
        }
    }

    private static void identical(RandomizableContainerBlockEntity target) {
        var expected = legacy(target);
        var actual = ADAPTER.capture(target);
        assertEquals(expected.adapterId(), actual.adapterId());
        assertEquals(expected, actual);
        assertEquals(expected.uncompressedBytes(), actual.uncompressedBytes());
        assertArrayEquals(bytes(expected.data()), bytes(actual.data()));
    }

    // Native fixtures already have ascending slots and valid values. Select precisely the old
    // whitelist, independently of the production direct-reader implementation.
    private static RocketBlockEntityPayload legacy(RandomizableContainerBlockEntity target) {
        var nativeTag = target.saveWithoutMetadata();
        var approved = new CompoundTag();
        for (String key : List.of("Items", "LootTable", "LootTableSeed", "CustomName", "Lock")) {
            if (nativeTag.contains(key)) { approved.put(key, nativeTag.get(key).copy()); }
        }
        return new RocketBlockEntityPayload(VanillaContainerRocketAdapter.ID, approved);
    }

    private static byte[] bytes(CompoundTag tag) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) { NbtIo.write(tag, out); }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }

    private static RandomizableContainerBlockEntity target(boolean chest) {
        return chest ? new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState())
                : new BarrelBlockEntity(BlockPos.ZERO, Blocks.BARREL.defaultBlockState());
    }

    private static CompoundTag inventory(int occupied, int count) {
        var data = new CompoundTag();
        var items = new ListTag();
        for (int slot = 0; slot < occupied; slot++) {
            var item = new ItemStack(Items.DIAMOND, count).save(new CompoundTag());
            item.putByte("Slot", (byte) slot);
            items.add(item);
        }
        data.put("Items", items);
        return data;
    }

    private static CompoundTag blob(int size) {
        var data = inventory(1, 1);
        var tag = new CompoundTag();
        tag.putByteArray("blob", new byte[size]);
        data.getList("Items", 10).getCompound(0).put("tag", tag);
        return data;
    }

    private static final class GuardedChest extends ChestBlockEntity {
        GuardedChest() { super(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()); }
        @Override protected void saveAdditional(CompoundTag tag) { throw new AssertionError("BE serialization"); }
        @Override public void unpackLootTable(Player player) { throw new AssertionError("Loot unpacking"); }
        @Override public ItemStack getItem(int slot) { throw new AssertionError("Container.getItem"); }
    }

    private static final class GuardedBarrel extends BarrelBlockEntity {
        GuardedBarrel() { super(BlockPos.ZERO, Blocks.BARREL.defaultBlockState()); }
        @Override protected void saveAdditional(CompoundTag tag) { throw new AssertionError("BE serialization"); }
        @Override public void unpackLootTable(Player player) { throw new AssertionError("Loot unpacking"); }
        @Override public ItemStack getItem(int slot) { throw new AssertionError("Container.getItem"); }
    }
}
