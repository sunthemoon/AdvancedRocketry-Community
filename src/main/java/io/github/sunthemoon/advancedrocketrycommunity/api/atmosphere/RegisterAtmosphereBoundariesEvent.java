package io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere;

import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * One synchronous owner-bound MOD-bus event during queued common setup, after block
 * registration and before atmosphere services are installed. Subscribe during mod
 * construction. Constructing this event does not grant access to the host registry.
 */
public final class RegisterAtmosphereBoundariesEvent extends Event implements IModBusEvent {
    private final AtmosphereBoundaryRegistrar registrar;

    public RegisterAtmosphereBoundariesEvent(AtmosphereBoundaryRegistrar registrar) {
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }

    public void register(ResourceLocation providerId, Set<ResourceLocation> blockIds,
                         AtmosphereBoundaryProvider provider) {
        registrar.register(providerId, blockIds, provider);
    }
}
