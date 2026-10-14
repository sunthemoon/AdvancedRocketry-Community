package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnderwaterLifeSupportTest {
    @Test
    void breathableWaterRestoresAirOnlyOnEachTwentyTickPayment() {
        for (boolean base : new boolean[]{false, true}) {
            int oxygen = 3;
            int phase = 0;
            for (int tick = 1; tick <= 60; tick++) {
                var decision = PlayerLifeSupportEngine.tick(input(base, BreathabilityState.BREATHABLE, 4, oxygen, phase));
                assertEquals(tick % 20 == 0, decision.restoreUnderwaterAir(), "tick " + tick);
                assertEquals(tick % 20 == 0 ? 1 : 0, decision.oxygenConsumed());
                assertEquals(3 - tick / 20, decision.oxygenUnits());
                assertEquals(0.0F, decision.damage());
                oxygen = decision.oxygenUnits();
                phase = decision.vacuumPhase();
            }
            // The last paid tick restores air, but an empty tank cannot restore it again.
            assertFalse(PlayerLifeSupportEngine.tick(input(base, BreathabilityState.BREATHABLE, 4, 0, phase))
                    .restoreUnderwaterAir());
        }
    }

    @Test
    void vacuumAndWaterShareOneCadenceIncludingEntryDuringAnInterval() {
        int oxygen = 10;
        int phase = 0;
        for (int tick = 1; tick <= 60; tick++) {
            boolean underwater = tick > 7;
            var decision = PlayerLifeSupportEngine.tick(new PlayerLifeSupportInput(false, BreathabilityState.VACUUM,
                    4, oxygen, phase, underwater));
            assertEquals(tick % 20 == 0 ? 1 : 0, decision.oxygenConsumed());
            assertEquals(10 - tick / 20, decision.oxygenUnits());
            assertEquals(underwater && tick % 20 == 0, decision.restoreUnderwaterAir());
            assertEquals(0.0F, decision.damage());
            oxygen = decision.oxygenUnits();
            phase = decision.vacuumPhase();
        }
    }

    @Test
    void emptyPartialMissingAndUnsupportedSuitsGrantNothingInWater() {
        for (boolean base : new boolean[]{false, true}) {
            for (int pieces = 0; pieces <= 4; pieces++) {
                for (int oxygen : new int[]{0, 5}) {
                    if (pieces == 4 && oxygen > 0) { continue; }
                    // Unsupported equipment is counted as zero recognized pieces by the adapter.
                    var decision = PlayerLifeSupportEngine.tick(input(base, BreathabilityState.VACUUM, pieces, oxygen, 19));
                    assertFalse(decision.restoreUnderwaterAir());
                    assertEquals(0, decision.oxygenConsumed());
                    assertEquals(oxygen, decision.oxygenUnits());
                    assertEquals(base ? 0.0F : PlayerLifeSupportEngine.VACUUM_DAMAGE, decision.damage());
                    assertEquals(0, decision.vacuumPhase());
                }
            }
        }
    }

    @Test
    void failedDebitReevaluationRevokesAirWithoutAddingAnAtmosphereAttempt() {
        for (boolean base : new boolean[]{false, true}) {
            var input = input(base, BreathabilityState.VACUUM, 4, 10, 19);
            assertTrue(PlayerLifeSupportEngine.tick(input).restoreUnderwaterAir());
            for (int survivingPieces : new int[]{0, 4}) {
                var failed = PlayerLifeSupportEngine.unpaid(input, survivingPieces);
                assertFalse(failed.restoreUnderwaterAir());
                assertEquals(0, failed.oxygenConsumed());
                assertEquals(0, failed.vacuumPhase());
                assertEquals(base ? 0.0F : PlayerLifeSupportEngine.VACUUM_DAMAGE, failed.damage());
            }
        }
    }

    @Test
    void reserveReplenishmentPaysOnceForWaterAndCombinedVacuum() {
        for (boolean base : new boolean[]{false, true}) {
            var input = input(base, BreathabilityState.VACUUM, 4, 0, 19);
            var step = SuitReserveTransition.tick(input, 5, 5, 2_000, true);
            assertEquals(5, step.transferredUnits());
            assertEquals(0, step.reserveUnits());
            assertEquals(4, step.decision().oxygenUnits());
            assertEquals(1, step.decision().oxygenConsumed());
            assertTrue(step.decision().restoreUnderwaterAir());
            assertFalse(SuitReserveTransition.tick(input, 5, 5, 2_000, false).decision().restoreUnderwaterAir());
            assertEquals(0, SuitReserveTransition.tick(input(base, BreathabilityState.VACUUM, 4, 0, 18),
                    5, 5, 2_000, true).transferredUnits());
        }
    }

    @Test
    void noUnderwaterDemandRetainsVanillaAirAndDryCadence() {
        // The adapter clears demand for disabled equipment or native water breathing.
        var dry = PlayerLifeSupportEngine.tick(new PlayerLifeSupportInput(true, BreathabilityState.VACUUM, 4, 10, 19));
        assertEquals(10, dry.oxygenUnits());
        assertEquals(0, dry.vacuumPhase());
        assertFalse(dry.restoreUnderwaterAir());
        var vacuum = PlayerLifeSupportEngine.tick(new PlayerLifeSupportInput(false, BreathabilityState.VACUUM, 4, 10, 19));
        assertEquals(1, vacuum.oxygenConsumed());
        assertFalse(vacuum.restoreUnderwaterAir());
    }

    @Test
    void airGrantRequiresOneSuitDebit() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerLifeSupportDecision(
                PlayerProtectionStatus.SUIT_OXYGEN, 10, 0, 0, 0.0F, true));
        assertThrows(IllegalArgumentException.class, () -> new PlayerLifeSupportDecision(
                PlayerProtectionStatus.BREATHABLE_ENVIRONMENT, 10, 1, 0, 0.0F, true));
    }

    private static PlayerLifeSupportInput input(boolean base, BreathabilityState volume, int pieces, int oxygen, int phase) {
        return new PlayerLifeSupportInput(base, volume, pieces, oxygen, phase, true);
    }
}
