package io.github.sunthemoon.advancedrocketrycommunity.client.sky;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.AmbientController;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkySelection.Selection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

final class PlanetaryAmbience implements AmbientController.Playback {
    private SoundInstance sound;

    @Override public boolean active() {
        return sound != null && Minecraft.getInstance().getSoundManager().isActive(sound);
    }

    @Override public void stop() {
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().stop(sound);
            sound = null;
        }
    }

    @Override public void play(Selection selection) {
        var id = selection.profile().ambientSound().orElseThrow();
        var manager = Minecraft.getInstance().getSoundManager();
        if (BuiltInRegistries.SOUND_EVENT.containsKey(id) && manager.getSoundEvent(id) != null) {
            sound = instance(BuiltInRegistries.SOUND_EVENT.get(id), (float) selection.profile().soundVolume());
            manager.play(sound);
        }
    }

    static SoundInstance instance(SoundEvent event, float volume) {
        // The platform factory takes pitch before volume.
        return SimpleSoundInstance.forLocalAmbience(event, 1, volume);
    }
}
