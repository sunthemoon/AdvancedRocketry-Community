package io.github.sunthemoon.advancedrocketrycommunity.station.notice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationGridCell;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationReservation;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The bounded store of ADR-065 section 5.3: an empty summary for a new station, in-place replacement with an increasing
 * revision, exactly-once acknowledgement, removed-member privacy, the 4,096-entry and 32-acknowledgement bounds.
 */
class StationNoticeBookTest {
    private static final ResourceLocation MOON = ModIdentity.id("moon");
    private static final ResourceLocation EARTH = ModIdentity.id("earth");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    static StationState station(int cell, UUID owner, List<UUID> members) {
        StationState station = StationState.fromReservation(new StationReservation(new UUID(22, cell), owner,
                "Notice " + cell, new StationGridCell(cell, 0), EARTH, 0));
        for (UUID member : members) {
            station = station.withMember(member);
        }
        return station;
    }

    static StationNotice committed(String name, int balance) {
        return StationNotice.committed(name, EARTH, MOON, "in_system", 2_000_000, balance);
    }

    @Test
    void aNewStationHasTheEmptySummaryAndAnOutcomeReplacesItInPlace() {
        UUID owner = new UUID(1, 1);
        StationState station = station(1, owner, List.of());
        StationNoticeBook book = new StationNoticeBook();
        assertSame(StationNoticeSummary.EMPTY, book.summary(station.stationId()));
        assertEquals(StationNoticeBook.Admission.ADMITTED, book.add(station.stationId()));
        assertEquals(StationNoticeBook.Admission.UNCHANGED, book.add(station.stationId()));
        assertEquals(0L, book.summary(station.stationId()).revision());
        assertTrue(book.summary(station.stationId()).outcome().isEmpty());
        assertTrue(book.pending(station, owner).isEmpty(), "an empty summary shows nothing");
        long bytes = StationNoticeCodec.encode(book).toString().length();
        for (int event = 1; event <= 50; event++) {
            StationNotice outcome = event % 2 == 0 ? committed(station.name(), event)
                    : StationNotice.aborted(station.name(), StationManagementCode.STATION_CHANGED, MOON);
            assertEquals(StationNoticeBook.Admission.ADMITTED, book.record(station, outcome, List.of()));
            assertEquals(event, book.summary(station.stationId()).revision(), "revisions increase by one");
            assertEquals(Optional.of(outcome), book.summary(station.stationId()).outcome());
            assertEquals(1, book.size(), "one summary per station, never an event history");
        }
        assertTrue(StationNoticeCodec.encode(book).toString().length() < bytes + 400,
                "fifty outcomes do not grow the store");
    }

    @Test
    void anOfflineMemberSeesTheLatestOutcomeExactlyOnce() {
        UUID owner = new UUID(2, 1);
        UUID online = new UUID(2, 2);
        UUID offline = new UUID(2, 3);
        StationState station = station(2, owner, List.of(online, offline));
        StationNoticeBook book = new StationNoticeBook();
        UUID visitor = new UUID(2, 9);
        book.record(station, committed(station.name(), 1), List.of(owner, online, visitor));
        book.record(station, committed(station.name(), 2), List.of(owner, online, visitor));
        StationNoticeSummary summary = book.summary(station.stationId());
        assertEquals(OptionalLong.of(2L), summary.acknowledged(owner));
        assertEquals(OptionalLong.of(2L), summary.acknowledged(online));
        assertEquals(OptionalLong.empty(), summary.acknowledged(offline));
        assertEquals(OptionalLong.empty(), summary.acknowledged(visitor), "a visitor is never a summary recipient");
        assertTrue(book.pending(station, owner).isEmpty() && book.pending(station, online).isEmpty());
        assertTrue(book.pending(station, visitor).isEmpty());
        // Rapid events are summarized: only the latest outcome, once.
        assertEquals(Optional.of(committed(station.name(), 2)), book.pending(station, offline));
        assertTrue(book.acknowledge(station, offline));
        assertTrue(book.pending(station, offline).isEmpty());
        assertFalse(book.acknowledge(station, offline), "a second acknowledgement changes nothing");
        // The next outcome is unacknowledged again for everyone not told live.
        book.record(station, StationNotice.aborted(station.name(), StationManagementCode.WARP_CANCELLED, MOON),
                List.of(online));
        assertEquals(3L, book.summary(station.stationId()).revision());
        assertTrue(book.pending(station, owner).isPresent() && book.pending(station, offline).isPresent());
        assertTrue(book.pending(station, online).isEmpty());
    }

    @Test
    void aRemovedMembersAcknowledgementIsRemovedAndTheyAreNeverTold() {
        UUID owner = new UUID(3, 1);
        UUID member = new UUID(3, 2);
        UUID removed = new UUID(3, 3);
        StationState station = station(3, owner, List.of(member, removed));
        StationNoticeBook book = new StationNoticeBook();
        book.record(station, committed(station.name(), 1), List.of(owner, member, removed));
        assertEquals(Set.of(owner, member, removed), book.summary(station.stationId()).players());
        StationState without = station.withoutMember(removed);
        assertTrue(book.reconcile(without));
        assertEquals(Set.of(owner, member), book.summary(station.stationId()).players(),
                "the removed member's acknowledgement is gone");
        assertFalse(book.reconcile(without));
        book.record(without, committed(station.name(), 2), List.of());
        assertTrue(book.pending(without, removed).isEmpty(), "membership is checked before anything is shown");
        assertFalse(book.acknowledge(without, removed));
        assertFalse(book.summary(station.stationId()).players().contains(removed));
        // Every change that names the live station reconciles first, even without the removal hook.
        StationNoticeBook lazy = new StationNoticeBook();
        lazy.record(station, committed(station.name(), 1), List.of(removed));
        lazy.acknowledge(without, member);
        assertEquals(Set.of(member), lazy.summary(station.stationId()).players());
    }

