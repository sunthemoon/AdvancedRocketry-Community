package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.*;
import org.junit.jupiter.api.Test;

class RocketRejoinPlacementTest {
    @Test void ownerProofRequiresTheWholeKeyAndAttemptNotOnlyGeneration() {
        var root = RocketRejoinCodecTest.fixture(); var old = root.entries().get(0); var batch = root.batches().get(0);
        var generation = java.util.UUID.randomUUID(); var attempt = new Attempt(generation, Target.PAD, 2.5, 70, 3.5);
        var entry = new Entry(old.release(), old.player(), 0, Placement.PENDING, Seat.PENDING, attempt);
        var proof = new RocketRejoinPlayerFile.Expected(entry.player(), entry.release(), generation, batch.dimension(), 2.5, 70, 3.5);
        assertTrue(RocketRejoinPlacement.proofMatches(proof, entry, batch));
        assertFalse(RocketRejoinPlacement.proofMatches(proof, old, batch));
        assertFalse(RocketRejoinPlacement.proofMatches(null, entry, batch));
        assertFalse(RocketRejoinPlacement.proofMatches(proof, new Entry(java.util.UUID.randomUUID(), entry.player(), 0,
                Placement.PENDING, Seat.PENDING, attempt), batch));
        assertFalse(RocketRejoinPlacement.proofMatches(proof, new Entry(entry.release(), java.util.UUID.randomUUID(), 0,
                Placement.PENDING, Seat.PENDING, attempt), batch));
        for (var changed : java.util.List.of(new Attempt(java.util.UUID.randomUUID(), Target.PAD, 2.5, 70, 3.5),
                new Attempt(generation, Target.PAD, 3.5, 70, 3.5), new Attempt(generation, Target.PAD, 2.5, 71, 3.5),
                new Attempt(generation, Target.PAD, 2.5, 70, 4.5))) {
            assertFalse(RocketRejoinPlacement.proofMatches(proof, new Entry(entry.release(), entry.player(), 0,
                    Placement.PENDING, Seat.PENDING, changed), batch));
        }
        assertFalse(RocketRejoinPlacement.proofMatches(new RocketRejoinPlayerFile.Expected(entry.player(), entry.release(),
                generation, "minecraft:the_nether", 2.5, 70, 3.5), entry, batch));
    }
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
