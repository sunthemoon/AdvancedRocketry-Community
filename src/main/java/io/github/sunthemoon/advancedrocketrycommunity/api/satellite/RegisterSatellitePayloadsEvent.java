package io.github.sunthemoon.advancedrocketrycommunity.api.satellite;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * Synchronous non-cancelable MOD-bus event in queued common setup on both sides.
 * Subscribe during mod construction. Registers at most 15 external payloads;
 * definition IDs belong to the receiving mod, item IDs may name other mods.
 * Each known, non-reserved item is claimed once; IDs are at most 128 characters.
 * Calls are atomic. Stale, off-thread and reentrant calls are rejected. The host
 * retains immutable data only, not callbacks. Whole untagged items are consumed
 * in the terminal data-storage slot; other assembly components remain fixed.
 */
public final class RegisterSatellitePayloadsEvent extends Event implements IModBusEvent {
    private final SatellitePayloadRegistrar registrar;

    public RegisterSatellitePayloadsEvent(SatellitePayloadRegistrar registrar) {
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }

    public void register(ResourceLocation definitionId, ResourceLocation payloadItem, SatelliteMissionDefinition mission) {
        registrar.register(definitionId, payloadItem, mission);
    }
}
