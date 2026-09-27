package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FuelLoaderStorageTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000902");

    @Test void missingRootStartsEmptyAndSchemaTwoRoundTripsEveryRole() {
        assertEquals(FuelLoaderData.empty(), FuelLoaderStorage.decode(new CompoundTag()).data());
        for (var role : FuelLoaderData.Role.values()) {
            var data = new FuelLoaderData(role, role == FuelLoaderData.Role.EMPTY ? new CompoundTag() : item(),
                    0, OWNER, null, null);
            assertEquals(data, decode(FuelLoaderStorage.encode(data)).data());
        }
    }

    @Test void batchRetainsTotalRemainderMetadataOwnerAndTargetWithoutLiveDefinitions() {
        var batch = new FuelLoaderData.Batch("fixture:removed", 2_048_000L, item());
        var data = new FuelLoaderData(FuelLoaderData.Role.EMPTY, new CompoundTag(), 2_047_975, OWNER, TARGET, batch);
        assertEquals(data, decode(FuelLoaderStorage.encode(data)).data());
        batch.remainder().putString("extra", "mutated");
        data.item().putString("extra", "mutated");
        assertEquals(item(), batch.remainder());
        assertTrue(data.item().isEmpty());
    }

    @Test void legacyAllItemsAndUnclaimedActiveBatchesMigrateWithoutExpandingOldBound() {
        for (var old : FuelLoaderPersistence.ItemState.values()) {
            var decoded = decode(FuelLoaderPersistence.encode(old, 0, OWNER, null));
            assertTrue(decoded.valid());
            assertEquals(old.networkId(), decoded.data().role().ordinal());
            assertEquals(OWNER, decoded.data().ownerId());
            assertEquals(2, FuelLoaderStorage.encode(decoded.data()).getInt("schema_version"));
        }
        for (UUID owner : new UUID[]{null, OWNER}) {
            for (long units : new long[]{1, 500}) {
                var decoded = decode(FuelLoaderPersistence.encode(FuelLoaderPersistence.ItemState.EMPTY, units,
                        owner, owner == null ? null : TARGET));
                assertTrue(decoded.valid());
                assertEquals(units, decoded.data().bufferedUnits());
                assertEquals(500, decoded.data().batch().totalUnits());
                assertEquals("advancedrocketrycommunity:empty_canister", decoded.data().batch().remainder().getString("id"));
                assertEquals(owner, decoded.data().ownerId());
            }
        }
        var over = FuelLoaderPersistence.encode(FuelLoaderPersistence.ItemState.EMPTY, 500, OWNER, TARGET);
        over.putLong("buffered_units", 501);
        assertBlocked(over);
    }

    @Test void futureUnknownKeysAndMalformedNonCompoundRootsArePreservedExactly() {
        CompoundTag future = FuelLoaderStorage.encode(FuelLoaderData.empty());
        future.putInt("schema_version", 3);
        future.putString("opaque", "keep");
        assertTrue(decode(future).future());
        assertBlocked(future);
        for (Tag raw : new Tag[]{StringTag.valueOf("not a compound"), IntTag.valueOf(81), new ListTag()}) {
            assertBlocked(raw);
        }
        var unknown = FuelLoaderStorage.encode(FuelLoaderData.empty());
        unknown.putString("extra", "keep");
        assertBlocked(unknown);
        var old = FuelLoaderPersistence.encode(FuelLoaderPersistence.ItemState.EMPTY, 0, null, null);
        old.putString("extra", "keep");
        assertBlocked(old);
    }

    @Test void inconsistentFieldsNeverDiscardTheRawRoot() {
        for (String fault : new String[]{"role", "type", "owner", "target", "buffer", "batch"}) {
            var raw = FuelLoaderStorage.encode(FuelLoaderData.empty());
            switch (fault) {
                case "role" -> raw.putInt("slot_role", 7);
                case "type" -> raw.putByte("slot_role", (byte) 0);
                case "owner" -> raw.putIntArray("owner_id", new int[3]);
                case "target" -> raw.putUUID("target_rocket_id", TARGET);
                case "buffer" -> raw.putLong("buffered_units", 1);
                case "batch" -> raw.put("batch", new CompoundTag());
                default -> throw new AssertionError(fault);
            }
            assertBlocked(raw);
        }
        assertThrows(IllegalArgumentException.class, () -> new FuelLoaderData(FuelLoaderData.Role.INPUT,
                item(), 1, OWNER, TARGET, new FuelLoaderData.Batch("fixture:x", 1, new CompoundTag())));
        assertThrows(IllegalArgumentException.class, () -> new FuelLoaderData(FuelLoaderData.Role.EMPTY,
                new CompoundTag(), 2, OWNER, TARGET, new FuelLoaderData.Batch("fixture:x", 1, new CompoundTag())));
    }

    @Test void nativeItemEnvelopePreservesTagAndCapabilitiesButRejectsInvalidCountsIdsAndTypes() {
        var data = new FuelLoaderData(FuelLoaderData.Role.INPUT, item(), 0, OWNER, null, null);
        assertEquals(item(), decode(FuelLoaderStorage.encode(data)).data().item());
        for (String fault : new String[]{"count", "type", "id", "air", "extra", "caps", "tag"}) {
            CompoundTag item = item();
            switch (fault) {
                case "count" -> item.putByte("Count", (byte) 2);
                case "type" -> item.putInt("Count", 1);
                case "id" -> item.putString("id", "no_namespace");
                case "air" -> item.putString("id", "minecraft:air");
                case "extra" -> item.putInt("extra", 1);
                case "caps" -> item.putString("ForgeCaps", "bad");
                case "tag" -> item.putInt("tag", 2);
                default -> throw new AssertionError(fault);
            }
            assertThrows(IllegalArgumentException.class, () -> new FuelLoaderData(FuelLoaderData.Role.INPUT,
                    item, 0, OWNER, null, null));
        }
    }

    @Test void byteDepthAndNodeBudgetsAreCheckedBeforeCopyOrNativeDecode() {
        var bytes = new CompoundTag(); bytes.putByteArray("huge", new byte[16384]);
        assertFalse(FuelPayloadBounds.root(bytes));
        assertTrue(decode(bytes).quarantined());
        assertSame(bytes, decode(bytes).preserved());
        var depth = new CompoundTag(); CompoundTag child = depth;
        for (int index = 0; index < 21; index++) { var next = new CompoundTag(); child.put("x", next); child = next; }
        assertFalse(FuelPayloadBounds.root(depth));
        assertSame(depth, decode(depth).preserved());
        var nodes = new CompoundTag();
        for (int index = 0; index < 1024; index++) { nodes.putInt("n" + index, 0); }
        assertFalse(FuelPayloadBounds.root(nodes));
        var small = item(); small.getCompound("tag").putByteArray("huge", new byte[4096]);
        assertFalse(FuelPayloadBounds.item(small));
        assertThrows(IllegalArgumentException.class, () -> new FuelLoaderData(FuelLoaderData.Role.INPUT, small, 0, null, null, null));
        assertTrue(FuelPayloadBounds.item(item()));
    }

    private static CompoundTag item() {
        var result = new CompoundTag(); result.putString("id", "fixture:fuel"); result.putByte("Count", (byte) 1);
        var tag = new CompoundTag(); tag.putString("label", "queued metadata"); result.put("tag", tag);
        var caps = new CompoundTag(); caps.putLong("fixture:energy", 137); result.put("ForgeCaps", caps);
        return result;
    }

    private static FuelLoaderStorage.Decoded decode(Tag raw) {
        var parent = new CompoundTag(); parent.put(FuelLoaderStorage.DATA_KEY, raw); return FuelLoaderStorage.decode(parent);
    }

    private static void assertBlocked(Tag raw) {
        var result = decode(raw);
        assertFalse(result.valid()); assertEquals(raw, result.preserved()); assertFalse(result.quarantined());
    }
}
