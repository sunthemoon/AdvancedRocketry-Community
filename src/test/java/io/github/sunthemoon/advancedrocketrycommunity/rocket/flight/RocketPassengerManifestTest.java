package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RocketPassengerManifestTest {
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000621");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000622");
    private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000623");

    @Test
    void assignmentUsesStableFirstFreeSeatsAndIsIdempotent() {
        RocketPassengerManifest empty = RocketPassengerManifest.empty(2);
        RocketPassengerManifest first = empty.assign(FIRST).orElseThrow();
        RocketPassengerManifest repeated = first.assign(FIRST).orElseThrow();
        RocketPassengerManifest second = repeated.assign(SECOND).orElseThrow();

        assertEquals(0, first.assignment(FIRST).orElseThrow().seatIndex());
        assertSame(first, repeated);
        assertEquals(1, second.assignment(SECOND).orElseThrow().seatIndex());
        assertTrue(second.full());
        assertTrue(second.assign(THIRD).isEmpty());
    }

    @Test
    void removalPreservesOtherSeatIdentity() {
        RocketPassengerManifest manifest = RocketPassengerManifest.empty(2)
                .assign(FIRST).orElseThrow()
                .assign(SECOND).orElseThrow();

        RocketPassengerManifest removed = manifest.remove(FIRST);

        assertFalse(removed.full());
        assertTrue(removed.assignment(FIRST).isEmpty());
        assertEquals(1, removed.assignment(SECOND).orElseThrow().seatIndex());
        assertSame(removed, removed.remove(THIRD));
    }

    @Test
    void declaredCapacityIsCappedAndPersistentDataIsStrict() {
        assertEquals(
                RocketFlightLimits.MAX_PASSENGERS,
                RocketPassengerManifest.empty(2_048).seatCapacity()
        );
        assertThrows(IllegalArgumentException.class, () -> RocketPassengerManifest.empty(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> RocketPassengerManifest.restore(
                        1,
                        List.of(new RocketPassengerSeat(FIRST, 1))
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RocketPassengerManifest.restore(
                        2,
                        List.of(
                                new RocketPassengerSeat(FIRST, 0),
                                new RocketPassengerSeat(FIRST, 1)
                        )
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RocketPassengerManifest.restore(
                        2,
                        List.of(
                                new RocketPassengerSeat(FIRST, 0),
                                new RocketPassengerSeat(SECOND, 0)
                        )
                )
        );
    }

    @Test
    void restoreSortsAssignmentsBySeatForDeterministicEncoding() {
        RocketPassengerManifest restored = RocketPassengerManifest.restore(
                2,
                List.of(
                        new RocketPassengerSeat(SECOND, 1),
                        new RocketPassengerSeat(FIRST, 0)
                )
        );

        assertEquals(List.of(FIRST, SECOND), restored.assignments().stream()
                .map(RocketPassengerSeat::passengerId)
                .toList());
    }
}
