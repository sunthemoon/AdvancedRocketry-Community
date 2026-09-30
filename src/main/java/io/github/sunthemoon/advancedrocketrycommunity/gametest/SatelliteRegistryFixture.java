package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * Serial, batch-owned satellite registries: a fresh one for the flush-failure batch and a blocked one (a future
 * root, ADR-050 section 9) for the fail-closed batch. The original registry is restored and saved afterwards,
 * even after a failed test.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
public final class SatelliteRegistryFixture {
    public static final String FLUSH_FAILURE_BATCH = "satellite_flush_failure";
    public static final String BLOCKED_BATCH = "satellite_blocked";

    private static SatelliteMissionSavedData original;

    private SatelliteRegistryFixture() {
    }

    static SatelliteMissionSavedData blockedRegistry() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 99);
        future.putString("future_payload", "preserve-exactly");
        return SatelliteMissionSavedData.load(future);
    }

    private static void install(ServerLevel level, SatelliteMissionSavedData fixture) {
        if (original != null) {
            throw new IllegalStateException("Satellite registry fixture already active");
        }
        var storage = level.getServer().overworld().getDataStorage();
        original = SatelliteMissionSavedData.get(level.getServer());
        storage.set(SatelliteMissionSavedData.DATA_NAME, fixture);
    }

    private static void restore(ServerLevel level) {
        if (original == null) {
            throw new IllegalStateException("Satellite registry fixture lifecycle mismatch");
        }
        try {
            var storage = level.getServer().overworld().getDataStorage();
            storage.set(SatelliteMissionSavedData.DATA_NAME, original);
            original.setDirty();
            storage.save();
        } finally {
            original = null;
        }
    }

    @BeforeBatch(batch = FLUSH_FAILURE_BATCH)
    public static void beforeFlushFailure(ServerLevel level) {
        install(level, SatelliteMissionSavedData.create(level.getServer().overworld().getGameTime()));
    }

    @AfterBatch(batch = FLUSH_FAILURE_BATCH)
    public static void afterFlushFailure(ServerLevel level) {
        restore(level);
    }

    @BeforeBatch(batch = BLOCKED_BATCH)
    public static void beforeBlocked(ServerLevel level) {
        install(level, blockedRegistry());
    }

    @AfterBatch(batch = BLOCKED_BATCH)
    public static void afterBlocked(ServerLevel level) {
        restore(level);
    }
}
