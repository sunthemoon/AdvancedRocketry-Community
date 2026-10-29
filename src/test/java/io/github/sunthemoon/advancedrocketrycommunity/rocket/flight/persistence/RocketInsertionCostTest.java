package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEvent;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventOutbox;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketInsertionCostTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void admissionDoesNotUnconditionallyReencodeEveryResidentSnapshot() throws Exception {
        var source = Files.readString(Path.of("src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/flight/persistence/RocketTransferSavedData.java"));
        var method = source.substring(source.indexOf("private void validateInsert("), source.indexOf("public void put("));
        assertFalse(method.contains("encode(candidate"), "Every write-back serializes the entire maximum journal on the tick thread");
        assertTrue(method.contains("RocketJournalInsertionBudget.validate("));
    }

    @Test void aFullAdmittedJournalOnlyEncodesTheCandidate() {
        var candidate = RocketTransferSavedDataTest.record();
        var calls = new ArrayList<Integer>();
        RocketJournalInsertionBudget.validate(Collections.nCopies(63, candidate), candidate, records -> {
            calls.add(records.size()); assertSame(candidate, records.get(0));
            return RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES + 4096;
        });
        assertEquals(List.of(1), calls);
    }

    @Test void anInconclusiveUpperBoundFallsBackToTheOriginalExactCheck() {
        var candidate = RocketTransferSavedDataTest.record();
        var calls = new ArrayList<Integer>();
        RocketJournalInsertionBudget.validate(Collections.nCopies(63, candidate), candidate, records -> {
            calls.add(records.size()); return RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES;
        });
        assertEquals(List.of(1, 64), calls, "A conservative bound must not invent a new refusal");
        assertThrows(IllegalStateException.class, () -> RocketJournalInsertionBudget.validate(
                List.of(), candidate, records -> { throw new IllegalStateException("codec bound"); }));
        assertThrows(IllegalStateException.class, () -> RocketJournalInsertionBudget.validate(
                Collections.nCopies(63, candidate), candidate, records -> {
                    if (records.size() > 1) { throw new IllegalStateException("root bound"); }
                    return RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES;
                }));
    }

    @Test void actualListEncodingMatchesAdditiveSizingForAllOutboxStatesAndBothRecordKinds() {
        var players = new ArrayList<UUID>();
        for (int i = 0; i < 16; i++) { players.add(new UUID(3, i)); }
        var event = FirstEvent.MoonLanding.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64), 0, 80, 0, 160, players);
        var elected = FirstEventOutbox.elected(event);
        for (var outbox : List.of(FirstEventOutbox.empty(), elected, elected.delivered())) {
            int framing = RocketNbtSize.uncompressedBytes(RocketTransferSavedData.encode(List.of(), outbox, new CompoundTag()));
            for (var record : List.of(RocketTransferSavedDataTest.record(), GovernedTransferFixture.create().record())) {
                int recordBytes = RocketNbtSize.uncompressedBytes(RocketTransferJournalCodec.encodeRecord(record));
                for (int count : List.of(1, 2, 63, 64)) {
                    int actual = RocketNbtSize.uncompressedBytes(RocketTransferSavedData.encode(
                            Collections.nCopies(count, record), outbox, new CompoundTag()));
                    assertEquals(framing + count * (recordBytes - 3), actual, "Named-root header is absent inside a list");
                    long upper = framing + (recordBytes - 3) + (long) (count - 1) * RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES;
                    assertTrue(actual <= upper && upper <= RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES);
                }
            }
        }
    }
}
