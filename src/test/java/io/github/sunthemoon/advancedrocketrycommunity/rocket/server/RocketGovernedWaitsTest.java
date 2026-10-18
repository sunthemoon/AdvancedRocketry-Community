package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W90 (R21 item 6): one INFO after 400 ticks continuously not ready and one when ready again; holds excluded. */
class RocketGovernedWaitsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static void visits(RocketGovernedWaits waits, RocketTransferRecord record, long from, long to,
                               RocketGovernedWaits.Wait wait, AtomicInteger margins) {
        for (long tick = from; tick <= to; tick += 8) {
            waits.observe(record, tick, wait, () -> { margins.incrementAndGet(); return "source=4/4 destination=1/4"; });
        }
    }

    @Test void aLongLoadingWaitWritesOneWaitingAndOneReadyLine() {
        var lines = new ArrayList<String>(); var waits = new RocketGovernedWaits(lines::add);
        var record = GovernedTransferFixture.create().record(); var margins = new AtomicInteger();
        visits(waits, record, 100, 492, RocketGovernedWaits.Wait.LOADING, margins);
        assertEquals(List.of(), lines, "A wait shorter than 400 ticks was reported");
        assertEquals(RocketGovernedWaits.Wait.LOADING, waits.wait(record.transferId()));
        visits(waits, record, 500, 2_000, RocketGovernedWaits.Wait.LOADING, margins);
        assertEquals(1, lines.size()); assertEquals(1, margins.get(), "Margins were read for more than the one line");
        assertEquals("ARCE_GOVERNED_ENDS_WAITING transfer=" + record.transferId() + " logical=" + record.logicalRocketId()
                + " phase=PREPARED cause=LOADING source=4/4 destination=1/4", lines.get(0));
        waits.observe(record, 2_008, RocketGovernedWaits.Wait.NONE, () -> fail("Ready line read the margins"));
        assertEquals("ARCE_GOVERNED_ENDS_READY transfer=" + record.transferId() + " waited=1908", lines.get(1));
        assertEquals(RocketGovernedWaits.Wait.NONE, waits.wait(record.transferId()));
        visits(waits, record, 2_016, 2_300, RocketGovernedWaits.Wait.NONE, margins);
        assertEquals(2, lines.size(), "Ready visits wrote more lines");
    }

    @Test void aCensusWaitIsReportedAsCensusAndAShortWaitEndsSilently() {
        var lines = new ArrayList<String>(); var waits = new RocketGovernedWaits(lines::add);
        var record = GovernedTransferFixture.create().record(); var margins = new AtomicInteger();
        visits(waits, record, 0, 392, RocketGovernedWaits.Wait.CENSUS, margins);
        waits.observe(record, 400, RocketGovernedWaits.Wait.NONE, () -> "unused");
        assertEquals(List.of(), lines, "A short wait wrote a ready line");
        visits(waits, record, 408, 808, RocketGovernedWaits.Wait.CENSUS, margins);
        assertEquals(1, lines.size()); assertTrue(lines.get(0).contains(" cause=CENSUS "));
    }

    @Test void holdsAndStoragePausesAreNotPartOfAContinuousWait() {
        var lines = new ArrayList<String>(); var waits = new RocketGovernedWaits(lines::add);
        var record = GovernedTransferFixture.create().record(); var margins = new AtomicInteger();
        visits(waits, record, 0, 392, RocketGovernedWaits.Wait.LOADING, margins);
        waits.excluded(record.transferId());
        assertEquals(RocketGovernedWaits.Wait.NONE, waits.wait(record.transferId()));
        visits(waits, record, 400, 792, RocketGovernedWaits.Wait.LOADING, margins);
        assertEquals(List.of(), lines, "An executor hold was counted as part of the wait");
        // A blocked journal skips visits; the wait restarts after the gap.
        visits(waits, record, 900, 1_292, RocketGovernedWaits.Wait.LOADING, margins);
        assertEquals(List.of(), lines, "A storage pause was counted as part of the wait");
        visits(waits, record, 1_300, 1_300, RocketGovernedWaits.Wait.LOADING, margins);
        assertEquals(1, lines.size());
    }

    @Test void ticketsAreBoundedAndZeroWhenExcludedAndStateIsDroppedWithItsRecord() {
        var waits = new RocketGovernedWaits(line -> { });
        var record = GovernedTransferFixture.create().record();
        var ids = new ArrayList<java.util.UUID>();
        for (int i = 0; i < 70; i++) { ids.add(java.util.UUID.randomUUID()); waits.tickets(ids.get(i), 8); }
        assertEquals(64, waits.size(), "Diagnostics grew past the transfer bound");
        assertEquals(8, waits.tickets(ids.get(0)));
        waits.excluded(ids.get(0)); assertEquals(0, waits.tickets(ids.get(0)));
        assertThrows(IllegalArgumentException.class, () -> waits.tickets(ids.get(1), 33));
        waits.retain(List.of(record));
        assertEquals(0, waits.size(), "Records absent from the journal kept diagnostics");
        waits.tickets(record.transferId(), 5); waits.retain(List.of(record));
        assertEquals(5, waits.tickets(record.transferId()));
        waits.clear(); assertEquals(0, waits.tickets(record.transferId()));
    }
}
