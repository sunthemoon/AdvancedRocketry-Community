package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlayerLifeSupportEngineTest {
    @Test
    void baseAtmosphereAndBreathableVolumeConsumeNoOxygen() {
        PlayerLifeSupportDecision base = PlayerLifeSupportEngine.tick(input(
                true, BreathabilityState.VACUUM, 0, 500, 19
        ));
        PlayerLifeSupportDecision room = PlayerLifeSupportEngine.tick(input(
                false, BreathabilityState.BREATHABLE, 0, 500, 19
        ));

        assertEquals(PlayerProtectionStatus.BREATHABLE_ENVIRONMENT, base.status());
        assertEquals(PlayerProtectionStatus.BREATHABLE_VOLUME, room.status());
        assertEquals(500, base.oxygenUnits());
        assertEquals(500, room.oxygenUnits());
        assertEquals(0, base.vacuumPhase());
        assertEquals(0, room.vacuumPhase());
    }

    @Test
    void completeSuitConsumesExactlyOneOxygenPerTwentyTicks() {
        PlayerLifeSupportDecision decision = null;
        int oxygen = 2;
        int phase = 0;
        int consumed = 0;
        for (int tick = 0; tick < 40; tick++) {
            decision = PlayerLifeSupportEngine.tick(input(
                    false, BreathabilityState.VACUUM, 4, oxygen, phase
            ));
            oxygen = decision.oxygenUnits();
            phase = decision.vacuumPhase();
            consumed += decision.oxygenConsumed();
            assertEquals(0.0F, decision.damage());
        }

        assertEquals(2, consumed);
        assertEquals(0, oxygen);
        assertEquals(PlayerProtectionStatus.SUIT_OXYGEN, decision.status());
    }

    @Test
    void partialEmptyAndMissingSuitFailClosedOnDeterministicCadence() {
        PlayerLifeSupportDecision partial = PlayerLifeSupportEngine.tick(input(
                false, BreathabilityState.VACUUM, 3, 10, 19
        ));
        PlayerLifeSupportDecision empty = PlayerLifeSupportEngine.tick(input(
                false, BreathabilityState.VACUUM, 4, 0, 19
        ));
        PlayerLifeSupportDecision exposed = PlayerLifeSupportEngine.tick(input(
                false, BreathabilityState.VACUUM, 0, 0, 19
        ));

        assertEquals(PlayerProtectionStatus.PARTIAL_SUIT, partial.status());
        assertEquals(PlayerProtectionStatus.OXYGEN_EMPTY, empty.status());
        assertEquals(PlayerProtectionStatus.EXPOSED, exposed.status());
        assertEquals(PlayerLifeSupportEngine.VACUUM_DAMAGE, partial.damage());
        assertEquals(PlayerLifeSupportEngine.VACUUM_DAMAGE, empty.damage());
        assertEquals(PlayerLifeSupportEngine.VACUUM_DAMAGE, exposed.damage());
    }

    @Test
    void pendingVolumeIsNotTreatedAsBreathable() {
        PlayerLifeSupportDecision pending = PlayerLifeSupportEngine.tick(input(
                false, BreathabilityState.PENDING, 0, 0, 19
        ));

        assertEquals(PlayerProtectionStatus.VOLUME_PENDING, pending.status());
        assertEquals(PlayerLifeSupportEngine.VACUUM_DAMAGE, pending.damage());
        assertTrue(!pending.status().protectedFromVacuum());
    }

    @Test
    void invalidFiniteStateIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> input(
                false, BreathabilityState.VACUUM, 5, 0, 0
        ));
        assertThrows(IllegalArgumentException.class, () -> input(
                false, BreathabilityState.VACUUM, 0, 0, 20
        ));
    }

    private static PlayerLifeSupportInput input(
            boolean baseBreathable,
            BreathabilityState volume,
            int suitPieces,
            int oxygen,
            int phase
    ) {
        return new PlayerLifeSupportInput(baseBreathable, volume, suitPieces, oxygen, phase);
    }
}
