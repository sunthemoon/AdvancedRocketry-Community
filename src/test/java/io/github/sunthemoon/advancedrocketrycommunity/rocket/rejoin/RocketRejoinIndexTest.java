package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinCodecTest.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class RocketRejoinIndexTest {
    @Test void pendingAcknowledgedAndUnpublishedPlayersAreIndexed() {
        var index = new RocketRejoinIndex(List.of(fixture()), List.of(entry(2, id(101))));
        assertTrue(index.contains(id(100))); assertTrue(index.contains(id(101)));
        assertFalse(index.contains(id(102))); assertEquals(2, index.size());
    }
    @Test void sameKeyAcrossGenerationsIsNotAConflictAndOldPendingIsNotLost() {
        var root = fixture(); var old = root.entries().get(0);
        var verified = root.update(new RocketRejoinRoot.Entry(old.release(), old.player(), 0,
                RocketRejoinRoot.Placement.VERIFIED, old.seat(), null));
        var index = new RocketRejoinIndex(List.of(root, root, verified), List.of());
        assertTrue(index.contains(old.player())); assertEquals(1, index.size());
        assertFalse(new RocketRejoinIndex(List.of(verified), List.of()).contains(old.player()));
    }
    @Test void notRequiredPlayersNeverGetAGuard() {
        var root = new RocketRejoinRoot(1, fixture().batches(), List.of(new RocketRejoinRoot.Entry(id(1), id(100), 0,
                RocketRejoinRoot.Placement.NOT_REQUIRED, RocketRejoinRoot.Seat.PENDING, null)));
        assertEquals(0, new RocketRejoinIndex(List.of(root), List.of()).size());
    }
    @Test void overflowNeverSilentlyForgetsANamedPlayer() {
        var entries = java.util.stream.IntStream.range(0, 257).mapToObj(i -> entry(i + 1, id(i + 100))).toList();
        var index = new RocketRejoinIndex(List.of(), entries);
        assertEquals(256, index.size()); assertTrue(index.overflow()); assertTrue(index.contains(id(356)));
        assertFalse(index.contains(id(357)));
    }
}
