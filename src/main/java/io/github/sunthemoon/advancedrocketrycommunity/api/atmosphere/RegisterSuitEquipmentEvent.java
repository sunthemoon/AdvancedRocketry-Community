package io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere;

import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * Synchronous MOD-bus event during queued common setup, after item registration, on
 * both physical sides. Subscribe in mod construction; do not retain the event or
 * register asynchronously. Constructing an event alone grants no host access.
 */
public final class RegisterSuitEquipmentEvent extends Event implements IModBusEvent {
    private final SuitEquipmentRegistrar registrar;

    public RegisterSuitEquipmentEvent(SuitEquipmentRegistrar registrar) {
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }

    public void register(ResourceLocation providerId, Map<ResourceLocation, EquipmentSlot> items,
                         int payloadVersion, SuitOxygenProvider oxygenProvider) {
        registrar.register(providerId, items, payloadVersion, oxygenProvider);
    }
}
