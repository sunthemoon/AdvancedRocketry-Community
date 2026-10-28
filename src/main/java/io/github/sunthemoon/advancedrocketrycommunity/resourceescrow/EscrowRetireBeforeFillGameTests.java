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
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRetiredPadGameTestProbe;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowRetireBeforeFillGameTests {
    private static final String BATCH = "escrow_retire_before_fill";
    private static final String CAPTURE_CONTENTION = "w164_retire_fill_capture_turn";
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

    @GameTest(template = "empty", batch = CAPTURE_CONTENTION, timeoutTicks = 2_400)
    public static void postFillLaunchWaitsForAnAvailableCaptureTurn(GameTestHelper helper) {
        new Scenario(helper, false, false, 48_024, true).start();
    }

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) { RUNNING.forEach(Scenario::cleanup); RUNNING.clear(); }

    @AfterBatch(batch = CAPTURE_CONTENTION)
    public static void cleanupCaptureContention(ServerLevel level) { cleanup(level); }

    private static final class Scenario {
        final GameTestHelper helper;
        final ServerLevel level, oldLevel;
        final Area destination;
        final RocketPosition oldOrigin;
        final GameTestTickPacer pacer;
        final boolean stale;
        final boolean occupyCapture;
        boolean captureOccupied;
        EscrowReason occupiedCaptureResult;
        RocketEntity rocket, oldCopy, arrival;
        /** W166: the Moon arrival's identity, and its column, which this fixture holds from landing until removal. */
        UUID arrivalId;
        Area arrivalColumn;
        FluidFuelLoaderBlockEntity loader;
        RocketTransferRecord record, next;
        UUID request;
        RocketRetiredPadGameTestProbe occupiedPad;
        int step, fills, removalTicks;
        boolean refused, sawCountdown, finished, launchPending;

        Scenario(GameTestHelper helper, boolean cross, boolean stale, int chunk) {
            this(helper, cross, stale, chunk, false);
        }
        Scenario(GameTestHelper helper, boolean cross, boolean stale, int chunk, boolean occupyCapture) {
            this.helper = helper; this.stale = stale; level = helper.getLevel();
            this.occupyCapture = occupyCapture;
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
            if (!destination.ticking() && step != 8 || step == 0 && !checked) { return; }
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
                if (stale) { occupiedPad = new RocketRetiredPadGameTestProbe(helper, rocket); step = 7; }
                else { step = 4; }
            }
            if (step == 7) {
                if (!occupiedPad.prepare()) { return; }
                destination.release(); step = 8; return;
            }
            if (step == 8) {
                if (level.areEntitiesLoaded(destination.chunk.toLong())) { return; }
                occupiedPad.cold(); destination.hold(); step = 9; return;
            }
            if (step == 9) {
                if (!destination.ticking()) { return; }
                rocket = (RocketEntity) level.getEntity(record.destinationEntityId().orElseThrow());
                loader = (FluidFuelLoaderBlockEntity) level.getBlockEntity(destination.at(10, 100, 8));
                helper.assertTrue(rocket != null && loader != null, "Retired pad lost its saved occupant or loader");
                if (!occupiedPad.blocked()) { return; }
                occupiedPad.close(); occupiedPad = null; step = 4;
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
                if (launchPending) { return; }
                if (occupyCapture && !captureOccupied) {
                    var runtime = EscrowRuntime.of(server); var barrier = runtime.barrier();
                    if (!barrier.availableOrdinary(level.dimension().location().toString(), server.getTickCount())) { return; }
                    // Another native consumer can spend this tick's one shared capture before the launch callback.
                    captureOccupied = barrier.beginOrdinary(runtime.store(level), level.dimension().location().toString(),
                            destination.chunk, root -> true, server.getTickCount(), label -> { }, result -> {
                                occupiedCaptureResult = result.reason();
                                if (result.proof() != null) { result.proof().release(); }
                            });
                    helper.assertTrue(captureOccupied, "The competing native capture was not admitted");
                }
                request = UUID.randomUUID();
                String beforeLaunch = launchState();
                var launch = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, request);
                helper.assertTrue(launch.code() == RocketFlightRequestCode.JOURNAL_PENDING || launch.code() == RocketFlightRequestCode.SUCCESS
                                || launch.code() == RocketFlightRequestCode.DESTINATION_LOADING,
                        "Post-fill launch refused: " + launch.code() + " before={" + beforeLaunch
                                + "} after={" + launchState() + "}");
                launchPending = launch.code() == RocketFlightRequestCode.JOURNAL_PENDING || launch.code() == RocketFlightRequestCode.SUCCESS;
                return;
            }
            if (step == 6) {
                var current = journal.find(next.transferId()).orElse(null);
                if (current != null) {
                    current.destinationEntityId().ifPresent(id -> {
                        var moon = server.getLevel(CelestialIds.MOON_LEVEL);
                        if (moon.getEntity(id) instanceof RocketEntity found) {
                            arrival = found;
                            if (arrivalColumn == null) {
                                // The flight's tickets still hold the column. F8 releases them before this fixture is
                                // done, and an unloaded chunk would leave the cached instance stale and its copy on disk.
                                arrivalId = id;
                                arrivalColumn = new Area(moon, found.chunkPosition().x, found.chunkPosition().z);
                                arrivalColumn.hold();
                            }
                        }
                    });
                    return;
                }
                if (arrival == null || !RocketEscrowGate.resourcesIdle(arrival)) { return; }
                helper.assertTrue(sawCountdown && arrival.flightData().orElseThrow().state() == RocketFlightState.LANDED,
                        "Governed launch did not complete");
                step = 10;
            }
            if (step >= 10) { removeArrival(); }
        }
        /**
         * W166: discards the arrival by its UUID while its held column has its entity sections loaded, then lets the
         * column unload, loads it again from disk and checks that no copy of the arrival came back. Discarding a cached
         * instance after its chunk had unloaded removed nothing on disk, and the copy later blocked other batches' pads.
         */
        void removeArrival() {
            var moon = arrivalColumn.level;
            helper.assertTrue(++removalTicks < 200, "The Moon arrival's removal check stalled at step " + step);
            if (step == 10) {
                if (!arrivalColumn.ticking()) { return; }
                var live = moon.getEntity(arrivalId);
                helper.assertTrue(live instanceof RocketEntity, "The held arrival column lost its landed rocket " + arrivalId);
                live.discard(); step = 11; return;
            }
            if (step == 11) {
                arrivalColumn.release();
                if (moon.areEntitiesLoaded(arrivalColumn.chunk.toLong())) { return; }
                arrivalColumn.hold(); step = 12; return;
            }
            if (!arrivalColumn.ticking()) { return; }
            var chunk = arrivalColumn.chunk;
            var copies = moon.getEntitiesOfClass(RocketEntity.class, new AABB(chunk.getMinBlockX(), moon.getMinBuildHeight(),
                    chunk.getMinBlockZ(), chunk.getMaxBlockX() + 1, moon.getMaxBuildHeight(), chunk.getMaxBlockZ() + 1),
                    found -> found.assemblyTransactionId().filter(next.logicalRocketId()::equals).isPresent());
            helper.assertTrue(moon.getEntity(arrivalId) == null && copies.isEmpty(),
                    "The discarded Moon arrival " + arrivalId + " came back from disk (" + copies.size() + " copies in " + chunk + ")");
            helper.assertTrue(!occupyCapture || captureOccupied && (occupiedCaptureResult == EscrowReason.NONE
                            || occupiedCaptureResult == EscrowReason.SUPERSEDING_SAVE),
                    "The competing capture did not complete: " + occupiedCaptureResult);
            finished = true; cleanup(); helper.succeed();
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
        String launchState() {
            var server = level.getServer(); var journal = RocketTransferSavedData.get(server);
            var logical = record.logicalRocketId(); var runtime = EscrowRuntime.of(server);
            return "tick=" + server.getTickCount() + " idle=" + RocketEscrowGate.idle(rocket)
                    + " mutation=" + RocketRuntime.resourceMutationAllowed(rocket)
                    + " governed=" + ResourceEscrowSavedData.existing(server).bind(server).governedAvailable(logical)
                    + " authority=" + journal.actionAuthorityAvailable(logical)
                    + " barrier=" + runtime.barrier().available(level.dimension().location().toString(), true, server.getTickCount())
                    + " effects=" + journal.outstandingEffectCount() + " dirty=" + journal.isDirty()
                    + " guard=" + journal.logicalGuard(logical);
        }
        void cleanup() {
            if (occupiedPad != null) { occupiedPad.close(); occupiedPad = null; }
            if (loader != null) {
                EscrowRuntime.of(level.getServer()).loaderAdmissionForTests(level.getServer(), device(loader), false);
                clear(level, loader.getBlockPos()); loader = null;
            }
            if (rocket != null) { rocket.discard(); }
            if (arrivalColumn != null) {
                // After a failure too: the held column keeps the live instance, never a stale cached one.
                if (arrivalColumn.level.getEntity(arrivalId) instanceof RocketEntity live) { live.discard(); }
                arrivalColumn.release();
            }
            if (oldCopy != null) { oldCopy.discard(); }
            destination.release();
        }
    }
}
