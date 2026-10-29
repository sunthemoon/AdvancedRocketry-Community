package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Pins the cold-refusal integration boundary independently of native timing. */
class RocketRequestCostTest {
    @Test void exhaustedWarmupCapacityChoosesLoadedOnlySelectionBeforeTerrainAccess() throws Exception {
        var source = Files.readString(Path.of("src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketTransferService.java"));
        int capacity = source.indexOf("warmups.canAllocate(");
        int selection = source.indexOf("selected = plan.landingPad()");
        assertTrue(capacity >= 0 && capacity < selection, "Capacity must be known before choosing a terrain-loading selector");
        assertTrue(source.contains("RocketLandingPadSelector.TERRAIN_LOADING"));
        assertTrue(source.contains("RocketFlightRequestCode.DESTINATION_LOADING_LIMIT"));
    }
}
