package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint.Outcome;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.ReleaseTestServers;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocketI6NativeCutTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void beforeRemovalAndWritebackObserveTheActualPreviousFile() throws Exception {
        for (String mode : new String[] {"PLAIN_MOVE", "K3_MOVE"}) {
            var file = target(mode); var calls = new ArrayList<String>();
            var policy = RocketI6NativeCut.policy(mode, boundary -> {
                calls.add(boundary);
                assertEquals(root(mode.equals("PLAIN_MOVE")), CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
            }, () -> false);
            var checkpoint = new CheckedJournalCheckpoint(CheckedSavedDataPolicy.DirectoryForce.PLATFORM, 64);
            assertEquals(Outcome.COMMITTED, checkpoint.write(file, policy, 1, root(true), Map.of()));
            assertTrue(calls.isEmpty(), "Initial COMMITTED insertion is not M3");
            assertEquals(Outcome.COMMITTED, checkpoint.write(file, policy, 2, root(false), Map.of()));
            assertEquals(Outcome.COMMITTED, checkpoint.write(file, policy, 3, root(true), Map.of()));
            assertEquals(java.util.List.of(mode), calls);
        }
    }

    @Test void unknownPersistsTheSelectedGenerationButNeverAcknowledgesRetries() throws Exception {
        for (String mode : new String[] {"PLAIN_UNKNOWN", "K3_UNKNOWN"}) {
            var file = target(mode); var calls = new ArrayList<String>();
            var policy = RocketI6NativeCut.policy(mode, calls::add, () -> false);
            var checkpoint = new CheckedJournalCheckpoint(CheckedSavedDataPolicy.DirectoryForce.PLATFORM, 64);
            assertEquals(Outcome.COMMITTED, checkpoint.write(file, policy, 1, root(true), Map.of()));
            boolean writeback = mode.equals("K3_UNKNOWN");
            assertEquals(writeback ? Outcome.COMMITTED : Outcome.UNKNOWN,
                    checkpoint.write(file, policy, 2, root(false), Map.of()));
            for (int sequence = 3; sequence < 6; sequence++) {
                assertEquals(Outcome.UNKNOWN, checkpoint.write(file, policy, sequence, root(writeback), Map.of()));
                assertTrue(checkpoint.acknowledged().isEmpty());
                assertEquals(root(writeback), CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
            }
            assertEquals(java.util.List.of(mode), calls);
        }
    }

    @Test void writebackSelectsOnlyTheIdenticalRecordPreviouslyRemoved() throws Exception {
        var file = target("exact"); var calls = new ArrayList<String>();
        var policy = RocketI6NativeCut.policy("K3_MOVE", calls::add, () -> false);
        var checkpoint = new CheckedJournalCheckpoint(CheckedSavedDataPolicy.DirectoryForce.PLATFORM, 64);
        assertEquals(Outcome.COMMITTED, checkpoint.write(file, policy, 1, root(true), Map.of()));
        assertEquals(Outcome.COMMITTED, checkpoint.write(file, policy, 2, root(false), Map.of()));
        var changed = root(true); changed.getList("transfers", 10).getCompound(0).putString("checksum", "changed");
        assertEquals(Outcome.COMMITTED, checkpoint.write(file, policy, 3, changed, Map.of()));
        assertTrue(calls.isEmpty());
    }

    @Test void normalStopFinalFailureLeavesTheRemovedFileNotTheRamWriteback() throws Exception {
        var file = target("stop"); var calls = new ArrayList<String>(); var stopped = new AtomicBoolean();
        var policy = RocketI6NativeCut.policy("K10_FAIL", boundary -> {
            calls.add(boundary);
            if (boundary.equals("K10_REMOVAL_MOVED")) {
                assertEquals(root(false), CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
                stopped.set(true);
            }
        }, stopped::get);
        var checkpoint = new CheckedJournalCheckpoint(CheckedSavedDataPolicy.DirectoryForce.PLATFORM, 64);
        assertEquals(Outcome.COMMITTED, checkpoint.write(file, policy, 1, root(true), Map.of()));
        assertEquals(Outcome.COMMITTED, checkpoint.write(file, policy, 2, root(false), Map.of()));
        for (int sequence = 3; sequence < 6; sequence++) {
            assertEquals(Outcome.NOT_COMMITTED, checkpoint.write(file, policy, sequence, root(true), Map.of()));
            assertEquals(root(false), CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
        }
        assertEquals(1, calls.stream().filter("K10_REMOVAL_MOVED"::equals).count());
        assertEquals(3, calls.stream().filter("K10_FINAL_WRITE_REFUSED"::equals).count());
    }

    @Test void k9RefusesOnlyTheFirstRemovalAndTheRetryIsAnOrdinaryCommit() throws Exception {
        var file = target("late"); var calls = new ArrayList<String>();
        var policy = RocketI6NativeCut.policy("K9_LATE", calls::add, () -> false);
        var checkpoint = new CheckedJournalCheckpoint(CheckedSavedDataPolicy.DirectoryForce.PLATFORM, 64);
        assertEquals(Outcome.COMMITTED, checkpoint.write(file, policy, 1, root(true), Map.of()));
        assertEquals(Outcome.NOT_COMMITTED, checkpoint.write(file, policy, 2, root(false), Map.of()));
        assertEquals(root(true), CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
        assertEquals(Outcome.COMMITTED, checkpoint.write(file, policy, 2, root(false), Map.of()));
        assertEquals(Outcome.COMMITTED, checkpoint.write(file, policy, 3, root(true), Map.of()));
        assertEquals(java.util.List.of("K9_LATE_REFUSED"), calls);
    }

    @Test void selectionRequiresTheExistingEmptyDedicatedServerGate() throws Exception {
        String hooks = System.getProperty(EscrowTestEndpoints.HOOKS), cut = System.getProperty(RocketI6NativeCut.PROPERTY);
        try (var servers = new ReleaseTestServers()) {
            System.setProperty(RocketI6NativeCut.PROPERTY, "PLAIN_MOVE");
            System.setProperty(EscrowTestEndpoints.HOOKS, "false");
            assertEquals("", RocketI6NativeCut.selection(servers.server(directory, false)));
            System.setProperty(EscrowTestEndpoints.HOOKS, "true");
            assertEquals("", RocketI6NativeCut.selection(null));
            assertThrows(IllegalArgumentException.class, () -> RocketI6NativeCut.selection(servers.server(directory, true)));
            var server = servers.server(directory, false);
            assertEquals("PLAIN_MOVE", RocketI6NativeCut.selection(server));
            servers.join(server, UUID.randomUUID());
            assertThrows(IllegalArgumentException.class, () -> RocketI6NativeCut.selection(server));
        } finally { restore(EscrowTestEndpoints.HOOKS, hooks); restore(RocketI6NativeCut.PROPERTY, cut); }
    }

    private Path target(String name) throws java.io.IOException {
        return java.nio.file.Files.createDirectory(directory.resolve(name)).resolve(TYPE.fileName());
    }

    private static void restore(String name, String value) {
        if (value == null) { System.clearProperty(name); } else { System.setProperty(name, value); }
    }
    private static CompoundTag root(boolean present) {
        var root = new CompoundTag(); var records = new ListTag();
        if (present) {
            var record = new CompoundTag(); record.putUUID("transfer_id", new UUID(0, 1));
            record.putUUID("logical_rocket_id", new UUID(0, 2)); record.putString("phase", "COMMITTED");
            record.putString("checksum", "exact"); records.add(record);
        }
        root.put("transfers", records); return root;
    }
}
