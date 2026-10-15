package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketTransferReservationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void candidateValidationChangesNeitherJournalNorAcknowledgement() {
        var journal = new RocketTransferSavedData(); var before = journal.save(new CompoundTag());
        var result = journal.checkedWriteResult(); var sequence = journal.mutationSequence();
        journal.validateNewRecord(GovernedTransferFixture.create().record());
        assertEquals(before, journal.save(new CompoundTag())); assertEquals(sequence, journal.mutationSequence());
        assertEquals(result, journal.checkedWriteResult()); assertFalse(journal.isDirty());
        assertFalse(journal.acknowledgedCurrent()); assertTrue(journal.entries().isEmpty());
    }

    @Test void replayAndOverlappingDestinationRefuseWithoutDirtyingTheRoot() {
        var journal = new RocketTransferSavedData(); var record = GovernedTransferFixture.create().record();
        journal.put(record); journal.setDirty(false);
        var before = journal.save(new CompoundTag()); var sequence = journal.mutationSequence();
        assertThrows(IllegalArgumentException.class, () -> journal.validateNewRecord(record));
        // This ordinary record has different identities but its destination overlaps the governed one.
        assertThrows(IllegalArgumentException.class, () -> journal.validateNewRecord(RocketTransferSavedDataTest.record()));
        assertEquals(before, journal.save(new CompoundTag())); assertEquals(sequence, journal.mutationSequence());
        assertFalse(journal.isDirty());
    }

    @Test void unsupportedJournalCannotAcquireANewReservation() {
        var future = new RocketTransferSavedData().save(new CompoundTag());
        future.putInt("schema_version", RocketTransferSavedData.ROOT_SCHEMA_VERSION + 1);
        var journal = RocketTransferSavedData.load(future);
        assertThrows(IllegalArgumentException.class, () -> journal.validateNewRecord(GovernedTransferFixture.create().record()));
        assertEquals(future, journal.preservedBlockedData().orElseThrow()); assertFalse(journal.isDirty());
    }
}
