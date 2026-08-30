package io.github.sunthemoon.advancedrocketrycommunity.registry;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(
            ForgeRegistries.SOUND_EVENTS,
            AdvancedRocketryCommunity.MOD_ID
    );

    public static final RegistryObject<SoundEvent> UI_SELECT = SOUND_EVENTS.register(
            "ui_select",
            () -> SoundEvent.createVariableRangeEvent(
                    ModIdentity.id("ui_select")
            )
    );

    private ModSounds() {
    }

    public static void register(IEventBus modBus) {
        SOUND_EVENTS.register(modBus);
    }
}
