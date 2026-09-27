package io.github.sunthemoon.advancedrocketrycommunity.api.rocket;

import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * Synchronous MOD-bus event in queued common setup on both physical sides.
 * Subscribe in mod construction. Definition IDs belong to the receiving mod;
 * known input/remainder items may belong to other mods. Calls are atomic and
 * bounded (256 definitions, 64 items each, 1,024 total, excluding built-in fuel).
 * Retained, asynchronous and reentrant calls are invalid. No callback is retained.
 */
public final class RegisterRocketFuelsEvent extends Event implements IModBusEvent {
    private final RocketFuelRegistrar registrar;

    public RegisterRocketFuelsEvent(RocketFuelRegistrar registrar) {
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }

    public void register(ResourceLocation definitionId, Set<ResourceLocation> items, RocketFuelDefinition definition) {
        registrar.register(definitionId, items, definition);
    }
}
