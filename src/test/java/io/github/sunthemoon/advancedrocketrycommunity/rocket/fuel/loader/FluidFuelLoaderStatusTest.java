package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class FluidFuelLoaderStatusTest {
    /** Design section 3's members in its order: the refusals, then the progress members. The menu sends the ordinal. */
    @Test void membersKeepTheDesignOrder() {
        assertEquals(List.of("ADMISSION_DISABLED", "ESCROW_FULL", "LEDGER_UNAVAILABLE", "TRANSFER_JOURNAL_UNPROVED",
                        "ESCROW_PENDING", "UNBOUND", "STALE_BINDING", "WAITING_UNLOADED", "OUT_OF_RANGE", "NOT_OWNER",
                        "ROCKET_BUSY", "EMPTY", "BANK_FULL", "INCOMPATIBLE_FLUID", "AMBIGUOUS_BANK", "UNSUPPORTED_DATA",
                        "INVALID_DATA", "READY", "PREPARING", "TRANSFERRING"),
                Arrays.stream(FluidFuelLoaderStatus.values()).map(Enum::name).toList());
    }

    @Test void onlyReadyPreparingAndTransferringAreProgress() {
        for (FluidFuelLoaderStatus status : FluidFuelLoaderStatus.values()) {
            assertEquals(!List.of("READY", "PREPARING", "TRANSFERRING").contains(status.name()), status.refusal(), status.name());
        }
        assertEquals(17, Arrays.stream(FluidFuelLoaderStatus.values()).filter(FluidFuelLoaderStatus::refusal).count());
    }

    @Test void everyMemberHasItsOwnLowercaseMessageKey() {
        assertEquals("message.advancedrocketrycommunity.fluid_fuel_loader.escrow_full",
                FluidFuelLoaderStatus.ESCROW_FULL.translationKey());
        assertEquals("message.advancedrocketrycommunity.fluid_fuel_loader.transfer_journal_unproved",
                FluidFuelLoaderStatus.TRANSFER_JOURNAL_UNPROVED.translationKey());
        var keys = new HashSet<String>();
        for (FluidFuelLoaderStatus status : FluidFuelLoaderStatus.values()) {
            String key = status.translationKey();
            assertTrue(key.matches("message\\.advancedrocketrycommunity\\.fluid_fuel_loader\\.[a-z_]+"), key);
            assertEquals(FluidFuelLoaderStatus.KEY_PREFIX + status.name().toLowerCase(java.util.Locale.ROOT), key);
            assertTrue(keys.add(key), key);
        }
    }
}
