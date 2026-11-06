package io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteMissionRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SatelliteTerminalMenuStatusTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void discoveryCompletionClearsTheMissionAndSupersedesOnlyTheHistoricalDiscoveryWait() {
        var registry = SatelliteMissionRegistry.create(1_000);
        var satellite = UUID.randomUUID();
        var mission = UUID.randomUUID();
        var owner = UUID.randomUUID();
        var definition = new SatelliteDefinition(1, ModIdentity.id("data_satellite"), 200, 120, 100,
                List.of(ModIdentity.id("earth"), ModIdentity.id("moon")));
        assertEquals(SatelliteOperationCode.SUCCESS, registry.launch(satellite, mission, owner, definition,
                ModIdentity.id("moon"), 1_000, true).code());
        registry.completeDue(1_200);
        assertEquals(SatelliteOperationCode.PENDING_DISCOVERY, registry.claim(mission, owner, 1_201).code());
        assertEquals(MissionStatus.CLAIM_PENDING_DISCOVERY, registry.mission(mission).orElseThrow().status());
        var pending = registry.satellite(satellite);
        assertTrue(pending.orElseThrow().currentMissionId().isPresent());
        assertEquals(SatelliteOperationCode.SUCCESS, registry.finishDiscovery(mission).code());
        var finished = registry.satellite(satellite);
        assertTrue(finished.orElseThrow().currentMissionId().isEmpty());
        for (var result : SatelliteOperationCode.values()) {
            assertEquals(result.ordinal(), SatelliteTerminalMenuData.displayResult(result.ordinal(), pending));
            assertEquals(result == SatelliteOperationCode.PENDING_DISCOVERY ? SatelliteOperationCode.SUCCESS.ordinal()
                    : result.ordinal(), SatelliteTerminalMenuData.displayResult(result.ordinal(), finished));
            assertEquals(result.ordinal(), SatelliteTerminalMenuData.displayResult(result.ordinal(), Optional.empty()));
        }
    }
}
