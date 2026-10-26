package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowWarmupFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketMarginGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketWarmupGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadEntry;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W90b (Root decisions R21 item 4 and R22 amendments 1 and 4): the warm-up never weakens F1 or F2. A stale copy that it
 * loads is refused by F1's census; a discard after F2's {@code journal.put} releases only the warm-up; a named pad's
 * claim lasts only as long as its request. Each scenario has its own batch.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowGovernedWarmupRefusalGameTests {
    private EscrowGovernedWarmupRefusalGameTests() { }

    @GameTest(template = "empty", batch = "rocket_warmup_stale_copy", timeoutTicks = 1_800)
    public static void aStaleCopyThatTheWarmupLoadsIsRefusedByF1AndKept(GameTestHelper helper) {
        new StaleCopy(helper).start();
    }

    @GameTest(template = "empty", batch = "rocket_warmup_discard", timeoutTicks = 2_000)
    public static void aDiscardAfterPublicationReleasesOnlyTheWarmupAndTheFlightLands(GameTestHelper helper) {
        new Discard(helper).start();
    }

    @GameTest(template = "empty", batch = "rocket_warmup_named_pad", timeoutTicks = 2_000)
    public static void aNamedPadClaimIsReleasedWhileWarmingReservedForOthersAndClaimedByTheRelaunch(GameTestHelper helper) {
        new NamedPad(helper).start();
    }

    /** A cold launch whose warm-up reaches READY, then the relaunch; shared by the three scenarios. */
    private abstract static class Launch {
        final GameTestHelper helper;
        final ServerLevel earth, moon;
        final GameTestTickPacer pacer;
        final UUID key = UUID.randomUUID(), start = UUID.randomUUID(), launch = UUID.randomUUID();
        final BlockPos source;
        ResourceEscrowSavedData data;
        RocketEntity rocket;
        RocketFlightData departure;
        RocketWarmupGameTestSupport.View warming;
        RocketTransferRecord prepared;
        long observedAt = -1;
        int step;

        Launch(GameTestHelper helper, BlockPos source) {
            this.helper = helper; this.source = source; earth = helper.getLevel(); moon = earth.getServer().getLevel(CelestialIds.MOON_LEVEL);
            helper.assertTrue(moon != null, "Moon Level is missing");
            pacer = new GameTestTickPacer(earth.getServer());
        }
        void start() { helper.onEachTick(this::tick); }

        private void tick() {
            if (step == 99) { return; }
            pacer.pace();
            var server = earth.getServer(); var journal = RocketTransferSavedData.get(server);
            if (step == 0) {
                if (!journal.retryChecked(server)) { return; }
                data = ResourceEscrowSavedData.forTests(server); data.bind(server);
                earth.getChunkSource().addRegionTicket(FIXTURE, new ChunkPos(source), 2, key);
                rocket = governed(earth, source, data.ledger().id());
                setup(server); step = 1; return;
            }
            if (step == 1) {
                if (!columnReady(earth, source)) { return; }
                if (!rocket.isAddedToWorld()) { helper.assertTrue(earth.addFreshEntity(rocket), "Warm-up source publication failed"); return; }
                if (!journal.retryChecked(server) || !RocketWarmupGameTestSupport.live(server).isEmpty() || !prepare(server)) { return; }
                departure = rocket.flightData().orElseThrow();
                var result = request(start);
                warming = warmup(server, rocket);
                helper.assertTrue(result.code() == RocketFlightRequestCode.DESTINATION_LOADING && warming != null
                        && warming.state().equals("WARMING") && warming.request().equals(start)
                        && tickets(server, rocket).equals(RocketWarmupGameTestSupport.columns(warming)),
                        "The cold launch did not start a warm-up: " + result.code() + " " + result.detailKey());
                unlaunched(journal);
                afterWarming(server); step = 2; return;
            }
            if (step == 2) {
                var current = warmup(server, rocket);
                helper.assertTrue(current != null && current.started() == warming.started(), "The warm-up ended while warming");
                unlaunched(journal);
                if (!current.state().equals("READY") || !readyToRelaunch(server) || !journal.retryChecked(server)) { return; }
                relaunch(server, journal); return;
            }
            later(server, journal);
        }

        void unlaunched(RocketTransferSavedData journal) {
            var logical = logical(rocket);
            helper.assertTrue(journal.findByLogicalRocket(logical).isEmpty()
                    && data.ledger().armedFlights().stream().noneMatch(arm -> arm.logical().equals(logical))
                    && RocketEscrowGate.idle(rocket) && rocket.flightData().orElseThrow().equals(departure),
                    "A refused or warming request armed, wrote a record or changed the rocket");
        }

        /** Waits for F8, then checks the single checked survivor on the warmed pad and the exact debit. */
        boolean landedOn(MinecraftServer server, RocketTransferSavedData journal, ServerLevel level) {
            if (journal.find(launch).isPresent()) { return false; }
            var origin = prepared.destinationSnapshot().sourceOrigin();
            var column = new ChunkPos(origin.x() >> 4, origin.z() >> 4);
            if (observedAt < 0) { observedAt = server.getTickCount(); level.getChunkSource().addRegionTicket(FIXTURE, column, 2, key); }
            if (data.ledger().armedFlights().stream().anyMatch(arm -> arm.transfer().equals(launch))) { return false; }
            var entity = level.getEntity(prepared.nativeTransfer().orElseThrow().armed().destination());
            if (entity == null) { helper.assertTrue(server.getTickCount() - observedAt < 400, "F8 lost the checked survivor"); return false; }
            var arrival = (RocketEntity) entity; var flight = arrival.flightData().orElseThrow();
            var debited = departure.fuel().debit(prepared.sourceFlightData().plan().orElseThrow()).state();
            helper.assertTrue(arrival.operational() && RocketEscrowGate.idle(arrival) && flight.state() == RocketFlightState.LANDED
                    && flight.fuel().committedDebits().equals(List.of(launch)) && flight.fuel().totalAmount() == debited.totalAmount()
                    && region(arrival.snapshot().orElseThrow()).equals(warming.region()), "The landing changed resources or its pad");
            helper.assertTrue(tickets(server, rocket).isEmpty() && RocketMarginGameTestSupport.flightTicketColumns(server, launch).isEmpty(),
                    "F8 left a flight or warm-up ticket");
            arrival.discard(); level.getChunkSource().removeRegionTicket(FIXTURE, column, 2, key);
            return true;
        }

        void finish() {
            earth.getChunkSource().removeRegionTicket(FIXTURE, new ChunkPos(source), 2, key);
            step = 99; helper.succeed();
        }

        void setup(MinecraftServer server) { }
        /** Fixture preparation before the first request; false to wait. */
        abstract boolean prepare(MinecraftServer server);
        abstract RocketFlightRequestResult request(UUID request);
        void afterWarming(MinecraftServer server) { }
        boolean readyToRelaunch(MinecraftServer server) { return true; }
        abstract void relaunch(MinecraftServer server, RocketTransferSavedData journal);
        abstract void later(MinecraftServer server, RocketTransferSavedData journal);
    }

    /**
     * Design test 5: a copy of L is saved in the predicted pad's margin column while that column is unloaded. The cold
     * launch's warm-up loads it; at READY the relaunch is refused by F1's census (TRANSFER_JOURNAL_BLOCKED), with no
     * ARMED, record, debit, discard or cancellation, the copy is still there, and the warm-up is released.
     */
    private static final class StaleCopy extends Launch {
        private RocketStructureSnapshot pad;
        private UUID copy;
        private BlockPos copyAt;
        private int stage;

        StaleCopy(GameTestHelper helper) { super(helper, new BlockPos(656_008, 100, 656_008)); }

        @Override boolean prepare(MinecraftServer server) {
            if (stage == 0) {
                var picks = predict(server, moon, rocket, 1);
                helper.assertTrue(picks != null, "No admissible Moon pad");
                pad = picks.get(0);
                var origin = pad.sourceOrigin();
                // In the census margin (x - 1 .. x + 2) but outside the pad's own box and structure.
                copyAt = new BlockPos(origin.x() + 2, origin.y(), origin.z());
                helper.assertTrue(new ChunkPos(copyAt).equals(RocketDiscoveryColumns.of(pad).origin()), "The copy is not in the margin column");
                moon.getChunkSource().addRegionTicket(FIXTURE, new ChunkPos(copyAt), 2, key);
                stage = 1; return false;
            }
            if (stage == 1) {
                if (!columnReady(moon, copyAt)) { return false; }
                var created = ModEntities.ROCKET.get().create(moon);
                helper.assertTrue(created != null, "Missing copy rocket type");
                var snapshot = rocket.snapshot().orElseThrow().relocated(UUID.randomUUID(), moon.dimension().location(),
                        new RocketPosition(copyAt.getX(), copyAt.getY(), copyAt.getZ()), moon.getGameTime());
                created.initialize(snapshot, logical(rocket), rocket.ownerId().orElseThrow());
                helper.assertTrue(moon.addFreshEntity(created), "The stale copy was not added");
                copy = created.getUUID();
                moon.getChunkSource().removeRegionTicket(FIXTURE, new ChunkPos(copyAt), 2, key);
                stage = 2; return false;
            }
            // Saved with its chunk: the column is unloaded and the copy is in no entity index.
            // Our temporary ticket is gone; diagnose a permanent hold before the unload checks can short-circuit.
            requireReleasable(helper, server, List.of(pad));
            return moon.getChunkSource().getChunkNow(copyAt.getX() >> 4, copyAt.getZ() >> 4) == null
                    && moon.getEntity(copy) == null && cold(server, pad);
        }

        @Override RocketFlightRequestResult request(UUID request) {
            return RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, request);
        }

        @Override void afterWarming(MinecraftServer server) {
            helper.assertTrue(warming.region().equals(region(pad)), "The warm-up did not take the copy's pad");
        }

        /** The copy read back from its chunk: a new instance with the same UUID and logical rocket. */
        private RocketEntity loadedCopy() {
            return moon.getEntity(copy) instanceof RocketEntity loaded && loaded.isAlive()
                    && loaded.assemblyTransactionId().filter(logical(rocket)::equals).isPresent() ? loaded : null;
        }

        @Override boolean readyToRelaunch(MinecraftServer server) {
            return loadedCopy() != null;
        }

        @Override void relaunch(MinecraftServer server, RocketTransferSavedData journal) {
            var result = request(launch);
            helper.assertTrue(result.code() == RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED,
                    "F1 did not refuse the stale copy: " + result.code() + " " + result.detailKey());
            unlaunched(journal);
            helper.assertTrue(journal.find(launch).isEmpty() && warmup(server, rocket) == null && tickets(server, rocket).isEmpty(),
                    "The refused launch kept its warm-up or wrote a record");
            var kept = loadedCopy();
            helper.assertTrue(kept != null, "Something discarded the stale copy");
            helper.assertTrue(EscrowGovernedPreparation.pendingDestinations(server).stream().noneMatch(region(pad)::equals),
                    "The refused launch kept a pending pad");
            kept.discard(); rocket.discard(); finish();
        }

        @Override void later(MinecraftServer server, RocketTransferSavedData journal) { }
    }

    /**
     * R22 amendment 4: F2's countdown effect fails once after {@code journal.put} (a test cut throws at
     * F2_PREPARED_ACK), so the launch's {@code discarded} callback runs with PREPARED published. It releases the warm-up
     * and nothing else: the record, its ARMED entry and its journal reservation stay, F3 starts the countdown itself,
     * and the flight lands on the warmed pad with the exact debit once.
     */
    private static final class Discard extends Launch {
        private final AtomicBoolean thrown = new AtomicBoolean();
        private RocketStructureSnapshot pad;
        private long relaunchedAt;

        Discard(GameTestHelper helper) { super(helper, new BlockPos(658_008, 100, 658_008)); }

        @Override boolean prepare(MinecraftServer server) {
            var picks = predict(server, moon, rocket, 1);
            helper.assertTrue(picks != null, "No admissible Moon pad");
            pad = picks.get(0);
            requireReleasable(helper, server, picks);
            return cold(server, pad);
        }

        @Override RocketFlightRequestResult request(UUID request) {
            return RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, request);
        }

        @Override void relaunch(MinecraftServer server, RocketTransferSavedData journal) {
            // The cut removes itself before it throws, so it can never reach another test's flight.
            EscrowGameTestTerminalCut.install(helper, checkpoint -> {
                if (checkpoint.equals("F2_PREPARED_ACK") && thrown.compareAndSet(false, true)) {
                    EscrowRuntime.of(server).terminalCutForTests(server, null);
                    throw new IllegalStateException("W90b fixture: F2's countdown effect fails after journal.put");
                }
            });
            var result = request(launch);
            var bound = warmup(server, rocket);
            if (result.code() != RocketFlightRequestCode.JOURNAL_PENDING) { EscrowRuntime.of(server).terminalCutForTests(server, null); }
            helper.assertTrue(result.code() == RocketFlightRequestCode.JOURNAL_PENDING && bound != null
                    && bound.state().equals("BOUND") && launch.equals(bound.transfer()), "The relaunch did not bind: " + result.code());
            relaunchedAt = server.getTickCount(); step = 3;
        }

        @Override void later(MinecraftServer server, RocketTransferSavedData journal) {
            if (step == 3) {
                if (!thrown.get()) {
                    var bound = warmup(server, rocket);
                    if (bound == null || server.getTickCount() - relaunchedAt > 400) { EscrowRuntime.of(server).terminalCutForTests(server, null); }
                    helper.assertTrue(bound != null && bound.state().equals("BOUND"), "The warm-up ended before the discard");
                    helper.assertTrue(server.getTickCount() - relaunchedAt <= 400, "F2's countdown effect never ran");
                    return;
                }
                var record = journal.find(launch).orElse(null);
                helper.assertTrue(record != null && record.phase() == RocketTransferPhase.PREPARED && record.nativeTransfer().isPresent(),
                        "The discard was not after publication");
                helper.assertTrue(warmup(server, rocket) == null && tickets(server, rocket).isEmpty(), "The discard kept the warm-up");
                helper.assertTrue(data.ledger().armedFlights().stream().anyMatch(arm -> arm.transfer().equals(launch))
                        && RocketWarmupGameTestSupport.launchReservations(server, journal, UUID.randomUUID()).contains(region(record.destinationSnapshot()))
                        && EscrowGovernedPreparation.pendingDestinations(server).stream().noneMatch(region(record.destinationSnapshot())::equals),
                        "The warm-up release touched ARMED or journal authority, or F1 kept its pending pad");
                prepared = record; step = 4; return;
            }
            if (landedOn(server, journal, moon)) { finish(); }
        }
    }

    /**
     * Design 7.4 and R22 answer 3 through the trusted admin pad entry. The named pad's chunks are loaded but not
     * entity-ticking, so selection succeeds and the warm-up starts: the tentative claim is released at once. Another
     * rocket's selection of the same pad is refused as reserved, and the READY relaunch claims the pad again and lands.
     */
    private static final class NamedPad extends Launch {
        private final BlockPos other = new BlockPos(660_040, 100, 660_008);
        private ServerLevel space;
        private StationState station;
        private LandingPadEntry entry;
        private RocketEntity rival;
        private List<ChunkPos> padColumns;

        NamedPad(GameTestHelper helper) { super(helper, new BlockPos(660_008, 100, 660_008)); }

        @Override void setup(MinecraftServer server) {
            space = server.getLevel(CelestialIds.SPACE_LEVEL);
            helper.assertTrue(space != null, "Space Level is missing");
            station = new StationCreationService(new StationPlatformGenerator(), id -> true)
                    .create(server, UUID.randomUUID(), "Warm-up Pad Station", CelestialIds.EARTH_ID, false).station().orElseThrow();
            var origin = new BlockPos(station.landingPad().x(), 80, station.landingPad().z());
            var pad = origin.offset(10, -1, 0);
            for (int x = -1; x <= 12; x++) {
                for (int z = -1; z <= 1; z++) {
                    var at = origin.offset(x, -1, z);
                    space.getChunk(at); space.setBlock(at, Blocks.GRAY_CONCRETE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            space.setBlock(pad, ModBlocks.LANDING_PAD.get().defaultBlockState(), Block.UPDATE_ALL);
            entry = LandingPadService.register(space, pad, station.ownerId(), false, "Warm-up Dock");
            helper.assertTrue(entry != null, "The named pad was not registered");
            // Loaded (FULL) but not entity-ticking: named selection never loads, and readiness needs entity ticking.
            var landing = rocket.snapshot().orElseThrow().relocated(UUID.randomUUID(), space.dimension().location(),
                    new RocketPosition(pad.getX(), pad.getY() + 1, pad.getZ()), space.getGameTime());
            padColumns = RocketDiscoveryColumns.of(landing).columns();
            padColumns.forEach(column -> space.getChunkSource().addRegionTicket(FIXTURE, column, 0, key));
            earth.getChunkSource().addRegionTicket(FIXTURE, new ChunkPos(other), 2, key);
            rival = governed(earth, other, data.ledger().id());
        }

        @Override boolean prepare(MinecraftServer server) {
            if (!columnReady(earth, other)) { return false; }
            if (!rival.isAddedToWorld()) { helper.assertTrue(earth.addFreshEntity(rival), "Rival publication failed"); return false; }
            return padColumns.stream().allMatch(column -> space.getChunkSource().getChunkNow(column.x, column.z) != null)
                    && padColumns.stream().noneMatch(column -> space.isPositionEntityTicking(column.getWorldPosition()));
        }

        @Override RocketFlightRequestResult request(UUID request) {
            return RocketRuntime.requestAdminPadFlight(rocket, station.stationId(), entry.reference(), request);
        }

        private LandingPadEntry current() {
            return LandingPadService.resolve(earth.getServer(), station.stationId(), entry.reference());
        }

        @Override void afterWarming(MinecraftServer server) {
            helper.assertTrue(warming.level().equals(space.dimension().location()) && current().claim() == null,
                    "The warming request kept its named-pad claim");
            var refused = RocketRuntime.requestAdminPadFlight(rival, station.stationId(), entry.reference(), UUID.randomUUID());
            helper.assertTrue(refused.code() == RocketFlightRequestCode.LANDING_PAD_UNAVAILABLE
                    && "message.advancedrocketrycommunity.pad.reason.reserved".equals(refused.detailKey())
                    && warmup(server, rival) == null && current().claim() == null,
                    "Another rocket's selection was not refused as reserved: " + refused.code() + " " + refused.detailKey());
        }

        @Override void relaunch(MinecraftServer server, RocketTransferSavedData journal) {
            var result = request(launch);
            var bound = warmup(server, rocket);
            helper.assertTrue(result.code() == RocketFlightRequestCode.JOURNAL_PENDING && bound != null && bound.state().equals("BOUND")
                    && launch.equals(current().claim()), "The relaunch did not claim the pad: " + result.code() + " " + result.detailKey());
            step = 3;
        }

        @Override void later(MinecraftServer server, RocketTransferSavedData journal) {
            if (step == 3) {
                var record = journal.find(launch).orElse(null);
                if (record == null || !journal.acknowledgedCurrent()) { return; }
                prepared = record; step = 4; return;
            }
            if (!landedOn(server, journal, space)) { return; }
            padColumns.forEach(column -> space.getChunkSource().removeRegionTicket(FIXTURE, column, 0, key));
            earth.getChunkSource().removeRegionTicket(FIXTURE, new ChunkPos(other), 2, key);
            rival.discard(); finish();
        }
    }
}
