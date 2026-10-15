package io.github.sunthemoon.advancedrocketrycommunity.station.warp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FirstEventStationFixturesTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void acceptsOnlyTheRequestedStationsExplicitClearMotionDiagnostic() {
        UUID station = new UUID(44, 4);
        String prefix = "station=" + station + " orbit=earth balance=4000000 pending=0 cooldown_ready=true";
        assertTrue(FirstEventStationFixtures.motionClear(prefix + " rockets_in_motion=false countdown=false", station));
        assertFalse(FirstEventStationFixtures.motionClear(prefix + " rockets_in_motion=true countdown=false", station));
        assertFalse(FirstEventStationFixtures.motionClear(prefix + " countdown=false", station));
        assertFalse(FirstEventStationFixtures.motionClear(prefix + " rockets_in_motion=false countdown=false", new UUID(44, 5)));
        assertFalse(FirstEventStationFixtures.motionClear("countdown " + prefix + " rockets_in_motion=false countdown=false", station));
    }
}
