package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import static io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W44 A0: section 3.3 election dispositions and permanent holds, including every unavailable election gate. */
class FirstEventElectionTest {
    private static final FirstEventKind KIND = FirstEventKind.FIRST_STATION_WARP;
    private static final FirstEventOutbox EMPTY = FirstEventOutbox.empty();

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void onlyAKnownEmptySourceWithAValidatedAbsentReceiptAllowsAnElection() {
        var absent = ClassicProgressionSavedData.provenAbsent();
        assertEquals(FirstEventDisposition.ALLOW, disposition(FirstEventSourceView.known(EMPTY), EMPTY, absent));
        var loadedAbsent = ClassicProgressionSavedData.load(absent.save(new CompoundTag()));
        assertEquals(FirstEventDisposition.ALLOW, disposition(FirstEventSourceView.known(EMPTY), EMPTY, loadedAbsent));
        // Every unavailable path defers; none can allow, even with EMPTY RAM.
        for (var view : List.of(FirstEventSourceView.unconfirmed(),
                FirstEventSourceView.unavailable(FirstEventSourceView.Reason.QUARANTINED),
                FirstEventSourceView.unavailable(FirstEventSourceView.Reason.ROOT_UNREADABLE),
                FirstEventSourceView.unavailable(FirstEventSourceView.Reason.ROOT_BLOCKED))) {
            assertEquals(FirstEventDisposition.DEFER, disposition(view, EMPTY, absent), view.toString());
        }
        for (var presence : List.of(ManagedRootPresence.UNREADABLE)) {
            assertEquals(FirstEventDisposition.DEFER, disposition(FirstEventSourceView.known(EMPTY), EMPTY,
                    ClassicProgressionSavedData.created(presence)));
        }
        CompoundTag invalid = absent.save(new CompoundTag());
        invalid.putInt("schema_version", 9);
        assertEquals(FirstEventDisposition.DEFER, disposition(FirstEventSourceView.known(EMPTY), EMPTY,
                ClassicProgressionSavedData.load(invalid)), "a blocked store never allows");
        assertEquals(FirstEventDisposition.DEFER, disposition(FirstEventSourceView.known(EMPTY), EMPTY, null));
    }

    @Test void anOccupiedSlotOrAKnownEventIsNotFirst() {
        var event = warp(1, List.of(id(4)));
        var absent = ClassicProgressionSavedData.provenAbsent();
        var elected = FirstEventOutbox.elected(event);
        assertEquals(FirstEventDisposition.NOT_FIRST, disposition(FirstEventSourceView.known(EMPTY), elected, absent),
                "no later action can win while the RAM slot is occupied");
        assertEquals(FirstEventDisposition.NOT_FIRST, disposition(FirstEventSourceView.unconfirmed(), elected, absent));
        assertEquals(FirstEventDisposition.NOT_FIRST, disposition(FirstEventSourceView.known(elected), EMPTY, absent));
        assertEquals(FirstEventDisposition.NOT_FIRST,
                disposition(FirstEventSourceView.known(elected.delivered()), EMPTY, absent));
    }

    @Test void permanentHoldsNeedAProvenComparison() {
        var event = warp(1, List.of(id(4)));
        var other = warp(2, List.of(id(4)));
        var occurred = ClassicProgressionSavedData.provenAbsent();
        occurred.insert(event);
        var absent = ClassicProgressionSavedData.provenAbsent();
        var elected = FirstEventOutbox.elected(event);
        var known = FirstEventSourceView.known(EMPTY);
        assertEquals(Optional.of(FirstEventElection.Hold.SOURCE_ROLLBACK), FirstEventElection.hold(KIND, known, occurred));
        assertEquals(FirstEventDisposition.NOT_FIRST, disposition(known, EMPTY, occurred), "travel proceeds as not first");
        var mismatch = ClassicProgressionSavedData.provenAbsent();
        mismatch.insert(other);
        assertEquals(Optional.of(FirstEventElection.Hold.RECEIPT_MISMATCH),
                FirstEventElection.hold(KIND, FirstEventSourceView.known(elected), mismatch));
        assertEquals(Optional.of(FirstEventElection.Hold.RECEIPT_MISMATCH),
                FirstEventElection.hold(KIND, FirstEventSourceView.known(elected.delivered()), mismatch));
        assertEquals(Optional.of(FirstEventElection.Hold.RECEIPT_MISSING),
                FirstEventElection.hold(KIND, FirstEventSourceView.known(elected.delivered()), absent));
        assertEquals(Optional.empty(), FirstEventElection.hold(KIND, FirstEventSourceView.known(elected), absent),
                "ELECTED with an absent receipt inserts it; that is not a hold");
        assertEquals(Optional.empty(), FirstEventElection.hold(KIND, FirstEventSourceView.known(elected), occurred));
        // Missing writer confirmation or an unavailable store is never a corruption verdict.
        assertEquals(Optional.empty(), FirstEventElection.hold(KIND, FirstEventSourceView.unconfirmed(), occurred));
        assertEquals(Optional.empty(), FirstEventElection.hold(KIND, known,
                ClassicProgressionSavedData.created(ManagedRootPresence.UNREADABLE)));
    }

    private static FirstEventDisposition disposition(FirstEventSourceView view, FirstEventOutbox ram,
                                                     ClassicProgressionSavedData progression) {
        return FirstEventElection.disposition(KIND, view, ram, progression);
    }
}
