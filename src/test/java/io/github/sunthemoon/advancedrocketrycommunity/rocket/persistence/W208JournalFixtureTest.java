package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class W208JournalFixtureTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void ballastIsDeterministicBoundedAndOutsideLoadedDimensions() {
        var entry = W208JournalFixture.ballast(0, 4096);
        assertEquals(RocketTransactionCodec.encodeEntry(entry), RocketTransactionCodec.encodeEntry(W208JournalFixture.ballast(0, 4096)));
        assertEquals("advancedrocketrycommunity:w208_absent", entry.snapshot().sourceDimension().toString());
        assertEquals(2048, entry.snapshot().blocks().size());
        var data = new RocketTransactionSavedData();
        data.journalFor(entry.snapshot(), entry.ownerId()).write(entry.record());
        var loaded = RocketTransactionSavedData.load(data.save(new CompoundTag()));
        assertTrue(loaded.operational());
        assertEquals(data.save(new CompoundTag()), loaded.save(new CompoundTag()));
        assertFalse(loaded.acknowledgedCurrent());
    }

    @Test void sizingLeavesOneRealTransactionSlotAndBoundedHeadroom() {
        int padding = W208JournalFixture.padding();
        long bytes = W208JournalFixture.projectedBytes(padding);
        assertTrue(bytes >= 0.99 * W208JournalFixture.BOUND);
        assertTrue(bytes <= W208JournalFixture.BOUND - 8192);
        assertEquals(255, W208JournalFixture.BALLAST);
        assertTrue(W208JournalFixture.projectedBytes(padding + 1) > W208JournalFixture.BOUND - 8192);
        assertThrows(IllegalArgumentException.class, () -> W208JournalFixture.ballast(-1, 0));
    }
}
