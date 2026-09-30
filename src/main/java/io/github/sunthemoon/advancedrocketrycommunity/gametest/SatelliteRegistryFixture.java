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
    public static final String LIFECYCLE_BATCH = "satellite_lifecycle";
    /** In the lifecycle batch: a mission whose satellite is gone, and a satellite whose mission is gone. */
    static java.util.UUID orphanMission;
    static java.util.UUID strandedSatellite;

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

    /** ADR-050 section 9 fixture: a root whose broken references load as a quarantine and a recovery. */
    static SatelliteMissionSavedData brokenRegistry(long gameTime) {
        SatelliteMissionSavedData source = SatelliteMissionSavedData.create(gameTime);
        java.util.UUID owner = java.util.UUID.randomUUID();
        java.util.UUID lostSatellite = java.util.UUID.randomUUID();
        orphanMission = java.util.UUID.randomUUID();
        strandedSatellite = java.util.UUID.randomUUID();
        java.util.UUID lostMission = java.util.UUID.randomUUID();
        var definition = io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent.surveySatellite();
        var target = io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds.MOON_ID;
        source.launch(lostSatellite, orphanMission, owner, definition, target, gameTime, false);
        source.launch(strandedSatellite, lostMission, owner, definition, target, gameTime, false);
        CompoundTag root = source.save(new CompoundTag());
        root.getList("satellites", net.minecraft.nbt.Tag.TAG_COMPOUND).removeIf(tag ->
                ((CompoundTag) tag).getUUID("satellite_id").equals(lostSatellite));
        root.getList("missions", net.minecraft.nbt.Tag.TAG_COMPOUND).removeIf(tag ->
                ((CompoundTag) tag).getUUID("mission_id").equals(lostMission));
        return SatelliteMissionSavedData.load(root);
    }

    @BeforeBatch(batch = LIFECYCLE_BATCH)
    public static void beforeLifecycle(ServerLevel level) {
        install(level, brokenRegistry(level.getServer().overworld().getGameTime()));
    }

    @AfterBatch(batch = LIFECYCLE_BATCH)
    public static void afterLifecycle(ServerLevel level) {
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
