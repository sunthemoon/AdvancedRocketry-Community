package io.github.sunthemoon.advancedrocketrycommunity.sound;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModSounds;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** ADR-066 section 7.1 (W45): every registered mod sound event's location equals its registry ID. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClassicSoundGameTests {
    private ClassicSoundGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void everyRegisteredSoundEventLocationEqualsItsRegistryId(GameTestHelper helper) {
        helper.assertTrue(ModSounds.SOUND_EVENTS.getEntries().size() == ClassicSound.values().length + 1,
                "Expected ui_select and the ten classic sound events, got " + ModSounds.SOUND_EVENTS.getEntries());
        for (RegistryObject<SoundEvent> entry : ModSounds.SOUND_EVENTS.getEntries()) {
            helper.assertTrue(entry.isPresent(), entry.getId() + " is not registered");
            SoundEvent event = entry.get();
            helper.assertTrue(entry.getId().equals(ForgeRegistries.SOUND_EVENTS.getKey(event)),
                    entry.getId() + " is registered as " + ForgeRegistries.SOUND_EVENTS.getKey(event));
            helper.assertTrue(entry.getId().equals(event.getLocation()),
                    entry.getId() + " plays the sound location " + event.getLocation());
        }
        for (ClassicSound sound : ClassicSound.values()) {
            RegistryObject<SoundEvent> entry = ModSounds.CLASSIC.get(sound);
            helper.assertTrue(sound.registryId().equals(entry.getId()),
                    sound + " is registered as " + entry.getId());
            helper.assertTrue(sound.location().equals(entry.get().getLocation()),
                    sound + " plays " + entry.get().getLocation() + " instead of " + sound.location());
            helper.assertTrue(entry.get().getRange(1.0F) == sound.range(), sound + " lost its fixed range");
        }
        helper.succeed();
    }
}
