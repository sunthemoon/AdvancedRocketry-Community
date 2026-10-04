package io.github.sunthemoon.advancedrocketrycommunity.machine.combustion;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CombustionBurnTest {
    @Test void coalProducesExactlySixtyFourThousandWithoutExtraFuel() {
        var state = new CombustionBurn.State(0, 0, 0);
        int generated = 0;
        int consumed = 0;
        for (int tick = 0; tick < 1_600; tick++) {
            var step = CombustionBurn.tick(state, 1_600);
            consumed += step.consumeFuel() ? 1 : 0;
            generated += step.state().energy();
            state = step.state().withEnergy(0);
        }
        assertEquals(64_000, generated);
        assertEquals(1, consumed);
        assertEquals(0, state.remaining());
    }

    @ParameterizedTest @ValueSource(ints = {19_961, 19_999, 20_000})
    void noCreditOrFuelIsSpentWithoutSpaceForAWholeTick(int energy) {
        for (int remaining : new int[]{0, 1, 400}) {
            var before = new CombustionBurn.State(energy, 400, remaining);
            var paused = CombustionBurn.tick(before, 1_600);
            assertEquals(before, paused.state());
            assertFalse(paused.consumeFuel());
            assertEquals(CombustionBurn.Status.BUFFER_FULL, paused.status());
        }
    }

    @Test void fullBufferResumesItsOriginalFuelCredit() {
        var paused = new CombustionBurn.State(20_000, 1_600, 700);
        var resumed = CombustionBurn.tick(paused.withEnergy(19_960), 20_000);
        assertFalse(resumed.consumeFuel());
        assertEquals(new CombustionBurn.State(20_000, 1_600, 699), resumed.state());
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 1_000_001, Integer.MAX_VALUE})
    void absentOrOverLimitFuelIsRefusedIntact(int ticks) {
        var before = new CombustionBurn.State(40, 0, 0);
        var refused = CombustionBurn.tick(before, ticks);
        assertEquals(before, refused.state());
        assertFalse(refused.consumeFuel());
    }

    @ParameterizedTest @ValueSource(ints = {1, 1_600, 20_000, 1_000_000})
    void supportedCreditStartsAndDebitsExactlyOneTick(int ticks) {
        var result = CombustionBurn.tick(new CombustionBurn.State(0, 0, 0), ticks);
        assertTrue(result.consumeFuel());
        assertEquals(new CombustionBurn.State(40, ticks, ticks - 1), result.state());
    }

    @Test void impossibleStatesCannotBeConstructed() {
        int[][] bad = {{-1, 0, 0}, {20_001, 0, 0}, {0, -1, 0}, {0, 1_000_001, 0}, {0, 0, 1}, {0, 1, -1}};
        for (int[] values : bad) {
            assertThrows(IllegalArgumentException.class, () -> new CombustionBurn.State(values[0], values[1], values[2]));
        }
    }
}
