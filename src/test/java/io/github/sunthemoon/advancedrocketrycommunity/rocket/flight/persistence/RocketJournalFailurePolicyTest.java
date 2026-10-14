package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketJournalFailurePolicyTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void admissionRefusesEveryNonemptyCandidateButAllowsEmptyBaseline() {
        assertFalse(RocketJournalFailurePolicy.reject("ADMISSION", root(null, 0), null));
        for (var phase : RocketTransferPhase.values()) {
            assertTrue(RocketJournalFailurePolicy.reject("ADMISSION", root(phase, 1), null));
        }
        assertTrue(RocketJournalFailurePolicy.reject("ADMISSION", root(RocketTransferPhase.PREPARED,
                RocketFlightLimits.MAX_ACTIVE_TRANSFERS), null));
    }

    @Test void advancementAllowsPreparationsAtTheLimitButRefusesEveryLaterPhase() {
        assertFalse(RocketJournalFailurePolicy.reject("ADVANCEMENT", root(RocketTransferPhase.PREPARED,
                RocketFlightLimits.MAX_ACTIVE_TRANSFERS), null));
        for (var phase : RocketTransferPhase.values()) {
            assertEquals(phase != RocketTransferPhase.PREPARED,
                    RocketJournalFailurePolicy.reject("ADVANCEMENT", root(phase, 1), null));
        }
    }

    @Test void recoveryCannotDeleteOldPreparedAuthorityThroughTheInjectedFailure() {
        assertFalse(RocketJournalFailurePolicy.reject("ADVANCEMENT", root(null, 0), null));
        assertFalse(RocketJournalFailurePolicy.reject("ADVANCEMENT", root(null, 0), root(null, 0)));
        assertTrue(RocketJournalFailurePolicy.reject("ADVANCEMENT", root(null, 0), root(RocketTransferPhase.PREPARED, 1)));
    }

    @Test void missingLiveServerCannotEnableFaultsEvenWithThePropertiesSet() {
        String old = System.getProperty(RocketJournalFailurePolicy.PROPERTY);
        String oldHooks = System.getProperty(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints.HOOKS);
        try {
            System.setProperty(RocketJournalFailurePolicy.PROPERTY, "ADMISSION");
            System.setProperty(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints.HOOKS, "true");
            assertNotNull(RocketJournalFailurePolicy.checked(null));
        } finally {
            if (old == null) { System.clearProperty(RocketJournalFailurePolicy.PROPERTY); }
            else { System.setProperty(RocketJournalFailurePolicy.PROPERTY, old); }
            if (oldHooks == null) { System.clearProperty(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints.HOOKS); }
            else { System.setProperty(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints.HOOKS, oldHooks); }
        }
        assertThrows(IllegalArgumentException.class, () -> RocketJournalFailurePolicy.reject("other", root(null, 0), null));
    }

    private static CompoundTag root(RocketTransferPhase phase, int count) {
        var root = new CompoundTag(); var records = new ListTag();
        for (int i = 0; i < count; i++) { var record = new CompoundTag(); record.putString("phase", phase.name()); records.add(record); }
        root.put("transfers", records); return root;
    }
}