    @Test
    void anOwnershipTransferMovesAcknowledgementsBetweenTheirFields() {
        UUID owner = new UUID(4, 1);
        UUID heir = new UUID(4, 2);
        StationState station = station(4, owner, List.of(heir));
        StationNoticeBook book = new StationNoticeBook();
        book.record(station, committed(station.name(), 1), List.of(owner, heir));
        StationState transferred = station.transferOwnership(heir);
        assertTrue(book.reconcile(transferred));
        StationNoticeSummary summary = book.summary(station.stationId());
        assertEquals(Optional.of(new StationNoticeSummary.Acknowledgement(heir, 1L)), summary.owner());
        assertEquals(Map.of(owner, 1L), summary.members());
        assertTrue(book.pending(transferred, owner).isEmpty() && book.pending(transferred, heir).isEmpty());
    }

    @Test
    void thirtyTwoMembersAndTheOwnerAreAllAcknowledgedWithinTheBound() {
        UUID owner = new UUID(5, 0);
        List<UUID> members = new ArrayList<>();
        for (int index = 1; index <= StationLimits.MAX_MEMBERS; index++) {
            members.add(new UUID(5, index));
        }
        assertEquals(StationLimits.MAX_MEMBERS, StationNoticeLimits.MAX_MEMBER_ACKNOWLEDGEMENTS);
        StationState station = station(5, owner, members);
        StationNoticeBook book = new StationNoticeBook();
        List<UUID> everyone = new ArrayList<>(members);
        everyone.add(owner);
        book.record(station, committed(station.name(), 1), everyone);
        StationNoticeSummary summary = book.summary(station.stationId());
        assertEquals(StationNoticeLimits.MAX_MEMBER_ACKNOWLEDGEMENTS, summary.members().size());
        assertEquals(OptionalLong.of(1L), summary.acknowledged(owner));
        Map<UUID, Long> overflow = new java.util.HashMap<>(summary.members());
        overflow.put(new UUID(5, 99), 1L);
        assertThrows(IllegalArgumentException.class, () -> new StationNoticeSummary(1L, summary.outcome(),
                summary.owner(), overflow), "a 33rd member acknowledgement is refused");
    }

    @Test
    void theStoreHoldsAtMost4096StationsAndDropsMissingOnes() {
        StationNoticeBook book = new StationNoticeBook();
        for (int index = 0; index < StationNoticeLimits.MAX_STATIONS; index++) {
            assertEquals(StationNoticeBook.Admission.ADMITTED, book.add(new UUID(6, index)));
        }
        assertEquals(StationLimits.MAX_STATIONS, StationNoticeLimits.MAX_STATIONS);
        UUID owner = new UUID(6, -1);
        StationState extra = station(6, owner, List.of());
        assertEquals(StationNoticeBook.Admission.FULL, book.add(extra.stationId()));
        assertEquals(StationNoticeBook.Admission.FULL, book.record(extra, committed(extra.name(), 1), List.of()));
        assertEquals(StationNoticeLimits.MAX_STATIONS, book.size(), "a refused change leaves the book as it was");
        assertEquals(1, book.retain(stationId -> !stationId.equals(new UUID(6, 0))));
        assertEquals(StationNoticeBook.Admission.ADMITTED, book.record(extra, committed(extra.name(), 1), List.of()));
        assertTrue(book.remove(extra.stationId()));
        assertFalse(book.remove(extra.stationId()));
    }

    @Test
    void anExhaustedRevisionRefusesWithoutChange() {
        UUID owner = new UUID(7, 1);
        StationState station = station(7, owner, List.of());
        StationNoticeBook book = new StationNoticeBook();
        StationNoticeSummary last = new StationNoticeSummary(Long.MAX_VALUE, Optional.of(committed(station.name(), 1)),
                Optional.empty(), Map.of());
        book.restore(station.stationId(), last);
        assertEquals(StationNoticeBook.Admission.EXHAUSTED, book.record(station, committed(station.name(), 2),
                List.of()));
        assertSame(last, book.summary(station.stationId()));
    }

    @Test
    void summariesRefuseInconsistentRevisions() {
        StationNotice outcome = committed("Alpha", 1);
        UUID player = new UUID(8, 1);
        assertThrows(IllegalArgumentException.class, () -> new StationNoticeSummary(0L, Optional.of(outcome),
                Optional.empty(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new StationNoticeSummary(1L, Optional.empty(),
                Optional.empty(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new StationNoticeSummary(-1L, Optional.empty(),
                Optional.empty(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new StationNoticeSummary(1L, Optional.of(outcome),
                Optional.of(new StationNoticeSummary.Acknowledgement(player, 2L)), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new StationNoticeSummary(1L, Optional.of(outcome),
                Optional.empty(), Map.of(player, 0L)));
        assertThrows(IllegalArgumentException.class, () -> new StationNoticeSummary(1L, Optional.of(outcome),
                Optional.of(new StationNoticeSummary.Acknowledgement(player, 1L)), Map.of(player, 1L)));
        assertThrows(IllegalArgumentException.class, () -> new StationNoticeSummary(1L,
                Optional.of(StationNotice.countdown("Alpha", MOON, 1)), Optional.empty(), Map.of()));
    }
}
