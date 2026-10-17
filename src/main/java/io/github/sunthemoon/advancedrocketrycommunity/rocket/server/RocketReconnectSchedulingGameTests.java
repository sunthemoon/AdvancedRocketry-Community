package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.FirstEventGameTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalSchedulingGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** G6: recovery and login use the passenger's record, including a pending publication for that same record. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketReconnectSchedulingGameTests {
    private RocketReconnectSchedulingGameTests() { }
    @GameTest(template = "rocket_test", batch = "journal_concurrent_flight", timeoutTicks = 250)
    public static void acknowledgedArrivalRecoversAndReconnectsWhileADisjointWriteIsHeld(GameTestHelper helper) {
        new Scenario(helper, false).start();
    }
    @GameTest(template = "rocket_test", batch = "journal_concurrent_flight", timeoutTicks = 250)
    public static void reconnectWaitsForItsQueuedSpawnWithoutRecoveringTheSource(GameTestHelper helper) {
        new Scenario(helper, true).start();
    }

    private static final class Scenario {
        final GameTestHelper helper;
        final boolean pending;
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final RocketTransferService service = new RocketTransferService();
        final HashSet<UUID> live = new HashSet<>(), settled = new HashSet<>();
        final RocketTransferRecoveryService recovery = new RocketTransferRecoveryService(live, settled);
        final GameTestTickPacer pacer;
        RocketJournalSchedulingGameTestSupport.HeldWrite held;
        RocketEntity source, destination;
        RocketTransferRecord record, other;
        ServerPlayer player;
        Path directory, file;
        int stage, waits, heldTicks;
        boolean closed;
        Scenario(GameTestHelper helper, boolean pending) {
            this.helper = helper; this.pending = pending; pacer = new GameTestTickPacer(helper.getLevel().getServer());
        }
        void start() { helper.onEachTick(this::tick); }
        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < 220, "Reconnect fixture exceeded its bounded wait");
                var server = helper.getLevel().getServer();
                if (stage == 0) { setup(); }
                else if (stage == 1 && held.entered()) {
                    if (pending) {
                        service.spawnDestination(server, journal, record, source);
                        helper.assertTrue(journal.find(record.transferId()).orElseThrow().phase() == RocketTransferPhase.DESTINATION_SPAWNED,
                                "Own spawn did not queue");
                        recovery.onPlayerLoggedIn(player, journal); stage = 2;
                    } else {
                        recovery.recoverNext(server, journal);
                        helper.assertTrue(settled.contains(record.transferId()) && !journal.clean(),
                                "Unclassified acknowledged arrival did not recover during disjoint IO");
                        player.stopRiding(); recovery.onPlayerLoggedIn(player, journal);
                        helper.assertTrue(player.getVehicle() == destination && !journal.clean(),
                                "Passenger reconnect waited for another rocket's write");
                        helper.assertTrue(RocketTransferFenceGameTests.flightTickets(server, other.transferId()) == 0,
                                "Recovery loaded the unacknowledged other record");
                        close(); helper.succeed();
                    }
                } else if (stage == 2) {
                    journal.pollWriter(server.getTickCount()); recovery.recoverNext(server, journal); recovery.tickReconnects(server, journal);
                    helper.assertTrue(player.getVehicle() == null && source.isAlive()
                            && source.flightData().orElseThrow().state() == RocketFlightState.TRANSIT
                            && journal.outstandingEffectCount() == 1,
                            "Pending publication reconnected to or recovered the source");
                    helper.assertTrue(RocketTransferFenceGameTests.flightTickets(server, record.transferId()) == 0
                            && RocketTransferFenceGameTests.flightTickets(server, other.transferId()) == 0,
                            "An unacknowledged record received a recovery ticket");
                    if (++heldTicks == 5) { held.close(); stage = 3; }
                } else if (stage == 3) {
                    journal.pollWriter(server.getTickCount());
                    if (journal.clean()) {
                        recovery.tickReconnects(server, journal);
                        var spawned = journal.find(record.transferId()).orElseThrow();
                        destination = (RocketEntity) helper.getLevel().getEntity(spawned.destinationEntityId().orElseThrow());
                        helper.assertTrue(destination != null && player.getVehicle() == destination && source.isAlive()
                                && source.flightData().orElseThrow().state() == RocketFlightState.TRANSIT
                                && RocketTransferEntities.loadedMatches(server, spawned, true).size() == 1,
                                "Post-publication reconnect did not use the unique destination");
                        close(); helper.succeed();
                    }
                }
                if (!closed) { pacer.pace(); }
            } catch (IOException failure) { close(); throw new java.io.UncheckedIOException(failure); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        void setup() throws IOException {
            var level = helper.getLevel(); var origin = helper.absolutePos(new BlockPos(3, 2, 3));
            var target = helper.absolutePos(new BlockPos(14, 2, 3));
            if (!level.isPositionEntityTicking(origin) || !level.isPositionEntityTicking(target)) { return; }
            directory = Files.createTempDirectory("arce-reconnect-schedule-");
            file = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
            player = FirstEventGameTestPlayers.join(level.getServer(), UUID.randomUUID(), "JournalLogin", level, origin.above(2));
            long now = level.getGameTime(); UUID logical = UUID.randomUUID(), transfer = UUID.randomUUID(), owner = UUID.randomUUID();
            var propulsion = new RocketPropulsion(RocketPropulsion.Mode.ABSTRACT, 1_000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS);
            var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(), position(origin),
                    List.of(new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of()))),
                    List.of(new RocketPosition(0, 0, 0)), new RocketStats(1, 100, 1_000, 1_000, 1, 1, 1, 0), now, propulsion);
            var arrivalSnapshot = snapshot.relocated(UUID.randomUUID(), level.dimension().location(), position(target), now);
            var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    level.dimension().location(), level.dimension().location(), 500, now);
            var fuel = RocketFuelState.empty(propulsion).fill(1_000).state();
            var flight = RocketFlightData.initial(logical, 1_000, 1, plan.sourceBody(), plan.sourceDimension(), position(origin), now).withFuel(fuel, now);
            flight = flight.withPassengers(flight.passengers().assign(player.getUUID()).orElseThrow()).withPlan(plan)
                    .startCountdown(now).completeCountdown(now).beginTransit(transfer, now);
            var arrival = flight.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(), plan.destinationDimension(), position(target), now);
            source = ModEntities.ROCKET.get().create(level); helper.assertTrue(source != null, "No source fixture");
            record = RocketTransferRecord.create(transfer, logical, owner, source.getUUID(), snapshot, arrivalSnapshot, flight, arrival, 500, now);
            if (pending) {
                source.initializeTransferred(snapshot, logical, owner, flight); RocketTransferEntities.positionAtAltitude(source, position(origin));
                helper.assertTrue(level.addFreshEntity(source), "Source was not published");
            } else {
                destination = ModEntities.ROCKET.get().create(level); helper.assertTrue(destination != null, "No destination fixture");
                destination.initializeTransferred(arrivalSnapshot, logical, owner, arrival.land(now));
                RocketTransferEntities.positionAtOrigin(destination, position(target));
                helper.assertTrue(level.addFreshEntity(destination), "Arrival was not published");
                record = record.destinationSpawned(destination.getUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                        .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
            }
            other = RocketFailBackAdmissionGameTests.record(helper, UUID.randomUUID(), owner, UUID.randomUUID(), null, origin, 64);
            journal.put(record); journal.put(other); RocketJournalGameTestSupport.confirmDetached(helper, journal, file);
            journal.put(other.destinationSpawned(UUID.randomUUID()));
            held = RocketJournalSchedulingGameTestSupport.hold(helper, journal, file); stage = 1;
        }
        static RocketPosition position(BlockPos pos) { return new RocketPosition(pos.getX(), pos.getY(), pos.getZ()); }
        void close() {
            if (closed) { return; } closed = true; if (held != null) { held.close(); }
            journal.abort(); service.clear(); recovery.clear();
            if (player != null) { player.stopRiding(); helper.getLevel().getServer().getPlayerList().remove(player); }
            if (source != null) { source.discard(); }
            for (var entry : journal.entries()) {
                entry.destinationEntityId().map(helper.getLevel()::getEntity).ifPresent(net.minecraft.world.entity.Entity::discard);
                RocketTransferEntities.releaseFlightTickets(helper.getLevel().getServer(), entry);
            }
            if (directory != null) {
                try (var paths = Files.walk(directory)) {
                    for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) { Files.delete(path); }
                } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        }
    }
}
