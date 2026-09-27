package io.github.sunthemoon.advancedrocketrycommunity.api.satellite;

import net.minecraft.resources.ResourceLocation;

/** Loading-thread, owner-scoped registration. Do not retain this handle. */
@FunctionalInterface
public interface SatellitePayloadRegistrar {
    void register(ResourceLocation definitionId, ResourceLocation payloadItem, SatelliteMissionDefinition mission);
}
