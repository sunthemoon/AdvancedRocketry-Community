package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class SealedProofTurnsTest {
    @Test void aFuturePollOrRemovedEntryCannotKeepTheOtherClassWaiting() throws Exception {
        var due = new AtomicBoolean(true); var turns = new SealedProofTurns(); turns.bind(() -> true);
        var id = UUID.randomUUID();
        turns.ordinaryRoom(id, due::get);
        turns.sealedEnded(); assertFalse(turns.sealedRoom());
        due.set(false); assertTrue(turns.sealedRoom(), "A future poll or removed entry retained the ordinary turn");
        due.set(true); assertFalse(turns.sealedRoom(), "A still-pending return must rejoin when due");
    }
    @Test void waitingClassesAlternateAndOrdinaryCandidatesKeepTheirOrder() {
        var turns = new SealedProofTurns(); turns.bind(() -> true);
        var first = UUID.randomUUID(); var second = UUID.randomUUID();
        assertFalse(turns.ordinaryRoom(first)); assertFalse(turns.ordinaryRoom(second));
        assertTrue(turns.sealedRoom()); turns.sealedEnded();
        assertFalse(turns.sealedRoom()); assertFalse(turns.ordinaryRoom(second)); assertTrue(turns.ordinaryRoom(first));
        turns.ordinaryStarted(first);
        assertTrue(turns.sealedRoom()); assertFalse(turns.ordinaryRoom(first)); assertFalse(turns.ordinaryRoom(second));
        turns.sealedEnded();
        assertFalse(turns.ordinaryRoom(first)); assertTrue(turns.ordinaryRoom(second));
        turns.ordinaryStarted(second); assertTrue(turns.sealedRoom());
    }
    @Test void withdrawnOrdinaryDemandDoesNotLeaveAPhantomTurn() {
        var turns = new SealedProofTurns(); turns.bind(() -> true); var id = UUID.randomUUID();
        turns.ordinaryRoom(id); turns.sealedEnded(); assertFalse(turns.sealedRoom());
        turns.withdraw(id); assertTrue(turns.sealedRoom());
    }
    @Test void ordinaryConcurrencyIsUnchangedWhenNoSealedCandidateWaits() {
        var waiting = new AtomicBoolean(); var turns = new SealedProofTurns(); turns.bind(waiting::get);
        for (int i = 0; i < 4; i++) { var id = UUID.randomUUID(); assertTrue(turns.ordinaryRoom(id)); turns.ordinaryStarted(id); }
        waiting.set(true); assertFalse(turns.ordinaryRoom(UUID.randomUUID()));
        turns.clear(); assertTrue(turns.sealedRoom());
    }
}
