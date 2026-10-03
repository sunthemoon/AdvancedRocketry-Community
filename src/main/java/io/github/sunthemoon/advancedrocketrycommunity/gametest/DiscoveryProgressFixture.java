package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;

/** Serial batch-owned progress; restores original authorities even after a failed test. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
public final class DiscoveryProgressFixture {
    private static DiscoveryProgressFixture active;
    private final ServerLevel level;
    private final CelestialSavedData progress;
    private final SatelliteMissionSavedData missions;

    private DiscoveryProgressFixture(ServerLevel level, boolean known) {
        this.level = level;
        var server = level.getServer();
        progress = CelestialSavedData.get(server);
        missions = known ? null : SatelliteMissionSavedData.get(server);
        var fixture = CelestialSavedData.create();
        if (known) {
            fixture.discover(PlanetaryContent.MARS, server.overworld().getGameTime());
            fixture.discover(PlanetaryContent.VENUS, server.overworld().getGameTime());
        }
        server.overworld().getDataStorage().set(CelestialSavedData.DATA_NAME, fixture);
        if (missions != null) {
            server.overworld().getDataStorage().set(SatelliteMissionSavedData.DATA_NAME,
                    SatelliteMissionSavedData.create(server.overworld().getGameTime()));
        }
    }

    static void install(ServerLevel level, boolean known) {
        if (active != null) { throw new IllegalStateException("Discovery fixture already active"); }
        active = new DiscoveryProgressFixture(level, known);
    }

    static void restore(ServerLevel level) {
        var fixture = active;
        if (fixture == null || fixture.level != level) { throw new IllegalStateException("Discovery fixture lifecycle mismatch"); }
        try {
            var storage = level.getServer().overworld().getDataStorage();
            storage.set(CelestialSavedData.DATA_NAME, fixture.progress);
            fixture.progress.setDirty();
            if (fixture.missions != null) {
                storage.set(SatelliteMissionSavedData.DATA_NAME, fixture.missions);
                fixture.missions.setDirty();
            }
            storage.save();
        } finally { active = null; }
    }

    @BeforeBatch(batch = "rocket_navigation")
    public static void beforeNavigation(ServerLevel level) { install(level, true); }
    @AfterBatch(batch = "rocket_navigation")
    public static void afterNavigation(ServerLevel level) { restore(level); }
    @BeforeBatch(batch = "rocket_navigation_menu")
    public static void beforeMenu(ServerLevel level) { install(level, true); }
    @AfterBatch(batch = "rocket_navigation_menu")
    public static void afterMenu(ServerLevel level) { restore(level); }
    @BeforeBatch(batch = "rocket_navigation_refresh")
    public static void beforeRefresh(ServerLevel level) { install(level, true); }
    @AfterBatch(batch = "rocket_navigation_refresh")
    public static void afterRefresh(ServerLevel level) { restore(level); }
    @BeforeBatch(batch = "planetary_discovery")
    public static void beforeDiscovery(ServerLevel level) { install(level, false); }
    @AfterBatch(batch = "planetary_discovery")
    public static void afterDiscovery(ServerLevel level) { restore(level); }
    @BeforeBatch(batch = "planetary_discovery_recovery")
    public static void beforeRecovery(ServerLevel level) { install(level, false); }
    @AfterBatch(batch = "planetary_discovery_recovery")
    public static void afterRecovery(ServerLevel level) { restore(level); }
    @BeforeBatch(batch = "planetary_physical_admission")
    public static void beforePhysical(ServerLevel level) { install(level, true); }
    @AfterBatch(batch = "planetary_physical_admission")
    public static void afterPhysical(ServerLevel level) { restore(level); }
    @BeforeBatch(batch = "planetary_changed_pad")
    public static void beforePad(ServerLevel level) { install(level, true); }
    @AfterBatch(batch = "planetary_changed_pad")
    public static void afterPad(ServerLevel level) { restore(level); }
    @BeforeBatch(batch = TauCetiPathGameTests.BATCH)
    public static void beforeTauCetiPath(ServerLevel level) { install(level, false); }
    @AfterBatch(batch = TauCetiPathGameTests.BATCH)
    public static void afterTauCetiPath(ServerLevel level) { restore(level); }
}
