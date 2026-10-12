package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RocketFluidExchangeAdmissionTest {
    private final RocketPropulsion descriptor = new RocketPropulsion(RocketPropulsion.Mode.MONO, 3_000, 0,
            1, 1, RocketPropulsion.Settings.DEFAULTS);

    @Test
    void removedDefinitionStillAllowsEveryWholeContainerDrainIncludingNonemptyRemainders() {
        var removed = new RocketFluidFuelCatalog(Map.of());
        var fluid = ModIdentity.id("rocket_fuel");
        assertTrue(RocketPropulsionInteraction.catalogAllowsExchange(descriptor, RocketPropulsion.Role.MONO,
                fluid, 3_000, 2_000, removed));
        assertTrue(RocketPropulsionInteraction.catalogAllowsExchange(descriptor, RocketPropulsion.Role.MONO,
                fluid, 2_000, 1_000, removed));
        assertTrue(RocketPropulsionInteraction.catalogAllowsExchange(descriptor, RocketPropulsion.Role.MONO,
                fluid, 1_000, 0, removed));
        assertFalse(RocketPropulsionInteraction.catalogAllowsExchange(descriptor, RocketPropulsion.Role.MONO,
                fluid, 1_000, 2_000, removed));
    }

    @Test
    void incomingFluidStillNeedsBothCapturedPermissionAndCurrentDefinition() {
        var fluid = ModIdentity.id("rocket_fuel");
        var catalog = RocketFluidFuelCatalog.defaults();
        assertTrue(RocketPropulsionInteraction.catalogAllowsExchange(descriptor, RocketPropulsion.Role.MONO,
                fluid, 0, 1_000, catalog));
        var denied = new RocketPropulsion(RocketPropulsion.Mode.MONO, 3_000, 0, 1, 1,
                descriptor.settings().withAllowedFluids(Map.of(RocketPropulsion.Role.MONO, java.util.List.of(),
                        RocketPropulsion.Role.BIPROPELLANT_FUEL, java.util.List.of(), RocketPropulsion.Role.OXIDIZER,
                        java.util.List.of(), RocketPropulsion.Role.WORKING_FLUID, java.util.List.of())));
        assertFalse(RocketPropulsionInteraction.catalogAllowsExchange(denied, RocketPropulsion.Role.MONO,
                fluid, 0, 1_000, catalog));
        assertTrue(RocketPropulsionInteraction.catalogAllowsExchange(denied, RocketPropulsion.Role.MONO,
                fluid, 2_000, 1_000, catalog));
    }
}
