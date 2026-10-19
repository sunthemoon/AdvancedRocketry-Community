package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransferBayRecoveryGate.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickDriver;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Actual entity/checked-callback boundaries behind a RAM-only stub, not a bay deployment fixture. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketTransferBayGameTests {
    private RocketTransferBayGameTests() { }
    @GameTest(template = "rocket_test", batch = "bay_transfer_hook", timeoutTicks = 200)
    public static void readySourceContinuesTransitIntoDestinationWithoutRollback(GameTestHelper helper) { new Fixture(helper, 0).start(); }
    @GameTest(template = "rocket_test", batch = "bay_transfer_hook", timeoutTicks = 200)
    public static void endedUnconsumedClaimUsesOrdinaryStationaryRecovery(GameTestHelper helper) { new Fixture(helper, 1).start(); }
    @GameTest(template = "rocket_test", batch = "bay_transfer_hook", timeoutTicks = 200)
    public static void destinationCallbackCapturedBeforeClaimIsRefused(GameTestHelper helper) { new Fixture(helper, 2).start(); }

    private static final class Stub implements RocketTransferBayRecoveryGate {
        Claim current = Claim.held(State.OWNED_WAIT, "PROVING_ENDPOINTS");
        boolean held = true;
        @Override public Claim claim(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) {
            Claim result = current;
            if (result.kind() == Kind.READY_SOURCE || result.kind() == Kind.RELEASED_TO_ORDINARY) { current = Claim.unclaimed(); }
            return result;
        }
        @Override public Boundary beforeAction(MinecraftServer server, RocketTransferSavedData journal,
                RocketTransferRecord record, Action action) { return held ? Boundary.held() : Boundary.unclaimed(); }
    }
    private static final class Fixture {
        final GameTestHelper helper;
        final int mode;
        final GameTestTickDriver driver;
        final GameTestTickPacer pacer;
        final RocketTransferService service = new RocketTransferService();
        final RocketTransferRecoveryService recovery = new RocketTransferRecoveryService(new HashSet<>(), new HashSet<>());
        final Stub gate = new Stub();
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        RocketEntity source;
        RocketTransferRecord record;
        Path directory;
        int attempts;
        boolean closed;
        Fixture(GameTestHelper helper, int mode) {
            this.helper = helper; this.mode = mode; driver = new GameTestTickDriver(helper);
            pacer = new GameTestTickPacer(helper.getLevel().getServer());
            if (mode == 1) { recovery.bays().install(gate); } else { service.installBayRecoveryGate(gate); }
        }
        void start() { driver.after(1, () -> guard(this::setup)); }
        void setup() {
            var level = helper.getLevel(); var origin = helper.absolutePos(new BlockPos(3, 2, 3));
            var target = helper.absolutePos(new BlockPos(14, 2, 3));
            if (!level.isPositionEntityTicking(origin) || !level.isPositionEntityTicking(target)) {
                waitFor(this::setup); return;
            }
            var blocks = List.of(new RocketBlock(new RocketPosition(0, 0, 0),
                    new RocketBlockState(net.minecraft.resources.ResourceLocation.tryParse("minecraft:iron_block"), Map.of())),
                    new RocketBlock(new RocketPosition(0, 1, 0),
                            new RocketBlockState(net.minecraft.resources.ResourceLocation.tryParse("minecraft:iron_block"), Map.of())));
            var stats = new RocketStats(2, 100, 1_000, 1_000, 1, 1, 1, 0);
            var propulsion = new RocketPropulsion(RocketPropulsion.Mode.ABSTRACT, 1_000, 0, 1, 1,
                    RocketPropulsion.Settings.DEFAULTS);
            var sourceSnapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(), position(origin),
                    blocks, List.of(new RocketPosition(0, 1, 0)), stats, level.getGameTime(), propulsion);
            var destination = sourceSnapshot.relocated(UUID.randomUUID(), level.dimension().location(), position(target), level.getGameTime());
            UUID logical = UUID.randomUUID(), transfer = UUID.randomUUID();
            var fuel = RocketFuelState.empty(propulsion).fill(1_000).state();
            helper.assertTrue(fuel.propulsion().isPresent(), "Live schema-3 fixture needs a native fuel vector");
            var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    level.dimension().location(), level.dimension().location(), 500, level.getGameTime());
            var flight = RocketFlightData.initial(logical, 1_000, 1, plan.sourceBody(), plan.sourceDimension(), position(origin), level.getGameTime())
                    .withFuel(fuel, level.getGameTime()).withPlan(plan).startCountdown(level.getGameTime())
                    .completeCountdown(level.getGameTime()).beginTransit(transfer, level.getGameTime());
            var arrival = flight.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(), plan.destinationDimension(), position(target), level.getGameTime());
            source = ModEntities.ROCKET.get().create(level); helper.assertTrue(source != null, "No source entity");
            record = RocketTransferRecord.create(transfer, logical, UUID.randomUUID(), source.getUUID(), sourceSnapshot, destination,
                    flight, arrival, 500, level.getGameTime());
            source.initializeTransferred(sourceSnapshot, logical, record.ownerId(), flight);
            RocketTransferEntities.positionAtAltitude(source, sourceSnapshot.sourceOrigin());
            helper.assertTrue(level.addFreshEntity(source), "Source was not published");
            helper.assertTrue(source.operational() && level.getEntity(source.getUUID()) == source, "Source is not visible/operational");
            journal.put(record);
            try { directory = Files.createTempDirectory("arce-bay-hook-"); journal.save(directory.resolve(RocketTransferSavedData.DATA_NAME + ".dat").toFile()); }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            waitFor(this::handoff);
        }
        void handoff() {
            var server = helper.getLevel().getServer(); journal.pollWriter(server.getTickCount());
            if (!journal.acknowledgedCurrent()) { waitFor(this::handoff); return; }
            if (mode == 1) { recovery.classify(server, journal); } else { service.classifyBayRecovery(server, journal); }
            if (mode == 1) {
                helper.assertTrue(recovery.recoverById(server, journal, record.transferId()).status() == RocketTransferRecoveryService.Status.RETRY_LATER,
                        "Held operator recovery was admitted");
            } else { service.tickLive(server, journal, record); }
            helper.assertTrue(RocketTransferFenceGameTests.flightTickets(server, record.transferId()) == 0,
                    "Held claim received an ordinary flight ticket");
            helper.assertTrue(source.flightData().orElseThrow().state() == RocketFlightState.TRANSIT, "Held claim rolled back the source");
            gate.held = false; gate.current = mode == 1 ? Claim.ordinary() : Claim.readySource(source.getUUID());
            if (mode == 1) {
                helper.assertTrue(recovery.recoverById(server, journal, record.transferId()).status() == RocketTransferRecoveryService.Status.RETRY_LATER,
                        "Unacknowledged settlement reported success");
                helper.assertTrue(source.flightData().orElseThrow().state() == RocketFlightState.FUELED,
                        "Unconsumed handoff did not restore stationary source");
                waitFor(this::assertOrdinary); return;
            }
            service.classifyBayRecovery(server, journal); waitFor(this::fly);
        }
        void fly() {
            var server = helper.getLevel().getServer(); service.tickLive(server, journal, record);
            helper.assertTrue(RocketTransferFenceGameTests.flightTickets(server, record.transferId()) > 0,
                    "The READY_SOURCE control never entered live flight ticketing");
            helper.assertTrue(source.flightData().orElseThrow().state() == RocketFlightState.TRANSIT
                    && source.getY() == record.sourceSnapshot().sourceOrigin().y() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS,
                    "READY_SOURCE was rolled back instead of continued at altitude");
            if (journal.find(record.transferId()).orElseThrow().phase() == RocketTransferPhase.PREPARED) { waitFor(this::fly); return; }
            helper.assertTrue(!journal.acknowledgedCurrent(), "Destination phase synchronously joined IO");
            if (mode == 2) { gate.held = true; gate.current = Claim.held(State.OWNED_WAIT, "CALLBACK_HOLD"); }
            waitFor(this::assertDestination);
        }
        void assertDestination() {
            journal.pollWriter(helper.getLevel().getServer().getTickCount());
            if (!journal.acknowledgedCurrent()) { waitFor(this::assertDestination); return; }
            var spawned = journal.find(record.transferId()).orElseThrow();
            helper.assertTrue(spawned.phase() == RocketTransferPhase.DESTINATION_SPAWNED, "No destination phase");
            var entity = helper.getLevel().getEntity(spawned.destinationEntityId().orElseThrow());
            if (mode == 2) {
                helper.assertTrue(entity == null, "A callback captured before the claim published its destination");
            } else {
                helper.assertTrue(entity instanceof RocketEntity rocket && rocket.operational()
                        && rocket.flightData().orElseThrow().fuel().amount() == 500, "READY_SOURCE did not publish exact destination resources");
            }
            helper.assertTrue(!source.isRemoved() && source.flightData().orElseThrow().fuel().amount() == 1_000, "Source authority changed early");
            close(); helper.succeed();
        }
        void assertOrdinary() {
            journal.pollWriter(helper.getLevel().getServer().getTickCount());
            // W98 (N3-A1): the stationary source's own slot is read back before its record is removed.
            recovery.tickRetirements(helper.getLevel().getServer());
            if (!journal.acknowledgedCurrent() || recovery.retirements().pending(record.logicalRocketId())) {
                waitFor(this::assertOrdinary); return;
            }
            helper.assertTrue(journal.find(record.transferId()).isEmpty(), "Stationary settlement journal was not removed");
            helper.assertTrue(source.flightData().orElseThrow().state() == RocketFlightState.FUELED
                    && source.flightData().orElseThrow().fuel().amount() == 1_000
                    && source.getY() == record.sourceSnapshot().sourceOrigin().y(), "Ordinary recovery changed resources/origin");
            helper.assertTrue(recovery.recoverById(helper.getLevel().getServer(), journal, record.transferId()).status()
                    == RocketTransferRecoveryService.Status.RECOVERED, "Exact settlement acknowledgement was not reported");
            close(); helper.succeed();
        }
        void waitFor(Runnable next) { helper.assertTrue(++attempts <= 80, "Bay fixture exceeded bounded loading/IO waits"); pacer.pace(); driver.after(1, () -> guard(next)); }
        void guard(Runnable action) {
            try { action.run(); } catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        void close() {
            if (closed) { return; } closed = true;
            journal.find(record == null ? new UUID(0, 0) : record.transferId()).flatMap(RocketTransferRecord::destinationEntityId)
                    .map(helper.getLevel()::getEntity).ifPresent(net.minecraft.world.entity.Entity::discard);
            if (source != null) { source.discard(); }
            journal.abort(); service.clear(); recovery.clear();
            if (directory != null) {
                try { try (var paths = Files.walk(directory)) {
                    for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) { Files.delete(path); }
                } } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        }
        static RocketPosition position(BlockPos pos) { return new RocketPosition(pos.getX(), pos.getY(), pos.getZ()); }
    }
}
