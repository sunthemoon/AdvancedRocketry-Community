package io.github.sunthemoon.advancedrocketrycommunity.equipment.laser.client;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.client.sound.ClassicSoundClient;
import io.github.sunthemoon.advancedrocketrycommunity.sound.ClassicSound;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Vanilla S2C sound delivery feeds W16's distance/lifetime/concurrency-limited one-shot playback. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LaserGunSoundDelivery {
    private static boolean forwarding;
    private LaserGunSoundDelivery() { }
    @SubscribeEvent public static void sound(PlaySoundEvent event) {
        var sound = event.getSound();
        if (forwarding || sound == null || !sound.getLocation().equals(ClassicSound.BASIC_LASER_GUN.location())) { return; }
        event.setSound(null);
        forwarding = true;
        try { ClassicSoundClient.oneShot(ClassicSound.BASIC_LASER_GUN, sound.getX(), sound.getY(), sound.getZ()); }
        finally { forwarding = false; }
    }
}
