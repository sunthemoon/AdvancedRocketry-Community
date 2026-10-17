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

    /** W85 K9 and K1: only a write that drops a COMMITTED record still on disk is a landed removal. */
    @Test void landedRemovalRefusesOnlyTheDropOfACommittedRecordOnDisk() {
        var landed = withId(root(RocketTransferPhase.COMMITTED, 1), 0);
        var prepared = withId(root(RocketTransferPhase.PREPARED, 1), 0);
        assertTrue(RocketJournalFailurePolicy.reject("LANDED_REMOVAL", root(null, 0), landed));
        assertTrue(RocketJournalFailurePolicy.landedRemoval(withId(root(RocketTransferPhase.PREPARED, 1), 1), landed));
        assertFalse(RocketJournalFailurePolicy.reject("LANDED_REMOVAL", landed, landed), "A write-back keeps T");
        assertFalse(RocketJournalFailurePolicy.reject("LANDED_REMOVAL", root(null, 0), null), "No previous file");
        assertFalse(RocketJournalFailurePolicy.reject("LANDED_REMOVAL", root(null, 0), prepared), "Not a landed record");
        assertFalse(RocketJournalFailurePolicy.reject("LANDED_REMOVAL", landed, root(null, 0)), "An admission");
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

    private static CompoundTag withId(CompoundTag root, long id) {
        root.getList("transfers", 10).getCompound(0).putUUID("transfer_id", new java.util.UUID(0x85L, id));
        return root;
    }

    private static CompoundTag root(RocketTransferPhase phase, int count) {
        var root = new CompoundTag(); var records = new ListTag();
        for (int i = 0; i < count; i++) { var record = new CompoundTag(); record.putString("phase", phase.name()); records.add(record); }
        root.put("transfers", records); return root;
    }
}
