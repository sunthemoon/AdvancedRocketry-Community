package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Schema one has only stored FE, for either profile. Refused roots are retained by the adapter, not decoded to defaults. */
public final class SolarSave {
    public static final String ROOT = "arce_solar_generator";
    /** The array controller's independent root; same schema and preflight, its own capacity. */
    public static final String ARRAY_ROOT = "arce_solar_array";
    public static final int MAX_BYTES = 4_096;
    private static final Set<String> KEYS = Set.of("schema", "energy");

    public static boolean bounded(Tag root) {
        try { return BoundedNbt.fits(root, MAX_BYTES, 16, 1_024); }
        catch (RuntimeException malformed) { return false; }
    }
    public static CompoundTag encode(int energy) { return encode(energy, SolarGeneration.CAPACITY); }
    public static CompoundTag encode(int energy, int capacity) {
        SolarGeneration.requireEnergy(energy, capacity);
        CompoundTag root = new CompoundTag();
        root.putInt("schema", 1);
        root.putInt("energy", energy);
        return root;
    }
    public static int decode(Tag raw) { return decode(raw, SolarGeneration.CAPACITY); }
    public static int decode(Tag raw, int capacity) {
        if (!bounded(raw) || !(raw instanceof CompoundTag root) || !root.getAllKeys().equals(KEYS)
                || !root.contains("schema", Tag.TAG_INT) || root.getInt("schema") != 1
                || !root.contains("energy", Tag.TAG_INT)) {
            throw new IllegalArgumentException("Unsupported solar root");
        }
        int energy = root.getInt("energy");
        SolarGeneration.requireEnergy(energy, capacity);
        return energy;
    }
    private SolarSave() { }
}
