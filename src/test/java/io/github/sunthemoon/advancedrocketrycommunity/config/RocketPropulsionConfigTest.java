package io.github.sunthemoon.advancedrocketrycommunity.config;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import java.util.List;
import org.junit.jupiter.api.Test;

class RocketPropulsionConfigTest {
    @Test void commonKeysAndDefaultsAreCapturedInTheDescriptor() {
        assertEquals(RocketPropulsion.Settings.DEFAULTS, CommonConfig.ROCKET.capture());
        assertEquals(List.of("ROCKET", "thrustMultiplier"), CommonConfig.ROCKET.thrustMultiplier.getPath());
        assertEquals(List.of("ROCKET", "fuelCapacityMultiplier"), CommonConfig.ROCKET.fuelCapacityMultiplier.getPath());
        assertEquals(List.of("ROCKET", "nuclearCoreThrustRatio"), CommonConfig.ROCKET.nuclearCoreThrustRatio.getPath());
        assertEquals(List.of("advancedrocketrycommunity:hydrogen"), CommonConfig.ROCKET.allowed(RocketPropulsion.Role.WORKING_FLUID));
        assertEquals(List.of("advancedrocketrycommunity:oxygen"), CommonConfig.ROCKET.allowed(RocketPropulsion.Role.OXIDIZER));
        assertEquals(List.of("ROCKET", "rocketBipropellants"), CommonConfig.ROCKET.bipropellantFuels.getPath());
        assertEquals(List.of("ROCKET", "rocketOxidizers"), CommonConfig.ROCKET.oxidizers.getPath());
        assertEquals(List.of("ROCKET", "rocketNuclearWorkingFluids"), CommonConfig.ROCKET.nuclearWorkingFluids.getPath());
    }
    @Test void fluidListsRequireCanonicalBoundedIds() {
        assertTrue(RocketPropulsionConfig.validId("test:hydrogen"));
        for (Object value : List.of("hydrogen", "Test:hydrogen", "test:hydrogen:0", "test:" + "a".repeat(124), 4)) {
            assertFalse(RocketPropulsionConfig.validId(value));
        }
    }
}
