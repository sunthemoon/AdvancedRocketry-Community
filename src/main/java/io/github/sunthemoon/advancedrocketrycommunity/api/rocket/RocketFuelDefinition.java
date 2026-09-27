package io.github.sunthemoon.advancedrocketrycommunity.api.rocket;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Fuel value of one whole item, independent of its metadata/capabilities.
 * Units are 1..2,048,000; an optional remainder is one newly constructed native
 * item, not the input's metadata. Captured loader batches retain their values
 * after definitions change or disappear. Registration never grants tank capacity.
 */
public record RocketFuelDefinition(long units, Optional<ResourceLocation> remainderItem) {
    public RocketFuelDefinition {
        Objects.requireNonNull(remainderItem, "remainderItem");
        if (units < 1L || units > 2_048_000L || remainderItem.filter(id ->
                id.toString().length() > 255 || id.equals(ResourceLocation.tryParse("minecraft:air"))).isPresent()) {
            throw new IllegalArgumentException("Invalid rocket item fuel value or remainder ID");
        }
    }
}
