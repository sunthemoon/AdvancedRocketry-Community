package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowGovernedReturnTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void pendingReturnsIncludeCheckpointsAndRefuseOneBeyondTheFlightLimit() {
        var pending = new HashMap<UUID, Boolean>();
        for (int index = 0; index < RocketFlightLimits.MAX_ACTIVE_TRANSFERS; index++) {
            var id = new UUID(0, index);
            EscrowGovernedReturn.requireCapacity(pending, id); pending.put(id, index % 2 == 0);
        }
        assertEquals(64, pending.size());
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedReturn.requireCapacity(pending, UUID.randomUUID()));
        assertDoesNotThrow(() -> EscrowGovernedReturn.requireCapacity(pending, new UUID(0, 0)));
        pending.remove(new UUID(0, 0));
        assertDoesNotThrow(() -> EscrowGovernedReturn.requireCapacity(pending, UUID.randomUUID()));
    }
}
