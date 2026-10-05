package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Schema one has only stored FE. Refused roots are retained by the adapter, not decoded to defaults. */
public final class SolarSave {
    public static final String ROOT = "arce_solar_generator";
    public static final int MAX_BYTES = 4_096;
    private static final Set<String> KEYS = Set.of("schema", "energy");

    public static boolean bounded(Tag root) {
        try { return BoundedNbt.fits(root, MAX_BYTES, 16, 1_024); }
        catch (RuntimeException malformed) { return false; }
    }
    public static CompoundTag encode(int energy) {
        SolarGeneration.requireEnergy(energy);
        CompoundTag root = new CompoundTag();
        root.putInt("schema", 1);
        root.putInt("energy", energy);
        return root;
    }
    public static int decode(Tag raw) {
        if (!bounded(raw) || !(raw instanceof CompoundTag root) || !root.getAllKeys().equals(KEYS)
                || !root.contains("schema", Tag.TAG_INT) || root.getInt("schema") != 1
                || !root.contains("energy", Tag.TAG_INT)) {
            throw new IllegalArgumentException("Unsupported solar root");
        }
        int energy = root.getInt("energy");
        SolarGeneration.requireEnergy(energy);
        return energy;
    }
    private SolarSave() { }
}
