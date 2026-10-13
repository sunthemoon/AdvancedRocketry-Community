package io.github.sunthemoon.advancedrocketrycommunity.beacon.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconIndex;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconLimits;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconRecord;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The schema-1 index root and the block-entity root: exact round trips, exact byte accounting against the real NBT
 * writer, the 1 MiB bound, and refusal of every malformed, future or over-bound root without a partial result.
 */
class BeaconIndexCodecTest {
    private static final String EARTH = "advancedrocketrycommunity:earth";
    private static final String OVERWORLD = "minecraft:overworld";

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    private static BeaconRecord record(UUID owner, int x, String name) {
        return new BeaconRecord(UUID.randomUUID(), owner, EARTH, OVERWORLD, x, -64, -x, 7, x % 2 == 0, name);
    }

    private static long payload(Tag tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        tag.write(new DataOutputStream(bytes));
        return bytes.size();
    }

    @Test
    void theWorldLimitsMatchMinecraft() {
        assertEquals(Level.MAX_LEVEL_SIZE, BeaconLimits.MAX_HORIZONTAL);
        assertEquals(DimensionType.MIN_Y, BeaconLimits.MIN_Y);
        assertEquals(DimensionType.MAX_Y, BeaconLimits.MAX_Y);
    }

    @Test
    void aPopulatedIndexRoundTripsCanonicallyWithExactSizes() throws IOException {
        BeaconIndex index = BeaconIndexCodec.emptyIndex();
        UUID owner = UUID.randomUUID();
        String[] names = {"", "Base", "中".repeat(32), "🚀 launch", "x".repeat(32)};
        for (int i = 0; i < names.length; i++) {
            BeaconRecord record = record(owner, i, names[i]);
            assertEquals(BeaconIndex.Admission.ADMITTED, index.put(record));
            assertEquals(payload(BeaconIndexCodec.encode(record)), BeaconIndexCodec.recordBytes(record));
        }
        CompoundTag root = BeaconIndexCodec.encode(index);
        assertEquals(payload(root), index.encodedBytes());
        assertEquals(payload(BeaconIndexCodec.encode(BeaconIndexCodec.emptyIndex())), BeaconIndexCodec.ROOT_OVERHEAD);
        BeaconIndex decoded = BeaconIndexCodec.decode(root.copy());
        assertEquals(index.records(), decoded.records());
        assertEquals(root, BeaconIndexCodec.encode(decoded));
        assertEquals(index.encodedBytes(), decoded.encodedBytes());
    }

    @Test
    void theLargestRecordsStopAtTheRootByteBound() throws IOException {
        BeaconIndex index = BeaconIndexCodec.emptyIndex();
        String body = "advancedrocketrycommunity:" + "b".repeat(BeaconLimits.MAX_ID_CHARS - 26);
        String level = "advancedrocketrycommunity:" + "l".repeat(BeaconLimits.MAX_ID_CHARS - 26);
        int admitted = 0;
        BeaconIndex.Admission last = BeaconIndex.Admission.ADMITTED;
        for (int i = 0; last == BeaconIndex.Admission.ADMITTED; i++) {
            String bodyId = body.substring(0, body.length() - 3) + String.format("%03d", i / BeaconLimits.MAX_PER_BODY);
            BeaconRecord record = new BeaconRecord(UUID.randomUUID(), UUID.randomUUID(), bodyId, level, i, 0, 0,
                    Long.MAX_VALUE, true, "中".repeat(BeaconLimits.MAX_NAME_CHARS));
            last = index.put(record);
            if (last == BeaconIndex.Admission.ADMITTED) {
                admitted++;
            }
        }
        assertEquals(BeaconIndex.Admission.BYTES_FULL, last);
        assertTrue(admitted < BeaconLimits.MAX_TOTAL, "The byte bound binds before the count: " + admitted);
        CompoundTag root = BeaconIndexCodec.encode(index);
        long size = payload(root);
        assertEquals(index.encodedBytes(), size);
        assertTrue(size <= BeaconLimits.MAX_ROOT_BYTES, "root " + size);
        assertEquals(admitted, BeaconIndexCodec.decode(root).size());
    }

    @Test
    void malformedFutureAndOverBoundRootsAreRefused() {
        BeaconIndex index = BeaconIndexCodec.emptyIndex();
        UUID owner = UUID.randomUUID();
        index.put(record(owner, 2, "a"));
        CompoundTag valid = BeaconIndexCodec.encode(index);
        BeaconIndexCodec.decode(valid.copy());
        List<Consumer<CompoundTag>> mutations = List.of(
                root -> root.putInt("schema_version", 2),
                root -> root.putInt("schema_version", 0),
                root -> root.putString("schema_version", "1"),
                root -> root.remove("schema_version"),
                root -> root.remove("beacons"),
                root -> root.putString("extra", "x"),
                root -> root.put("beacons", new CompoundTag()),
                root -> {
                    ListTag strings = new ListTag();
                    strings.add(StringTag.valueOf("x"));
                    root.put("beacons", strings);
                },
                root -> list(root).add(list(root).getCompound(0).copy()),
                root -> first(root).putString("extra", "x"),
                root -> first(root).remove("name"),
                root -> first(root).putInt("active", 1),
                root -> first(root).putByte("active", (byte) 2),
                root -> first(root).putIntArray("pos", new int[] {1, 2}),
                root -> first(root).putIntArray("pos", new int[] {30_000_001, 0, 0}),
                root -> first(root).putIntArray("id", new int[] {1, 2, 3}),
                root -> first(root).putString("owner", owner.toString()),
                root -> first(root).putLong("generation", -1),
                root -> first(root).putInt("generation", 1),
                root -> first(root).putString("body", "Earth"),
                root -> first(root).putString("level", "minecraft:" + "x".repeat(200)),
                root -> first(root).putString("name", "x".repeat(33)),
                root -> first(root).putString("name", "bad\u0007"));
        for (int i = 0; i < mutations.size(); i++) {
            CompoundTag root = valid.copy();
            mutations.get(i).accept(root);
            int mutation = i;
            assertThrows(IllegalArgumentException.class, () -> BeaconIndexCodec.decode(root), "mutation " + mutation);
        }
        assertThrows(IllegalArgumentException.class, () -> BeaconIndexCodec.decode(null));
    }

