package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceView;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Presentation of the existing bounded view strings; no wire fields or server decisions change. */
final class EndgameViewText {
    private static final String VIEW = "advancedrocketrycommunity.endgame.view.";

    static Component line(EndgameDeviceView.Line line) {
        return Component.translatable(line.label()).append(": ").append(value(line));
    }

    static Component value(EndgameDeviceView.Line line) {
        if (line.translated()) { return Component.translatable(line.value()); }
        return switch (line.label()) {
            case VIEW + "body" -> BodyDisplayNames.bodyName(line.value());
            case VIEW + "fuel_table" -> fuelTable(line.value());
            case VIEW + "destination_at", VIEW + "far_end_at" -> location(line.value());
            case VIEW + "anchor" -> anchor(line.value());
            default -> Component.literal(line.value());
        };
    }

    private static Component fuelTable(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id == null ? Component.literal(value) : Component.translatableWithFallback(
                "black_hole_fuel." + id.getNamespace() + "." + id.getPath().replace('/', '.'), value);
    }

    private static Component location(String value) {
        int space = value.indexOf(' ');
        if (space < 0 || !canonicalId(value.substring(0, space))) {
            return Component.literal(value);
        }
        return BodyDisplayNames.levelName(value.substring(0, space)).copy().append(value.substring(space));
    }

    private static Component anchor(String value) {
        String[] parts = value.split(" ", 4);
        if (parts.length != 4 || !parts[0].equals("elevator_anchor")
                || !canonicalId(parts[2])) {
            return Component.literal(value);
        }
        return Component.translatable("block.advancedrocketrycommunity.elevator_anchor")
                .append(" " + parts[1] + " ").append(BodyDisplayNames.levelName(parts[2])).append(" " + parts[3]);
    }

    private static boolean canonicalId(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id != null && id.toString().equals(value);
    }

    private EndgameViewText() { }
}
