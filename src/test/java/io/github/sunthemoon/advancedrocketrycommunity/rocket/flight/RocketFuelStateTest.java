package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RocketFuelStateTest {
    @Test
    void fillIsBoundedAndReportsTheExactAcceptedAmount() {
        RocketFuelState empty = RocketFuelState.empty(1_000L);

        RocketFuelMutation first = empty.fill(600L);
        RocketFuelMutation second = first.state().fill(600L);
        RocketFuelMutation full = second.state().fill(1L);

        assertEquals(RocketFuelCode.SUCCESS, first.code());
        assertEquals(600L, first.unitsChanged());
        assertEquals(600L, first.state().amount());
        assertEquals(RocketFuelCode.SUCCESS, second.code());
        assertEquals(400L, second.unitsChanged());
        assertEquals(1_000L, second.state().amount());
        assertEquals(RocketFuelCode.TANK_FULL, full.code());
        assertSame(second.state(), full.state());
    }

    @Test
    void invalidFillAndZeroCapacityNeverMutate() {
        RocketFuelState empty = RocketFuelState.empty(0L);

        assertEquals(RocketFuelCode.INVALID_AMOUNT, empty.fill(0L).code());
        assertEquals(RocketFuelCode.INVALID_AMOUNT, empty.fill(-1L).code());
        assertEquals(RocketFuelCode.NO_CAPACITY, empty.fill(1L).code());
        assertEquals(0L, empty.amount());
    }

    @Test
    void debitCommitsOnceAndAReplayCannotConsumeAgain() {
        UUID transaction = UUID.fromString("00000000-0000-0000-0000-000000000601");
        RocketFuelState full = RocketFuelState.empty(1_000L).fill(1_000L).state();

        RocketFuelMutation debit = full.debit(transaction, 367L);
        RocketFuelMutation replay = debit.state().debit(transaction, 999L);

        assertTrue(debit.success());
        assertEquals(367L, debit.unitsChanged());
        assertEquals(633L, debit.state().amount());
        assertTrue(debit.state().wasDebited(transaction));
        assertEquals(RocketFuelCode.REQUEST_REPLAYED, replay.code());
        assertSame(debit.state(), replay.state());
        assertEquals(633L, replay.state().amount());
    }

    @Test
    void failedDebitLeavesFuelAndHistoryUntouched() {
        RocketFuelState state = RocketFuelState.empty(500L).fill(200L).state();
        UUID transaction = UUID.fromString("00000000-0000-0000-0000-000000000602");

        RocketFuelMutation invalid = state.debit(transaction, 0L);
        RocketFuelMutation insufficient = state.debit(transaction, 201L);

        assertEquals(RocketFuelCode.INVALID_AMOUNT, invalid.code());
        assertEquals(RocketFuelCode.INSUFFICIENT_FUEL, insufficient.code());
        assertSame(state, invalid.state());
        assertSame(state, insufficient.state());
        assertFalse(state.wasDebited(transaction));
    }

    @Test
    void debitHistoryFailsClosedAtItsFixedBound() {
        RocketFuelState state = RocketFuelState.empty(10_000L).fill(10_000L).state();
        for (int index = 0; index < RocketFlightLimits.MAX_COMMITTED_FUEL_DEBITS; index++) {
            RocketFuelMutation result = state.debit(id(index), 1L);
            assertTrue(result.success());
            state = result.state();
        }

        RocketFuelMutation full = state.debit(id(10_000), 1L);

        assertEquals(RocketFuelCode.DEBIT_LEDGER_FULL, full.code());
        assertSame(state, full.state());
        assertEquals(
                RocketFlightLimits.MAX_COMMITTED_FUEL_DEBITS,
                state.committedDebits().size()
        );
        assertEquals(10_000L - RocketFlightLimits.MAX_COMMITTED_FUEL_DEBITS, state.amount());
    }

    @Test
    void restoreDefensivelyValidatesAllPersistentBounds() {
        UUID duplicate = id(1);
        assertThrows(IllegalArgumentException.class, () -> RocketFuelState.empty(-1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> RocketFuelState.empty(RocketFlightLimits.MAX_FUEL_CAPACITY + 1L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RocketFuelState.restore(10L, 11L, List.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RocketFuelState.restore(10L, 0L, List.of(duplicate, duplicate))
        );
        ArrayList<UUID> tooMany = new ArrayList<>();
        for (int index = 0; index <= RocketFlightLimits.MAX_COMMITTED_FUEL_DEBITS; index++) {
            tooMany.add(id(index));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> RocketFuelState.restore(10L, 0L, tooMany)
        );
    }

    private static UUID id(int index) {
        return UUID.nameUUIDFromBytes(("fuel-" + index).getBytes(StandardCharsets.UTF_8));
    }
}
