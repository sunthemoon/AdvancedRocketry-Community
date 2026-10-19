package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.FirstEventGameTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W96-R2: a passenger who logs out during COUNTDOWN and back in during ASCENT is remounted on the live flight.
 * The reconnect must not retire the live record; the flight continues under it.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketLiveReconnectGameTests {
    private RocketLiveReconnectGameTests() { }

    @GameTest(template = "rocket_test", batch = "ordinary_source_retirement", timeoutTicks = 300)
    public static void reconnectDuringAscentKeepsTheLiveRecordAndTheFlightContinues(GameTestHelper helper) {
        new Scenario(helper).start();
    }

    private static final class Scenario {
        final GameTestHelper helper;
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final RocketTransferService service = new RocketTransferService();
        final GameTestTickPacer pacer;
        final UUID passenger = UUID.randomUUID();
        RocketSourceRetirementFixtures.Flight flight;
        RocketTransferRecord record;
        RocketEntity source;
        ServerPlayer player;
        Path directory;
        int stage, waits, ascentTicks;
        double reconnectY;
        boolean closed;

        Scenario(GameTestHelper helper) { this.helper = helper; pacer = new GameTestTickPacer(helper.getLevel().getServer()); }

        void start() { helper.onEachTick(this::tick); }

        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < 250, "Live reconnect fixture exceeded its bounded wait");
                var server = helper.getLevel().getServer();
                if (stage == 0) { setup(); }
                else {
                    journal.pollWriter(server.getTickCount());
                    var current = journal.find(record.transferId()).orElse(null);
                    helper.assertTrue(current == record, "The live flight's record was removed or replaced");
                    helper.assertTrue(RocketSourceRetirementFixtures.live(service).contains(record.transferId()),
                            "The live flight left forward dispatch");
                    service.tickLive(server, journal, record);
                    var state = source.flightData().orElseThrow().state();
                    if (stage == 1 && state == RocketFlightState.ASCENT) {
                        player = FirstEventGameTestPlayers.join(server, passenger, "R2Passenger", helper.getLevel(),
                                helper.absolutePos(new BlockPos(3, 2, 6)));
                        RocketSourceRetirementFixtures.recovery(service).onPlayerLoggedIn(player, journal);
                        helper.assertTrue(journal.find(record.transferId()).orElse(null) == record,
                                "Reconnect during ASCENT removed the live flight's record (W96-R2)");
                        helper.assertTrue(player.getVehicle() == source, "Reconnect did not remount the passenger on the live flight");
                        reconnectY = source.getY(); stage = 2;
                    } else if (stage == 2) {
                        helper.assertTrue(state == RocketFlightState.ASCENT || state == RocketFlightState.TRANSIT,
                                "The flight did not continue after the reconnect: " + state);
                        if (++ascentTicks == 10) {
                            helper.assertTrue(source.getY() > reconnectY && player.getVehicle() == source,
                                    "The flight did not keep climbing with its passenger");
                            close(); helper.succeed(); return;
                        }
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
            var server = level.getServer();
            directory = Files.createTempDirectory("arce-w98-r2-");
            // The countdown is two ticks from completion, so ASCENT starts promptly.
            flight = RocketSourceRetirementFixtures.countdown(helper, origin, target, List.of(passenger),
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits.COUNTDOWN_TICKS - 2);
            record = flight.record();
            player = FirstEventGameTestPlayers.join(server, passenger, "R2Passenger", level, origin.above(2));
            source = flight.spawnCountdown(helper);
            helper.assertTrue(player.startRiding(source, true), "The passenger did not board during COUNTDOWN");
            journal.put(record);
            RocketJournalGameTestSupport.confirmDetached(helper, journal, directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
            RocketSourceRetirementFixtures.live(service).add(record.transferId());
            // Logging out during COUNTDOWN dismounts the passenger; the seat stays in the manifest.
            server.getPlayerList().remove(player);
            service.onPlayerLoggedOut(passenger);
            helper.assertTrue(source.isAlive() && source.getPassengers().isEmpty()
                    && source.flightData().orElseThrow().passengers().assignment(passenger).isPresent(),
                    "Logout did not leave the rocket alive with the passenger's seat assigned");
            player = null; stage = 1;
        }

        void close() {
            if (closed) { return; } closed = true;
            journal.abort(); service.clear();
            var server = helper.getLevel().getServer();
            if (player != null) { player.stopRiding(); server.getPlayerList().remove(player); }
            if (source != null) { source.discard(); }
            if (record != null) { RocketTransferEntities.releaseFlightTickets(server, record); }
            if (directory != null) {
                try (var paths = Files.walk(directory)) {
                    for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) { Files.delete(path); }
                } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        }
    }
}
