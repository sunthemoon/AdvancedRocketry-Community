package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Small vanilla-backed effects layer; flight authority remains entirely server-side. */
final class RocketFlightFeedback {
    private static final int TRAIL_INTERVAL_TICKS = 4;
    private static final int TRAIL_PARTICLES = 4;

    private RocketFlightFeedback() {
    }

    static void countdownAccepted(RocketEntity rocket) {
        sound(rocket, SoundEvents.BEACON_ACTIVATE, 0.8F, 1.2F);
    }

    static void ascentStarted(RocketEntity rocket) {
        sound(rocket, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.2F, 0.75F);
        trail(rocket, 0L, true);
    }

    static void ascentTrail(RocketEntity rocket, long elapsed) {
        trail(rocket, elapsed, true);
    }

    static void transitStarted(RocketEntity rocket) {
        sound(rocket, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0F, 0.65F);
    }

    static void destinationSpawned(RocketEntity rocket) {
        sound(rocket, SoundEvents.ENDERMAN_TELEPORT, 0.9F, 0.8F);
    }

    static void descentTrail(RocketEntity rocket, long elapsed) {
        trail(rocket, elapsed, false);
    }

    static void landed(RocketEntity rocket) {
        sound(rocket, SoundEvents.ANVIL_LAND, 0.8F, 1.25F);
        if (rocket.level() instanceof ServerLevel level) {
            level.sendParticles(
                    ParticleTypes.CLOUD,
                    rocket.getX(),
                    rocket.getY() + 0.1D,
                    rocket.getZ(),
                    12,
                    0.6D,
                    0.1D,
                    0.6D,
                    0.02D
            );
        }
    }

    static void returnedToSource(RocketEntity rocket) {
        sound(rocket, SoundEvents.BEACON_DEACTIVATE, 0.8F, 0.8F);
    }

    private static void trail(RocketEntity rocket, long elapsed, boolean flame) {
        if (elapsed % TRAIL_INTERVAL_TICKS != 0L || !(rocket.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(
                flame ? ParticleTypes.FLAME : ParticleTypes.CLOUD,
                rocket.getX(),
                rocket.getY() - 0.2D,
                rocket.getZ(),
                TRAIL_PARTICLES,
                0.25D,
                0.1D,
                0.25D,
                flame ? 0.02D : 0.01D
        );
        level.sendParticles(
                ParticleTypes.CAMPFIRE_COSY_SMOKE,
                rocket.getX(),
                rocket.getY() - 0.35D,
                rocket.getZ(),
                2,
                0.2D,
                0.1D,
                0.2D,
                0.01D
        );
    }

    private static void sound(RocketEntity rocket, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (rocket.level() instanceof ServerLevel level) {
            level.playSound(
                    null,
                    rocket.blockPosition(),
                    sound,
                    SoundSource.BLOCKS,
                    volume,
                    pitch
            );
        }
    }
}
