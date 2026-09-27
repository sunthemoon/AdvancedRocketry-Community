package io.github.sunthemoon.advancedrocketrycommunity.api.rocket;

import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * Synchronous MOD-bus event in queued common setup after block registration, on
 * both physical sides. Subscribe in mod construction, never retain the event or
 * register asynchronously. Constructing an event alone grants no host access.
 */
public final class RegisterRocketComponentsEvent extends Event implements IModBusEvent {
    private final RocketComponentRegistrar registrar;

    public RegisterRocketComponentsEvent(RocketComponentRegistrar registrar) {
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }

    public void register(ResourceLocation definitionId, Set<ResourceLocation> blocks,
                         RocketComponentDefinition definition) {
        registrar.register(definitionId, blocks, definition);
    }
}
