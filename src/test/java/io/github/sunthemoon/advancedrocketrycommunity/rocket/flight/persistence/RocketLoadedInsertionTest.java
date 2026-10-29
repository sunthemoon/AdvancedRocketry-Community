package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Serialized-input regression adapted from the independent W199 review probe. */
class RocketLoadedInsertionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void loadedUnencodableResidentRetainsWholeRootRefusal(boolean writeBack) throws Exception {
        var loaded = RocketTransferSavedData.load(abbreviatedRoot(1));
        assertTrue(loaded.operational(), "Load policy is unchanged");
        assertEquals(1, loaded.entries().size());
        var candidate = candidate();
        var all = new ArrayList<>(loaded.entries()); all.add(candidate);
        var expected = assertThrows(RuntimeException.class,
                () -> RocketTransferSavedData.encode(all, loaded.firstMoonLandingRam(), new CompoundTag()));
        long sequence = loaded.mutationSequence();
        for (int attempt = 0; attempt < 2; attempt++) {
            var actual = assertThrows(expected.getClass(), () -> validate(loaded, candidate, writeBack),
                    "An unproven loaded resident must retain the exact whole-root check on every failed attempt");
            assertEquals(expected.getMessage(), actual.getMessage());
        }
        assertEquals(sequence, loaded.mutationSequence());
        assertEquals(1, loaded.entries().size());
        assertTrue(loaded.operational());
        assertFalse(loaded.acknowledgedCurrent());
        assertThrows(RuntimeException.class, () -> loaded.save(new CompoundTag()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void canonicalBoundaryResidentIsStillAcceptedWithoutAcknowledgement(boolean writeBack) throws Exception {
        var loaded = RocketTransferSavedData.load(abbreviatedRoot(0));
        assertTrue(loaded.operational());
        var candidate = candidate();
        assertDoesNotThrow(() -> validate(loaded, candidate, writeBack));
        assertDoesNotThrow(() -> validate(loaded, candidate, writeBack));
        assertEquals(1, loaded.entries().size(), "Validation does not insert or acknowledge");
        assertFalse(loaded.acknowledgedCurrent());
    }

    private static void validate(RocketTransferSavedData data, RocketTransferRecord candidate, boolean writeBack) {
        if (writeBack) { data.validateWriteBack(candidate); } else { data.validateNewRecord(candidate); }
    }

    static RocketTransferRecord candidate() {
        return RocketTransferSavedDataTest.record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new RocketPosition(20000, 80, 20000));
    }

    private static CompoundTag abbreviatedRoot(int excess) throws Exception {
        var base = RocketTransferSavedDataTest.record();
        int bytes = RocketNbtSize.uncompressedBytes(unchecked(enlarged(base.sourceSnapshot(), 250000)));
        int relocationGrowth = RocketNbtSize.uncompressedBytes(unchecked(base.destinationSnapshot()))
                - RocketNbtSize.uncompressedBytes(unchecked(base.sourceSnapshot()));
        int sourceExcess = excess == 0 ? -Math.max(0, relocationGrowth) : excess;
        var source = enlarged(base.sourceSnapshot(), 250000 + RocketLimits.MAX_TOTAL_NBT_BYTES + sourceExcess - bytes);
        var destination = source.relocated(base.destinationSnapshot().snapshotId(),
                base.destinationSnapshot().sourceDimension(), base.destinationSnapshot().sourceOrigin(),
                base.destinationSnapshot().createdAtGameTime());
        var record = RocketTransferRecord.create(base.transferId(), base.logicalRocketId(), base.ownerId(),
                base.sourceEntityId(), source, destination, base.sourceFlightData(), base.destinationFlightData(),
                base.requiredFuel(), base.createdAtGameTime());
        var root = new RocketTransferSavedData(); root.put(base);
        var rawRoot = root.save(new CompoundTag());
        var rawRecord = rawRoot.getList("transfers", Tag.TAG_COMPOUND).getCompound(0);
        rawRecord.put("source_snapshot", abbreviated(source));
        rawRecord.put("destination_snapshot", abbreviated(destination));
        rawRecord.putString("checksum", record.checksum());
        assertEquals(RocketLimits.MAX_TOTAL_NBT_BYTES + sourceExcess, RocketNbtSize.uncompressedBytes(unchecked(source)));
        if (excess == 0) {
            assertEquals(RocketLimits.MAX_TOTAL_NBT_BYTES, Math.max(RocketNbtSize.uncompressedBytes(unchecked(source)),
                    RocketNbtSize.uncompressedBytes(unchecked(destination))));
        }
        return rawRoot;
    }

    private static RocketStructureSnapshot enlarged(RocketStructureSnapshot original, int lastLength) {
        var blocks = new ArrayList<RocketBlock>();
        for (int i = 0; i < original.blocks().size(); i++) {
            var block = original.blocks().get(i);
            var data = new CompoundTag(); data.putByteArray("padding", new byte[i == 3 ? lastLength : 262000]);
            blocks.add(new RocketBlock(block.position(), block.state(),
                    new RocketBlockEntityPayload(new ResourceLocation("minecraft:container"), data)));
        }
        return RocketStructureSnapshot.create(original.snapshotId(), original.sourceDimension(), original.sourceOrigin(),
                blocks, original.passengerAnchors(), new RocketStats(4, 200, 1000, 1000, 1, 1, 1, 4), original.createdAtGameTime());
    }

    private static CompoundTag unchecked(RocketStructureSnapshot snapshot) throws Exception {
        // Fixture construction only; never inject or alter a loaded journal's resident records.
        var method = RocketSnapshotNbtCodec.class.getDeclaredMethod("encodeUnchecked", RocketStructureSnapshot.class);
        method.setAccessible(true); return (CompoundTag) method.invoke(null, snapshot);
    }

    private static CompoundTag abbreviated(RocketStructureSnapshot snapshot) throws Exception {
        var tag = unchecked(snapshot);
        for (var entry : tag.getList("block_palette", Tag.TAG_COMPOUND)) {
            var state = (CompoundTag) entry;
            state.putString("id", state.getString("id").replace("minecraft:", ""));
        }
        for (var entry : tag.getList("relative_blocks", Tag.TAG_COMPOUND)) {
            ((CompoundTag) entry).getCompound("block_entity").putString("adapter", "container");
        }
        assertTrue(RocketNbtSize.uncompressedBytes(tag) <= RocketLimits.MAX_TOTAL_NBT_BYTES);
        return tag;
    }
}
