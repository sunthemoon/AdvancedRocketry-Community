package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.FirstEventGameTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Production commands with the real checked release, native seat proofs and connected player readback. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketRejoinAdmissionGameTests {
    private RocketRejoinAdmissionGameTests() { }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_command_h1", timeoutTicks = 600)
    public static void twoRidersReleaseAndRejoinThroughTheCommand(GameTestHelper helper) { new Scenario(helper, false).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_command_h2", timeoutTicks = 600)
    public static void landedHoldUsesTheSameCommandAdmission(GameTestHelper helper) { new Scenario(helper, true).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_command_refuse_h1", timeoutTicks = 600)
    public static void sourceHoldRefusalsDoNotMutateAuthority(GameTestHelper helper) { new Scenario(helper, false, true).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_command_refuse_h2", timeoutTicks = 600)
    public static void landedHoldRefusalsDoNotMutateAuthority(GameTestHelper helper) { new Scenario(helper, true, true).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_command_unloaded", timeoutTicks = 600)
    public static void unloadedAuthorityRefusesWithoutLoadingItsSection(GameTestHelper helper) { new Scenario(helper, false, true, true).start(); }

    @GameTest(template = "rocket_test", batch = "rocket_rejoin_command_view")
    public static void viewCapsEightEntriesAndFiltersOfflineUuidWithoutWrites(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer(); var transfers = RocketRuntime.installedTransfers();
        var original = RocketRejoinEvents.get(server); var oldOwner = transfers.swapReleaseOwnerForGameTest(server, null);
        var version = new RocketRejoinRoot.Version("a".repeat(64), "PREPARED", null);
        var ids = java.util.stream.IntStream.range(0, 16).mapToObj(i -> UUID.randomUUID()).toList();
        var release = UUID.randomUUID(); var pos = new RocketRejoinRoot.Position(0, 64, 0);
        var b = new RocketRejoinRoot.Batch(release, 0, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), RocketRejoinRoot.Hold.H1,
                "minecraft:overworld", pos, pos, pos, new RocketRejoinRoot.Resources("b".repeat(64), "c".repeat(64)), version, version, ids,
                List.of(new RocketRejoinRoot.Slot("minecraft:overworld", 0, 0)));
        var root = RocketRejoinRoot.empty().admit(b, ids.stream().map(id -> new RocketRejoinRoot.Entry(release, id, ids.indexOf(id),
                RocketRejoinRoot.Placement.PENDING, RocketRejoinRoot.Seat.PENDING, null)).toList());
        var data = RocketRejoinSavedData.load(RocketRejoinCodec.encode(root)); var output = new Output();
        server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), data);
        try {
            var source = server.createCommandSourceStack().withSource(output); var dispatcher = server.getCommands().getDispatcher();
            helper.assertTrue(dispatcher.execute("arce rocket rejoins", source) == 8 && output.lines.size() == 1,
                    "View did not cap one response at eight entries");
            output.lines.clear();
            helper.assertTrue(dispatcher.execute("arce rocket rejoins " + ids.get(15), source) == 1 && output.lines.size() == 1, "UUID filter did not find a later entry");
            helper.assertTrue(root.equals(data.root()) && !data.isDirty() && data.pendingRoots().isEmpty() && data.completions() == 0,
                    "View changed the sidecar");
            helper.assertTrue(transfers.existingReleaseOwner(server) == null, "View created a release owner");
        } finally {
            data.close(); server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), original);
            transfers.swapReleaseOwnerForGameTest(server, oldOwner);
        }
        helper.succeed();
    }

    private static final class Output implements CommandSource {
        final List<Component> lines = new ArrayList<>();
        public void sendSystemMessage(Component text) { lines.add(text); }
        public boolean acceptsSuccess() { return true; }
        public boolean acceptsFailure() { return true; }
        public boolean shouldInformAdmins() { return false; }
    }
    private static final class Scenario {
        final GameTestHelper helper; final ServerLevel level; final MinecraftServer server;
        final boolean landed, refusalOnly, unloadedOnly; final RocketTransferService transfers; final RocketTransferSavedData journal;
        final GameTestTickPacer pacer; final Output output = new Output();
        final List<UUID> riders = List.of(UUID.randomUUID(), UUID.randomUUID());
        final List<ServerPlayer> players = new ArrayList<>();
        RocketRejoinSavedData original, data; RocketReleaseAccess.Owner originalOwner;
        RocketTransferRecord transfer; RocketEntity rocket; RocketRejoinRoot.Resources resources;
        Path file; int phase, ticks; boolean closed, installed;
        Scenario(GameTestHelper helper, boolean landed) { this(helper, landed, false); }
        Scenario(GameTestHelper helper, boolean landed, boolean refusalOnly) { this(helper, landed, refusalOnly, false); }
        Scenario(GameTestHelper helper, boolean landed, boolean refusalOnly, boolean unloadedOnly) {
            this.helper = helper; this.landed = landed; this.refusalOnly = refusalOnly; this.unloadedOnly = unloadedOnly; level = helper.getLevel(); server = level.getServer();
            transfers = RocketRuntime.installedTransfers(); journal = RocketTransferSavedData.get(server); pacer = new GameTestTickPacer(server);
        }
        void start() { helper.onEachTick(this::tick); }
        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++ticks < 580, "Command release fixture timed out at phase " + phase);
                switch (phase) {
                    case 0 -> setup();
                    case 1 -> admit();
                    case 2 -> rejoin();
                    case 3 -> finish();
                    default -> throw new IllegalStateException("Command fixture phase");
                }
                if (!closed) { pacer.pace(); }
            } catch (Exception failure) { close(); throw new RuntimeException(failure); }
            catch (Error failure) { close(); throw failure; }
        }
        void setup() throws Exception {
            BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 4));
            if (!level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(pos).toLong())) { return; }
            original = RocketRejoinEvents.get(server); file = ManagedRootSupplier.dataFile(server, RocketRejoinSavedData.TYPE);
            helper.assertTrue(original.root().equals(RocketRejoinRoot.empty()) && !Files.exists(file), "Command fixture requires unused sidecar");
            data = RocketRejoinSavedData.open(file);
            server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), data);
            originalOwner = transfers.swapReleaseOwnerForGameTest(server, null); installed = true;
            // All candidate supports fit inside the loaded test chunk; no atmosphere/fluid/hazard relaxation.
            for (int x = -4; x <= 5; x++) { for (int z = -4; z <= 5; z++) {
                level.setBlockAndUpdate(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                for (int y = 0; y < 4; y++) { level.setBlockAndUpdate(pos.offset(x, y, z), Blocks.AIR.defaultBlockState()); }
            } }
            var fixture = RocketSourceRetirementFixtures.countdown(level, pos, pos.offset(1, 0, 0), riders, 0);
            transfer = fixture.record();
            rocket = ModEntities.ROCKET.get().create(level); helper.assertTrue(rocket != null, "Rocket type missing");
            if (landed) {
                transfer = transfer.destinationSpawned(rocket.getUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                        .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
                rocket.initializeTransferred(transfer.destinationSnapshot(), transfer.logicalRocketId(), transfer.ownerId(), transfer.destinationFlightData().land(level.getGameTime()));
            } else {
                rocket.setUUID(transfer.sourceEntityId());
                rocket.initializeTransferred(transfer.sourceSnapshot(), transfer.logicalRocketId(), transfer.ownerId(), RocketTransferEntities.stationarySource(transfer, level.getGameTime()));
            }
            RocketTransferEntities.positionAtOrigin(rocket, rocket.snapshot().orElseThrow().sourceOrigin());
            journal.put(transfer); transfers.holdForGameTest(server, transfer.transferId(), landed, false);
            helper.assertTrue(level.addFreshEntity(rocket), "Authority not added");
            resources = RocketReleasePlan.resources(rocket.snapshot().orElseThrow(), rocket.flightData().orElseThrow()); phase = 1;
        }
        int command(String text) throws Exception {
            output.lines.clear(); return server.getCommands().getDispatcher().execute(text, server.createCommandSourceStack().withSource(output));
        }
        String release() { return "arce rocket recover " + transfer.transferId() + " release-absent"; }
        void admit() throws Exception {
            if (!journal.retryChecked(server)) { return; }
            helper.assertTrue(command("arce rocket rejoins") == 0 && !Files.exists(file) && !data.isDirty(), "Unused view wrote a sidecar");
            var before = RocketRejoinCodec.encode(data.root());
            helper.assertTrue(command("arce rocket recover " + UUID.randomUUID() + " release-absent") == 0, "Unknown transfer admitted");
            helper.assertTrue(before.equals(RocketRejoinCodec.encode(data.root())) && !data.isDirty(), "Refusal changed sidecar");
            if (refusalOnly) {
                if (unloadedOnly) { unloaded(); } else { refusals(); }
                close(); helper.succeed(); return;
            }
            helper.assertTrue(command(release()) == 1 && output.lines.size() == 1, "Release not admitted with one reply");
            var owner = transfers.existingReleaseOwner(server); var id = owner.coordinator.release(transfer.transferId()).orElseThrow();
            helper.assertTrue(command(release()) == 1 && owner.coordinator.release(transfer.transferId()).orElseThrow().equals(id), "Repeated submission changed release");
            helper.assertTrue(command("arce rocket rejoins") == 2 && output.lines.size() == 1, "B0 admission missing from bounded view");
            helper.assertTrue(command("arce rocket rejoins " + riders.get(0)) == 1, "Offline UUID filter missing");
            phase = 2;
        }
        void refused(String name) throws Exception {
            var before = RocketRejoinCodec.encode(data.root()); var record = journal.find(transfer.transferId());
            var image = new net.minecraft.nbt.CompoundTag(); rocket.save(image);
            helper.assertTrue(command(release()) == 0 && output.lines.size() == 1, name + " was not refused once");
            var after = new net.minecraft.nbt.CompoundTag(); rocket.save(after);
            helper.assertTrue(before.equals(RocketRejoinCodec.encode(data.root())) && record.equals(journal.find(transfer.transferId()))
                    && image.equals(after) && data.pendingRoots().isEmpty(), name + " changed state");
        }
        void refusals() throws Exception {
            ready("service refusals");
            var storage = server.overworld().getDataStorage();
            storage.set(RocketRejoinSavedData.TYPE.dataName(), null);
            try { refused("Missing sidecar service"); } finally { storage.set(RocketRejoinSavedData.TYPE.dataName(), data); }
            storage.set(RocketTransferSavedData.DATA_NAME, RocketTransferSavedData.load(new net.minecraft.nbt.CompoundTag()));
            try { refused("Blocked journal"); } finally { storage.set(RocketTransferSavedData.DATA_NAME, journal); }
            transfers.holdForGameTest(server, transfer.transferId(), landed, true);
            try { refused("Neither legacy hold"); } finally { transfers.holdForGameTest(server, transfer.transferId(), landed, false); }
            RocketRejoinRefusalFixtures.verify(helper, transfers, journal, data, transfer, rocket, landed, () -> command(release()));
            ready("authority refusals");
            var flight = rocket.flightData().orElseThrow();
            if (landed) {
                rocket.updateFlightData(flight.withPassengers(RocketPassengerManifest.empty(flight.passengers().seatCapacity())));
                try { refused("Empty absent set"); } finally { rocket.updateFlightData(flight); }
            }
            rocket.updateFlightData(transfer.sourceFlightData());
            try { refused("Authority outside stationary hold"); } finally { rocket.updateFlightData(flight); }
            ready("duplicate authority");
            var duplicate = ModEntities.ROCKET.get().create(level);
            helper.assertTrue(duplicate != null, "Duplicate fixture type missing");
            duplicate.initializeTransferred(rocket.snapshot().orElseThrow(), transfer.logicalRocketId(), transfer.ownerId(), flight);
            duplicate.setPos(rocket.position()); helper.assertTrue(level.addFreshEntity(duplicate), "Duplicate fixture not loaded");
            try { refused("Nonunique authority"); } finally { duplicate.discard(); }
            ready("double seat");
            var other = RocketSourceRetirementFixtures.countdown(level, rocket.blockPosition().offset(3, 0, 0), rocket.blockPosition().offset(4, 0, 0), riders, 0);
            var otherEntity = other.spawn(helper, level, RocketTransferEntities.stationarySource(other.record(), level.getGameTime()), false);
            try { refused("Double seated passenger"); } finally { otherEntity.discard(); }
            journal.put(other.record());
            try { refused("Passenger named by another transfer"); } finally { journal.remove(other.record().transferId()); }
            ready("escrow hold");
            var image = new net.minecraft.nbt.CompoundTag(); rocket.save(image);
            var marked = image.copy();
            marked.getCompound("RocketEntityData").put("resource_escrow", new io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection(
                    UUID.randomUUID(), 0, List.of()).encode());
            rocket.load(marked);
            helper.assertTrue(!io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate.idle(rocket), "Escrow fixture was not held");
            try { refused("Escrow-held authority"); }
            finally {
                rocket.discard(); rocket = ModEntities.ROCKET.get().create(level);
                helper.assertTrue(rocket != null, "Authority restore type missing"); rocket.load(image);
                helper.assertTrue(level.addFreshEntity(rocket), "Authority restore failed");
            }
        }
        void unloaded() throws Exception {
            ready("unloaded authority");
            var slot = new net.minecraft.world.level.ChunkPos(29_000_000 >> 4, 29_000_000 >> 4);
            helper.assertTrue(!level.hasChunk(slot.x, slot.z), "Remote fixture chunk was already loaded");
            rocket.setPos(29_000_000.5, rocket.getY(), 29_000_000.5);
            helper.assertTrue(!RocketTransferEntities.loadedEntity(rocket), "Unloaded authority fixture remained loaded");
            refused("Unloaded authority section");
            helper.assertTrue(!level.hasChunk(slot.x, slot.z), "Command loaded the remote entity section");
        }
        void ready(String name) {
            helper.assertTrue(transfers.releaseOwner(server).world.plan(data.root(), transfer.transferId()) != null,
                    "Baseline is not admissible before " + name);
        }
        void rejoin() throws Exception {
            if (data.root().entries().isEmpty() || data.acknowledgedRoot() == null
                    || data.acknowledgedRoot().entries().stream().anyMatch(e -> e.seat() == RocketRejoinRoot.Seat.PENDING)) { return; }
            helper.assertTrue(data.root().nextSequence() == 1 && data.root().entries().size() == 2, "Release duplicated or lost obligations");
            helper.assertTrue(command(release()) == 1 && data.root().nextSequence() == 1, "Placement-only repeat created a batch");
            for (int i = 0; i < riders.size(); i++) {
                var player = FirstEventGameTestPlayers.join(server, riders.get(i), "W160Rider" + i, level, rocket.blockPosition());
                players.add(player); helper.assertTrue(RocketRejoinProtection.guarded(player), "Released login was not guarded");
            }
            phase = 3;
        }
        void finish() throws Exception {
            if (data.acknowledgedRoot() == null || !data.acknowledgedRoot().entries().isEmpty()
                    || players.stream().anyMatch(RocketRejoinProtection::guarded)) { return; }
            helper.assertTrue(data.root().batches().isEmpty() && data.root().nextSequence() == 1, "Final retirement changed sequence");
            for (var player : players) {
                helper.assertTrue(player.isAlive() && player.getVehicle() == null && !player.getPersistentData().contains("arce_passenger_rejoin"), "Rejoin did not finish cleanly");
            }
            helper.assertTrue(resources.equals(RocketReleasePlan.resources(rocket.snapshot().orElseThrow(), rocket.flightData().orElseThrow())), "Release changed resources");
            helper.assertTrue(command("arce rocket rejoins") == 0, "Completed view retained an entry");
            close(); helper.succeed();
        }
        void close() {
            if (closed) { return; } closed = true;
            for (var player : players) { if (server.getPlayerList().getPlayer(player.getUUID()) == player) { server.getPlayerList().remove(player); } }
            if (rocket != null) { rocket.discard(); }
            if (installed) {
                var owner = transfers.swapReleaseOwnerForGameTest(server, originalOwner); if (owner != null) { owner.close(); }
                data.close(); server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), original);
            }
            if (transfer != null) {
                journal.remove(transfer.transferId()); transfers.holdForGameTest(server, transfer.transferId(), landed, true);
                RocketTransferEntities.releaseFlightTickets(server, transfer);
            }
            if (file != null && installed) {
                try { Files.deleteIfExists(file); } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        }
    }
}
