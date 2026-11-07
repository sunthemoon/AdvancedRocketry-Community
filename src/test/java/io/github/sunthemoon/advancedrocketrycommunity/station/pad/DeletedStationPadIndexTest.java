package io.github.sunthemoon.advancedrocketrycommunity.station.pad;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DeletedStationPadIndexTest {
    private static final UUID OWNER = new UUID(319, 1);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void oldOrphansSurviveLoadAndConsumeGlobalButNotAnotherStationsEightPadLimit() {
        var entries = orphanEntries(LandingPadIndexCodec.MAX_STATIONS);
        var root = LandingPadIndexCodec.encode(entries.size(), entries, false);
        var index = LandingPadIndex.load(root);
        assertTrue(index.operational()); assertFalse(index.isDirty());
        assertEquals(root, index.save(new CompoundTag()));
        assertThrows(IllegalArgumentException.class, () -> register(index, UUID.randomUUID(), 0));
        UUID existing = entries.get(0).stationId();
        for (int i = 1; i < LandingPadIndexCodec.MAX_PADS; i++) { register(index, existing, i); }
        assertEquals(LandingPadIndexCodec.MAX_PADS, index.entries(existing).size());
        assertThrows(IllegalArgumentException.class, () -> register(index, existing, LandingPadIndexCodec.MAX_PADS));
    }

    @Test void oldOrphanSaveBytesGrowWithTheNumberOfDeletedStationBuckets() throws Exception {
        var empty = LandingPadIndexCodec.encode(0, List.of(), false);
        var first = orphanEntries(1);
        var many = orphanEntries(128);
        int emptyBytes = size(empty);
        int firstBytes = size(LandingPadIndexCodec.encode(1, first, false));
        var manyRoot = LandingPadIndexCodec.encode(many.size(), many, false);
        assertEquals(128 * (firstBytes - emptyBytes), size(manyRoot) - emptyBytes);
        assertEquals(manyRoot, LandingPadIndex.load(manyRoot).save(new CompoundTag()));
        assertTrue(LandingPadIndexCodec.fits(manyRoot));
    }

    @Test void deletionRemovesOnlyItsBucketAndChoicesAndKeepsClaimsEpochsAndSchema() {
        var index = new LandingPadIndex();
        UUID station = UUID.randomUUID(); UUID neighbor = UUID.randomUUID();
        var first = register(index, station, 0);
        for (int i = 1; i < LandingPadIndexCodec.MAX_PADS; i++) { register(index, station, i); }
        var retained = register(index, neighbor, 0); UUID transfer = UUID.randomUUID();
        assertTrue(index.claim(neighbor, retained.reference(), retained.physicalId(), transfer));
        assertTrue(index.claim(station, first.reference(), first.physicalId(), UUID.randomUUID()));
        UUID actor = UUID.randomUUID(); UUID defaultActor = UUID.randomUUID(); UUID otherActor = UUID.randomUUID();
        index.choose(actor, station, first.reference()); index.choose(defaultActor, station, null);
        index.choose(otherActor, neighbor, retained.reference());
        index.setDirty(false);
        assertEquals(LandingPadIndexCodec.MAX_PADS, index.removeStation(station));
        assertTrue(index.isDirty()); assertTrue(index.entries(station).isEmpty());
        assertNull(index.choice(actor).target()); assertNull(index.choice(defaultActor).target());
        assertEquals(retained.reference(), index.choice(otherActor).landingPad());
        var saved = index.save(new CompoundTag());
        assertEquals(1, saved.getInt("schema"));
        assertEquals(LandingPadIndexCodec.MAX_PADS + 1L, saved.getLong("next_epoch"));
        var restored = LandingPadIndex.load(saved);
        assertTrue(restored.operational()); assertEquals(saved, restored.save(new CompoundTag()));
        assertEquals(transfer, restored.find(neighbor, retained.reference()).orElseThrow().claim());
        index.setDirty(false); assertEquals(0, index.removeStation(station)); assertFalse(index.isDirty());
        var replacement = register(index, station, 0);
        assertTrue(replacement.epoch() > retained.epoch()); assertNotEquals(first.reference(), replacement.reference());
        assertFalse(index.claim(station, first.reference(), first.physicalId(), UUID.randomUUID()));
    }

    @Test void deletionFreesGlobalCapacityButDoesNotPruneOtherOldOrphans() {
        var entries = orphanEntries(LandingPadIndexCodec.MAX_STATIONS);
        var index = LandingPadIndex.load(LandingPadIndexCodec.encode(entries.size(), entries, false));
        assertEquals(1, index.removeStation(entries.get(0).stationId()));
        var restored = LandingPadIndex.load(index.save(new CompoundTag()));
        var added = register(restored, UUID.randomUUID(), 0);
        assertEquals(LandingPadIndexCodec.MAX_STATIONS, added.epoch());
        var saved = restored.save(new CompoundTag());
        assertEquals(LandingPadIndexCodec.MAX_STATIONS, saved.getList("pads", 10).size());
        assertEquals(entries.subList(1, entries.size()), LandingPadIndexCodec.decode(saved).entries()
                .subList(0, entries.size() - 1));
    }

    @Test void repeatedDeletionDoesNotAccumulateSaveBytesOrResetEpochs() throws Exception {
        var index = new LandingPadIndex(); int emptyBytes = size(index.save(new CompoundTag()));
        for (int i = 0; i < 128; i++) {
            UUID station = UUID.randomUUID(); var entry = register(index, station, 0);
            assertEquals(i, entry.epoch()); assertEquals(1, index.removeStation(station));
            var saved = index.save(new CompoundTag());
            assertEquals(emptyBytes, size(saved));
            index = LandingPadIndex.load(saved);
        }
        assertEquals(128, index.save(new CompoundTag()).getLong("next_epoch"));
    }

    @Test void emptyBucketStillReleasesAllBoundedTransientSlotsWithoutDirtyingSavedData() {
        var index = new LandingPadIndex(); UUID station = UUID.randomUUID();
        for (int i = 0; i < 128; i++) { assertTrue(index.choose(new UUID(319, i), station, null)); }
        assertFalse(index.choose(UUID.randomUUID(), UUID.randomUUID(), null));
        assertEquals(0, index.removeStation(station)); assertFalse(index.isDirty());
        assertTrue(index.choose(UUID.randomUUID(), UUID.randomUUID(), null));
        for (int i = 0; i < 128; i++) { assertNull(index.choice(new UUID(319, i)).target()); }
    }

    @Test void deletionDoesNotNormalizeOrDirtyUnavailableRoots() {
        var future = new CompoundTag(); future.putInt("schema", 999);
        var oversized = new CompoundTag(); oversized.putByteArray("large", new byte[LandingPadIndexCodec.MAX_BYTES]);
        for (var root : List.of(future, oversized)) {
            var index = LandingPadIndex.load(root);
            assertFalse(index.operational()); assertEquals(0, index.removeStation(UUID.randomUUID()));
            assertEquals(root, index.save(new CompoundTag())); assertFalse(index.isDirty());
        }
    }

    private static LandingPadEntry register(LandingPadIndex index, UUID station, int x) {
        return index.register(station, OWNER, UUID.randomUUID(), "Dock", new StationPosition(x, 80, 0));
    }

    private static List<LandingPadEntry> orphanEntries(int count) {
        var entries = new ArrayList<LandingPadEntry>();
        for (int i = 0; i < count; i++) {
            entries.add(new LandingPadEntry(new UUID(319, i + 2), UUID.randomUUID(), UUID.randomUUID(), OWNER,
                    "Dock", new StationPosition(0, 80, 0), i, null));
        }
        return entries;
    }

    private static int size(CompoundTag root) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) { NbtIo.write(root, output); }
        return bytes.size();
    }
}
