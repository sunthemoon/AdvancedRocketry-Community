package io.github.sunthemoon.advancedrocketrycommunity.sound;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/**
 * The ten classic sound events of ADR-066 section 7.1. Registry IDs use the mod namespace, and each event's sound
 * location equals its registry ID: the one current mod-namespace {@code sounds.json}, written by the v1.8 DataGen,
 * defines these ten events and the v0.1 {@code ui_select}, and the build no longer packages the immutable v0.1 copy.
 * Every audio file is NEW code-synthesized work.
 */
public enum ClassicSound {
    AIR_HISS_LOOP(true, SoundSource.BLOCKS, 32),
    BASIC_LASER_GUN(false, SoundSource.PLAYERS, 16),
    COMBUSTION_ROCKET(true, SoundSource.NEUTRAL, 32),
    ELECTRIC_SHOCK_SMALL(false, SoundSource.AMBIENT, 16),
    GRAVITY_CONTROLLER(true, SoundSource.BLOCKS, 32),
    LASER_DRILL(true, SoundSource.BLOCKS, 32),
    LATHE(true, SoundSource.BLOCKS, 32),
    MACHINE_LARGE(true, SoundSource.BLOCKS, 32),
    RAILGUN_BANG(false, SoundSource.BLOCKS, 32),
    ROLLING_MACHINE(true, SoundSource.BLOCKS, 32);

    private final boolean loop;
    private final SoundSource source;
    private final int range;

    ClassicSound(boolean loop, SoundSource source, int range) {
        this.loop = loop;
        this.source = source;
        this.range = range;
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public boolean loop() { return loop; }
    public SoundSource source() { return source; }
    /** The fixed audible range in blocks. */
    public int range() { return range; }
    public ResourceLocation registryId() { return ModIdentity.id(id()); }
    /** The sound location that {@code sounds.json} defines; it equals the registry ID. */
    public ResourceLocation location() { return registryId(); }
    /** The NEW audio file, in the mod namespace's sounds directory. */
    public ResourceLocation file() { return ModIdentity.id(id()); }
    public String subtitle() { return "subtitle." + ModIdentity.MOD_ID + "." + id(); }
}
