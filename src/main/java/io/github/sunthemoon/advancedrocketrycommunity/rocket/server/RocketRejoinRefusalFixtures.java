package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;

/** Synchronous command refusals; temporary roots are never installed across a server tick. */
final class RocketRejoinRefusalFixtures {
    @FunctionalInterface interface Command { int run() throws Exception; }
    private RocketRejoinRefusalFixtures() { }

    static void verify(GameTestHelper h, RocketTransferService transfers, RocketTransferSavedData journal,
            RocketRejoinSavedData original, RocketTransferRecord record, RocketEntity rocket, boolean landed, Command command) throws Exception {
        var server = h.getLevel().getServer(); var storage = server.overworld().getDataStorage();
        var savedOwner = transfers.swapReleaseOwnerForGameTest(server, null);
        var directory = Files.createTempDirectory("arce-w160-refusals-"); var file = directory.resolve(RocketRejoinSavedData.TYPE.fileName());
        var snapshot = rocket.snapshot().orElseThrow(); var flight = rocket.flightData().orElseThrow();
        var player = flight.passengers().assignments().get(0).passengerId();
        var version = RocketReleasePlan.version(record);
        try {
            var governed = RocketTransferRecognitionFixtures.governed(snapshot.sourceOrigin());
            var nativeJournal = new RocketTransferSavedData(); nativeJournal.put(governed);
            storage.set(RocketTransferSavedData.DATA_NAME, nativeJournal);
            try {
                var before = nativeJournal.save(new CompoundTag());
                int result = server.getCommands().getDispatcher().execute("arce rocket recover " + governed.transferId() + " release-absent",
                        server.createCommandSourceStack().withSource(net.minecraft.commands.CommandSource.NULL));
                h.assertTrue(result == 0 && before.equals(nativeJournal.save(new CompoundTag())) && original.root().equals(RocketRejoinRoot.empty()),
                        "Governed record admitted or changed");
            } finally {
                var owner = transfers.swapReleaseOwnerForGameTest(server, null); if (owner != null) { owner.close(); }
                storage.set(RocketTransferSavedData.DATA_NAME, journal);
            }
            var journalFile = io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier.dataFile(server,
                    io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.ROCKET_TRANSFERS);
            var retained = directory.resolve("retained-journal.dat");
            boolean present = Files.exists(journalFile);
            if (present) { Files.move(journalFile, retained); }
            storage.set(RocketTransferSavedData.DATA_NAME, null);
            try {
                h.assertTrue(RocketTransferSavedData.existing(server) == null && command.run() == 0, "Missing journal admitted");
                h.assertTrue(!Files.exists(journalFile) && original.root().equals(RocketRejoinRoot.empty()), "Missing journal refusal wrote data");
            } finally {
                if (present) { Files.move(retained, journalFile); }
                storage.set(RocketTransferSavedData.DATA_NAME, journal);
            }
            for (String name : List.of("closed writer", "busy writer", "entry and batch capacity", "conflicting placement", "other release of L", "per-player capacity")) {
                var batches = new ArrayList<Batch>(); var entries = new ArrayList<Entry>();
                int size = name.equals("entry and batch capacity") ? MAX_ENTRIES : name.equals("per-player capacity") ? MAX_PER_PLAYER : 1;
                for (int i = 0; i < size; i++) {
                    UUID release = UUID.randomUUID();
                    UUID rider = name.equals("conflicting placement") || name.equals("per-player capacity") ? player : UUID.randomUUID();
                    var pos = new Position(snapshot.sourceOrigin().x(), snapshot.sourceOrigin().y(), snapshot.sourceOrigin().z());
                    batches.add(new Batch(release, i, UUID.randomUUID(), name.equals("other release of L") ? record.logicalRocketId() : UUID.randomUUID(),
                            UUID.randomUUID(), landed ? Hold.H2 : Hold.H1, snapshot.sourceDimension().toString(), pos, pos, pos,
                            RocketReleasePlan.resources(snapshot, flight), version, version, List.of(rider),
                            List.of(new Slot(snapshot.sourceDimension().toString(), pos.x() >> 4, pos.z() >> 4))));
                    entries.add(new Entry(release, rider, 0, name.equals("per-player capacity") ? Placement.NOT_REQUIRED : Placement.PENDING, Seat.PENDING, null));
                }
                var root = new RocketRejoinRoot(size, batches, entries);
                CheckedSavedDataPolicy.atomic(RocketRejoinSavedData.TYPE).replace(file, () -> RocketRejoinCodec.encode(root));
                try (var data = RocketRejoinSavedData.open(file)) {
                    storage.set(RocketRejoinSavedData.TYPE.dataName(), data);
                    if (name.equals("closed writer")) { data.close(); }
                    if (name.equals("busy writer")) { h.assertTrue(data.submit(root), "Writer fixture did not occupy a generation"); }
                    var before = data.root(); var pending = data.pendingRoots(); var journalBefore = journal.save(new CompoundTag());
                    var entityBefore = new CompoundTag(); rocket.save(entityBefore);
                    h.assertTrue(command.run() == 0, name + " admitted a release");
                    var entityAfter = new CompoundTag(); rocket.save(entityAfter);
                    h.assertTrue(before.equals(data.root()) && pending.equals(data.pendingRoots())
                            && journalBefore.equals(journal.save(new CompoundTag())) && entityBefore.equals(entityAfter), name + " mutated authority");
                } finally {
                    var temporaryOwner = transfers.swapReleaseOwnerForGameTest(server, null);
                    if (temporaryOwner != null) { temporaryOwner.close(); }
                    storage.set(RocketRejoinSavedData.TYPE.dataName(), original);
                }
            }
            // A held bay reaches the real final boundary, without replacing the installed bay coordinator.
            var live = new HashSet<UUID>(); var settled = new HashSet<UUID>();
            (landed ? live : settled).add(record.transferId());
            var recovery = new RocketTransferRecoveryService(live, settled);
            recovery.bays().install(new RocketTransferBayRecoveryGate() {
                public Claim claim(net.minecraft.server.MinecraftServer s, RocketTransferSavedData j, RocketTransferRecord t) {
                    return Claim.held(State.OWNED_WAIT, "W160_TEST");
                }
                public Boundary beforeAction(net.minecraft.server.MinecraftServer s, RocketTransferSavedData j, RocketTransferRecord t, Action a) {
                    return Boundary.held();
                }
            });
            try (var owner = new RocketReleaseAccess.Owner(server, recovery, live, settled)) {
                transfers.swapReleaseOwnerForGameTest(server, owner);
                var before = journal.save(new CompoundTag());
                h.assertTrue(command.run() == 0 && before.equals(journal.save(new CompoundTag())) && original.root().equals(RocketRejoinRoot.empty()),
                        "Transfer bay admitted or mutated a release");
            } finally { transfers.swapReleaseOwnerForGameTest(server, null); }
        } finally {
            storage.set(RocketRejoinSavedData.TYPE.dataName(), original);
            transfers.swapReleaseOwnerForGameTest(server, savedOwner);
            // Only this fixture's newly allocated directory and its exact writer files are removed.
            try (var children = Files.list(directory)) { for (var child : children.toList()) { Files.delete(child); } }
            Files.delete(directory);
        }
    }
}
