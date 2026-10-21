package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketMarginGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowRetireBeforeFillGameTests {
    private static final String BATCH = "escrow_retire_before_fill";
    private static final List<Scenario> RUNNING = new ArrayList<>();
    private EscrowRetireBeforeFillGameTests() { }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 2_400)
    public static void sameLevelLandedTransferRetiresBeforeFillAndGovernedLaunch(GameTestHelper helper) {
        new Scenario(helper, false, false, 48_000).start();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 2_400)
    public static void crossLevelLandedTransferRetiresBeforeFillAndGovernedLaunch(GameTestHelper helper) {
        new Scenario(helper, true, false, 48_008).start();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 2_400)
    public static void oldCopyLoadedAfterRetirementStartsIsDiscardedBeforeProof(GameTestHelper helper) {
        new Scenario(helper, true, true, 48_016).start();
    }

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) { RUNNING.forEach(Scenario::cleanup); RUNNING.clear(); }

    private static final class Scenario {
        final GameTestHelper helper;
        final ServerLevel level, oldLevel;
        final Area destination;
        final RocketPosition oldOrigin;
        final GameTestTickPacer pacer;
        final boolean stale;
        RocketEntity rocket, oldCopy, arrival;
        FluidFuelLoaderBlockEntity loader;
        RocketTransferRecord record, next;
        UUID request;
        int step, fills;
        boolean refused, sawCountdown, finished;

        Scenario(GameTestHelper helper, boolean cross, boolean stale, int chunk) {
            this.helper = helper; this.stale = stale; level = helper.getLevel();
            oldLevel = cross ? level.getServer().getLevel(CelestialIds.MOON_LEVEL) : level;
            destination = new Area(level, chunk, 48_000);
            oldOrigin = new RocketPosition((chunk + 200) * 16 + 8, 100, 48_000 * 16 + 8);
            pacer = new GameTestTickPacer(level.getServer());
        }
        void start() { RUNNING.add(this); destination.hold(); helper.onEachTick(this::tick); }
        void tick() {
            if (finished) { return; }
            pacer.pace();
            var server = level.getServer(); var journal = RocketTransferSavedData.get(server);
            boolean checked = journal.retryChecked(server);
            if (!destination.ticking() || step == 0 && !checked) { return; }
            if (step == 0) {
                rocket = rocket(level, destination.at(8, 100, 8), UUID.randomUUID(), mono(4_000));
                helper.assertTrue(level.addFreshEntity(rocket), "Landed fixture publication failed");
                record = EscrowLandedFillFixtures.landed(level, journal, rocket, oldLevel.dimension().location(), oldOrigin);
                loader = loader(level, destination.at(10, 100, 8), rocket.ownerId().orElseThrow(), tank(ROCKET_FUEL, 5_000), binding(rocket));
                step = 1; return;
            }
            if (step == 1) {
                if (!RocketRuntime.resourceMutationAllowed(rocket)) { return; }
                RocketMarginGameTestSupport.removeFlightTickets(server, record);
                step = 2; return;
            }
            if (step == 2) {
                if (oldLevel.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(oldOrigin.x() >> 4, oldOrigin.z() >> 4))) { return; }
                helper.assertTrue(journal.find(record.transferId()).isPresent(), "Startup retired a landed record without demand");
                attempt();
                helper.assertTrue(loader.lastAttempt() == FluidFuelLoaderStatus.TRANSFER_JOURNAL_UNPROVED,
                        "First fill did not wait for retirement: " + loader.lastAttempt());
                helper.assertTrue(amount(loader) == 5_000 && bank(rocket, false) == 0, "Retirement admission moved fuel");
                refused = true; step = 3; return;
            }
            if (step == 3) {
                if (stale && oldCopy == null) {
                    if (!oldLevel.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(oldOrigin.x() >> 4, oldOrigin.z() >> 4))) { return; }
                    helper.assertTrue(journal.find(record.transferId()).isPresent(), "Late-copy setup missed the retirement window");
                    oldCopy = ModEntities.ROCKET.get().create(oldLevel);
                    helper.assertTrue(oldCopy != null, "Old copy creation failed");
                    oldCopy.initializeTransferred(record.sourceSnapshot(), record.logicalRocketId(), record.ownerId(), record.sourceFlightData());
                    oldCopy.setUUID(record.sourceEntityId()); oldCopy.setPos(oldOrigin.x() + .5, oldOrigin.y(), oldOrigin.z() + .5);
                    helper.assertTrue(oldLevel.addFreshEntity(oldCopy), "Late copy publication failed");
                }
                if (journal.find(record.transferId()).isPresent() || journal.acknowledgedGuard(record.logicalRocketId()).isEmpty()) { return; }
                helper.assertTrue(!stale || oldCopy != null && oldCopy.isRemoved(), "Retirement did not discard the late source copy");
                helper.assertTrue(RocketMarginGameTestSupport.flightTicketColumns(server, record.transferId()).isEmpty(), "Retirement kept old flight tickets");
                helper.assertTrue(!new RocketLandingPadSelector().availableLoaded(level, rocket.snapshot().orElseThrow()),
                        "The retired rocket's occupied pad became available");
                step = 4;
            }
            if (step == 4) {
                if (fills == 0 || idle()) {
                    if (fills < 2) {
                        attempt();
                        if (loader.lastAttempt() == FluidFuelLoaderStatus.PREPARING) { fills++; }
                        return;
                    }
                    helper.assertTrue(refused && bank(rocket, false) == 2_000 && amount(loader) == 3_000,
                            "Two NONE fills did not conserve their fuel");
                    var ledger = ResourceEscrowSavedData.existing(server).ledger();
                    helper.assertTrue(ledger.floors().stream().noneMatch(f -> record.logicalRocketId().equals(f.getUUID("rocket_id"))),
                            "NONE fills left a transfer floor");
                    step = 5;
                } else { return; }
            }
            if (step == 5) {
                var flight = rocket.flightData().orElseThrow();
                if (flight.state() == RocketFlightState.COUNTDOWN) {
                    sawCountdown = true; next = journal.findByLogicalRocket(record.logicalRocketId()).orElseThrow();
                    helper.assertTrue(next.nativeTransfer().isPresent(), "The launch was not governed"); step = 6; return;
                }
                if (request != null && journal.find(request).isPresent()) { return; }
                request = UUID.randomUUID();
                var launch = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, request);
                helper.assertTrue(launch.code() == RocketFlightRequestCode.JOURNAL_PENDING || launch.code() == RocketFlightRequestCode.SUCCESS
                                || launch.code() == RocketFlightRequestCode.DESTINATION_LOADING,
                        "Post-fill launch refused: " + launch.code());
                return;
            }
            if (step == 6) {
                var current = journal.find(next.transferId()).orElse(null);
                if (current != null) {
                    current.destinationEntityId().ifPresent(id -> {
                        var moon = server.getLevel(CelestialIds.MOON_LEVEL);
                        if (moon.getEntity(id) instanceof RocketEntity found) { arrival = found; }
                    });
                    return;
                }
                if (arrival == null || !RocketEscrowGate.resourcesIdle(arrival)) { return; }
                helper.assertTrue(sawCountdown && arrival.flightData().orElseThrow().state() == RocketFlightState.LANDED,
                        "Governed launch did not complete");
                finished = true; cleanup(); helper.succeed();
            }
        }
        void attempt() {
            var runtime = EscrowRuntime.of(level.getServer());
            runtime.loaderAdmissionForTests(level.getServer(), device(loader), true);
            try { loader.admitFromQueue(); }
            finally { runtime.loaderAdmissionForTests(level.getServer(), device(loader), false); }
        }
        boolean idle() {
            var data = ResourceEscrowSavedData.existing(level.getServer());
            return data != null && data.bind(level.getServer()).unresolvedNaming(rocket.getUUID()).isEmpty()
                    && !section(loader).locked() && rocket.escrowSection().markers().isEmpty();
        }
        void cleanup() {
            if (loader != null) {
                EscrowRuntime.of(level.getServer()).loaderAdmissionForTests(level.getServer(), device(loader), false);
                clear(level, loader.getBlockPos()); loader = null;
            }
            if (rocket != null) { rocket.discard(); }
            if (arrival != null) { arrival.discard(); }
            if (oldCopy != null) { oldCopy.discard(); }
            destination.release();
        }
    }
}
