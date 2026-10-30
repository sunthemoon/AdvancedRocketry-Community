package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowWarmupFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestAutosave;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorBinding;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorQuote;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketMarginGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** The monitor's unattended production entry must retain governed F1/F2 and exactly-once debit. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowMonitorGameTests {
    private EscrowMonitorGameTests() { }
    @GameTest(template = "empty", batch = "monitor_governed", timeoutTicks = 2400)
    public static void offlineMonitorUsesGovernedPreparationAndOneAcknowledgedDebit(GameTestHelper helper) {
        new Flight(helper, false, false).start();
    }
    @GameTest(template = "empty", batch = "monitor_governed_cutoff", timeoutTicks = 2400)
    public static void disarmingAfterPreparedKeepsTheAcceptedLaunchAndItsRealOutcome(GameTestHelper helper) {
        new Flight(helper, true, false).start();
    }
    @GameTest(template = "empty", batch = "monitor_governed_cold", timeoutTicks = 2400)
    public static void offlineMonitorWarmsAColdDestinationAndLaunchesOnlyOnAFreshEdge(GameTestHelper helper) {
        new Flight(helper, false, true).start();
    }
    @GameTest(template = "empty", batch = "monitor_membership_before", timeoutTicks = 2400)
    public static void stationMembershipRevokedBeforePreparedRefusesWithoutLaunchOrDebit(GameTestHelper helper) {
        new Flight(helper, Revocation.BEFORE).start();
    }
    @GameTest(template = "empty", batch = "monitor_membership_after", timeoutTicks = 2400)
    public static void stationMembershipRevokedAfterPreparedLaunchesAndInvalidatesArming(GameTestHelper helper) {
        new Flight(helper, Revocation.AFTER).start();
    }
    private enum Revocation { NONE, BEFORE, AFTER }
    private static final class Flight implements net.minecraft.gametest.framework.GameTestListener {
        // Three force periods cover F1's source proof and ARMED capture, as in the Moon landing fixture.
        private static final int F1_WINDOW_TICKS = 3 * EscrowBarrierBudget.CADENCE;
        final GameTestHelper helper;
        final ServerLevel earth, destinationLevel;
        final GameTestTickPacer pacer;
        final BlockPos source, device;
        final UUID key = UUID.randomUUID();
        ResourceEscrowSavedData data;
        RocketEntity rocket;
        RocketMonitorBlockEntity monitor;
        RocketStructureSnapshot destination;
        RocketMonitorQuote quote;
        RocketTransferRecord prepared;
        StationState station;
        TravelTarget target = new TravelTarget.BodySurface(CelestialIds.MOON_ID);
        int step;
        final boolean disarmAfterPrepared, coldDestination;
        final Revocation revocation;
        boolean cutInstalled, revoked;
        long revokedAt;
        long warmupStarted;
        boolean observingArrival;
        Flight(GameTestHelper helper, boolean disarmAfterPrepared, boolean coldDestination) {
            this(helper, disarmAfterPrepared, coldDestination, Revocation.NONE);
        }
        Flight(GameTestHelper helper, Revocation revocation) { this(helper, false, false, revocation); }
        Flight(GameTestHelper helper, boolean disarmAfterPrepared, boolean coldDestination, Revocation revocation) {
            this.disarmAfterPrepared = disarmAfterPrepared; this.coldDestination = coldDestination;
            this.revocation = revocation;
            source = new BlockPos(660008 + (revocation != Revocation.NONE ? 64 + 32 * revocation.ordinal()
                    : coldDestination ? 64 : disarmAfterPrepared ? 32 : 0), 100, 660008);
            device = source.south(3);
            this.helper = helper; earth = helper.getLevel();
            destinationLevel = earth.getServer().getLevel(stationDestination() ? CelestialIds.SPACE_LEVEL : CelestialIds.MOON_LEVEL);
            helper.assertTrue(destinationLevel != null, "Destination is unavailable"); pacer = new GameTestTickPacer(earth.getServer());
        }
        void start() {
            try {
                var field = GameTestHelper.class.getDeclaredField("testInfo"); field.setAccessible(true);
                ((net.minecraft.gametest.framework.GameTestInfo) field.get(helper)).addListener(this);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot install monitor fixture cleanup", failure); }
            helper.onEachTick(this::tick);
        }
        @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
        @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info) { releaseTickets(); }
        @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info) { releaseTickets(); }
        private void releaseTickets() {
            if (cutInstalled) { EscrowRuntime.of(earth.getServer()).terminalCutForTests(earth.getServer(), null); cutInstalled = false; }
            if (monitor != null && earth.getBlockEntity(device) == monitor) {
                earth.removeBlockEntity(device); earth.setBlockAndUpdate(device, Blocks.AIR.defaultBlockState());
                earth.setBlockAndUpdate(device.east(), Blocks.AIR.defaultBlockState());
            }
            earth.getChunkSource().removeRegionTicket(FIXTURE, new ChunkPos(source), 2, key);
            if (destination != null) {
                for (var column : RocketDiscoveryColumns.of(destination).columns()) { destinationLevel.getChunkSource().removeRegionTicket(FIXTURE, column, 2, key); }
            }
        }
        void tick() {
            if (step == 99) { return; }
            pacer.pace(); var server = earth.getServer(); var journal = RocketTransferSavedData.get(server);
            if (revoked && revocation == Revocation.BEFORE) {
                helper.assertTrue(journal.findByLogicalRocket(logical(rocket)).isEmpty(), "Refused membership produced PREPARED");
                helper.assertTrue(rocket.flightData().orElseThrow().equals(quote.source()), "Refused membership launched or debited");
                helper.assertTrue(monitor.lastFlight() != RocketFlightRequestCode.SUCCESS, "Refused membership reported launched");
                if (server.getTickCount() - revokedAt < 240 || !RocketEscrowGate.idle(rocket)) { return; }
                helper.assertTrue(monitor.lastFlight() == RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED,
                        "Pre-cutoff revocation did not report refusal");
                helper.assertTrue(!monitor.root().armed() && monitor.root().powered(), "Revocation did not invalidate held arming");
                helper.assertTrue(data.ledger().armedFlights().stream().noneMatch(arm -> arm.logical().equals(logical(rocket))),
                        "Refused launch retained an ARMED tuple");
                rocket.discard(); step = 99; helper.succeed(); return;
            }
            if (revoked && revocation == Revocation.AFTER) {
                helper.assertTrue(monitor.lastFlight() == RocketFlightRequestCode.JOURNAL_PENDING
                        || monitor.lastFlight() == RocketFlightRequestCode.SUCCESS, "Accepted membership launch reported refusal");
            }
            if (step == 0) {
                if (!journal.retryChecked(server)) { return; }
                data = ResourceEscrowSavedData.forTests(server); data.bind(server);
                earth.getChunkSource().addRegionTicket(FIXTURE, new ChunkPos(source), 2, key);
                rocket = governed(earth, source, data.ledger().id());
                if (stationDestination()) {
                    // A fresh station isolates the cold pad from entities left by earlier Moon flights.
                    station = new StationCreationService(new StationPlatformGenerator(), id -> true)
                            .create(server, revocation == Revocation.NONE ? rocket.ownerId().orElseThrow() : UUID.randomUUID(),
                                    "Monitor Station", CelestialIds.EARTH_ID, false)
                            .station().orElseThrow();
                    if (revocation != Revocation.NONE) {
                        station = StationRegistrySavedData.get(server).addMember(station.stationId(), rocket.ownerId().orElseThrow());
                    }
                    target = new TravelTarget.Station(station.stationId());
                }
                step = 1; return;
            }
            if (step == 1) {
                if (!columnReady(earth, source)) { return; }
                if (!rocket.isAddedToWorld()) { helper.assertTrue(earth.addFreshEntity(rocket), "Source admission failed"); return; }
                if (!journal.retryChecked(server)) { return; }
                if (stationDestination()) {
                    var pad = new RocketLandingPadSelector().selectStation(destinationLevel, rocket.snapshot().orElseThrow(),
                            UUID.randomUUID(), station, List.of(), destinationLevel.getGameTime(), true);
                    helper.assertTrue(pad.success(), "Fresh station pad is unavailable: " + pad.detail());
                    destination = pad.snapshot().orElseThrow();
                    if (coldDestination) {
                        requireReleasable(helper, server, List.of(destination));
                        if (!cold(server, destination)) { return; }
                    } else {
                        for (var column : RocketDiscoveryColumns.of(destination).columns()) {
                            destinationLevel.getChunkSource().addRegionTicket(FIXTURE, column, 2, key);
                        }
                    }
                } else {
                    var pads = predict(server, destinationLevel, rocket, 1); if (pads == null) { return; }
                    destination = pads.get(0);
                    for (var column : RocketDiscoveryColumns.of(destination).columns()) { destinationLevel.getChunkSource().addRegionTicket(FIXTURE, column, 2, key); }
                }
                earth.setBlockAndUpdate(device, ModBlocks.ROCKET_MONITORING_STATION.get().defaultBlockState());
                monitor = (RocketMonitorBlockEntity) earth.getBlockEntity(device);
                var root = new RocketMonitorRoot(rocket.ownerId().orElseThrow(), UUID.randomUUID(), RocketMonitorBinding.capture(rocket), 0, false, false, null, "");
                var outer = new CompoundTag(); outer.put(RocketMonitorRoot.KEY, root.encode()); monitor.load(outer);
                step = 2; return;
            }
            if (step == 2) {
                if (!coldDestination && !RocketMarginGameTestSupport.endReady(server, destination) || !journal.retryChecked(server)) { return; }
                // An autosave can supersede F1 and legitimately discard this edge before PREPARED exists.
                if (GameTestAutosave.within(server, F1_WINDOW_TICKS)) { return; }
                quote = RocketRuntime.monitorFlights().orElseThrow().quote(monitor, target);
                helper.assertTrue(quote.ready() && monitor.configure(quote, true, 0), "Cannot arm governed monitor: " + quote.code());
                helper.assertTrue(quote.cost() == quote.plan().propulsionDebit().primary(), "Quote does not display the native primary debit");
                helper.assertTrue(server.getPlayerList().getPlayer(monitor.root().owner()) == null, "Owner is not offline");
                installMembershipCut();
                earth.setBlockAndUpdate(device.east(), Blocks.REDSTONE_BLOCK.defaultBlockState()); monitor.signal(true);
                if (coldDestination) {
                    helper.assertTrue(monitor.lastFlight() == RocketFlightRequestCode.DESTINATION_LOADING, "Cold launch did not warm");
                    warmupStarted = server.getTickCount(); step = 10; return;
                }
                helper.assertTrue(monitor.lastFlight() == RocketFlightRequestCode.JOURNAL_PENDING,
                        "Monitor did not enter governed preparation: " + monitor.lastFlight());
                helper.assertTrue(monitor.lastCost() == quote.cost(), "Pending launch changed the displayed debit");
                step = 3; return;
            }
            if (step == 10) {
                var warming = warmup(server, rocket);
                helper.assertTrue(warming != null && warming.player().equals(monitor.root().owner()), "Offline owner lost its attributed warm-up");
                helper.assertTrue(journal.findByLogicalRocket(logical(rocket)).isEmpty()
                        && rocket.flightData().orElseThrow().fuel().equals(quote.source().fuel()), "Held edge launched or debited");
                if (!warming.state().equals("READY") || server.getTickCount() - warmupStarted < 20) { return; }
                if (GameTestAutosave.within(server, F1_WINDOW_TICKS)) { return; }
                earth.setBlockAndUpdate(device.east(), Blocks.AIR.defaultBlockState()); monitor.signal(false);
                earth.setBlockAndUpdate(device.east(), Blocks.REDSTONE_BLOCK.defaultBlockState()); monitor.signal(true);
                helper.assertTrue(monitor.lastFlight() == RocketFlightRequestCode.JOURNAL_PENDING,
                        "Fresh ready edge did not prepare: " + monitor.lastFlight());
                step = 3; return;
            }
            if (step == 3) {
                prepared = journal.findByLogicalRocket(logical(rocket)).orElse(null);
                if (prepared == null) {
                    helper.assertTrue(EscrowRuntime.of(server).flightCheckpoint().locked(logical(rocket)),
                            "Monitor launch ended before PREPARED: " + monitor.lastFlight());
                    return;
                }
                helper.assertTrue(prepared.nativeTransfer().isPresent(), "Monitor bypassed governed preparation");
                helper.assertTrue(!prepared.transferId().equals(quote.plan().requestId()), "Preview ID became a flight request");
                if (disarmAfterPrepared) {
                    monitor.disarm();
                    helper.assertTrue(!monitor.root().armed() && monitor.root().powered(), "Disarm lost the held edge");
                }
                step = 4;
            }
            if (journal.find(prepared.transferId()).isPresent()) { return; }
            if (coldDestination && !observingArrival) {
                // Production tickets may expire once T retires. Observe F8 like the existing warm-up fixture,
                // without making the cold destination ready before admission or holding it through the flight.
                for (var column : RocketDiscoveryColumns.of(destination).columns()) {
                    destinationLevel.getChunkSource().addRegionTicket(FIXTURE, column, 2, key);
                }
                observingArrival = true;
            }
            if (data.ledger().armedFlights().stream().anyMatch(armed -> armed.transfer().equals(prepared.transferId()))) { return; }
            var entity = destinationLevel.getEntity(prepared.nativeTransfer().orElseThrow().armed().destination());
            if (!(entity instanceof RocketEntity arrival)) { return; }
            var flight = arrival.flightData().orElseThrow();
            helper.assertTrue(flight.state() == RocketFlightState.LANDED && RocketEscrowGate.idle(arrival), "Flight did not settle");
            helper.assertTrue(flight.fuel().totalAmount() == quote.source().fuel().totalAmount() - quote.cost()
                    && flight.fuel().committedDebits().equals(java.util.List.of(prepared.transferId())), "Governed flight did not debit exactly once");
            helper.assertTrue(!rocket.isAlive(), "Governed source survived");
            monitor.tick(); helper.assertTrue(monitor.lastFlight() == RocketFlightRequestCode.SUCCESS, "Acknowledged governed flight still displays pending");
            if (revocation != Revocation.NONE) {
                helper.assertTrue(revoked && !monitor.root().armed() && monitor.root().powered(), "Membership cut/arming invalidation not observed");
            }
            arrival.discard(); earth.removeBlockEntity(device); earth.setBlockAndUpdate(device, Blocks.AIR.defaultBlockState());
            earth.setBlockAndUpdate(device.east(), Blocks.AIR.defaultBlockState());
            step = 99; helper.succeed();
        }
        private boolean stationDestination() { return coldDestination || revocation != Revocation.NONE; }
        private void installMembershipCut() {
            if (revocation == Revocation.NONE) { return; }
            var server = earth.getServer(); var runtime = EscrowRuntime.of(server);
            String selected = revocation == Revocation.BEFORE ? "F1_ARM_ACK" : "F2_PREPARED_SUBMITTED";
            EscrowGameTestTerminalCut.install(helper, cut -> {
                if (!selected.equals(cut)) { return; }
                runtime.terminalCutForTests(server, null); cutInstalled = false;
                var journal = RocketTransferSavedData.get(server);
                var submitted = journal.findByLogicalRocket(logical(rocket)).orElse(null);
                helper.assertTrue((submitted != null) == (revocation == Revocation.AFTER), "Membership cut is on the wrong side of PREPARED");
                if (submitted != null) {
                    helper.assertTrue(submitted.nativeTransfer().isPresent() && !journal.logicalAcknowledged(logical(rocket)),
                            "Post-submission cut is not before acknowledgement");
                    prepared = submitted;
                }
                helper.assertTrue(data.ledger().armedFlights().stream().anyMatch(arm -> arm.logical().equals(logical(rocket))),
                        "Cut occurred before ARMED");
                helper.assertTrue(rocket.flightData().orElseThrow().equals(quote.source()), "Cut occurred after launch/debit");
                var stations = StationRegistrySavedData.get(server);
                helper.assertTrue(stations.find(station.stationId()).orElseThrow().members().contains(monitor.root().owner()),
                        "Rocket owner was not a live station member");
                stations.removeMember(station.stationId(), monitor.root().owner());
                helper.assertTrue(RocketRuntime.monitorFlights().orElseThrow().authority(monitor, target).isEmpty(),
                        "Membership removal did not revoke live authority");
                revoked = true; revokedAt = server.getTickCount();
            });
            cutInstalled = true;
        }
    }
}
