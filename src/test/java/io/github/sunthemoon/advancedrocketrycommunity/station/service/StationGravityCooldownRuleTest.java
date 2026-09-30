package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Review F8: committed and failed gravity writes start the cooldown; refusals before a write do not. */
final class StationGravityCooldownRuleTest {
    @Test
    void writesAndFailedWritesStartTheCooldown() {
        Set<StationManagementCode> starting = EnumSet.of(StationManagementCode.GRAVITY_SET,
                StationManagementCode.WRITE_FAILED, StationManagementCode.OUTCOME_UNKNOWN);
        for (StationManagementCode code : StationManagementCode.values()) {
            assertEquals(starting.contains(code), StationGravityService.startsCooldown(code), code.name());
        }
    }
}
