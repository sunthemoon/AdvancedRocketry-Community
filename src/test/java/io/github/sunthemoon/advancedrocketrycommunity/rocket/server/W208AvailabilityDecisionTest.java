package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class W208AvailabilityDecisionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void recoveryDoesNotWaitForStartOnlyTraffic() {
        assertTrue(W208NativeAvailability.trafficObserved("near-traffic", "recover1", 0, null));
        assertTrue(W208NativeAvailability.trafficObserved("near-traffic", "recover2", 0, null));
    }

    @Test void startStillRequiresTheBoundedTrafficOrAnOperationOutcome() {
        assertFalse(W208NativeAvailability.trafficObserved("near-traffic", "start", 19, null));
        assertTrue(W208NativeAvailability.trafficObserved("near-traffic", "start", 20, null));
        assertTrue(W208NativeAvailability.trafficObserved("near-traffic", "start", 0, "ROLLBACK_PROVED"));
        assertTrue(W208NativeAvailability.trafficObserved("near-quiet", "start", 0, null));
    }
}
