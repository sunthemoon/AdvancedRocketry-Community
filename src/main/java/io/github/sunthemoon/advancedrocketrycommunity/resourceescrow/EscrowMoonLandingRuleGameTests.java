package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowWarmupFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.MoonLandingRuleGameTests;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.MoonPadArea;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketMarginGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-075 case 12: the y 80 recheck threshold at the governed boundary, with two real governed launches (a governed
 * record cannot be replaced, because its escrow entry binds its checksum). Below y 80, removing the destination's support
 * during the flight makes the boundary return the rocket to its source with its original fuel. On the developer
 * platform at y 80, removing the platform during the flight leaves the legacy recheck, and the rocket lands at its
 * recorded origin without support (ADR-075's first residual). Both join the other ADR-075 cases' batch and turns.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowMoonLandingRuleGameTests {
    private static final int GROUND = 30;
    private static final int REACH = 4;

    private EscrowMoonLandingRuleGameTests() { }

    @GameTest(template = "empty", batch = MoonLandingRuleGameTests.BATCH, timeoutTicks = MoonLandingRuleGameTests.TIMEOUT)
    public static void aGovernedFlightBelowY80WhoseSupportIsRemovedReturnsToItsSource(GameTestHelper helper) {
        new Flight(helper, false, new BlockPos(676_008, 100, 676_008)).start();
    }

    @GameTest(template = "empty", batch = MoonLandingRuleGameTests.BATCH, timeoutTicks = MoonLandingRuleGameTests.TIMEOUT)
    public static void aGovernedFlightAtY80KeepsTheLegacyRecheckAndLandsAtItsRecordedOrigin(GameTestHelper helper) {
        new Flight(helper, true, new BlockPos(676_040, 100, 676_008)).start();
    }

    private static final class Flight {
        final GameTestHelper helper;
        final boolean platform;
        final BlockPos source;
        final ServerLevel earth;
        final ServerLevel moon;
        final GameTestTickPacer pacer;
        final UUID key = UUID.randomUUID();
        final UUID launch = UUID.randomUUID();
        final MoonPadArea.Changes changes;
        ResourceEscrowSavedData data;
        RocketEntity rocket;
        RocketStructureSnapshot destination;
        RocketFlightData departure;
        RocketTransferRecord prepared;
        boolean returned;
        int step;

        Flight(GameTestHelper helper, boolean platform, BlockPos source) {
            this.helper = helper; this.platform = platform; this.source = source;
            earth = helper.getLevel(); moon = earth.getServer().getLevel(CelestialIds.MOON_LEVEL);
            helper.assertTrue(moon != null, "Moon Level is unavailable");
            pacer = new GameTestTickPacer(earth.getServer());
            changes = new MoonPadArea.Changes(moon);
        }

        void start() { helper.onEachTick(this::tick); }

        private void tick() {
            if (step == 99 || !MoonPadArea.turn(helper, this::cleanup)) { return; }
            pacer.pace();
            var server = earth.getServer(); var journal = RocketTransferSavedData.get(server);
            switch (step) {
                case 0 -> setup(server, journal);
                case 1 -> predict(server, journal);
                case 2 -> request(server, journal);
                case 3 -> prepared(journal);
                case 4 -> { if (platform) { landed(server, journal); } else { returned(server, journal); } }
                default -> { }
            }
        }

        private void setup(MinecraftServer server, RocketTransferSavedData journal) {
            if (!journal.retryChecked(server)) { return; }
            // As the other ADR-075 cases: no earlier flight's record or rocket may reserve or occupy the pads.
            journal.entries().forEach(record -> journal.remove(record.transferId()));
            journal.flush(server);
            discard(moon, null);
            data = ResourceEscrowSavedData.forTests(server); data.bind(server);
            earth.getChunkSource().addRegionTicket(FIXTURE, new ChunkPos(source), 2, key);
            rocket = governed(earth, source, data.ledger().id());
            step = 1;
        }

        private void predict(MinecraftServer server, RocketTransferSavedData journal) {
            if (!columnReady(earth, source)) { return; }
            if (!rocket.isAddedToWorld()) { helper.assertTrue(earth.addFreshEntity(rocket), "Source admission failed"); return; }
            if (!journal.retryChecked(server)) { return; }
            changes.shape(0, REACH, GROUND);
            if (platform) {
                BlockPos pad = SafeCelestialTravel.FIXED_FEET_POSITION;
                for (int dx = -SafeCelestialTravel.PLATFORM_RADIUS; dx <= SafeCelestialTravel.PLATFORM_RADIUS; dx++) {
                    for (int dz = -SafeCelestialTravel.PLATFORM_RADIUS; dz <= SafeCelestialTravel.PLATFORM_RADIUS; dz++) {
                        for (int dy = -1; dy < SafeCelestialTravel.CLEARANCE; dy++) { changes.record(pad.offset(dx, dy, dz)); }
                    }
                }
                new SafeCelestialTravel().prepareFixedPlatform(moon);
            }
            var pads = EscrowWarmupFixtures.predict(server, moon, rocket, 1);
            helper.assertTrue(pads != null, "No admissible Moon pad for the governed fixture");
            destination = pads.get(0);
            int lowest = MoonPadArea.lowest(destination);
            helper.assertTrue(lowest == (platform ? SafeCelestialTravel.FIXED_FEET_POSITION.getY() : GROUND + 1)
                    && MoonPadArea.supported(moon, destination), "The governed fixture's pad is not on the shaped ground: y " + lowest);
            for (var column : RocketDiscoveryColumns.of(destination).columns()) { moon.getChunkSource().addRegionTicket(FIXTURE, column, 2, key); }
            step = 2;
        }

        private void request(MinecraftServer server, RocketTransferSavedData journal) {
            if (!RocketMarginGameTestSupport.endReady(server, destination) || !journal.retryChecked(server)) { return; }
            departure = rocket.flightData().orElseThrow();
            var result = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, launch);
            helper.assertTrue(result.code() == RocketFlightRequestCode.JOURNAL_PENDING,
                    "The governed launch was not accepted: " + result.code() + " " + result.detailKey());
            step = 3;
        }

        /** PREPARED: the recorded destination is the prediction; then the fault is installed for the flight. */
        private void prepared(RocketTransferSavedData journal) {
            var record = journal.find(launch).orElse(null);
            if (record == null || !journal.acknowledgedCurrent()) { return; }
            helper.assertTrue(record.nativeTransfer().isPresent() && record.phase() == RocketTransferPhase.PREPARED,
                    "The flight bypassed governed preparation");
            helper.assertTrue(RocketRegion.fromSnapshot(record.destinationSnapshot()).equals(RocketRegion.fromSnapshot(destination)),
                    "The product selected another pad than the fixture's prediction");
            prepared = record;
            var selector = new RocketLandingPadSelector();
            helper.assertTrue(selector.availableLoaded(moon, destination), "The recorded destination was not available");
            if (platform) {
                BlockPos pad = SafeCelestialTravel.FIXED_FEET_POSITION;
                for (int dx = -SafeCelestialTravel.PLATFORM_RADIUS; dx <= SafeCelestialTravel.PLATFORM_RADIUS; dx++) {
                    for (int dz = -SafeCelestialTravel.PLATFORM_RADIUS; dz <= SafeCelestialTravel.PLATFORM_RADIUS; dz++) {
                        changes.set(pad.offset(dx, -1, dz), Blocks.AIR.defaultBlockState());
                    }
                }
                helper.assertTrue(!MoonPadArea.supported(moon, destination), "The platform still supports the destination");
                helper.assertTrue(selector.availableLoaded(moon, destination),
                        "The y 80 destination lost the legacy recheck without its platform");
            } else {
                MoonPadArea.below(destination).forEach(below -> changes.set(below, Blocks.AIR.defaultBlockState()));
                helper.assertTrue(!selector.availableLoaded(moon, destination), "The unsupported destination remained available");
            }
            step = 4;
        }

        /** Below y 80: the boundary returns the source before any destination is published; F8 restores it idle. */
        private void returned(MinecraftServer server, RocketTransferSavedData journal) {
            var arm = prepared.nativeTransfer().orElseThrow().armed();
            helper.assertTrue(moon.getEntity(arm.destination()) == null, "A destination was published over the removed support");
            var record = journal.find(launch).orElse(null);
            if (record != null && !returned && EscrowRuntime.of(server).returns().handles(record, rocket)) {
                helper.assertTrue((record.phase() == RocketTransferPhase.PREPARED || record.phase() == RocketTransferPhase.DESTINATION_SPAWNED)
                        && rocket.flightData().orElseThrow().state() == RocketFlightState.TRANSIT
                        && !new RocketLandingPadSelector().availableLoaded(moon, destination),
                        "The return was not the boundary's refusal of the unsupported destination");
                returned = true;
            }
            if (record != null || data.ledger().armedFlights().stream().anyMatch(value -> value.transfer().equals(launch))
                    || !RocketEscrowGate.idle(rocket)) { return; }
            var flight = rocket.flightData().orElseThrow();
            helper.assertTrue(returned, "The flight ended without a boundary return");
            helper.assertTrue(rocket.isAlive() && flight.state() == RocketFlightState.FUELED
                    && rocket.getX() == source.getX() + 0.5 && rocket.getY() == source.getY() && rocket.getZ() == source.getZ() + 0.5,
                    "The return did not restore the source at its origin");
            helper.assertTrue(flight.fuel().equals(departure.fuel()) && flight.fuel().committedDebits().isEmpty(),
                    "The return changed the source's fuel or debits");
            helper.assertTrue(count(server) == 1, "The return duplicated or lost the logical rocket");
            step = 99; helper.succeed();
        }

        /** At y 80: the legacy recheck lands the rocket at its recorded origin, without support, debited once. */
        private void landed(MinecraftServer server, RocketTransferSavedData journal) {
            if (journal.find(launch).isPresent()
                    || data.ledger().armedFlights().stream().anyMatch(value -> value.transfer().equals(launch))) { return; }
            var entity = moon.getEntity(prepared.nativeTransfer().orElseThrow().armed().destination());
            if (!(entity instanceof RocketEntity arrival)) { return; }
            var flight = arrival.flightData().orElseThrow();
            var debited = departure.fuel().debit(prepared.sourceFlightData().plan().orElseThrow()).state();
            helper.assertTrue(arrival.operational() && RocketEscrowGate.idle(arrival) && flight.state() == RocketFlightState.LANDED
                    && flight.fuel().committedDebits().equals(List.of(launch)) && flight.fuel().totalAmount() == debited.totalAmount()
                    && earth.getEntity(rocket.getUUID()) == null, "The landing changed resources, the debit or source absence");
            var placed = arrival.snapshot().orElseThrow();
            var origin = prepared.destinationSnapshot().sourceOrigin();
            helper.assertTrue(placed.sourceOrigin().equals(origin) && arrival.blockPosition().getY() == origin.y()
                    && MoonPadArea.lowest(placed) == SafeCelestialTravel.FIXED_FEET_POSITION.getY()
                    && !MoonPadArea.supported(moon, placed), "The rocket did not land unsupported at its recorded y 80 origin");
            helper.assertTrue(count(server) == 1, "The landing duplicated or lost the logical rocket");
            step = 99; helper.succeed();
        }

        private int count(MinecraftServer server) {
            int count = 0;
            for (var level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof RocketEntity found && found.operational()
                            && found.assemblyTransactionId().filter(logical(rocket)::equals).isPresent()) { count++; }
                }
            }
            return count;
        }

        /** Runs once when the case passes or fails, before the next case takes the pads. */
        private void cleanup() {
            var server = earth.getServer();
            earth.getChunkSource().removeRegionTicket(FIXTURE, new ChunkPos(source), 2, key);
            if (destination != null) {
                for (var column : RocketDiscoveryColumns.of(destination).columns()) {
                    moon.getChunkSource().removeRegionTicket(FIXTURE, column, 2, key);
                }
            }
            if (rocket != null) {
                for (var level : server.getAllLevels()) { discard(level, logical(rocket)); }
            }
            changes.restore();
        }
    }

    /** Discards the Level's rockets of one logical rocket, or all of them when {@code logical} is null. */
    private static void discard(ServerLevel level, UUID logical) {
        var found = new java.util.ArrayList<RocketEntity>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof RocketEntity rocket
                    && (logical == null || rocket.assemblyTransactionId().filter(logical::equals).isPresent())) { found.add(rocket); }
        }
        found.forEach(Entity::discard);
    }
}
