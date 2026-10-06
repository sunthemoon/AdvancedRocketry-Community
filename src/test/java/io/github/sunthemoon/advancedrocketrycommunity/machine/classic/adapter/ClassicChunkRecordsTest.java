package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class ClassicChunkRecordsTest {
    @Test void exactManagedIdsAreCapturedWithoutDecodingMissingOrForeignRoots() {
        CompoundTag hatch = entry("advancedrocketrycommunity:classic_hatch", -16, -64, 15);
        hatch.putString("arce_classic_hatch", "unreadable");
        CompoundTag lathe = entry("advancedrocketrycommunity:lathe", -1, 319, 0);
        var scan = read(hatch, lathe);
        assertFalse(scan.refused()); assertEquals(2, scan.records().size());
        assertEquals("advancedrocketrycommunity:classic_hatch", scan.records().get(new ClassicChunkRecords.Position(-16, -64, 15)));
        assertEquals("advancedrocketrycommunity:lathe", scan.records().get(new ClassicChunkRecords.Position(-1, 319, 0)));
        assertEquals("unreadable", hatch.getString("arce_classic_hatch"));
        assertFalse(lathe.contains("arce_classic_machine"));
    }

    @Test void emptyNativeListIsDistinctFromMissingOrWrongOuterData() {
        assertFalse(read().refused()); assertTrue(read().records().isEmpty());
        assertTrue(ClassicChunkRecords.read(new CompoundTag(), -1, 0, -64, 384).refused());
        CompoundTag wrong = new CompoundTag(); wrong.putString("block_entities", "wrong");
        assertTrue(ClassicChunkRecords.read(wrong, -1, 0, -64, 384).refused());
        ListTag list = new ListTag(); list.add(IntTag.valueOf(1)); wrong.put("block_entities", list);
        assertTrue(ClassicChunkRecords.read(wrong, -1, 0, -64, 384).refused());
    }

    @Test void recognizedRootUnderOtherTypeRefusesBeforeInspectingItsValue() {
        for (String root : List.of("arce_classic_hatch", "arce_classic_resources", "arce_classic_machine")) {
            CompoundTag other = entry("other:device", -1, 0, 0); other.put(root, new CompoundTag());
            assertTrue(read(other).refused()); assertTrue(other.contains(root));
        }
        CompoundTag other = entry("other:device", -1, 0, 0);
        other.putString("arce_process", "unrelated older machine root");
        assertFalse(read(other).refused()); assertTrue(read(other).records().isEmpty());
    }

    @Test void numericCoercionMissingCoordinatesAndWrongIdTypeAreRefused() {
        for (String coordinate : List.of("x", "y", "z")) {
            CompoundTag value = entry("advancedrocketrycommunity:classic_hatch", -1, 0, 0);
            value.putByte(coordinate, (byte) 0); assertTrue(read(value).refused());
            value.remove(coordinate); assertTrue(read(value).refused());
        }
        CompoundTag value = entry("advancedrocketrycommunity:classic_hatch", -1, 0, 0);
        value.putInt("id", 1); assertTrue(read(value).refused());
    }

    @Test void duplicatesIncludingForeignEntriesNeverProduceAPartialExpectedSet() {
        CompoundTag managed = entry("advancedrocketrycommunity:classic_hatch", -1, 0, 0);
        var scan = read(managed, entry("other:device", -1, 0, 0));
        assertTrue(scan.refused()); assertTrue(scan.records().isEmpty());
        assertTrue(read(managed, managed.copy()).refused());
    }

    @Test void chunkAndBuildHeightBoundariesAreExactAndInclusiveOnlyAtTheBottom() {
        for (int[] position : List.of(new int[]{-17, 0, 0}, new int[]{0, 0, 0}, new int[]{-1, -65, 0},
                new int[]{-1, 320, 0}, new int[]{-1, 0, -1}, new int[]{-1, 0, 16})) {
            assertTrue(read(entry("advancedrocketrycommunity:classic_hatch", position[0], position[1], position[2])).refused());
        }
        assertFalse(read(entry("advancedrocketrycommunity:classic_hatch", -16, -64, 0)).refused());
        assertFalse(read(entry("advancedrocketrycommunity:classic_hatch", -1, 319, 15)).refused());
    }

    @Test void physicalRecordBoundRejectsBeforeTraversalAndHasNoTruncation() {
        ListTag list = new ListTag();
        for (int z = 0; z < 16; z++) {
            for (int x = -16; x < 0; x++) { list.add(entry("advancedrocketrycommunity:classic_hatch", x, 0, z)); }
        }
        CompoundTag parent = new CompoundTag(); parent.put("block_entities", list);
        var scan = ClassicChunkRecords.read(parent, -1, 0, 0, 1);
        assertFalse(scan.refused()); assertEquals(256, scan.records().size());
        list.add(entry("other:device", -1, 1, 0));
        assertTrue(ClassicChunkRecords.read(parent, -1, 0, 0, 1).refused()); assertEquals(257, list.size());
    }

    @Test void metadataOwnsOnlyImmutableScalarsAndDoesNotFollowLaterNbtMutation() {
        CompoundTag value = entry("advancedrocketrycommunity:classic_hatch", -1, 0, 0);
        var scan = read(value); value.putInt("x", -2); value.putString("id", "other:device");
        assertEquals("advancedrocketrycommunity:classic_hatch", scan.records().get(new ClassicChunkRecords.Position(-1, 0, 0)));
        assertThrows(UnsupportedOperationException.class, () -> scan.records().clear());
    }

    @Test void invalidBuildDimensionsAndSignedCoordinateLimitsDoNotWrap() {
        CompoundTag empty = parent();
        assertTrue(ClassicChunkRecords.read(empty, -1, 0, 0, 0).refused());
        assertTrue(ClassicChunkRecords.read(empty, -1, 0, 0, -1).refused());
        assertTrue(ClassicChunkRecords.read(empty, -1, 0, Integer.MAX_VALUE, 1).refused());
        assertTrue(ClassicChunkRecords.read(empty, -1, 0, 0, Integer.MAX_VALUE).refused());
        CompoundTag edge = parent(entry("advancedrocketrycommunity:classic_hatch", Integer.MIN_VALUE, 0, Integer.MAX_VALUE));
        var scan = ClassicChunkRecords.read(edge, Integer.MIN_VALUE >> 4, Integer.MAX_VALUE >> 4, 0, 1);
        assertFalse(scan.refused());
        assertTrue(scan.records().containsKey(new ClassicChunkRecords.Position(Integer.MIN_VALUE, 0, Integer.MAX_VALUE)));
        assertNotEquals(new ClassicChunkRecords.Position(0, 0, 0), new ClassicChunkRecords.Position(1 << 26, 0, 0));
    }

    @Test void foreignParentAndEntryImplementationsAreNotInvoked() {
        CompoundTag foreign = new CompoundTag() { @Override public Tag get(String key) { fail("Foreign getter called"); return null; } };
        assertTrue(ClassicChunkRecords.read(foreign, -1, 0, -64, 384).refused());
        assertTrue(read(foreign).refused());
        assertTrue(ClassicChunkRecords.read(null, -1, 0, -64, 384).refused());
    }

    private static ClassicChunkRecords.Scan read(CompoundTag... entries) {
        return ClassicChunkRecords.read(parent(entries), -1, 0, -64, 384);
    }
    private static CompoundTag parent(CompoundTag... entries) {
        ListTag list = new ListTag(); for (CompoundTag entry : entries) { list.add(entry); }
        CompoundTag parent = new CompoundTag(); parent.put("block_entities", list); return parent;
    }
    private static CompoundTag entry(String id, int x, int y, int z) {
        CompoundTag value = new CompoundTag(); value.putString("id", id);
        value.putInt("x", x); value.putInt("y", y); value.putInt("z", z); return value;
    }
}
