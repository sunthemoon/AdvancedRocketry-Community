package io.github.sunthemoon.advancedrocketrycommunity.registry;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.sound.ClassicSound;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
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

    /** ADR-066 section 7.1: the ten classic sound events, each with a fixed audible range. */
    public static final Map<ClassicSound, RegistryObject<SoundEvent>> CLASSIC = classic();

    private ModSounds() {
    }

    private static Map<ClassicSound, RegistryObject<SoundEvent>> classic() {
        Map<ClassicSound, RegistryObject<SoundEvent>> sounds = new EnumMap<>(ClassicSound.class);
        for (ClassicSound sound : ClassicSound.values()) {
            sounds.put(sound, SOUND_EVENTS.register(sound.id(),
                    () -> SoundEvent.createFixedRangeEvent(sound.location(), sound.range())));
        }
        return Collections.unmodifiableMap(sounds);
    }

    public static SoundEvent classic(ClassicSound sound) {
        return CLASSIC.get(sound).get();
    }

    public static void register(IEventBus modBus) {
        SOUND_EVENTS.register(modBus);
    }
}
