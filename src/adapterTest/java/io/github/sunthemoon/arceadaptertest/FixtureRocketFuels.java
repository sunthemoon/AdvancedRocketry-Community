package io.github.sunthemoon.arceadaptertest;

import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RegisterRocketFuelsEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketFuelDefinition;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Whole-item fixtures use vanilla items, not host internals or private registry objects. */
final class FixtureRocketFuels {
    private FixtureRocketFuels() { }

    static String variant() {
        String value = System.getProperty("arce_adapter_test.fuelVariant", "standard");
        if (!value.equals("standard") && !value.equals("updated")) {
            throw new IllegalArgumentException("Unknown fuel fixture variant");
        }
        return value;
    }

    static void register(RegisterRocketFuelsEvent event) {
        boolean updated = variant().equals("updated");
        event.register(AdapterTestMod.id("charcoal"), Set.of(id("charcoal")),
                new RocketFuelDefinition(updated ? 301 : 73, Optional.of(id(updated ? "bowl" : "stick"))));
        event.register(AdapterTestMod.id("stick"), Set.of(id("stick")), new RocketFuelDefinition(9, Optional.empty()));
        event.register(AdapterTestMod.id("coal"), Set.of(id("coal")), new RocketFuelDefinition(127, Optional.empty()));
        event.register(AdapterTestMod.id("large_fuel"), Set.of(id("blaze_powder")),
                new RocketFuelDefinition(updated ? 2001 : 1573, Optional.of(id(updated ? "bowl" : "stick"))));
    }

    private static ResourceLocation id(String name) { return ResourceLocation.tryParse("minecraft:" + name); }
}
