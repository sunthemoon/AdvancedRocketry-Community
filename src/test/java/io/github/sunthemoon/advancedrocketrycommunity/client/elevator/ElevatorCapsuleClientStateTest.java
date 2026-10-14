package io.github.sunthemoon.advancedrocketrycommunity.client.elevator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.client.elevator.ElevatorCapsuleClientState.Candidate;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorCapsuleCodec.Entry;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ElevatorCapsuleClientStateTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    private static Candidate candidate(long endpoint, double distance, int rider) {
        return new Candidate(endpoint, distance, List.of(new Entry(rider, 100)));
    }

    @Test
    void sixtyFourNearestEndpointsWinRegardlessOfOfferOrder() {
        for (boolean reverse : List.of(false, true)) {
            ElevatorCapsuleClientState state = new ElevatorCapsuleClientState();
            for (int i = 0; i < 1000; i++) {
                int id = reverse ? 999 - i : i;
                state.offer(candidate(id, id * id, id));
                assertTrue(state.endpoints().size() <= 64);
            }
            assertEquals(64, state.endpoints().size());
            for (int i = 0; i < 64; i++) {
                assertEquals(i, state.endpoints().get(i).endpoint());
            }
        }
    }

    @Test
    void equalDistanceUsesStableEndpointTieBreaker() {
        ElevatorCapsuleClientState state = new ElevatorCapsuleClientState();
        for (int i = 100; i >= 0; i--) {
            state.offer(candidate(i, 1, i));
        }
        assertEquals(0, state.endpoints().get(0).endpoint());
        assertEquals(63, state.endpoints().get(63).endpoint());
    }

    @Test
    void oneEffectPerRiderUsesTheNearestEndpoint() {
        ElevatorCapsuleClientState state = new ElevatorCapsuleClientState();
        state.offer(candidate(3, 100, 42));
        state.offer(candidate(2, 10, 42));
        state.offer(candidate(1, 5, 43));
        assertEquals(2, state.effects().size());
        assertEquals(1, state.effects().get(0).endpoint());
        assertEquals(2, state.effects().get(1).endpoint());
        assertEquals(42, state.effects().get(1).ride().entityId());
    }

    @Test
    void replacementOmissionCancelAndCommitDoNotLeaveEffects() {
        ElevatorCapsuleClientState state = new ElevatorCapsuleClientState();
        state.offer(candidate(1, 5, 42));
        state.offer(candidate(1, 5, 43));
        assertEquals(43, state.effects().get(0).ride().entityId());
        state.offer(new Candidate(1, 5, List.of()));
        assertTrue(state.effects().isEmpty());
        state.offer(new Candidate(1, 5, List.of(new Entry(42, 0))));
        assertTrue(state.effects().isEmpty());
    }

    @Test
    void lifecycleClearDiscardsEveryCandidateAndAllowsFreshObservation() {
        ElevatorCapsuleClientState state = new ElevatorCapsuleClientState();
        for (String lifecycle : List.of("frame end", "disconnect", "Level change", "Level unload")) {
            state.offer(candidate(1, 1, 42));
            state.clear();
            assertTrue(state.effects().isEmpty(), lifecycle);
            assertTrue(state.endpoints().isEmpty(), lifecycle);
        }
        state.offer(candidate(1, 1, 43));
        assertEquals(43, state.effects().get(0).ride().entityId());
    }

    @Test
    void invalidDistancesAndOversizedCandidatesAreNotAdmitted() {
        for (double distance : List.of(Double.NaN, Double.POSITIVE_INFINITY, -1.0D)) {
            assertThrows(IllegalArgumentException.class, () -> candidate(1, distance, 42));
        }
        assertThrows(IllegalArgumentException.class, () -> new Candidate(1, 1,
                List.of(new Entry(1, 1), new Entry(2, 1), new Entry(3, 1), new Entry(4, 1), new Entry(5, 1))));
    }
}
