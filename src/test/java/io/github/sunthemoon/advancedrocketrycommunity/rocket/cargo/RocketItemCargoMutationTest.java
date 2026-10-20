package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.ItemCargoTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RocketItemCargoMutationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest @ValueSource(strings = {"minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel"})
    void bothDirectionsPreserveIdentityGeometryPropulsionNamesAndLocks(String id) throws Exception {
        var block = ResourceLocation.tryParse(id);
        var name = Component.Serializer.toJson(Component.literal("Cargo hold"));
        var before = snapshot(block(0, state(block), container(name, "key",
                entry(0, stack(Items.STONE, 10)), entry(5, stack(Items.DIRT, 3)))),
                block(1, state(BARREL), container(null, null, entry(0, stack(Items.STONE, 1)))));
        var encodedBefore = RocketSnapshotNbtCodec.encode(before);
        for (boolean load : new boolean[] {true, false}) {
            var delta = load ? delta(before, 0, stack(Items.STONE, 5), 1, 0) : delta(before, 0, stack(Items.DIRT, 3), 5, 2);
            var after = RocketItemCargoMutation.apply(before, delta, load);
            var slots = slots(after, 0);
            if (load) {
                assertEquals(15, count(slots.get(0))); assertEquals(3, count(slots.get(5)));
            } else {
                assertEquals(10, count(slots.get(0))); assertFalse(slots.containsKey(5));
            }
            assertEquals(name, data(after, 0).getString("CustomName")); assertEquals("key", data(after, 0).getString("Lock"));
            assertEquals(java.util.Set.of("Items", "CustomName", "Lock"), data(after, 0).getAllKeys());
            assertEquals(before.snapshotId(), after.snapshotId());
            assertEquals(before.sourceDimension(), after.sourceDimension());
            assertEquals(before.sourceOrigin(), after.sourceOrigin());
            assertEquals(before.createdAtGameTime(), after.createdAtGameTime());
            assertEquals(before.bounds(), after.bounds()); assertEquals(before.stats(), after.stats());
            assertEquals(before.passengerAnchors(), after.passengerAnchors()); assertEquals(before.propulsion(), after.propulsion());
            assertEquals(before.structureDigest(), after.structureDigest()); assertNotEquals(before.contentHash(), after.contentHash());
            for (var other : before.blocks()) {
                if (other.position().x() != 0 || other.position().y() != 0) {
                    assertSame(other, after.blocks().stream().filter(b -> b.position().equals(other.position())).findFirst().orElseThrow());
                }
            }
            var again = RocketItemCargoMutation.apply(before, delta, load);
            assertEquals(after.contentHash(), again.contentHash());
            assertEquals(sha256(RocketSnapshotNbtCodec.encode(after)), sha256(RocketSnapshotNbtCodec.encode(again)));
            assertEquals(encodedBefore, RocketSnapshotNbtCodec.encode(before));
        }
    }

    @Test void insertedAndEmptiedSlotsKeepCanonicalOrderOverTwentySevenSlots() {
        // An unsorted synthetic list still leaves sorted, as the adapter's whitelist does.
        var before = snapshot(CHEST, entry(26, stack(Items.STONE, 2)), entry(0, stack(Items.DIRT, 1)), entry(13, stack(Items.STONE, 1)));
        var loaded = RocketItemCargoMutation.apply(before, delta(before, 0, stack(Items.STONE, 4), 0, 4), true);
        assertEquals(List.of(0, 4, 13, 26), slotOrder(loaded));
        var emptied = RocketItemCargoMutation.apply(loaded, delta(loaded, 0, stack(Items.STONE, 1), 13, 3), false);
        assertEquals(List.of(0, 4, 26), slotOrder(emptied));
        var last = RocketItemCargoMutation.apply(before, delta(before, 0, stack(Items.STONE, 1), 0, 26), true);
        assertEquals(3, count(slots(last, 0).get(26)));
        var single = snapshot(BARREL, entry(26, stack(Items.STONE, 1)));
        var empty = RocketItemCargoMutation.apply(single, delta(single, 0, stack(Items.STONE, 1), 26, 0), false);
        assertEquals(0, data(empty, 0).getList("Items", 10).size()); assertTrue(data(empty, 0).contains("Items", 9));
        for (int slot : new int[] {27, 255}) {
            var invalid = snapshot(CHEST, entry(slot, stack(Items.STONE, 1)));
            assertThrows(IllegalArgumentException.class,
                    () -> RocketItemCargoMutation.apply(invalid, delta(invalid, 0, stack(Items.STONE, 1), 0, 0), true));
        }
        var duplicate = snapshot(CHEST, entry(3, stack(Items.STONE, 1)), entry(3, stack(Items.DIRT, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> RocketItemCargoMutation.apply(duplicate, delta(duplicate, 0, stack(Items.STONE, 1), 0, 0), true));
    }

    @Test void partialCountsAtBothEndsNeverOverflowOrUnderflow() {
        var full = snapshot(CHEST, entry(0, stack(Items.STONE, 64)));
        assertThrows(IllegalArgumentException.class, () -> RocketItemCargoMutation.apply(full, delta(full, 0, stack(Items.STONE, 1), 0, 0), true));
        var pearls = snapshot(CHEST, entry(0, stack(Items.ENDER_PEARL, 10)));
        assertEquals(16, count(slots(RocketItemCargoMutation.apply(pearls, delta(pearls, 0, stack(Items.ENDER_PEARL, 6), 0, 0), true), 0).get(0)));
        assertThrows(IllegalArgumentException.class,
                () -> RocketItemCargoMutation.apply(pearls, delta(pearls, 0, stack(Items.ENDER_PEARL, 7), 0, 0), true));
        var one = snapshot(CHEST, entry(2, stack(Items.STONE, 1)));
        assertFalse(slots(RocketItemCargoMutation.apply(one, delta(one, 0, stack(Items.STONE, 1), 2, 0), false), 0).containsKey(2));
        assertThrows(IllegalArgumentException.class, () -> RocketItemCargoMutation.apply(one, delta(one, 0, stack(Items.STONE, 2), 2, 0), false));
        assertThrows(IllegalArgumentException.class, () -> RocketItemCargoMutation.apply(one, delta(one, 0, stack(Items.STONE, 1), 3, 0), false));

        var buffer = buffer(stack(Items.STONE, 1), stack(Items.ENDER_PEARL, 10));
        var taken = RocketItemCargoMutation.buffer(buffer, delta(one, 0, stack(Items.STONE, 1), 0, 5), true);
        assertTrue(taken.slots().get(0).isEmpty(), "The last item of a buffer slot leaves an empty slot");
        var merged = RocketItemCargoMutation.buffer(buffer, delta(pearls, 0, stack(Items.ENDER_PEARL, 6), 0, 1), false);
        assertEquals(16, count(merged.slots().get(1)));
        assertThrows(IllegalArgumentException.class,
                () -> RocketItemCargoMutation.buffer(buffer, delta(pearls, 0, stack(Items.ENDER_PEARL, 7), 0, 1), false));
        assertThrows(IllegalArgumentException.class,
                () -> RocketItemCargoMutation.buffer(buffer, delta(one, 0, stack(Items.STONE, 2), 0, 5), true));
        assertEquals(stack(Items.DIRT, 3), RocketItemCargoMutation.buffer(buffer, delta(one, 0, stack(Items.DIRT, 3), 4, 2), false).slots().get(2));
    }

    @Test void lootAndOtherSubclassesRefuseWithTheirOwnStatusWhileDoubleChestHalvesHoldCargo() {
        var lootData = new CompoundTag(); lootData.putString("LootTable", "minecraft:chests/simple_dungeon"); lootData.putLong("LootTableSeed", 5);
        var loot = snapshot(block(0, state(CHEST), new RocketBlockEntityPayload(
                io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter.ID, lootData)));
        assertEquals(Status.UNRESOLVED_LOOT, refusal(loot, true));
        // R44: each half of a vanilla double chest is its own 27-slot chest or trapped chest payload.
        for (var side : List.of("left", "right")) {
            for (var id : List.of(CHEST, TRAPPED)) {
                var halfState = new RocketBlockState(id, Map.of("facing", "north", "type", side, "waterlogged", "false"));
                var half = snapshot(block(0, halfState, container(null, null, entry(0, stack(Items.STONE, 1)))));
                assertTrue(RocketItemCargoMutation.storage(halfState), side + " " + id);
                var loaded = RocketItemCargoMutation.apply(half, delta(half, 0, stack(Items.STONE, 1), 0, 1), true);
                assertEquals(1, count(slots(loaded, 0).get(1)), side + " " + id);
                var unloaded = RocketItemCargoMutation.apply(half, delta(half, 0, stack(Items.STONE, 1), 0, 0), false);
                assertTrue(slots(unloaded, 0).isEmpty(), side + " " + id);
                for (var after : List.of(loaded, unloaded)) {
                    assertEquals(halfState, after.blocks().stream().filter(b -> b.position().equals(new RocketPosition(0, 0, 0)))
                            .findFirst().orElseThrow().state());
                    assertEquals(half.structureDigest(), after.structureDigest());
                }
            }
        }
        var large = snapshot(block(0, new RocketBlockState(ResourceLocation.tryParse("fixture:large_chest"), Map.of()),
                container(null, null, entry(0, stack(Items.STONE, 1)), entry(53, stack(Items.STONE, 1)))));
        assertEquals(Status.NO_CARGO_STORAGE, refusal(large, true));
        var other = snapshot(block(0, new RocketBlockState(ResourceLocation.tryParse("fixture:copper_chest"), Map.of()),
                container(null, null, entry(0, stack(Items.STONE, 1)))));
        assertEquals(Status.NO_CARGO_STORAGE, refusal(other, false));
        var extra = snapshot(CHEST, entry(0, stack(Items.STONE, 1)));
        var withField = data(extra, 0); withField.putString("Owner", "nobody");
        var unknown = snapshot(block(0, state(CHEST), new RocketBlockEntityPayload(
                io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter.ID, withField)));
        assertFalse(assertThrows(IllegalArgumentException.class, () -> RocketItemCargoMutation.apply(unknown,
                delta(unknown, 0, stack(Items.STONE, 1), 0, 0), false)) instanceof RocketItemCargoMutation.Refusal);
    }

    @Test void mergingNeedsTheSameItemAndTagsAndDisplayNamesNeverDecide() {
        var named = new ItemStack(Items.STONE, 3); named.setHoverName(Component.literal("Stone"));
        var before = snapshot(CHEST, entry(0, named.save(new CompoundTag())));
        assertThrows(IllegalArgumentException.class, () -> RocketItemCargoMutation.apply(before, delta(before, 0, stack(Items.STONE, 1), 0, 0), true));
        var other = new ItemStack(Items.STONE, 1); other.setHoverName(Component.literal("Stone")); other.getOrCreateTag().putInt("mark", 1);
        assertThrows(IllegalArgumentException.class, () -> RocketItemCargoMutation.apply(before, delta(before, 0, other.save(new CompoundTag()), 0, 0), true));
        assertEquals(4, count(slots(RocketItemCargoMutation.apply(before, delta(before, 0, named.copyWithCount(1).save(new CompoundTag()), 0, 0), true), 0).get(0)));
        var caps = stack(Items.STONE, 3); caps.put("ForgeCaps", new CompoundTag());
        var capped = snapshot(CHEST, entry(0, caps));
        var refused = assertThrows(RocketItemCargoMutation.Refusal.class,
                () -> RocketItemCargoMutation.apply(capped, delta(capped, 0, stack(Items.STONE, 1), 0, 0), false));
        assertEquals(Status.INVALID_DATA, refused.status());
        var missing = stack(Items.STONE, 1); missing.putString("id", "missing:item");
        var unregistered = delta(before, 0, missing, 0, 1);
        assertEquals(Status.INVALID_DATA, assertThrows(RocketItemCargoMutation.Refusal.class,
                () -> RocketItemCargoMutation.apply(before, unregistered, true)).status());
    }

    @Test void cursorAndDirectionNeverRetarget() {
        var before = snapshot(CHEST, entry(0, stack(Items.STONE, 3)));
        var moved = stack(Items.STONE, 1);
        var adapter = io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter.ID.toString();
        for (var cursor : List.of(new EscrowCargoPayload.Cursor(UUID.randomUUID(), 0, 0, 0, adapter),
                new EscrowCargoPayload.Cursor(before.snapshotId(), 1, 0, 0, adapter),
                new EscrowCargoPayload.Cursor(before.snapshotId(), 9, 0, 0, PressurizedTankRocketAdapter.ID.toString()))) {
            assertThrows(IllegalArgumentException.class,
                    () -> RocketItemCargoMutation.apply(before, new EscrowItemCargoPayload(cursor, moved, 0, 0), true));
        }
        assertThrows(IllegalArgumentException.class, () -> RocketItemCargoMutation.apply(before, delta(before, 0, moved, 5, 0), true));
        assertThrows(IllegalArgumentException.class, () -> RocketItemCargoMutation.apply(before, delta(before, 0, moved, 0, 5), false));
    }

    private static Status refusal(RocketStructureSnapshot before, boolean load) {
        var delta = load ? delta(before, 0, stack(Items.STONE, 1), 0, 1) : delta(before, 0, stack(Items.STONE, 1), 0, 0);
        return assertThrows(RocketItemCargoMutation.Refusal.class, () -> RocketItemCargoMutation.apply(before, delta, load)).status();
    }
    private static List<Integer> slotOrder(RocketStructureSnapshot value) {
        var items = (ListTag) data(value, 0).get("Items");
        return items.stream().map(tag -> Byte.toUnsignedInt(((CompoundTag) tag).getByte("Slot"))).toList();
    }
    static String sha256(CompoundTag tag) throws IOException, java.security.NoSuchAlgorithmException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) { NbtIo.write(tag, out); }
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
    }
}