    @Test
    void anOwnerOverItsBoundOrAnOversizedListIsRefusedWhole() {
        BeaconIndex index = BeaconIndexCodec.emptyIndex();
        UUID owner = UUID.randomUUID();
        for (int i = 0; i < BeaconLimits.MAX_PER_OWNER; i++) {
            index.put(record(owner, i, ""));
        }
        CompoundTag root = BeaconIndexCodec.encode(index);
        ListTag beacons = root.getList("beacons", Tag.TAG_COMPOUND);
        beacons.add(BeaconIndexCodec.encode(record(owner, 99, "")));
        assertThrows(IllegalArgumentException.class, () -> BeaconIndexCodec.decode(root));
        CompoundTag huge = new CompoundTag();
        huge.putInt("schema_version", 1);
        ListTag many = new ListTag();
        for (int i = 0; i <= BeaconLimits.MAX_TOTAL; i++) {
            many.add(new CompoundTag());
        }
        huge.put("beacons", many);
        assertThrows(IllegalArgumentException.class, () -> BeaconIndexCodec.decode(huge));
        CompoundTag empty = new CompoundTag();
        empty.putInt("schema_version", 1);
        empty.put("beacons", new ListTag());
        assertEquals(0, BeaconIndexCodec.decode(empty).size());
    }

    @Test
    void aRefusedRootIsKeptByReferenceAndTheIndexIsUnavailable() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 9);
        future.put("beacons", new ListTag());
        future.put("unknown", IntTag.valueOf(3));
        BeaconIndexSavedData data = BeaconIndexSavedData.load(future);
        assertTrue(!data.operational());
        assertSame(future, data.save(new CompoundTag()));
        BeaconRecord record = record(UUID.randomUUID(), 1, "a");
        assertEquals(Optional.empty(), data.put(record));
        assertTrue(data.ownedBy(record.owner()).isEmpty());
        assertTrue(!data.remove(record.beaconId()) && !data.isDirty());
        BeaconIndexSavedData fresh = new BeaconIndexSavedData();
        assertEquals(Optional.of(BeaconIndex.Admission.ADMITTED), fresh.put(record));
        assertTrue(fresh.isDirty());
        assertEquals(List.of(record), BeaconIndexSavedData.load(fresh.save(new CompoundTag())).ownedBy(record.owner()));
    }

    @Test
    void theBlockEntityRootRoundTripsAndRefusesMalformedData() {
        BeaconStateCodec.State unowned = BeaconStateCodec.State.fresh();
        assertEquals(unowned, BeaconStateCodec.decode(BeaconStateCodec.encode(unowned)));
        BeaconStateCodec.State owned = unowned.withOwner(UUID.randomUUID()).withName("中继").withEnabled(false)
                .nextGeneration();
        assertEquals(owned, BeaconStateCodec.decode(BeaconStateCodec.encode(owned)));
        BeaconStateCodec.State saturated = new BeaconStateCodec.State(UUID.randomUUID(), Optional.empty(), "", true,
                Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, saturated.nextGeneration().generation());
        CompoundTag valid = BeaconStateCodec.encode(owned);
        List<Consumer<CompoundTag>> mutations = List.of(
                tag -> tag.putInt("schema_version", 2),
                tag -> tag.remove("beacon_id"),
                tag -> tag.putString("owner", "x"),
                tag -> tag.put("enabled", ByteTag.valueOf((byte) 3)),
                tag -> tag.putLong("generation", -5),
                tag -> tag.putString("name", "x".repeat(40)),
                tag -> tag.putString("extra", "x"));
        for (Consumer<CompoundTag> mutation : mutations) {
            CompoundTag tag = valid.copy();
            mutation.accept(tag);
            assertThrows(IllegalArgumentException.class, () -> BeaconStateCodec.decode(tag));
        }
        assertThrows(IllegalArgumentException.class, () -> BeaconStateCodec.decode(StringTag.valueOf("x")));
    }

    private static ListTag list(CompoundTag root) {
        return root.getList("beacons", Tag.TAG_COMPOUND);
    }

    private static CompoundTag first(CompoundTag root) {
        return list(root).getCompound(0);
    }
}
