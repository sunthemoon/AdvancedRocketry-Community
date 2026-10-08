package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SuitReserveTransition.Result;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class SuitReserveTransitionTest {
    private static final int LAST_PHASE = 19;
    private static final float DAMAGE = PlayerLifeSupportEngine.VACUUM_DAMAGE;
    private static final boolean[] BOOLEANS = {false, true};

    @Test
    void transferOccursOnlyOnTheScheduledDebitPhase() {
        for (int phase = 0; phase < 20; phase++) {
            Result empty = run(vacuumSuit(0, phase), 50, 50, 50, true);
            Result partial = run(vacuumSuit(100, phase), 500, 500, 150, true);
            if (phase == LAST_PHASE) {
                assertDecision(empty, PlayerProtectionStatus.SUIT_OXYGEN, 49, 1, 0, 0.0F);
                assertReserve(empty, 0, 50);
                assertDecision(partial, PlayerProtectionStatus.SUIT_OXYGEN, 149, 1, 0, 0.0F);
                assertReserve(partial, 450, 50);
            } else {
                assertDecision(empty, PlayerProtectionStatus.OXYGEN_EMPTY, 0, 0, phase + 1, 0.0F);
                assertReserve(empty, 50, 0);
                assertDecision(partial, PlayerProtectionStatus.SUIT_OXYGEN, 100, 0, phase + 1, 0.0F);
                assertReserve(partial, 500, 0);
            }
        }
    }

    @Test
    void vacuumAndPendingVolumesAdmitTransferButBreathableVolumeDoesNot() {
        Result vacuum = run(input(false, BreathabilityState.VACUUM, 4, 0, LAST_PHASE), 10, 10, 10, true);
        Result pending = run(input(false, BreathabilityState.PENDING, 4, 0, LAST_PHASE), 10, 10, 10, true);
        Result room = run(input(false, BreathabilityState.BREATHABLE, 4, 0, LAST_PHASE), 10, 10, 10, true);
        Result pendingWithoutReserve = run(
                input(false, BreathabilityState.PENDING, 4, 0, LAST_PHASE), 0, 10, 10, true
        );

        assertDecision(vacuum, PlayerProtectionStatus.SUIT_OXYGEN, 9, 1, 0, 0.0F);
        assertReserve(vacuum, 0, 10);
        assertDecision(pending, PlayerProtectionStatus.SUIT_OXYGEN, 9, 1, 0, 0.0F);
        assertReserve(pending, 0, 10);
        assertDecision(room, PlayerProtectionStatus.BREATHABLE_VOLUME, 0, 0, 0, 0.0F);
        assertReserve(room, 10, 0);
        assertDecision(pendingWithoutReserve, PlayerProtectionStatus.VOLUME_PENDING, 0, 0, 0, DAMAGE);
        assertReserve(pendingWithoutReserve, 0, 0);
    }

    @Test
    void breathableBaseAtmosphereNeverDrawsReserve() {
        for (BreathabilityState volume : BreathabilityState.values()) {
            Result result = run(input(true, volume, 4, 0, LAST_PHASE), 10, 10, 10, true);

            assertDecision(result, PlayerProtectionStatus.BREATHABLE_ENVIRONMENT, 0, 0, 0, 0.0F);
            assertReserve(result, 10, 0);
        }
    }

    @Test
    void partialAndMissingSuitsNeverDrawReserve() {
        for (int pieces = 0; pieces < 4; pieces++) {
            PlayerProtectionStatus status = pieces == 0
                    ? PlayerProtectionStatus.EXPOSED
                    : PlayerProtectionStatus.PARTIAL_SUIT;
            Result empty = run(input(false, BreathabilityState.VACUUM, pieces, 0, LAST_PHASE), 10, 10, 10, true);
            Result charged = run(input(false, BreathabilityState.VACUUM, pieces, 5, LAST_PHASE), 10, 10, 10, true);

            assertDecision(empty, status, 0, 0, 0, DAMAGE);
            assertReserve(empty, 10, 0);
            assertDecision(charged, status, 5, 0, 0, DAMAGE);
            assertReserve(charged, 10, 0);
        }
        Result complete = run(vacuumSuit(0, LAST_PHASE), 10, 10, 10, true);

        assertDecision(complete, PlayerProtectionStatus.SUIT_OXYGEN, 9, 1, 0, 0.0F);
        assertReserve(complete, 0, 10);
    }

    @Test
    void ineligibleReserveKeepsTheOrdinaryActiveDebit() {
        Result debit = run(vacuumSuit(500, LAST_PHASE), 1_000, 1_000, 2_000, false);
        Result empty = run(vacuumSuit(0, LAST_PHASE), 1_000, 1_000, 2_000, false);
        Result between = run(vacuumSuit(500, 7), 1_000, 1_000, 2_000, false);

        assertDecision(debit, PlayerProtectionStatus.SUIT_OXYGEN, 499, 1, 0, 0.0F);
        assertReserve(debit, 1_000, 0);
        assertDecision(empty, PlayerProtectionStatus.OXYGEN_EMPTY, 0, 0, 0, DAMAGE);
        assertReserve(empty, 1_000, 0);
        assertDecision(between, PlayerProtectionStatus.SUIT_OXYGEN, 500, 0, 8, 0.0F);
        assertReserve(between, 1_000, 0);
    }

    @Test
    void emptyPartialAndFullActiveBuffersAreToppedUpToTheTarget() {
        Result empty = run(vacuumSuit(0, LAST_PHASE), 5_000, 5_000, 2_000, true);
        Result partial = run(vacuumSuit(700, LAST_PHASE), 5_000, 5_000, 2_000, true);
        Result full = run(vacuumSuit(2_000, LAST_PHASE), 5_000, 5_000, 2_000, true);

        assertDecision(empty, PlayerProtectionStatus.SUIT_OXYGEN, 1_999, 1, 0, 0.0F);
        assertReserve(empty, 3_000, 2_000);
        assertDecision(partial, PlayerProtectionStatus.SUIT_OXYGEN, 1_999, 1, 0, 0.0F);
        assertReserve(partial, 3_700, 1_300);
        assertDecision(full, PlayerProtectionStatus.SUIT_OXYGEN, 1_999, 1, 0, 0.0F);
        assertReserve(full, 5_000, 0);
    }

    @Test
    void targetAtOrBelowActiveTransfersNothing() {
        Result below = run(vacuumSuit(700, LAST_PHASE), 1_000, 1_000, 500, true);
        Result equal = run(vacuumSuit(700, LAST_PHASE), 1_000, 1_000, 700, true);
        Result above = run(vacuumSuit(700, LAST_PHASE), 1_000, 1_000, 900, true);

        assertDecision(below, PlayerProtectionStatus.SUIT_OXYGEN, 699, 1, 0, 0.0F);
        assertReserve(below, 1_000, 0);
        assertDecision(equal, PlayerProtectionStatus.SUIT_OXYGEN, 699, 1, 0, 0.0F);
        assertReserve(equal, 1_000, 0);
        assertDecision(above, PlayerProtectionStatus.SUIT_OXYGEN, 899, 1, 0, 0.0F);
        assertReserve(above, 800, 200);
    }

    @Test
    void reserveBalanceBoundsTheTransfer() {
        Result zero = run(vacuumSuit(700, LAST_PHASE), 0, 0, 900, true);
        Result insufficient = run(vacuumSuit(700, LAST_PHASE), 150, 150, 900, true);
        Result exact = run(vacuumSuit(700, LAST_PHASE), 200, 300, 900, true);
        Result excess = run(vacuumSuit(700, LAST_PHASE), 1_000, 1_000, 900, true);

        assertDecision(zero, PlayerProtectionStatus.SUIT_OXYGEN, 699, 1, 0, 0.0F);
        assertReserve(zero, 0, 0);
        assertDecision(insufficient, PlayerProtectionStatus.SUIT_OXYGEN, 849, 1, 0, 0.0F);
        assertReserve(insufficient, 0, 150);
        assertDecision(exact, PlayerProtectionStatus.SUIT_OXYGEN, 899, 1, 0, 0.0F);
        assertReserve(exact, 0, 200);
        assertDecision(excess, PlayerProtectionStatus.SUIT_OXYGEN, 899, 1, 0, 0.0F);
        assertReserve(excess, 800, 200);
    }

    @Test
    void maximumReserveAndCapacityDoNotOverflow() {
        int max = Integer.MAX_VALUE;
        Result fill = run(vacuumSuit(0, LAST_PHASE), max, max, 2_000, true);
        Result idle = run(vacuumSuit(2_000, 3), max, max, 2_000, true);
        Result topUp = run(vacuumSuit(1_999, LAST_PHASE), max - 1, max, 2_000, true);
        Result emptyReserve = run(vacuumSuit(0, LAST_PHASE), 0, max, 2_000, true);

        assertDecision(fill, PlayerProtectionStatus.SUIT_OXYGEN, 1_999, 1, 0, 0.0F);
        assertReserve(fill, max - 2_000, 2_000);
        assertDecision(idle, PlayerProtectionStatus.SUIT_OXYGEN, 2_000, 0, 4, 0.0F);
        assertReserve(idle, max, 0);
        assertDecision(topUp, PlayerProtectionStatus.SUIT_OXYGEN, 1_999, 1, 0, 0.0F);
        assertReserve(topUp, max - 2, 1);
        assertDecision(emptyReserve, PlayerProtectionStatus.OXYGEN_EMPTY, 0, 0, 0, DAMAGE);
        assertReserve(emptyReserve, 0, 0);
    }

    @Test
    void scalarDomainBoundariesAreAccepted() {
        assertEquals(2_000, AtmosphereLimits.SUIT_OXYGEN_CAPACITY);
        Result zeroCapacity = run(vacuumSuit(0, LAST_PHASE), 0, 0, 1, true);
        Result minimumTarget = run(vacuumSuit(0, LAST_PHASE), 1, 1, 1, true);
        Result maximumTarget = run(vacuumSuit(0, LAST_PHASE), 2_000, 2_000, 2_000, true);

        assertDecision(zeroCapacity, PlayerProtectionStatus.OXYGEN_EMPTY, 0, 0, 0, DAMAGE);
        assertReserve(zeroCapacity, 0, 0);
        assertDecision(minimumTarget, PlayerProtectionStatus.SUIT_OXYGEN, 0, 1, 0, 0.0F);
        assertReserve(minimumTarget, 0, 1);
        assertDecision(maximumTarget, PlayerProtectionStatus.SUIT_OXYGEN, 1_999, 1, 0, 0.0F);
        assertReserve(maximumTarget, 0, 2_000);
    }

    @Test
    void illegalScalarsAreRejectedBeforeCalculation() {
        List<PlayerLifeSupportInput> inputs = List.of(
                input(true, BreathabilityState.BREATHABLE, 0, 0, 0),
                vacuumSuit(0, LAST_PHASE)
        );
        int max = Integer.MAX_VALUE;
        int min = Integer.MIN_VALUE;
        for (PlayerLifeSupportInput input : inputs) {
            for (boolean eligible : BOOLEANS) {
                assertIllegal(() -> SuitReserveTransition.tick(input, 0, -1, 1, eligible));
                assertIllegal(() -> SuitReserveTransition.tick(input, 0, min, 1, eligible));
                assertIllegal(() -> SuitReserveTransition.tick(input, -1, 10, 1, eligible));
                assertIllegal(() -> SuitReserveTransition.tick(input, min, 10, 1, eligible));
                assertIllegal(() -> SuitReserveTransition.tick(input, 11, 10, 1, eligible));
                assertIllegal(() -> SuitReserveTransition.tick(input, 1, 0, 1, eligible));
                assertIllegal(() -> SuitReserveTransition.tick(input, max, max - 1, 1, eligible));
                assertIllegal(() -> SuitReserveTransition.tick(input, 0, 10, 0, eligible));
                assertIllegal(() -> SuitReserveTransition.tick(input, 0, 10, -1, eligible));
                assertIllegal(() -> SuitReserveTransition.tick(input, 0, 10, 2_001, eligible));
                assertIllegal(() -> SuitReserveTransition.tick(input, 0, 10, max, eligible));
                assertIllegal(() -> SuitReserveTransition.tick(input, 0, 10, min, eligible));
            }
        }
        for (boolean eligible : BOOLEANS) {
            assertThrows(NullPointerException.class,
                    () -> SuitReserveTransition.tick(null, 0, 0, 1, eligible));
        }
    }

    @Test
    void resultFieldsAreValidated() {
        PlayerLifeSupportDecision decision = new PlayerLifeSupportDecision(
                PlayerProtectionStatus.SUIT_OXYGEN, 10, 0, 1, 0.0F
        );

        assertThrows(NullPointerException.class, () -> new Result(null, 0, 0));
        assertIllegal(() -> new Result(decision, -1, 0));
        assertIllegal(() -> new Result(decision, Integer.MIN_VALUE, 0));
        assertIllegal(() -> new Result(decision, 0, -1));
        assertIllegal(() -> new Result(decision, 0, 2_001));
        assertIllegal(() -> new Result(decision, 0, Integer.MAX_VALUE));

        Result low = new Result(decision, 0, 0);
        Result high = new Result(decision, Integer.MAX_VALUE, 2_000);
        assertSame(decision, low.decision());
        assertReserve(low, 0, 0);
        assertSame(decision, high.decision());
        assertReserve(high, Integer.MAX_VALUE, 2_000);
    }

    @Test
    void zeroTransferPreservesExistingEngineScenarios() {
        for (boolean eligible : BOOLEANS) {
            Result base = run(input(true, BreathabilityState.VACUUM, 0, 500, 19), 0, 0, 2_000, eligible);
            Result room = run(input(false, BreathabilityState.BREATHABLE, 0, 500, 19), 0, 0, 2_000, eligible);
            assertDecision(base, PlayerProtectionStatus.BREATHABLE_ENVIRONMENT, 500, 0, 0, 0.0F);
            assertDecision(room, PlayerProtectionStatus.BREATHABLE_VOLUME, 500, 0, 0, 0.0F);

            Result result = null;
            int oxygen = 2;
            int phase = 0;
            int consumed = 0;
            for (int tick = 0; tick < 40; tick++) {
                result = run(input(false, BreathabilityState.VACUUM, 4, oxygen, phase), 0, 0, 2_000, eligible);
                oxygen = result.decision().oxygenUnits();
                phase = result.decision().vacuumPhase();
                consumed += result.decision().oxygenConsumed();
                assertEquals(0.0F, result.decision().damage());
                assertReserve(result, 0, 0);
            }
            assertEquals(2, consumed);
            assertEquals(0, oxygen);
            assertEquals(PlayerProtectionStatus.SUIT_OXYGEN, result.decision().status());

            Result partial = run(input(false, BreathabilityState.VACUUM, 3, 10, 19), 0, 0, 2_000, eligible);
            Result empty = run(input(false, BreathabilityState.VACUUM, 4, 0, 19), 0, 0, 2_000, eligible);
            Result exposed = run(input(false, BreathabilityState.VACUUM, 0, 0, 19), 0, 0, 2_000, eligible);
            Result pending = run(input(false, BreathabilityState.PENDING, 0, 0, 19), 0, 0, 2_000, eligible);
            assertDecision(partial, PlayerProtectionStatus.PARTIAL_SUIT, 10, 0, 0, DAMAGE);
            assertDecision(empty, PlayerProtectionStatus.OXYGEN_EMPTY, 0, 0, 0, DAMAGE);
            assertDecision(exposed, PlayerProtectionStatus.EXPOSED, 0, 0, 0, DAMAGE);
            assertDecision(pending, PlayerProtectionStatus.VOLUME_PENDING, 0, 0, 0, DAMAGE);
            assertFalse(pending.decision().status().protectedFromVacuum());
        }
    }

    @Test
    void successiveScheduledTransfersKeepTheActiveBufferAtTarget() {
        int oxygen = 0;
        int reserve = 2_500;
        int phase = 0;
        int consumed = 0;
        int transferred = 0;
        int unprotectedTicks = 0;
        float damage = 0.0F;
        for (int tick = 0; tick < 100; tick++) {
            Result result = run(vacuumSuit(oxygen, phase), reserve, 4_000, 2_000, true);
            assertEquals(phase == LAST_PHASE, result.transferredUnits() > 0);
            oxygen = result.decision().oxygenUnits();
            reserve = result.reserveUnits();
            phase = result.decision().vacuumPhase();
            consumed += result.decision().oxygenConsumed();
            transferred += result.transferredUnits();
            damage += result.decision().damage();
            if (!result.decision().status().protectedFromVacuum()) {
                unprotectedTicks++;
            }
        }

        // The first window starts empty; afterwards each debit is refilled by one unit.
        assertEquals(1_999, oxygen);
        assertEquals(496, reserve);
        assertEquals(0, phase);
        assertEquals(5, consumed);
        assertEquals(2_004, transferred);
        assertEquals(19, unprotectedTicks);
        assertEquals(0.0F, damage);
        assertEquals(2_500L, (long) oxygen + reserve + consumed);
    }

    @Test
    void successiveTransfersFallBackToTheEngineWhenReserveIsExhausted() {
        int oxygen = 1;
        int reserve = 2;
        int phase = 0;
        int consumed = 0;
        int transferred = 0;
        int damageTicks = 0;
        int lastProtectedTick = -1;
        Result result = null;
        for (int tick = 0; tick < 80; tick++) {
            result = run(vacuumSuit(oxygen, phase), reserve, 2, 2, true);
            oxygen = result.decision().oxygenUnits();
            reserve = result.reserveUnits();
            phase = result.decision().vacuumPhase();
            consumed += result.decision().oxygenConsumed();
            transferred += result.transferredUnits();
            if (result.decision().damage() > 0.0F) {
                assertEquals(79, tick);
                damageTicks++;
            }
            if (result.decision().status().protectedFromVacuum()) {
                lastProtectedTick = tick;
            }
        }

        assertEquals(0, oxygen);
        assertEquals(0, reserve);
        assertEquals(0, phase);
        assertEquals(3, consumed);
        assertEquals(2, transferred);
        assertEquals(1, damageTicks);
        assertEquals(59, lastProtectedTick);
        assertEquals(PlayerProtectionStatus.OXYGEN_EMPTY, result.decision().status());
        assertEquals(3L, (long) oxygen + reserve + consumed);
    }

    @Test
    void finiteGridMatchesTheEngineOnTheIndependentlyTransferredInput() {
        int cases = 0;
        for (boolean base : BOOLEANS) {
            for (BreathabilityState volume : BreathabilityState.values()) {
                for (int pieces = 0; pieces <= 4; pieces++) {
                    for (int active : new int[] {0, 1, 700, 1_999, 2_000}) {
                        for (int phase = 0; phase < 20; phase++) {
                            cases += assertGrid(input(base, volume, pieces, active, phase));
                        }
                    }
                }
            }
        }

        assertEquals(2 * 3 * 5 * 5 * 20 * 5 * 2 * 3 * 2, cases);
    }

    private static int assertGrid(PlayerLifeSupportInput input) {
        int cases = 0;
        for (int reserve : new int[] {0, 1, 500, 2_000, Integer.MAX_VALUE}) {
            for (int capacity : new int[] {reserve, Integer.MAX_VALUE}) {
                for (int target : new int[] {1, 700, 2_000}) {
                    for (boolean eligible : BOOLEANS) {
                        boolean admitted = eligible
                                && input.equippedSuitPieces() == 4
                                && !input.baseAtmosphereBreathable()
                                && input.volumeState() != BreathabilityState.BREATHABLE
                                && input.vacuumPhase() == LAST_PHASE;
                        int expectedTransfer = admitted
                                ? Math.min(Math.max(0, target - input.oxygenUnits()), reserve)
                                : 0;
                        PlayerLifeSupportDecision expected = PlayerLifeSupportEngine.tick(input(
                                input.baseAtmosphereBreathable(),
                                input.volumeState(),
                                input.equippedSuitPieces(),
                                input.oxygenUnits() + expectedTransfer,
                                input.vacuumPhase()
                        ));

                        Result result = run(input, reserve, capacity, target, eligible);
                        assertEquals(expected, result.decision());
                        assertReserve(result, reserve - expectedTransfer, expectedTransfer);
                        cases++;
                    }
                }
            }
        }
        return cases;
    }

    /** Runs the helper and checks long conservation of active, reserve and consumed oxygen. */
    private static Result run(
            PlayerLifeSupportInput input,
            int reserve,
            int capacity,
            int target,
            boolean eligible
    ) {
        Result result = SuitReserveTransition.tick(input, reserve, capacity, target, eligible);
        long before = (long) input.oxygenUnits() + reserve;
        long after = (long) result.decision().oxygenUnits()
                + result.reserveUnits()
                + result.decision().oxygenConsumed();
        assertEquals(before, after);
        assertTrue(result.transferredUnits() <= reserve);
        return result;
    }

    private static void assertDecision(
            Result result,
            PlayerProtectionStatus status,
            int oxygen,
            int consumed,
            int phase,
            float damage
    ) {
        PlayerLifeSupportDecision decision = result.decision();
        assertEquals(status, decision.status());
        assertEquals(oxygen, decision.oxygenUnits());
        assertEquals(consumed, decision.oxygenConsumed());
        assertEquals(phase, decision.vacuumPhase());
        assertEquals(damage, decision.damage());
    }

    private static void assertReserve(Result result, int reserve, int transferred) {
        assertEquals(reserve, result.reserveUnits());
        assertEquals(transferred, result.transferredUnits());
    }

    private static void assertIllegal(Executable executable) {
        assertThrows(IllegalArgumentException.class, executable);
    }

    private static PlayerLifeSupportInput vacuumSuit(int oxygen, int phase) {
        return input(false, BreathabilityState.VACUUM, 4, oxygen, phase);
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
