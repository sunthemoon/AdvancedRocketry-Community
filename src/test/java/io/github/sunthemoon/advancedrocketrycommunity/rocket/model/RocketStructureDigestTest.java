package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotDecodeResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** W11c leaf C-I (W11CD-DESIGN section 2.5): the derived structure digest, with explicit before and after fixtures. */
final class RocketStructureDigestTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.tryParse("minecraft:overworld");
    private static final ResourceLocation CONTAINER = ResourceLocation.tryParse("advancedrocketrycommunity:vanilla_container_v1");
    private static final UUID SNAPSHOT = UUID.fromString("00000000-0000-4000-8000-00000000c001");
    private static final RocketPosition ORIGIN = new RocketPosition(10, 64, -20);
    private static final Map<String, String> CHEST = Map.of("facing", "north", "type", "single", "waterlogged", "false");

    @Test
    void contentHashesKeepTheirPreDigestValues() {
        // Computed at 10bc96e6, before the digest existed: the content hash's bytes are unchanged.
        assertEquals("fe6320add9700adfb4a23676d172fd9d5c22940b995cb802c1dded969850c053", before(propulsion()).contentHash());
        assertEquals("6c43ed8d702af502ea29c7cdb617ff18b4ea64a1ee307709915ec498db66d3a7", before(null).contentHash());
    }

    @Test
    void digestIsPinnedToItsPrefixAndExcludesPayloadData() throws Exception {
        for (var propulsion : java.util.Arrays.asList(propulsion(), null)) {
            var snapshot = before(propulsion);
            byte[] body = independentBody(snapshot);
            byte[] prefix = "arce-rocket-structure-1:".getBytes(StandardCharsets.UTF_8);
            byte[] input = new byte[prefix.length + body.length];
            System.arraycopy(prefix, 0, input, 0, prefix.length);
            System.arraycopy(body, 0, input, prefix.length, body.length);
            assertEquals(sha256(input), snapshot.structureDigest());
            assertNotEquals(sha256(body), snapshot.structureDigest(), "The prefix must be hashed");
            assertEquals(input.length, snapshot.structureDigestBytes(), "The charged byte work is the hashed input");
            assertTrue(snapshot.structureDigest().matches("[0-9a-f]{64}"));
        }
        // Golden value: a change here is a format change and needs a new prefix version.
        assertEquals("ef54582123c280168bf27b1d35a12198fccfb46ec52a9a52ebb6dbcfb3746dc5", before(propulsion()).structureDigest());
    }

    @Test
    void afterFixtureChangesOnlyPayloadDataSoTheDigestIsStable() {
        for (var propulsion : java.util.Arrays.asList(propulsion(), null)) {
            var before = before(propulsion);
            var after = snapshot(SNAPSHOT, OVERWORLD, ORIGIN, blocks(chest(2, CONTAINER, CHEST)), propulsion);
            assertEquals(before.snapshotId(), after.snapshotId());
            assertNotEquals(before.contentHash(), after.contentHash(), "The fixture must change cargo");
            assertEquals(before.structureDigest(), after.structureDigest());
            assertEquals(before.structureDigestBytes(), after.structureDigestBytes());
        }
        // Neither the snapshot ID nor the creation time is structure; the predicate compares the ID separately.
        var other = RocketStructureSnapshot.create(UUID.randomUUID(), OVERWORLD, ORIGIN, blocks(chest(1, CONTAINER, CHEST)),
                List.of(new RocketPosition(0, 1, 0)), stats(3, 1), 99, propulsion());
        assertEquals(before(propulsion()).structureDigest(), other.structureDigest());
    }

    @Test
    void geometryStateAdapterPropulsionAndPlacementChangeTheDigest() {
        var base = before(propulsion());
        var moved = new ArrayList<>(blocks(chest(1, CONTAINER, CHEST)));
        moved.set(2, new RocketBlock(new RocketPosition(-1, 0, 0), state("minecraft:iron_block", Map.of())));
        var variants = Map.of(
                "geometry", snapshot(SNAPSHOT, OVERWORLD, ORIGIN, moved, propulsion()),
                "state property", snapshot(SNAPSHOT, OVERWORLD, ORIGIN,
                        blocks(chest(1, CONTAINER, Map.of("facing", "south", "type", "single", "waterlogged", "false"))), propulsion()),
                "block", snapshot(SNAPSHOT, OVERWORLD, ORIGIN, blocks(new RocketBlock(new RocketPosition(0, 0, 0),
                        state("minecraft:barrel", Map.of("facing", "north", "open", "false")), payload(1, CONTAINER))), propulsion()),
                "payload adapter", snapshot(SNAPSHOT, OVERWORLD, ORIGIN,
                        blocks(chest(1, ResourceLocation.tryParse("advancedrocketrycommunity:pressurized_tank_v1"), CHEST)), propulsion()),
                "propulsion", snapshot(SNAPSHOT, OVERWORLD, ORIGIN, blocks(chest(1, CONTAINER, CHEST)),
                        new RocketPropulsion(RocketPropulsion.Mode.BIPROPELLANT, 1_000, 2_000, 1, 1, RocketPropulsion.Settings.DEFAULTS)),
                "propulsion absent", snapshot(SNAPSHOT, OVERWORLD, ORIGIN, blocks(chest(1, CONTAINER, CHEST)), null),
                "origin", base.relocated(SNAPSHOT, OVERWORLD, new RocketPosition(10, 65, -20), 5),
                "dimension", base.relocated(SNAPSHOT, ResourceLocation.tryParse("advancedrocketrycommunity:moon"), ORIGIN, 5));
        for (var variant : variants.entrySet()) {
            assertEquals(base.snapshotId(), variant.getValue().snapshotId());
            assertNotEquals(base.structureDigest(), variant.getValue().structureDigest(), variant.getKey());
        }
        var noPayload = new ArrayList<>(blocks(chest(1, CONTAINER, CHEST)));
        noPayload.set(0, new RocketBlock(new RocketPosition(0, 0, 0), state("minecraft:chest", CHEST)));
        var withoutPayload = RocketStructureSnapshot.create(SNAPSHOT, OVERWORLD, ORIGIN, noPayload,
                List.of(new RocketPosition(0, 1, 0)), stats(3, 0), 5, propulsion());
        var noAnchor = RocketStructureSnapshot.create(SNAPSHOT, OVERWORLD, ORIGIN, blocks(chest(1, CONTAINER, CHEST)),
                List.of(), new RocketStats(3, 30, 40, 1_000, 1, 0, 1, 1), 5, propulsion());
        var heavier = RocketStructureSnapshot.create(SNAPSHOT, OVERWORLD, ORIGIN, blocks(chest(1, CONTAINER, CHEST)),
                List.of(new RocketPosition(0, 1, 0)), new RocketStats(3, 31, 40, 1_000, 1, 1, 1, 1), 5, propulsion());
        for (var other : List.of(withoutPayload, noAnchor, heavier)) {
            assertNotEquals(base.structureDigest(), other.structureDigest());
        }
    }

    @Test
    void digestIsCachedAndNeverPersisted() {
        var snapshot = before(propulsion());
        assertSame(snapshot.structureDigest(), snapshot.structureDigest());
        var encoded = RocketSnapshotNbtCodec.encode(snapshot);
        assertFalse(contains(encoded, snapshot.structureDigest()), "The codec must not write the derived digest");
        var decoded = RocketSnapshotNbtCodec.decode(encoded);
        assertEquals(RocketSnapshotDecodeResult.Status.VALID, decoded.status());
        assertEquals(snapshot.structureDigest(), decoded.snapshot().orElseThrow().structureDigest());
        assertEquals(snapshot.contentHash(), decoded.snapshot().orElseThrow().contentHash());
    }

    @Test
    void digestWorkDoesNotGrowWithPayloadDataAtTheSnapshotLimits() throws Exception {
        var small = maximal(1);
        var full = maximal(8_000);
        assertEquals(2_048, full.blocks().size());
        assertEquals(128, full.blocks().stream().filter(block -> block.blockEntityPayload().isPresent()).count());
        assertNotEquals(small.contentHash(), full.contentHash());
        assertEquals(small.structureDigest(), full.structureDigest());
        assertEquals(small.structureDigestBytes(), full.structureDigestBytes());
        // One visit per block record and exactly the hashed input's bytes; the record reports this figure for C-P.
        assertEquals("arce-rocket-structure-1:".length() + independentBody(full).length, full.structureDigestBytes());
        assertEquals(97_676, full.structureDigestBytes());
    }

    private static RocketStructureSnapshot maximal(int payloadBytes) {
        var blocks = new ArrayList<RocketBlock>();
        for (int index = 0; index < 2_048; index++) {
            var position = new RocketPosition(index % 16, index / 256, (index / 16) % 16);
            if (index < 128) {
                var data = new CompoundTag(); data.putByteArray("cargo", new byte[payloadBytes]);
                blocks.add(new RocketBlock(position, state("minecraft:chest", CHEST), new RocketBlockEntityPayload(CONTAINER, data)));
            } else {
                blocks.add(new RocketBlock(position, state("minecraft:iron_block", Map.of())));
            }
        }
        return RocketStructureSnapshot.create(SNAPSHOT, OVERWORLD, ORIGIN, blocks, List.of(),
                new RocketStats(2_048, 2_048, 0, 0, 0, 0, 0, 128), 5);
    }

    static RocketStructureSnapshot before(RocketPropulsion propulsion) {
        return snapshot(SNAPSHOT, OVERWORLD, ORIGIN, blocks(chest(1, CONTAINER, CHEST)), propulsion);
    }

    private static RocketStructureSnapshot snapshot(UUID id, ResourceLocation dimension, RocketPosition origin,
                                                    List<RocketBlock> blocks, RocketPropulsion propulsion) {
        long fuel = propulsion == null ? 1_000 : propulsion.primaryCapacity();
        int entities = (int) blocks.stream().filter(block -> block.blockEntityPayload().isPresent()).count();
        return RocketStructureSnapshot.create(id, dimension, origin, blocks, List.of(new RocketPosition(0, 1, 0)),
                new RocketStats(3, 30, 40, fuel, 1, 1, 1, entities), 5, propulsion);
    }

    private static RocketStats stats(int blocks, int entities) {
        return new RocketStats(blocks, 30, 40, 1_000, 1, 1, 1, entities);
    }

    private static List<RocketBlock> blocks(RocketBlock first) {
        return List.of(first, new RocketBlock(new RocketPosition(0, 1, 0), state("test:seat", Map.of())),
                new RocketBlock(new RocketPosition(1, 0, 0), state("minecraft:iron_block", Map.of())));
    }

    private static RocketBlock chest(int stone, ResourceLocation adapter, Map<String, String> properties) {
        return new RocketBlock(new RocketPosition(0, 0, 0), state("minecraft:chest", properties), payload(stone, adapter));
    }

    private static RocketBlockEntityPayload payload(int stone, ResourceLocation adapter) {
        var item = new CompoundTag(); item.putByte("Slot", (byte) 0); item.putString("id", "minecraft:stone");
        item.putByte("Count", (byte) stone);
        var list = new ListTag(); list.add(item);
        var data = new CompoundTag(); data.put("Items", list);
        return new RocketBlockEntityPayload(adapter, data);
    }

    private static RocketBlockState state(String id, Map<String, String> properties) {
        return new RocketBlockState(ResourceLocation.tryParse(id), properties);
    }

    private static RocketPropulsion propulsion() {
        return new RocketPropulsion(RocketPropulsion.Mode.BIPROPELLANT, 1_000, 1_000, 1, 1, RocketPropulsion.Settings.DEFAULTS);
    }

    /** The section 2.5 field order, written here without the production writer. */
    private static byte[] independentBody(RocketStructureSnapshot snapshot) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeInt(snapshot.schemaVersion());
            string(output, snapshot.sourceDimension().toString());
            position(output, snapshot.sourceOrigin());
            position(output, snapshot.bounds().minimum());
            position(output, snapshot.bounds().maximum());
            output.writeInt(snapshot.blocks().size());
            for (var block : snapshot.blocks()) {
                position(output, block.position());
                string(output, block.state().blockId().toString());
                output.writeInt(block.state().properties().size());
                for (var property : block.state().properties().entrySet()) {
                    string(output, property.getKey());
                    string(output, property.getValue());
                }
                output.writeBoolean(block.blockEntityPayload().isPresent());
                if (block.blockEntityPayload().isPresent()) {
                    string(output, block.blockEntityPayload().orElseThrow().adapterId().toString());
                }
            }
            output.writeInt(snapshot.passengerAnchors().size());
            for (var anchor : snapshot.passengerAnchors()) {
                position(output, anchor);
            }
            var stats = snapshot.stats();
            output.writeInt(stats.blockCount());
            output.writeLong(stats.mass());
            output.writeLong(stats.thrust());
            output.writeLong(stats.fuelCapacity());
            output.writeInt(stats.engineCount());
            output.writeInt(stats.seatCount());
            output.writeInt(stats.guidanceCount());
            output.writeInt(stats.blockEntityCount());
            output.writeBoolean(snapshot.propulsion().isPresent());
            if (snapshot.propulsion().isPresent()) {
                string(output, snapshot.propulsion().orElseThrow().signature());
            }
        }
        return bytes.toByteArray();
    }

    private static void position(DataOutputStream output, RocketPosition position) throws IOException {
        output.writeInt(position.x());
        output.writeInt(position.y());
        output.writeInt(position.z());
    }

    private static void string(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String sha256(byte[] input) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
    }

    private static boolean contains(Tag tag, String value) {
        if (tag instanceof StringTag text) {
            return text.getAsString().contains(value);
        }
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                if (key.contains("structure") || contains(compound.get(key), value)) { return true; }
            }
        }
        if (tag instanceof ListTag list) {
            for (Tag entry : list) {
                if (contains(entry, value)) { return true; }
            }
        }
        return false;
    }
}
