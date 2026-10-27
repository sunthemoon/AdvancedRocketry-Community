package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.TestDataStorage;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W129bc-R1: a paying claim must retain its mutation for terminal DATA bookkeeping. */
class ReviewDiscoveryClaimBarrierTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void aPayingDiscoveryClaimRetainsItsMutationFlagForTheTerminalBarrier() throws Exception {
        var server = UnstartedServer.owned();
        UnstartedServer.set(MinecraftServer.class, server, "running", true);
        var access = UnstartedServer.allocate(LevelStorageSource.LevelStorageAccess.class);
        UnstartedServer.set(LevelStorageSource.LevelStorageAccess.class, access, "resources", new HashMap<>());
        UnstartedServer.set(LevelStorageSource.LevelStorageAccess.class, access, "levelDirectory",
                new LevelStorageSource.LevelDirectory(directory));
        UnstartedServer.set(MinecraftServer.class, server, "storageSource", access);
        var level = UnstartedServer.allocate(RegistryLevel.class);
        Path dataDirectory = Files.createDirectories(directory.resolve("data"));
        level.storage = TestDataStorage.over(dataDirectory);
        UnstartedServer.set(ServerLevel.class, level, "server", server);
        UnstartedServer.set(MinecraftServer.class, server, "levels", Map.of(Level.OVERWORLD, level));
        UUID owner = UUID.randomUUID(), satellite = UUID.randomUUID(), mission = UUID.randomUUID();
        var data = SatelliteMissionSavedData.create(0);
        var definition = PlanetaryContent.surveySatellite();
        assertTrue(data.launch(satellite, mission, owner, definition, PlanetaryContent.MARS, 0, true).changed());
        data.completeDue(200);
        data.flush(dataDirectory.resolve(SatelliteMissionSavedData.DATA_NAME + ".dat"));
        level.storage.set(SatelliteMissionSavedData.DATA_NAME, data);
        var celestial = CelestialSavedData.create();
        level.storage.set(CelestialSavedData.DATA_NAME, celestial);
        var player = UnstartedServer.allocate(ServerPlayer.class);
        UnstartedServer.set(Entity.class, player, "uuid", owner);
        UnstartedServer.set(Entity.class, player, "level", level);
        UnstartedServer.set(ServerPlayer.class, player, "server", server);
        var manager = new SatelliteManager(new SatelliteCatalogManager(), new CelestialCatalogManager(), ignored -> { });
        long before = data.mutationSequence();
        try {
            var result = manager.claimCurrent(player, new SatelliteIdentity(satellite, owner, definition.id()));
            assertEquals(SatelliteOperationCode.PENDING_DISCOVERY, result.code());
            assertTrue(data.mutationSequence() > before);
            assertEquals(20, data.account(owner).balance());
            assertEquals(MissionStatus.CLAIM_PENDING_DISCOVERY, data.mission(mission).orElseThrow().status());
            assertFalse(data.covers(data.token()));
            assertTrue(celestial.get(PlanetaryContent.MARS).isEmpty());
            assertTrue(result.changed(), "The paying claim changed RAM, but its returned result skips TerminalRegistryActions.dataAction's token entry");
        } finally {
            data.abort();
        }
    }

    private static final class RegistryLevel extends ServerLevel {
        DimensionDataStorage storage;
        RegistryLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        @Override public DimensionDataStorage getDataStorage() { return storage; }
        @Override public long getGameTime() { return 201; }
    }
}
