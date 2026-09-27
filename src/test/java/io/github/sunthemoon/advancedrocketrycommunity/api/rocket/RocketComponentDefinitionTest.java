package io.github.sunthemoon.advancedrocketrycommunity.api.rocket;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.TravelFuelFormula;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RocketComponentDefinitionTest {
    @Test
    void acceptsExactNumericBoundsAndCombinedRolesAsValues() {
        var minimum = new RocketComponentDefinition(1, 0, 0, false, false, false);
        var maximum = new RocketComponentDefinition(1_000_000, 1_000_000, 2_048_000, true, true, true);
        assertEquals(1, minimum.mass());
        assertEquals(1_000_000, maximum.thrust());
        assertEquals(RocketFlightLimits.MAX_FUEL_CAPACITY, maximum.fuelCapacity());
        assertTrue(maximum.engine() && maximum.seat() && maximum.guidance());
        assertEquals(maximum, new RocketComponentDefinition(1_000_000, 1_000_000, 2_048_000, true, true, true));
        assertEquals(maximum.hashCode(), new RocketComponentDefinition(1_000_000, 1_000_000, 2_048_000, true, true, true).hashCode());
        assertNotEquals(minimum, maximum);
    }

    @Test
    void rejectsInvalidAndOverflowProneNumericValues() {
        for (long mass : new long[]{Long.MIN_VALUE, -1, 0, 1_000_001, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new RocketComponentDefinition(mass, 0, 0, false, false, false));
        }
        for (long thrust : new long[]{Long.MIN_VALUE, -1, 1_000_001, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new RocketComponentDefinition(1, thrust, 0, true, false, false));
        }
        for (long capacity : new long[]{Long.MIN_VALUE, -1, 2_048_001, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new RocketComponentDefinition(1, 0, capacity, false, false, false));
        }
    }

    @Test
    void nonEngineThrustRejectsButZeroThrustEngineRemainsAComponent() {
        assertThrows(IllegalArgumentException.class, () -> new RocketComponentDefinition(1, 1, 0, false, false, false));
        assertTrue(new RocketComponentDefinition(1, 0, 0, true, false, false).engine());
    }

    @Test
    void definitionValidityDoesNotOverrideExistingTravelFuelBound() {
        var heavy = new RocketComponentDefinition(1_000_000, 1_000_000, 0, true, false, false);
        assertTrue(TravelFuelFormula.calculate(heavy.mass() * 2048, 1000, 165, 50,
                RocketFlightLimits.MAX_TRAVEL_FUEL).error().isPresent());
    }
}
