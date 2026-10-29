package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R154 A0: one bay claim at a time takes D6 to D7; the others wait in the order they first asked. */
final class BayProofTurnsTest {
    private static UUID id(int n) { return new UUID(154, n); }

    @Test void theFirstClaimToAskTakesAFreeTurnAndTheOthersWaitInTheOrderTheyAsked() {
        var turns = new BayProofTurns();
        assertTrue(turns.take(id(1)));
        assertFalse(turns.take(id(2)));
        assertFalse(turns.take(id(3)));
        assertTrue(turns.take(id(1)), "the holder keeps its turn while it asks again");
        assertFalse(turns.take(id(3)), "a claim that asks again keeps its place");
        assertEquals(Optional.of(id(1)), turns.holder());
        assertEquals(List.of(id(2), id(3)), turns.line());
    }

    @Test void aPassedTurnGoesToTheFirstClaimInLineEvenIfALaterOneAsksFirst() {
        var turns = new BayProofTurns();
        turns.take(id(1)); turns.take(id(2)); turns.take(id(3));
        turns.leave(id(1));
        assertTrue(turns.holder().isEmpty(), "the turn is free until the first in line asks");
        assertFalse(turns.take(id(3)), "the third claim cannot jump the line");
        assertTrue(turns.take(id(2)));
        assertTrue(turns.holds(id(2)));
        assertEquals(List.of(id(3)), turns.line());
    }

    @Test void aClaimThatAsksAgainAfterPassingItsTurnOnQueuesBehindEveryClaimWaiting() {
        var turns = new BayProofTurns();
        turns.take(id(1)); turns.take(id(2)); turns.take(id(3));
        turns.leave(id(1));
        assertFalse(turns.take(id(1)), "the claim that passed its turn on asks again");
        assertEquals(List.of(id(2), id(3), id(1)), turns.line());
        assertTrue(turns.take(id(2)));
        turns.leave(id(2));
        assertTrue(turns.take(id(3)));
        turns.leave(id(3));
        assertTrue(turns.take(id(1)));
    }

    @Test void aWaitingClaimThatStopsAskingLeavesTheLineSoItCannotBlockTheNext() {
        var turns = new BayProofTurns();
        turns.take(id(1)); turns.take(id(2)); turns.take(id(3));
        turns.leave(id(2));
        assertEquals(List.of(id(3)), turns.line());
        turns.leave(id(1));
        assertTrue(turns.take(id(3)), "the claim behind the one that left takes the free turn");
        turns.leave(id(9));
        assertTrue(turns.holds(id(3)), "a claim that never asked changes nothing");
    }

    @Test void clearingTheSessionDropsTheHolderAndTheLine() {
        var turns = new BayProofTurns();
        turns.take(id(1)); turns.take(id(2));
        turns.clear();
        assertTrue(turns.holder().isEmpty());
        assertTrue(turns.line().isEmpty());
        assertTrue(turns.take(id(2)));
        assertThrows(NullPointerException.class, () -> turns.take(null));
    }
}
