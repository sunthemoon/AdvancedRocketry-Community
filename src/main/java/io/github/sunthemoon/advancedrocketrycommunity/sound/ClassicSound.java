package io.github.sunthemoon.advancedrocketrycommunity.sound;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/**
 * The ten classic sound events of ADR-066 section 7.1. Registry IDs use the mod namespace. Their definitions live in
 * the v1.8 resource namespace's {@code sounds.json} (like the v1.8 language files), because the historical v0.1
 * {@code sounds.json} in the mod namespace is an immutable DataGen output; each event's sound location therefore
 * names that namespace. Every audio file is NEW code-synthesized work.
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

    /** The resource namespace of the v1.8 sound definitions. */
    public static final String DEFINITION_NAMESPACE = ModIdentity.MOD_ID + "_v180";

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
    public ResourceLocation location() { return new ResourceLocation(DEFINITION_NAMESPACE, id()); }
    /** The NEW audio file, in the mod namespace's sounds directory. */
    public ResourceLocation file() { return ModIdentity.id(id()); }
    public String subtitle() { return "subtitle." + ModIdentity.MOD_ID + "." + id(); }
}
