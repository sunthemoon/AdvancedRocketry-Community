package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.FirstEventGameTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W98 (P1-N3 leaf N3-A1): every ordinary source return keeps its record until the survivor's own entity slot is on disk.
 * Each case checks, at the tick the record first leaves RAM, that the saved slot already holds the stationary survivor.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketSourceRetirementGameTests {
    private static final String BATCH = "ordinary_source_retirement";
    private RocketSourceRetirementGameTests() { }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 400)
    public static void uncrewedCountdownCancellationRemovesTheRecordOnlyAfterItsSurvivorIsSaved(GameTestHelper helper) {
        new Case(helper) {
            void act() {
                source = flight.spawnCountdown(helper); prepare(true);
                helper.assertTrue(service.failBackToSource(server(), journal, record, source, RocketTransferReturnReason.COUNTDOWN_CANCELLED,
                        completion()), "Countdown cancellation was refused");
                assertReturnedAndHeld(true);
            }
        }.start();
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 400)
    public static void crewedCountdownCancellationRemountsTheCrewAndAnOfflineSeatDoesNotBlock(GameTestHelper helper) {
        UUID aboard = UUID.randomUUID(), offline = UUID.randomUUID();
        new Case(helper, List.of(aboard, offline)) {
            ServerPlayer player;
            void act() {
                source = flight.spawnCountdown(helper); prepare(true);
                player = join(aboard, origin.above(2));
                helper.assertTrue(player.startRiding(source, true), "The online passenger did not board");
                helper.assertTrue(service.failBackToSource(server(), journal, record, source, RocketTransferReturnReason.COUNTDOWN_CANCELLED,
                        completion()), "Crewed countdown cancellation was refused");
                assertReturnedAndHeld(true);
                helper.assertTrue(player.getVehicle() == source, "The online passenger was not remounted on the returned rocket");
            }
            void retired() {
                helper.assertTrue(player.getVehicle() == source || player.getVehicle() == null, "The passenger left the survivor's placement");
                helper.assertTrue(source.flightData().orElseThrow().passengers().assignment(offline).isPresent(),
                        "The offline passenger's seat was dropped");
            }
        }.start();
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 400)
    public static void blockedPadFailBackWaitsForItsOfflinePassengerUntilTheReconnect(GameTestHelper helper) {
        UUID passenger = UUID.randomUUID();
        new Case(helper, List.of(passenger)) {
            BlockState previous; ServerPlayer player; int held;
            void act() {
                previous = level().getBlockState(target);
                level().setBlock(target, Blocks.BEDROCK.defaultBlockState(), 3);
                source = flight.spawnInFlight(helper, level()); prepare(true);
                completion = false;
            }
            boolean before() {
                if (source.flightData().orElseThrow().state() == RocketFlightState.TRANSIT) {
                    service.spawnDestination(server(), journal, record, source);
                    if (source.flightData().orElseThrow().state() != RocketFlightState.TRANSIT) { assertReturnedAndHeld(true); }
                    return false;
                }
                if (held < 60) {
                    // End of transit: the offline seat keeps T, settled, with no reservation held (W96-R1, R3).
                    helper.assertTrue(journal.find(record.transferId()).orElse(null) == record && journal.actionable(record.logicalRocketId())
                            && journal.clean(), "A waiting return held its reservation or lost its record");
                    if (++held == 60) {
                        player = join(passenger, origin.above(4));
                        RocketSourceRetirementFixtures.recovery(service).onPlayerLoggedIn(player, journal);
                        helper.assertTrue(player.getVehicle() == source, "The reconnect did not place the passenger on the survivor");
                    }
                    return false;
                }
                return true;
            }
            void retired() { helper.assertTrue(player.getVehicle() == source, "The reconnected passenger left the survivor"); }
            void restore() { if (previous != null) { level().setBlock(target, previous, 3); } }
        }.start();
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 400)
    public static void keepSourceRecoveryRetiresAnInFlightImageOnlyAfterTheReturnIsSaved(GameTestHelper helper) {
        new Recovery(helper, RocketTransferRecoveryAction.KEEP_SOURCE, false).start();
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 400)
    public static void removeDestinationKeepSourceAlsoProvesTheDiscardedCopyAbsent(GameTestHelper helper) {
        new Recovery(helper, RocketTransferRecoveryAction.REMOVE_DESTINATION_KEEP_SOURCE, false).start();
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 400)
    public static void rebuildSourceRetiresOnlyAfterTheRebuiltRocketIsSaved(GameTestHelper helper) {
        new Recovery(helper, RocketTransferRecoveryAction.REBUILD_SOURCE, false).start();
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 400)
    public static void recoveredSourceWaitsForItsOfflinePassengerAndTheLastReconnectRetiresIt(GameTestHelper helper) {
        new Recovery(helper, RocketTransferRecoveryAction.KEEP_SOURCE, true).start();
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 400)
    public static void publicationRefusalReturnsTheCurrentDestinationSpawnedRecord(GameTestHelper helper) {
        new Case(helper) {
            RocketTransferRecord prepared;
            void act() {
                source = flight.spawnInFlight(helper, level()); prepare(true);
                prepared = record; record = record.destinationSpawned(UUID.randomUUID());
                journal.put(record); confirm();
                helper.assertTrue(service.failBackToSource(server(), journal, prepared, source,
                        RocketTransferReturnReason.DESTINATION_ENTITY_SPAWN_FAILED, completion()), "Publication refusal did not return");
                assertReturnedAndHeld(true);
            }
        }.start();
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 400)
    public static void aSaveOffLevelHoldsTheReturnWithoutAReservationUntilSavingResumes(GameTestHelper helper) {
        new Case(helper) {
            ServerLevel space; ChunkPos forced; boolean wasOff; int held;
            ServerLevel level() { return space; }
            boolean ready() {
                if (space == null) {
                    space = helper.getLevel().getServer().getLevel(CelestialIds.SPACE_LEVEL);
                    helper.assertTrue(space != null, "No Space Level");
                    origin = new BlockPos(2056, 80, 2056); target = new BlockPos(2056, 80, 2067);
                    forced = new ChunkPos(origin);
                    space.getChunkSource().addRegionTicket(TicketType.FORCED, forced, 2, forced);
                }
                return space.isPositionEntityTicking(origin) && space.isPositionEntityTicking(target);
            }
            void act() {
                source = flight.spawn(helper, space, flight.countdown(), false); prepare(true);
                wasOff = space.noSave; space.noSave = true;
                helper.assertTrue(service.failBackToSource(server(), journal, record, source, RocketTransferReturnReason.COUNTDOWN_CANCELLED,
                        completion()), "Countdown cancellation was refused");
                assertReturnedAndHeld(true);
            }
            boolean before() {
                if (held++ < 40) {
                    if (held > 2) {
                        helper.assertTrue(journal.find(record.transferId()).orElse(null) == record && journal.actionable(record.logicalRocketId())
                                && journal.clean() && accepted.get() == 0, "Save-off removed the record or kept a reservation (W96-R1, R5(d))");
                    }
                    return false;
                }
                space.noSave = wasOff;
                return true;
            }
            void restore() {
                if (space != null) { space.noSave = wasOff; space.getChunkSource().removeRegionTicket(TicketType.FORCED, forced, 2, forced); }
            }
        }.start();
    }

    /** I2: recovery of an unclassified PREPARED record from each source-side presence case. */
    private static final class Recovery extends Case {
        final RocketTransferRecoveryAction action;
        final boolean offline;
        RocketEntity destination;
        ServerPlayer player;
        int held;
        boolean waited;

        Recovery(GameTestHelper helper, RocketTransferRecoveryAction action, boolean offline) {
            super(helper, offline ? List.of(UUID.randomUUID()) : List.of());
            this.action = action; this.offline = offline;
        }

        void act() {
            if (action != RocketTransferRecoveryAction.REBUILD_SOURCE) { source = flight.spawnInFlight(helper, level()); }
            if (action == RocketTransferRecoveryAction.REMOVE_DESTINATION_KEEP_SOURCE) { destination = flight.spawnDestination(helper, level()); }
            prepare(false);
            completion = false;
        }

        boolean before() {
            var server = server();
            if (!waited) {
                var result = recovery().recoverById(server, journal, record.transferId());
                // A pending source return reports RETRY_LATER with no action; its completion later reports the action.
                if (!recovery().retirements().pending(record.logicalRocketId())) {
                    helper.assertTrue(result.status() == RocketTransferRecoveryService.Status.RETRY_LATER, "Recovery status " + result.status());
                    return false;
                }
                helper.assertTrue(offline ? result.status() == RocketTransferRecoveryService.Status.WAITING_FOR_PASSENGERS && result.action() == action
                        : result.status() == RocketTransferRecoveryService.Status.RETRY_LATER, "Recovery reported " + result.status());
                if (source == null) {
                    source = RocketTransferEntities.loadedMatches(server, record, false).stream().findFirst().orElse(null);
                    helper.assertTrue(source != null && !source.getUUID().equals(record.sourceEntityId()), "No rebuilt source");
                }
                waited = true;
                assertReturnedAndHeld(!offline);
                if (destination != null) { helper.assertTrue(destination.isRemoved(), "The destination copy was not discarded"); }
                return false;
            }
            if (offline && held < 40) {
                helper.assertTrue(journal.find(record.transferId()).orElse(null) == record && journal.actionable(record.logicalRocketId())
                        && journal.clean(), "A recovered source waiting for its crew held a reservation or lost its record");
                if (++held == 40) {
                    player = join(flight.record().sourceFlightData().passengers().assignments().get(0).passengerId(), origin.above(4));
                    recovery().onPlayerLoggedIn(player, journal);
                    helper.assertTrue(player.getVehicle() == source, "The reconnect did not place the passenger on the recovered source");
                }
                return false;
            }
            return true;
        }

        void retired() {
            if (destination != null) {
                helper.assertTrue(RocketSourceRetirementFixtures.diskCopies(level(), flight.destinationSlot(), record.logicalRocketId())
                        == (flight.destinationSlot().equals(flight.originSlot()) ? 1 : 0), "The discarded destination copy is still saved");
            }
            if (!offline) {
                var report = recovery().recoverById(server(), journal, record.transferId());
                helper.assertTrue(report.status() == RocketTransferRecoveryService.Status.RECOVERED && report.action() == action,
                        "The completed recovery was not reported: " + report.status());
            }
        }
    }

    /** One detached ordinary flight driven by this service's coordinator; subclasses act and then wait. */
    private abstract static class Case {
        final GameTestHelper helper;
        final List<UUID> crew;
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final RocketTransferService service = new RocketTransferService();
        final AtomicInteger accepted = new AtomicInteger(), discarded = new AtomicInteger();
        final List<ServerPlayer> players = new ArrayList<>();
        final GameTestTickPacer pacer;
        BlockPos origin, target;
        RocketSourceRetirementFixtures.Flight flight;
        RocketTransferRecord record;
        RocketEntity source;
        Path directory;
        boolean completion = true, acted, closed;
        int waits;

        Case(GameTestHelper helper) { this(helper, List.of()); }
        Case(GameTestHelper helper, List<UUID> crew) {
            this.helper = helper; this.crew = crew; pacer = new GameTestTickPacer(helper.getLevel().getServer());
        }

        abstract void act();
        /** Work before the retirement wait; true once the record may be retired. */
        boolean before() { return true; }
        void retired() { }
        void restore() { }

        ServerLevel level() { return helper.getLevel(); }
        MinecraftServer server() { return helper.getLevel().getServer(); }
        RocketTransferRecoveryService recovery() { return RocketSourceRetirementFixtures.recovery(service); }
        RocketRequestCompletion completion() { return new RocketRequestCompletion(accepted::incrementAndGet, discarded::incrementAndGet); }

        boolean ready() {
            origin = helper.absolutePos(new BlockPos(3, 2, 3)); target = helper.absolutePos(new BlockPos(14, 2, 3));
            return level().isPositionEntityTicking(origin) && level().isPositionEntityTicking(target);
        }

        void start() { helper.onEachTick(this::tick); }

        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < 380, "Source retirement fixture exceeded its bounded wait");
                if (!acted) {
                    if (!ready()) { pacer.pace(); return; }
                    directory = Files.createTempDirectory("arce-w98-retire-");
                    if (flight == null) { flight = RocketSourceRetirementFixtures.countdown(level(), origin, target, crew, 10); record = flight.record(); }
                    acted = true; act();
                } else {
                    journal.pollWriter(server().getTickCount());
                    recovery().tickRetirements(server());
                    if (before()) { await(); }
                }
                if (!closed) { pacer.pace(); }
            } catch (IOException failure) { close(); throw new java.io.UncheckedIOException(failure); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        /** The record first leaves RAM only when the survivor's slot already holds it; then the effects run once. */
        void await() {
            var current = journal.find(record.transferId()).orElse(null);
            if (current != null) {
                helper.assertTrue(current == record && accepted.get() == 0, "The return completed before its record was removed");
                return;
            }
            helper.assertTrue(RocketSourceRetirementFixtures.stationaryImage(
                    RocketSourceRetirementFixtures.diskImage(level(), flight.originSlot(), source.getUUID()), record),
                    "The record left RAM before the survivor's slot held the stationary rocket");
            helper.assertTrue(RocketSourceRetirementFixtures.diskCopies(level(), flight.originSlot(), record.logicalRocketId()) == 1,
                    "The survivor's slot holds another copy");
            if (!journal.clean() || completion && accepted.get() == 0) { return; }
            var live = RocketSourceRetirementFixtures.live(service); var settled = RocketSourceRetirementFixtures.settled(service);
            helper.assertTrue(!live.contains(record.transferId()) && !settled.contains(record.transferId())
                    && !recovery().retirements().pending(record.logicalRocketId()), "The retired record kept its session membership");
            helper.assertTrue(!completion || accepted.get() == 1 && discarded.get() == 0, "The request did not complete exactly once");
            helper.assertTrue(source.isAlive() && RocketTransferEntities.loadedMatches(server(), record, false).size() == 1,
                    "The survivor is not the single source");
            retired();
            close(); helper.succeed();
        }

        /** T in the detached journal, acknowledged, and live when the flight is still being driven. */
        void prepare(boolean live) {
            journal.put(record); confirm();
            if (live) { RocketSourceRetirementFixtures.live(service).add(record.transferId()); }
        }

        void confirm() {
            RocketJournalGameTestSupport.confirmDetached(helper, journal, directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
        }

        /** Immediately after the return: stationary at the origin, T retained and settled, nothing completed. */
        void assertReturnedAndHeld(boolean reserved) {
            var flightData = source.flightData().orElseThrow();
            var at = record.sourceSnapshot().sourceOrigin();
            helper.assertTrue((flightData.state() == RocketFlightState.FUELED || flightData.state() == RocketFlightState.ASSEMBLED)
                    && source.getY() == at.y() && flightData.fuel().amount() == RocketSourceRetirementFixtures.FUEL,
                    "The source did not return to its origin with its fuel");
            helper.assertTrue(journal.find(record.transferId()).orElse(null) == record, "The record left RAM before its survivor proof");
            helper.assertTrue(!RocketSourceRetirementFixtures.live(service).contains(record.transferId())
                    && RocketSourceRetirementFixtures.settled(service).contains(record.transferId()), "The return stayed in forward dispatch");
            helper.assertTrue(!reserved || !journal.actionable(record.logicalRocketId()), "The first attempt lost its reservation");
            helper.assertTrue(accepted.get() == 0 && discarded.get() == 0, "The request completed before the removal");
        }

        ServerPlayer join(UUID id, BlockPos at) {
            var player = FirstEventGameTestPlayers.join(server(), id, "W98Crew", level(), at);
            players.add(player); return player;
        }

        void close() {
            if (closed) { return; } closed = true;
            journal.abort(); service.clear();
            for (var player : players) { player.stopRiding(); server().getPlayerList().remove(player); }
            if (record != null) {
                for (var rocket : RocketTransferEntities.loadedMatches(server(), record, false)) { rocket.discard(); }
                for (var rocket : RocketTransferEntities.loadedMatches(server(), record, true)) { rocket.discard(); }
                RocketTransferEntities.releaseFlightTickets(server(), record);
            }
            restore();
            if (directory != null) {
                try (var paths = Files.walk(directory)) {
                    for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) { Files.delete(path); }
                } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        }
    }
}
