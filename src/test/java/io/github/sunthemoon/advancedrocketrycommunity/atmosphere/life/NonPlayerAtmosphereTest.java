package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.NonPlayerAtmosphere.Attempt;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.NonPlayerAtmosphere.Kind;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.environment.EnvironmentalExposure;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NonPlayerAtmosphereTest {
    private static final double DEFAULT_DAMAGE = PlayerLifeSupportEngine.VACUUM_DAMAGE;

    @Test
    void cadenceIsExactlyOneScheduledTickPerTwentyEntityTicks() {
        for (int entityId : new int[]{0, 1, 7, 19, 20, 12_345, -3, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            List<Integer> scheduled = new ArrayList<>();
            for (int tick = 0; tick < 200; tick++) {
                if (NonPlayerAtmosphere.scheduled(tick, entityId)) {
                    scheduled.add(tick);
                }
            }
            assertEquals(10, scheduled.size(), "entity " + entityId);
            for (int i = 1; i < scheduled.size(); i++) {
                assertEquals(NonPlayerAtmosphere.INTERVAL, scheduled.get(i) - scheduled.get(i - 1));
            }
        }
        assertEquals(20, NonPlayerAtmosphere.INTERVAL);
        // Neighbouring entity IDs that load together are spread over the interval.
        assertTrue(NonPlayerAtmosphere.scheduled(20, 0));
        assertFalse(NonPlayerAtmosphere.scheduled(20, 1));
        assertTrue(NonPlayerAtmosphere.scheduled(19, 1));
    }

    @Test
    void attemptReusesThePlayerDecisionWithNoSuitAndNoOxygen() {
        for (boolean base : new boolean[]{false, true}) {
            for (BreathabilityState state : BreathabilityState.values()) {
                PlayerLifeSupportDecision player = PlayerLifeSupportEngine.tick(
                        new PlayerLifeSupportInput(base, state, 0, 0, NonPlayerAtmosphere.INTERVAL - 1));
                Attempt attempt = NonPlayerAtmosphere.attempt(base, state, 0, DEFAULT_DAMAGE);
                assertEquals(player.damage(), attempt.damage(), base + " " + state);
                assertEquals(player.status().protectedFromVacuum() ? Kind.NONE : Kind.VACUUM, attempt.kind());
            }
        }
        assertSame(Attempt.NONE, NonPlayerAtmosphere.attempt(true, BreathabilityState.VACUUM, 0, DEFAULT_DAMAGE));
        assertSame(Attempt.NONE, NonPlayerAtmosphere.attempt(false, BreathabilityState.BREATHABLE, 0, DEFAULT_DAMAGE));
        assertEquals(new Attempt(Kind.VACUUM, 0, 2.0F),
                NonPlayerAtmosphere.attempt(false, BreathabilityState.PENDING, 0, DEFAULT_DAMAGE));
    }

    @Test
    void suffocationAndExposureNeverShareOneScheduledTick() {
        int all = EnvironmentalExposure.ALL;
        Attempt vacuum = NonPlayerAtmosphere.attempt(false, BreathabilityState.VACUUM, all, 3.5);
        assertEquals(new Attempt(Kind.VACUUM, 0, 3.5F), vacuum);

        Attempt breathable = NonPlayerAtmosphere.attempt(true, BreathabilityState.BREATHABLE, all, DEFAULT_DAMAGE);
        assertEquals(Kind.ENVIRONMENTAL, breathable.kind());
        assertEquals(EnvironmentalExposure.DAMAGE, breathable.damage());
        assertEquals("pressure", EnvironmentalExposure.primary(breathable.hazards()));
        int coldOnly = EnvironmentalExposure.COLD | EnvironmentalExposure.SOLAR;
        assertEquals("cold", EnvironmentalExposure.primary(
                NonPlayerAtmosphere.attempt(false, BreathabilityState.BREATHABLE, coldOnly, DEFAULT_DAMAGE).hazards()));
    }

    @Test
    void zeroVacuumDamageStopsOnlyVacuumDamageNotExposure() {
        assertSame(Attempt.NONE, NonPlayerAtmosphere.attempt(false, BreathabilityState.VACUUM, 0, 0.0));
        Attempt exposure = NonPlayerAtmosphere.attempt(false, BreathabilityState.VACUUM, EnvironmentalExposure.COLD, 0.0);
        assertEquals(new Attempt(Kind.ENVIRONMENTAL, EnvironmentalExposure.COLD, EnvironmentalExposure.DAMAGE), exposure);
        assertEquals(new Attempt(Kind.VACUUM, 0, 20.0F),
                NonPlayerAtmosphere.attempt(false, BreathabilityState.VACUUM, EnvironmentalExposure.COLD, 20.0));
    }

    @Test
    void invalidDamageOrHazardsAreRejected() {
        for (double damage : new double[]{-0.001, 20.001, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> NonPlayerAtmosphere.attempt(false, BreathabilityState.VACUUM, 0, damage));
        }
        assertThrows(IllegalArgumentException.class,
                () -> NonPlayerAtmosphere.attempt(true, BreathabilityState.BREATHABLE, 16, DEFAULT_DAMAGE));
        assertThrows(IllegalArgumentException.class,
                () -> NonPlayerAtmosphere.admitSpawn(true, Optional.empty(), 16));
        assertThrows(IllegalArgumentException.class, () -> new Attempt(Kind.NONE, 0, 1.0F));
        assertThrows(IllegalArgumentException.class, () -> new Attempt(Kind.VACUUM, EnvironmentalExposure.COLD, 2.0F));
        assertThrows(IllegalArgumentException.class, () -> new Attempt(Kind.ENVIRONMENTAL, 0, 2.0F));
    }

    @Test
    void spawnAdmissionDeniesUnavailablePendingAndVacuumEyeCells() {
        assertFalse(NonPlayerAtmosphere.admitSpawn(false, Optional.empty(), 0));
        assertFalse(NonPlayerAtmosphere.admitSpawn(false, Optional.of(BreathabilityState.PENDING), 0));
        assertFalse(NonPlayerAtmosphere.admitSpawn(false, Optional.of(BreathabilityState.VACUUM), 0));
        assertTrue(NonPlayerAtmosphere.admitSpawn(false, Optional.of(BreathabilityState.BREATHABLE), 0));
        // A breathable Level does not depend on a loaded volume.
        assertTrue(NonPlayerAtmosphere.admitSpawn(true, Optional.empty(), 0));
        assertTrue(NonPlayerAtmosphere.admitSpawn(true, Optional.of(BreathabilityState.VACUUM), 0));
    }

    @Test
    void spawnAdmissionDeniesAnyUnprotectedOptedInHazard() {
        for (int hazard : new int[]{EnvironmentalExposure.COLD, EnvironmentalExposure.HEAT,
                EnvironmentalExposure.PRESSURE, EnvironmentalExposure.SOLAR}) {
            assertFalse(NonPlayerAtmosphere.admitSpawn(true, Optional.of(BreathabilityState.BREATHABLE), hazard));
            assertFalse(NonPlayerAtmosphere.admitSpawn(false, Optional.of(BreathabilityState.BREATHABLE), hazard));
        }
        // A supplied sealed room yields no hazard mask (ADR-034 hazards with a controlled room).
        var conditions = new io.github.sunthemoon.advancedrocketrycommunity.celestial.environment
                .EnvironmentalConditions(true, 9.2, 737, 1.91);
        int room = EnvironmentalExposure.hazards(conditions, EnvironmentalExposure.Protection.NONE, true, true);
        int open = EnvironmentalExposure.hazards(conditions, EnvironmentalExposure.Protection.NONE, false, true);
        assertTrue(NonPlayerAtmosphere.admitSpawn(false, Optional.of(BreathabilityState.BREATHABLE), room));
        assertFalse(NonPlayerAtmosphere.admitSpawn(false, Optional.of(BreathabilityState.BREATHABLE), open));
    }
}
