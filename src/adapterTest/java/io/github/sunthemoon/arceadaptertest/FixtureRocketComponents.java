package io.github.sunthemoon.arceadaptertest;

import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RegisterRocketComponentsEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketComponentDefinition;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Static definitions over vanilla blocks; no extra registry objects or copied artwork. */
final class FixtureRocketComponents {
    private FixtureRocketComponents() { }

    static String variant() {
        String value = System.getProperty("arce_adapter_test.componentVariant", "standard");
        if (!value.equals("standard") && !value.equals("updated")) {
            throw new IllegalArgumentException("Unknown component fixture variant");
        }
        return value;
    }

    static void register(RegisterRocketComponentsEvent event) {
        boolean updated = variant().equals("updated");
        add(event, "engine", "diamond_block", new RocketComponentDefinition(updated ? 160 : 120,
                updated ? 2800 : 2400, 0, true, false, false));
        add(event, "tank", "emerald_block", new RocketComponentDefinition(80, 0, updated ? 2000 : 1500, false, false, false));
        add(event, "seat", "oak_planks", new RocketComponentDefinition(12, 0, 0, false, true, false));
        add(event, "guidance", "gold_block", new RocketComponentDefinition(35, 0, 0, false, false, true));
        add(event, "hull", "quartz_block", new RocketComponentDefinition(7, 0, 0, false, false, false));
    }

    private static void add(RegisterRocketComponentsEvent event, String id, String block, RocketComponentDefinition value) {
        event.register(AdapterTestMod.id("component_" + id), Set.of(ResourceLocation.tryParse("minecraft:" + block)), value);
    }
}
