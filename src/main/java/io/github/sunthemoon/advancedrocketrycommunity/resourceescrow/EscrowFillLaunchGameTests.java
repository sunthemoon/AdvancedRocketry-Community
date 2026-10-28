package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightConsoleFixtures;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W133 (Root decision R55) A1. An empty assembled rocket refuelled through the real escrow fill keeps ASSEMBLED with its
 * new fuel. Its wrapper is then ledger-bound, so its launch is the governed one: the launch promotes it, F1 and F2 start
 * the countdown, and the cancellation returns it with the same fuel. The same fuel shape on an unbound rocket uses the
 * ordinary launch and cancellation; an ordinary fuel change still makes FUELED at once and launches as before; an empty
 * assembled rocket is still refused with INVALID_STATE. Each flight has its own batch, because the checked journal is
 * global. An after-batch cleanup removes the fixtures of a failed or timed-out test.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowFillLaunchGameTests {
    private enum Kind { ESCROW, ASSEMBLED, ORDINARY, EMPTY }
    private static final String ESCROW = "w133_escrow_fill_launch";
    private static final String ASSEMBLED = "w133_assembled_ordinary_launch";
    private static final String ORDINARY = "w133_ordinary_fuel_launch";
    private static final String EMPTY = "w133_empty_assembled_refusal";
    private static final TicketType<UUID> PAD = TicketType.create("arce_w133_moon_pad", UUID::compareTo);
    private static final List<Scenario> RUNNING = new ArrayList<>();
    private EscrowFillLaunchGameTests() { }

    @GameTest(template = "empty", batch = ESCROW, timeoutTicks = 2_400)
    public static void escrowFilledAssembledRocketCountsDownAndItsCancellationRestoresIt(GameTestHelper helper) {
        new Scenario(helper, Kind.ESCROW, ESCROW).start();
    }

    @GameTest(template = "empty", batch = ASSEMBLED, timeoutTicks = 1_200)
    public static void unboundAssembledRocketWithFuelUsesTheOrdinaryLaunchAndCancellation(GameTestHelper helper) {
        new Scenario(helper, Kind.ASSEMBLED, ASSEMBLED).start();
    }

    @GameTest(template = "empty", batch = ORDINARY, timeoutTicks = 1_200)
    public static void ordinaryFuelChangeStillMakesFueledAndLaunchesAsBefore(GameTestHelper helper) {
        new Scenario(helper, Kind.ORDINARY, ORDINARY).start();
    }

    @GameTest(template = "empty", batch = EMPTY, timeoutTicks = 400)
    public static void emptyAssembledRocketIsStillRefused(GameTestHelper helper) {
        new Scenario(helper, Kind.EMPTY, EMPTY).start();
    }

    @AfterBatch(batch = ESCROW) public static void afterEscrow(ServerLevel level) { abandon(ESCROW); }
    @AfterBatch(batch = ASSEMBLED) public static void afterAssembled(ServerLevel level) { abandon(ASSEMBLED); }
    @AfterBatch(batch = ORDINARY) public static void afterOrdinary(ServerLevel level) { abandon(ORDINARY); }
    @AfterBatch(batch = EMPTY) public static void afterEmpty(ServerLevel level) { abandon(EMPTY); }

    /** Runs after a failure or a timeout too: closes the gate and removes the player, tickets, loader and rocket. */
    private static void abandon(String batch) {
        var unfinished = RUNNING.stream().filter(scenario -> scenario.batch.equals(batch) && !scenario.finished).toList();
        RUNNING.removeIf(scenario -> scenario.batch.equals(batch));
        if (unfinished.isEmpty()) { return; }
        RuntimeException failed = null;
        for (var scenario : unfinished) {
            try { scenario.undo(); } catch (RuntimeException failure) { if (failed == null) { failed = failure; } }
        }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W133_AFTER_BATCH batch={} unfinished={} cleanup_failed={}; "
                + "the fixtures of a failed or timed-out test were removed", batch, unfinished.size(), failed != null, failed);
    }

    private static final class Scenario {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final GameTestTickPacer pacer;
        private final Kind kind;
        private final String batch;
        private final Area area;
        private final UUID owner = UUID.randomUUID(), key = UUID.randomUUID();
        private final Set<ChunkPos> padColumns = new LinkedHashSet<>();
        private RocketEntity rocket;
        private FluidFuelLoaderBlockEntity loader;
        private ServerPlayer pilot;
        private RocketFlightData filled;
        private RocketStructureSnapshot pad;
        private UUID request;
        private long requestedAt;
        private boolean gateOpen;
        private boolean finished;
        private int step;

        Scenario(GameTestHelper helper, Kind kind, String batch) {
            this.helper = helper; this.kind = kind; this.batch = batch;
            level = helper.getLevel(); pacer = new GameTestTickPacer(level.getServer());
            area = new Area(level, 47_216 + 8 * kind.ordinal(), 47_216);
        }

        void start() { RUNNING.add(this); area.hold(); helper.onEachTick(this::tick); }

        private void tick() {
            if (finished) { return; }
            pacer.pace();
            switch (step) {
                case 0 -> setup();
                case 1 -> fuelled();
                case 2 -> launch();
                case 3 -> countdown();
                case 4 -> restored();
                default -> { }
            }
        }

        private void setup() {
            var server = level.getServer();
            if (!area.ticking() || !RocketTransferSavedData.get(server).retryChecked(server)) { area.hold(); return; }
            rocket = FluidFuelLoaderEscrowFixtures.rocket(level, area.at(8, 100, 8), owner, mono(2_000));
            helper.assertTrue(level.addFreshEntity(rocket), "Native rocket publication failed");
            var empty = rocket.flightData().orElseThrow();
            helper.assertTrue(empty.state() == RocketFlightState.ASSEMBLED && empty.fuel().totalAmount() == 0,
                    "The fixture rocket is not an empty assembled rocket: " + empty.state());
            var fuel = empty.fuel().withFluidBank(false, ROCKET_FUEL, null, 1_000);
            switch (kind) {
                case ESCROW -> {
                    loader = FluidFuelLoaderEscrowFixtures.loader(level, area.at(6, 100, 8), owner, tank(ROCKET_FUEL, 1_000), binding(rocket));
                    gate(true);
                }
                // Test state only: the exact shape an escrow fill leaves, on a rocket that no ledger binds.
                case ASSEMBLED -> rocket.updateFlightData(EscrowNativeEnvelope.fuelOnly(empty, fuel));
                // The ordinary fuel change that FuelLoaderBlockEntity and RocketPropulsionInteraction make.
                case ORDINARY -> rocket.updateFlightData(empty.withFuel(fuel, level.getGameTime()));
                case EMPTY -> { }
            }
            step = 1;
        }

        private void fuelled() {
            if (kind == Kind.ESCROW) {
                conserved();
                if (amount(loader) != 0 || bank(rocket, false) != 1_000 || !settled() || !RocketEscrowGate.idle(rocket)) { return; }
                gate(false);
            }
            filled = rocket.flightData().orElseThrow();
            var expected = kind == Kind.ORDINARY ? RocketFlightState.FUELED : RocketFlightState.ASSEMBLED;
            helper.assertTrue(filled.state() == expected && filled.plan().isEmpty() && filled.activeTransferId().isEmpty(),
                    kind + " fill left " + filled.state() + ", not " + expected);
            helper.assertTrue(filled.fuel().totalAmount() == (kind == Kind.EMPTY ? 0 : 1_000), "Unexpected fuel " + filled.fuel().summary());
            helper.assertTrue((rocket.escrowSection().ledger() != null) == (kind == Kind.ESCROW),
                    "Only the escrow fill binds the rocket to the ledger");
            helper.assertTrue(filled.launchReady() == (kind != Kind.EMPTY), "Launch readiness differs for " + kind);
            // A creative viewer that is never placed in the player list, so it loads no chunks around this far area.
            // W182: it has a game connection, since the server serves flight intents only to a connected player.
            pilot = RocketFlightConsoleFixtures.connect(new ServerPlayer(level.getServer(), level,
                    new GameProfile(UUID.randomUUID(), "W133" + kind.name())) {
                @Override public boolean isCreative() { return true; }
                @Override public boolean isSpectator() { return false; }
            });
            pilot.setPos(rocket.getX(), rocket.getY() + 1, rocket.getZ());
            var menu = new RocketFlightMenu(1_330 + kind.ordinal(), pilot.getInventory(), rocket);
            helper.assertTrue(menu.canLaunch() == (kind != Kind.EMPTY), "Menu launch flag differs for " + kind);
            var moon = RocketRuntime.navigation(pilot, rocket).quotes().forTarget(new TravelTarget.BodySurface(CelestialIds.MOON_ID));
            helper.assertTrue(kind == Kind.EMPTY ? moon.status() == RocketNavigationStatus.INVALID_STATE
                    : moon.status() == RocketNavigationStatus.READY, "Moon navigation for " + kind + " is " + moon.status());
            if (kind == Kind.EMPTY) { refused(); return; }
            step = 2;
        }

        /** An empty assembled rocket: both entries still refuse with INVALID_STATE and change nothing. */
        private void refused() {
            var journal = RocketTransferSavedData.get(level.getServer());
            request = UUID.randomUUID();
            var result = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, request);
            helper.assertTrue(result.code() == RocketFlightRequestCode.INVALID_STATE, "Empty launch answered " + result.code());
            helper.assertTrue(RocketFlightConsoleFixtures.send(pilot, rocket, () -> RocketRuntime.requestFlightIntent(pilot,
                    rocket.getId(), RocketFlightAction.LAUNCH, new TravelTarget.BodySurface(CelestialIds.MOON_ID),
                    UUID.randomUUID())), "The pilot could not open the flight menu");
            helper.assertTrue(filled.equals(rocket.flightData().orElseThrow()) && journal.find(request).isEmpty()
                    && journal.findByLogicalRocket(filled.logicalRocketId()).isEmpty() && RocketEscrowGate.idle(rocket),
                    "A refused launch changed the rocket or the journal");
            finish();
        }

        /** The selected Moon pad's margin is loaded first, so the governed launch needs no destination warm-up. */
        private void launch() {
            var server = level.getServer(); var journal = RocketTransferSavedData.get(server);
            var moon = server.getLevel(CelestialIds.MOON_LEVEL);
            helper.assertTrue(moon != null, "Moon Level is missing");
            if (pad == null) {
                var picks = EscrowWarmupFixtures.predict(server, moon, rocket, 1);
                helper.assertTrue(picks != null, "No admissible Moon pad");
                pad = picks.get(0);
                var columns = RocketDiscoveryColumns.of(pad);
                for (int x = columns.minColumnX(); x <= columns.maxColumnX(); x++) {
                    for (int z = columns.minColumnZ(); z <= columns.maxColumnZ(); z++) {
                        var column = new ChunkPos(x, z);
                        if (padColumns.add(column)) { moon.getChunkSource().addRegionTicket(PAD, column, 2, key); }
                        moon.getChunk(x, z);
                    }
                }
            }
            if (EscrowWarmupFixtures.cold(server, pad) || !journal.retryChecked(server) || !RocketRuntime.resourceMutationAllowed(rocket)) { return; }
            if (kind == Kind.ESCROW && !ResourceEscrowSavedData.existing(server).bind(server).governedAvailable(filled.logicalRocketId())) { return; }
            request = UUID.randomUUID(); requestedAt = helper.getTick();
            var result = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, request);
            helper.assertTrue(result.code() == RocketFlightRequestCode.JOURNAL_PENDING && !result.success(),
                    kind + " launch did not await its journal acknowledgement: " + result.code());
            // The launch promotes only its own countdown; the rocket keeps its state until the acknowledged countdown.
            helper.assertTrue(filled.equals(rocket.flightData().orElseThrow()), kind + " launch changed the rocket before its acknowledgement");
            step = 3;
        }

        private void countdown() {
            var server = level.getServer(); var journal = RocketTransferSavedData.get(server);
            var flight = rocket.flightData().orElseThrow();
            helper.assertTrue(flight.fuel().equals(filled.fuel()), kind + " launch changed the source fuel");
            var record = journal.find(request).orElse(null);
            if (record == null || flight.state() != RocketFlightState.COUNTDOWN || !journal.acknowledgedCurrent()) {
                helper.assertTrue(flight.state() == filled.state() || flight.state() == RocketFlightState.COUNTDOWN,
                        kind + " source left " + filled.state() + " before its countdown: " + flight.state());
                helper.assertTrue(helper.getTick() - requestedAt < 400, kind + " launch never reached its acknowledged countdown");
                return;
            }
            helper.assertTrue(record.phase() == RocketTransferPhase.PREPARED && record.nativeTransfer().isPresent() == (kind == Kind.ESCROW),
                    kind + " launch took the wrong journal branch: " + record.phase());
            helper.assertTrue(record.sourceFlightData().fuel().equals(filled.fuel())
                    && flight.plan().orElseThrow().destinationBody().equals(CelestialIds.MOON_ID), kind + " countdown lost its fuel or plan");
            if (kind == Kind.ESCROW) {
                var frozen = record.nativeTransfer().orElseThrow().sourceEnvelope(record);
                var source = RocketFlightNbtCodec.decode(frozen.getCompound("flight")).data().orElseThrow();
                helper.assertTrue(source.equals(filled), "The governed launch rewrote its checkpointed ASSEMBLED source");
            }
            helper.assertTrue(RocketFlightConsoleFixtures.send(pilot, rocket, () -> RocketRuntime.requestFlightIntent(pilot,
                    rocket.getId(), RocketFlightAction.CANCEL, flight.plan().orElseThrow().destinationTarget(),
                    UUID.randomUUID())), "The pilot could not open the flight menu");
            if (kind == Kind.ESCROW) {
                helper.assertTrue(EscrowRuntime.of(server).returns().handles(record, rocket), "The governed cancellation queued no return");
            }
            requestedAt = helper.getTick(); step = 4;
        }

        private void restored() {
            var server = level.getServer(); var journal = RocketTransferSavedData.get(server);
            var flight = rocket.flightData().orElseThrow();
            helper.assertTrue(rocket.isAlive() && flight.fuel().equals(filled.fuel()), kind + " cancellation lost the rocket or its fuel");
            helper.assertTrue(flight.state() == RocketFlightState.COUNTDOWN || flight.state() == RocketFlightState.FAILED_RECOVERABLE
                    || flight.state() == RocketFlightState.FUELED, kind + " cancellation was not applied; the source reached " + flight.state());
            var data = ResourceEscrowSavedData.existing(server);
            boolean armed = data != null && data.ledger().armedFlights().stream().anyMatch(arm -> arm.transfer().equals(request));
            var logical = filled.logicalRocketId();
            if (flight.state() != RocketFlightState.FUELED || armed || !journal.acknowledgedCurrent()
                    || journal.findByLogicalRocket(logical).isPresent() || !journal.actionable(logical) || !RocketEscrowGate.idle(rocket)) {
                helper.assertTrue(helper.getTick() - requestedAt < 800, kind + " cancellation did not restore the rocket: " + flight.state());
                return;
            }
            var origin = filled.currentOrigin();
            helper.assertTrue(rocket.getX() == origin.x() + 0.5 && rocket.getY() == origin.y() && rocket.getZ() == origin.z() + 0.5
                    && flight.plan().isEmpty() && flight.activeTransferId().isEmpty() && flight.launchReady()
                    && flight.currentTarget().equals(filled.currentTarget()) && RocketRuntime.resourceMutationAllowed(rocket),
                    kind + " cancellation did not restore a stationary launch-ready rocket at its origin");
            helper.assertTrue((rocket.escrowSection().ledger() != null) == (kind == Kind.ESCROW), kind + " cancellation changed the ledger binding");
            finish();
        }

        private void gate(boolean open) {
            EscrowRuntime.of(level.getServer()).loaderAdmissionForTests(level.getServer(), device(loader), open);
            gateOpen = open;
        }

        /** Nothing is created or lost while the fill runs: tank, bank and the amount escrow holds add up to 1,000. */
        private void conserved() {
            long sum = held(level.getServer(), rocket, List.of(loader)) + bank(rocket, false) + amount(loader);
            helper.assertTrue(sum == 1_000, "Conservation failed: " + sum + " of 1000");
        }

        private boolean settled() {
            var data = ResourceEscrowSavedData.existing(level.getServer());
            return data != null && data.operational() && data.bind(level.getServer()).unresolvedNaming(rocket.getUUID()).isEmpty()
                    && rocket.escrowSection().markers().isEmpty() && section(loader).markers().isEmpty();
        }

        private void finish() { undo(); helper.succeed(); }

        void undo() {
            finished = true;
            var server = level.getServer();
            if (loader != null) {
                if (gateOpen) { gate(false); }
                clear(level, loader.getBlockPos());
            }
            pilot = null;
            if (rocket != null) { rocket.discard(); }
            var moon = server.getLevel(CelestialIds.MOON_LEVEL);
            if (moon != null) { padColumns.forEach(column -> moon.getChunkSource().removeRegionTicket(PAD, column, 2, key)); }
            padColumns.clear(); area.release();
        }
    }
}
