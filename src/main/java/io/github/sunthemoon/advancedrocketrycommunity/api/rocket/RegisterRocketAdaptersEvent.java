package io.github.sunthemoon.advancedrocketrycommunity.api.rocket;

import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * One owner-bound MOD-bus event during common setup, before the catalog freezes.
 * Register listeners during mod construction. No runtime or reload registration.
 */
public final class RegisterRocketAdaptersEvent extends Event implements IModBusEvent {
    private final RocketAdapterRegistrar registrar;

    /** Host/test constructor; constructing an event does not grant registry access. */
    public RegisterRocketAdaptersEvent(RocketAdapterRegistrar registrar) {
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }

    public void register(ResourceLocation adapterId, Set<ResourceLocation> blockEntityTypes,
                         int payloadVersion, RocketBlockEntityAdapter adapter) {
        registrar.register(adapterId, blockEntityTypes, payloadVersion, adapter);
    }
}
