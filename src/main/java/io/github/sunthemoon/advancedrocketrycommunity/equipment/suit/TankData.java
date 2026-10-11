package io.github.sunthemoon.advancedrocketrycommunity.equipment.suit;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Strict finite tank schema; saved maxima are not recomputed from live configuration. */
public record TankData(Gas gas, int stored, int maximum) {
    public static final String KEY = "arce_pressure_tank";
    private static final Set<String> KEYS = Set.of("schema_version", "gas", "stored", "maximum");
    public enum Gas { EMPTY, OXYGEN, HYDROGEN }
    public TankData {
        if (gas == null || maximum < 1 || maximum > 32_000 || stored < 0 || stored > maximum
                || gas == Gas.EMPTY && stored != 0) { throw new IllegalArgumentException("Invalid finite tank"); }
    }
    public static int baseCapacity(String id) {
        return switch (id) {
            case "advancedrocketrycommunity:low_pressure_tank" -> 1_000;
            case "advancedrocketrycommunity:pressure_tank" -> 2_000;
            case "advancedrocketrycommunity:high_pressure_tank" -> 4_000;
            case "advancedrocketrycommunity:super_high_pressure_tank" -> 8_000;
            default -> 0;
        };
    }
    public static TankData read(Tag raw, int base, int creationMultiplier) {
        if (base == 0 || creationMultiplier < 1 || creationMultiplier > 4) { return null; }
        if (raw == null) { return new TankData(Gas.EMPTY, 0, base * creationMultiplier); }
        if (!(raw instanceof CompoundTag root) || !EquipmentNbt.bounded(root)
                || !root.getAllKeys().equals(KEYS) || !root.contains("schema_version", Tag.TAG_INT)
                || root.getInt("schema_version") != 1 || !root.contains("gas", Tag.TAG_STRING)
                || !root.contains("stored", Tag.TAG_INT) || !root.contains("maximum", Tag.TAG_INT)) { return null; }
        int max = root.getInt("maximum");
        int stored = root.getInt("stored");
        if (max < base || max > base * 4 || max % base != 0 || stored < 0 || stored > max) { return null; }
        Gas gas;
        try { gas = Gas.valueOf(root.getString("gas")); }
        catch (IllegalArgumentException invalid) { return null; }
        return gas == Gas.EMPTY && stored != 0 ? null : new TankData(gas, stored, max);
    }
    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema_version", 1); tag.putString("gas", gas.name());
        tag.putInt("stored", stored); tag.putInt("maximum", maximum);
        return tag;
    }
}
