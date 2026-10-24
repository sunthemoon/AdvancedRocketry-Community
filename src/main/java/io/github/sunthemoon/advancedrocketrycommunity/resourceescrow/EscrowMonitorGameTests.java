package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowWarmupFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
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
    private static final class Flight implements net.minecraft.gametest.framework.GameTestListener {
        final GameTestHelper helper;
        final ServerLevel earth, destinationLevel;
        final GameTestTickPacer pacer;
        final BlockPos source = new BlockPos(660008, 100, 660008), device = source.south(3);
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
        long warmupStarted;
        Flight(GameTestHelper helper, boolean disarmAfterPrepared, boolean coldDestination) {
            this.disarmAfterPrepared = disarmAfterPrepared; this.coldDestination = coldDestination;
            this.helper = helper; earth = helper.getLevel();
            destinationLevel = earth.getServer().getLevel(coldDestination ? CelestialIds.SPACE_LEVEL : CelestialIds.MOON_LEVEL);
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
            earth.getChunkSource().removeRegionTicket(FIXTURE, new ChunkPos(source), 2, key);
            if (destination != null) {
                for (var column : RocketDiscoveryColumns.of(destination).columns()) { destinationLevel.getChunkSource().removeRegionTicket(FIXTURE, column, 2, key); }
            }
        }
        void tick() {
            if (step == 99) { return; }
            pacer.pace(); var server = earth.getServer(); var journal = RocketTransferSavedData.get(server);
            if (step == 0) {
                if (!journal.retryChecked(server)) { return; }
                data = ResourceEscrowSavedData.forTests(server); data.bind(server);
                earth.getChunkSource().addRegionTicket(FIXTURE, new ChunkPos(source), 2, key);
                rocket = governed(earth, source, data.ledger().id());
                if (coldDestination) {
                    // A fresh station isolates the cold pad from entities left by earlier Moon flights.
                    station = new StationCreationService(new StationPlatformGenerator(), id -> true)
                            .create(server, rocket.ownerId().orElseThrow(), "Monitor Warm-up Station", CelestialIds.EARTH_ID, false)
                            .station().orElseThrow();
                    target = new TravelTarget.Station(station.stationId());
                }
                step = 1; return;
            }
            if (step == 1) {
                if (!columnReady(earth, source)) { return; }
                if (!rocket.isAddedToWorld()) { helper.assertTrue(earth.addFreshEntity(rocket), "Source admission failed"); return; }
                if (!journal.retryChecked(server)) { return; }
                if (coldDestination) {
                    var pad = new RocketLandingPadSelector().selectStation(destinationLevel, rocket.snapshot().orElseThrow(),
                            UUID.randomUUID(), station, List.of(), destinationLevel.getGameTime(), true);
                    helper.assertTrue(pad.success(), "Fresh station pad is unavailable: " + pad.detail());
                    destination = pad.snapshot().orElseThrow();
                    requireReleasable(helper, server, List.of(destination));
                    if (!cold(server, destination)) { return; }
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
                quote = RocketRuntime.monitorFlights().orElseThrow().quote(monitor, target);
                helper.assertTrue(quote.ready() && monitor.configure(quote, true, 0), "Cannot arm governed monitor: " + quote.code());
                helper.assertTrue(quote.cost() == quote.plan().propulsionDebit().primary(), "Quote does not display the native primary debit");
                helper.assertTrue(server.getPlayerList().getPlayer(monitor.root().owner()) == null, "Owner is not offline");
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
                earth.setBlockAndUpdate(device.east(), Blocks.AIR.defaultBlockState()); monitor.signal(false);
                earth.setBlockAndUpdate(device.east(), Blocks.REDSTONE_BLOCK.defaultBlockState()); monitor.signal(true);
                helper.assertTrue(monitor.lastFlight() == RocketFlightRequestCode.JOURNAL_PENDING,
                        "Fresh ready edge did not prepare: " + monitor.lastFlight());
                step = 3; return;
            }
            if (step == 3) {
                prepared = journal.findByLogicalRocket(logical(rocket)).orElse(null);
                if (prepared == null) { return; }
                helper.assertTrue(prepared.nativeTransfer().isPresent(), "Monitor bypassed governed preparation");
                helper.assertTrue(!prepared.transferId().equals(quote.plan().requestId()), "Preview ID became a flight request");
                if (disarmAfterPrepared) {
                    monitor.disarm();
                    helper.assertTrue(!monitor.root().armed() && monitor.root().powered(), "Disarm lost the held edge");
                }
                step = 4;
            }
            if (journal.find(prepared.transferId()).isPresent()
                    || data.ledger().armedFlights().stream().anyMatch(armed -> armed.transfer().equals(prepared.transferId()))) { return; }
            var entity = destinationLevel.getEntity(prepared.nativeTransfer().orElseThrow().armed().destination());
            if (!(entity instanceof RocketEntity arrival)) { return; }
            var flight = arrival.flightData().orElseThrow();
            helper.assertTrue(flight.state() == RocketFlightState.LANDED && RocketEscrowGate.idle(arrival), "Flight did not settle");
            helper.assertTrue(flight.fuel().totalAmount() == quote.source().fuel().totalAmount() - quote.cost()
                    && flight.fuel().committedDebits().equals(java.util.List.of(prepared.transferId())), "Governed flight did not debit exactly once");
            helper.assertTrue(!rocket.isAlive(), "Governed source survived");
            monitor.tick(); helper.assertTrue(monitor.lastFlight() == RocketFlightRequestCode.SUCCESS, "Acknowledged governed flight still displays pending");
            arrival.discard(); earth.removeBlockEntity(device); earth.setBlockAndUpdate(device, Blocks.AIR.defaultBlockState());
            earth.setBlockAndUpdate(device.east(), Blocks.AIR.defaultBlockState());
            step = 99; helper.succeed();
        }
    }
}
