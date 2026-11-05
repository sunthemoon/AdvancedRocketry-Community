package io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor;

import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Localizes body IDs in the existing string catalog; ordinary station names remain literal. */
public final class RocketMonitorLabels {
    private RocketMonitorLabels() { }

    public static Component display(String label, Predicate<String> translated) {
        var id = label.contains(":") ? ResourceLocation.tryParse(label) : null;
        if (id != null && id.toString().equals(label)) {
            String key = "body." + id.getNamespace() + "." + id.getPath().replace('/', '.');
            if (translated.test(key)) { return Component.translatable(key); }
        }
        return Component.literal(label);
    }
}
