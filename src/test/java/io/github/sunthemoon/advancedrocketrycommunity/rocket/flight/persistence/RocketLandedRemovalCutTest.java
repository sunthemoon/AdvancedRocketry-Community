package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint.Outcome;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocketLandedRemovalCutTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void unknownRemovalReplacesDiskButCannotAcknowledgeEvenOnEmptyRootRetries() {
        var file = directory.resolve(TYPE.fileName());
        var checkpoint = new CheckedJournalCheckpoint(CheckedSavedDataPolicy.DirectoryForce.PLATFORM, 64);
        var policy = RocketLandedRemovalCut.policy(true, () -> fail("UNKNOWN must not run the MOVED pause"));
        assertEquals(Outcome.COMMITTED, checkpoint.write(file, policy, 1, root("COMMITTED"), Map.of()));
        assertTrue(checkpoint.current(1));
        for (int sequence = 2; sequence <= 4; sequence++) {
            assertEquals(Outcome.UNKNOWN, checkpoint.write(file, policy, sequence, root(null), Map.of()));
            assertTrue(checkpoint.acknowledged().isEmpty());
            assertEquals(root(null), CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
        }
        // A fresh process without the selected start-only fault can acknowledge the actual empty journal.
        assertEquals(Outcome.COMMITTED, checkpoint.write(file, CheckedSavedDataPolicy.atomic(TYPE), 5, root(null), Map.of()));
        assertTrue(checkpoint.current(5));
    }

    @Test void movedPauseRunsOnlyAfterTheCommittedRecordDisappearsWithoutChangingPayloads() {
        var file = directory.resolve(TYPE.fileName());
        var checkpoint = new CheckedJournalCheckpoint(CheckedSavedDataPolicy.DirectoryForce.PLATFORM, 64);
        var calls = new AtomicInteger();
        var policy = RocketLandedRemovalCut.policy(false, () -> {
            assertEquals(root(null), CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
            calls.incrementAndGet();
        });
        var committed = root("COMMITTED");
        assertEquals(Outcome.COMMITTED, checkpoint.write(file, policy, 1, root("PREPARED"), Map.of()));
        assertEquals(Outcome.COMMITTED, checkpoint.write(file, policy, 2, root(null), Map.of()));
        assertEquals(0, calls.get());
        assertEquals(Outcome.COMMITTED, checkpoint.write(file, policy, 3, committed, Map.of()));
        assertEquals(Outcome.COMMITTED, checkpoint.write(file, policy, 4, committed.copy(), Map.of()));
        assertEquals(0, calls.get());
        assertEquals(Outcome.COMMITTED, checkpoint.write(file, policy, 5, root(null), Map.of()));
        assertEquals(Outcome.COMMITTED, checkpoint.write(file, policy, 6, root(null), Map.of()));
        assertEquals(1, calls.get());
        assertEquals(root("COMMITTED"), committed);
    }

    @Test void absentServerCannotEnableTheCutWithAnyReleaseCaseOrHookSetting() {
        String hooks = System.getProperty(EscrowTestEndpoints.HOOKS);
        String cut = System.getProperty("advancedrocketrycommunity.escrowCase");
        String scenario = System.getProperty("advancedrocketrycommunity.escrowScenario");
        try {
            for (String gate : new String[] {"false", "true"}) {
                System.setProperty(EscrowTestEndpoints.HOOKS, gate);
                for (String name : new String[] {"LF_M_R52_MOVED", "LF_M_R52_UNKNOWN", "LF_M_NAMED_E5"}) {
                    System.setProperty("advancedrocketrycommunity.escrowCase", name);
                    for (String phase : new String[] {"start", "recover"}) {
                        System.setProperty("advancedrocketrycommunity.escrowScenario", phase);
                        assertTrue(RocketLandedRemovalCut.checked(null).isEmpty());
                    }
                }
            }
        } finally {
            restore(EscrowTestEndpoints.HOOKS, hooks);
            restore("advancedrocketrycommunity.escrowCase", cut);
            restore("advancedrocketrycommunity.escrowScenario", scenario);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); }
    }

    private static CompoundTag root(String phase) {
        var root = new CompoundTag(); var records = new ListTag();
        if (phase != null) {
            var record = new CompoundTag(); record.putUUID("transfer_id", new UUID(0, 1));
            record.putString("phase", phase); records.add(record);
        }
        root.put("transfers", records); return root;
    }
}
