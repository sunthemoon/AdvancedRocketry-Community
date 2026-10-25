package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.*;
import org.junit.jupiter.api.Test;

class RocketRejoinPlacementTest {
    @Test void unacknowledgedSeatSuccessorDoesNotHideAlreadyCheckedP1() {
        var root = RocketRejoinCodecTest.fixture(); var entry = root.entries().get(0);
        var attempt = new Attempt(java.util.UUID.randomUUID(), Target.PAD, 2.5, 70, 3.5);
        var p1 = new Entry(entry.release(), entry.player(), 0, Placement.PENDING, Seat.PENDING, attempt);
        var installed = root.update(p1);
        var successor = new Entry(entry.release(), entry.player(), 0, Placement.PENDING, Seat.VERIFIED, attempt);
        assertTrue(RocketRejoinPlacement.attemptAcknowledged(installed, successor));
        assertFalse(RocketRejoinPlacement.attemptAcknowledged(root, successor));
        assertFalse(RocketRejoinPlacement.attemptAcknowledged(installed, new Entry(entry.release(), entry.player(), 0,
                Placement.PENDING, Seat.VERIFIED, new Attempt(java.util.UUID.randomUUID(), Target.PAD, 2.5, 70, 3.5))));
    }
}
