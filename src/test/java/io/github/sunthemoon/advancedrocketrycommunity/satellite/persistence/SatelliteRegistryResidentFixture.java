package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import net.minecraft.nbt.CompoundTag;

/** W129a B3a: W128's 16 MiB registry, loaded and resident; it writes nothing in this slice. */
public final class SatelliteRegistryResidentFixture {
    private SatelliteRegistryResidentFixture() { }

    public static SatelliteMissionSavedData resident() {
        var data = SatelliteMissionSavedData.load(SatelliteRegistryBenchmark.root());
        if (!data.operational()) { throw new IllegalStateException("The 16 MiB registry fixture did not load"); }
        return data;
    }

    public static int rootBytes(SatelliteMissionSavedData data) {
        return SatelliteNbtSize.uncompressedBytes(data.save(new CompoundTag()));
    }

    public static int missions(SatelliteMissionSavedData data) { return data.missions().size(); }
}
