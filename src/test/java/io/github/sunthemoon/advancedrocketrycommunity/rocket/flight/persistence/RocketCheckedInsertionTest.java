package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RocketCheckedInsertionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void validationAndPutEncodeTheExactCandidateOnlyOnce(boolean writeBack) {
        var calls = new ArrayList<RocketTransferRecord>();
        var journal = journal(calls);
        var record = RocketTransferSavedDataTest.record();
        if (writeBack) { journal.validateWriteBack(record); } else { journal.validateNewRecord(record); }
        assertEquals(List.of(record), calls);
        long sequence = journal.mutationSequence();
        assertTrue(journal.entries().isEmpty());
        assertFalse(journal.acknowledgedCurrent());
        journal.put(record);
        assertEquals(List.of(record), calls, "A validated candidate must not be encoded again in put");
        assertSame(record, journal.find(record.transferId()).orElseThrow());
        assertEquals(sequence + 1, journal.mutationSequence());
        assertFalse(journal.acknowledgedCurrent());
    }

    @Test void unvalidatedAndReusedCandidatesAlwaysEncodeNormally() {
        var calls = new ArrayList<RocketTransferRecord>();
        var journal = journal(calls);
        var record = RocketTransferSavedDataTest.record();
        journal.put(record); journal.remove(record.transferId());
        journal.validateWriteBack(record); journal.put(record);
        journal.put(record);
        assertEquals(List.of(record, record, record), calls);
    }

    @Test void anInterveningMutationInvalidatesTheWitness() {
        var calls = new ArrayList<RocketTransferRecord>();
        var journal = journal(calls);
        var resident = RocketTransferSavedDataTest.record();
        var candidate = RocketLoadedInsertionTest.candidate();
        journal.put(resident);
        calls.clear();
        journal.validateWriteBack(candidate);
        journal.remove(resident.transferId());
        journal.put(candidate);
        assertEquals(List.of(candidate, candidate), calls);
    }

    @Test void anEqualButDistinctObjectCannotUseTheWitness() {
        var calls = new ArrayList<RocketTransferRecord>();
        var journal = journal(calls);
        var record = RocketTransferSavedDataTest.record();
        var copy = RocketTransferJournalCodec.decodeRecord(RocketTransferJournalCodec.encodeRecord(record));
        assertNotSame(record, copy);
        journal.validateWriteBack(record); journal.put(copy);
        assertEquals(2, calls.size()); assertSame(copy, calls.get(1));
    }

    @Test void failedValidationClearsThePreviousWitnessAndCannotAcknowledge() {
        var calls = new ArrayList<RocketTransferRecord>();
        var journal = journal(calls);
        var resident = RocketTransferSavedDataTest.record();
        var candidate = RocketLoadedInsertionTest.candidate();
        journal.put(resident); calls.clear();
        journal.validateWriteBack(candidate);
        assertThrows(IllegalArgumentException.class, () -> journal.validateWriteBack(resident));
        journal.put(candidate);
        assertEquals(List.of(candidate, candidate), calls);
        assertFalse(journal.acknowledgedCurrent());
    }

    @Test void witnessesAreJournalLocalAndRequireCanonicalResidents() {
        var calls = new ArrayList<RocketTransferRecord>();
        var first = journal(calls); var second = journal(calls);
        var record = RocketTransferSavedDataTest.record();
        first.validateWriteBack(record); second.put(record);
        assertEquals(List.of(record, record), calls);
        first.put(record);
        assertEquals(2, calls.size(), "Another journal cannot consume this journal's witness");
        var checked = new RocketCheckedInsertion(value -> {
            calls.add(value); return RocketTransferJournalCodec.encodeRecord(value);
        });
        checked.validated(record, 10); checked.check(record, 10, false);
        checked.check(record, 10, true);
        assertEquals(4, calls.size(), "An unproven resident set cannot consume or retain the shortcut");
    }

    private static RocketTransferSavedData journal(List<RocketTransferRecord> calls) {
        return new RocketTransferSavedData(RocketJournalSchedule.LIMIT, new RocketCheckedInsertion(record -> {
            calls.add(record); return RocketTransferJournalCodec.encodeRecord(record);
        }));
    }
}
