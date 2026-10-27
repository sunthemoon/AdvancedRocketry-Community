package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** ADR-074 item 6 (Q2): the codes are persisted by ordinal, so W129c appends its two codes after every earlier one. */
class SatelliteOperationCodeOrderTest {
    @Test
    void theRegistryWaitCodesAreAppendedWithoutShiftingAnyPersistedOrdinal() {
        assertEquals(0, SatelliteOperationCode.SUCCESS.ordinal());
        assertEquals(23, SatelliteOperationCode.SERVER_ERROR.ordinal());
        assertEquals(40, SatelliteOperationCode.OFFLINE_REPAIR_REQUIRED.ordinal());
        assertEquals(41, SatelliteOperationCode.DEPLOYMENT_PENDING.ordinal());
        assertEquals(42, SatelliteOperationCode.REGISTRY_BLOCKED.ordinal());
        assertEquals(43, SatelliteOperationCode.REGISTRY_PENDING.ordinal());
        assertEquals(44, SatelliteOperationCode.REGISTRY_BUSY.ordinal());
        assertEquals(45, SatelliteOperationCode.values().length);
        assertEquals("status.advancedrocketrycommunity.satellite.registry_pending",
                SatelliteOperationCode.REGISTRY_PENDING.translationKey());
    }
}
