package io.github.sunthemoon.advancedrocketrycommunity.api.rocket;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Loading-thread, owner-scoped registration; do not retain the handle. */
@FunctionalInterface
public interface RocketFuelRegistrar {
    void register(ResourceLocation definitionId, Set<ResourceLocation> items, RocketFuelDefinition definition);
}
