package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EscrowLoaderS6AccountingTest {
    @Test void allFourPrefixesUseTheRecordAmountNotTheRawSum() {
        for (boolean sourceAfter : new boolean[] {false, true}) {
            for (boolean destinationAfter : new boolean[] {false, true}) {
                long source = sourceAfter ? 4_000 : 5_000, destination = destinationAfter ? 1_000 : 0;
                var result = EscrowLoaderS6Accounting.classify(true, source, destination, 1_000L,
                        sourceAfter, destinationAfter, (sourceAfter ? 1 : 0) + (destinationAfter ? 1 : 0));
                assertEquals(4_000, result.sourceAvailable());
                assertEquals(sourceAfter ? 0 : 1_000, result.sourceHeld());
                assertEquals(0, result.destinationAvailable());
                assertEquals(destinationAfter ? 1_000 : 0, result.destinationHeld());
                assertEquals(sourceAfter && !destinationAfter ? 1_000 : 0, result.inTransit());
                assertEquals(source + destination, result.sourceAvailable() + result.sourceHeld()
                        + result.destinationAvailable() + result.destinationHeld());
            }
        }
    }

    @Test void rejectsCreditBeyondTheRecordEvenWhenAnotherStoreOffsetsIt() {
        for (long source : new long[] {5_000, 4_000, 4_999, 3_999}) {
            assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6Accounting.classify(
                    true, source, 1_001, 1_000L, source < 5_000, true, 2));
        }
    }

    @Test void rejectsHeldCopiesOrMarkersWithoutARecord() {
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6Accounting.classify(
                true, 5_000, 1_000, null, false, true, 1));
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6Accounting.classify(
                true, 4_000, 1_000, null, true, true, 1));
    }

    @Test void completionRequiresExactTotalAndEachEndpoint() {
        var result = EscrowLoaderS6Accounting.classify(true, 4_000, 1_000, null, true, true, 0);
        assertEquals(new EscrowLoaderS6Accounting.Partition(4_000, 0, 1_000, 0, 0), result);
        for (long[] changed : new long[][] {{4_001, 1_000}, {4_000, 999}, {3_999, 1_001}}) {
            assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6Accounting.classify(
                    true, changed[0], changed[1], null, true, true, 0));
        }
    }

    @Test void unknownCannotBeClassifiedAsZeroOrEvenAsTheExpectedImage() {
        for (long[] image : new long[][] {{0, 0}, {4_000, 1_000}}) {
            assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6Accounting.classify(
                    false, image[0], image[1], null, true, true, 0));
        }
    }

    @Test void wrongRecordQuantityAndMarkerFrontierAreRejected() {
        for (long q : new long[] {-1, 0, 999, 1_001, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6Accounting.classify(
                    true, 5_000, q, q, false, true, 1));
        }
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6Accounting.classify(
                true, 4_000, 1_000, 1_000L, true, true, 0));
    }
}
