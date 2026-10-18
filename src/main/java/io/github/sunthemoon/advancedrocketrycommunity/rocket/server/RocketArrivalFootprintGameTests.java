package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.AuditLogCapture;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketScanResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W83b (Root decision R32): a due arrival waits at PREPARED until every column of its destination ticket set has its
 * entity sections loaded, its destination flight tickets cover that set so the wait ends, and an ordinary legacy flight
 * whose footprint crosses a chunk border still lands with no player near the destination.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketArrivalFootprintGameTests {
    private static final TicketType<UUID> FIXTURE = TicketType.create(
            "arce_gametest_arrival_footprint", Comparator.<UUID>naturalOrder(), 700);
    /** Far Moon columns that no other GameTest uses: x 1500..1503 from x = 16 * 1500 + 8; one z row per test. */
    private static final int BASE = 24_008;
    private static final int ARRIVAL_Y = 200;
    /** The arrival's second block: x + 44 lies in column 1503, three columns from the origin's 1500. */
    private static final int FAR_BLOCK = 44;

    private RocketArrivalFootprintGameTests() { }

    @GameTest(template = "rocket_test", batch = "arrival_footprint", timeoutTicks = 600)
    public static void parkedRocketInAnUnloadedFootprintColumnHoldsThenRefusesTheArrival(GameTestHelper helper) {
        new Footprint(helper, BASE, true).start();
    }

    @GameTest(template = "rocket_test", batch = "arrival_footprint", timeoutTicks = 400)
    public static void readyFreeFootprintPlacesTheArrivalOnItsFirstDueVisit(GameTestHelper helper) {
        new Footprint(helper, BASE + 512, false).start();
    }

    @GameTest(template = "rocket_test", batch = "arrival_footprint", timeoutTicks = 700)
    public static void legacyStationFlightAcrossAChunkBorderLandsWithNoPlayerNearby(GameTestHelper helper) {
        new BorderFlight(helper).start();
    }

    /** A detached journal and service, as RocketConcurrentFlightGameTests drives them, against a far Moon footprint. */
    private static final class Footprint {
        final GameTestHelper helper;
        final boolean parkedCase;
        final ServerLevel earth;
        final ServerLevel moon;
        final GameTestTickPacer pacer;
        final RocketTransferService service = new RocketTransferService();
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final BlockPos origin;
        final ChunkPos originColumn;
        final ChunkPos farColumn;
        final UUID ticket = UUID.randomUUID();
        final List<Map.Entry<ChunkPos, Integer>> fixtureTickets = new ArrayList<>();
        final UUID transfer = UUID.randomUUID();
        final UUID logical = UUID.randomUUID();
        final UUID owner = UUID.randomUUID();
        AuditLogCapture log;
        Path directory;
        RocketEntity source;
        RocketTransferRecord record;
        UUID parkedId;
        RocketEntity destination;
        int stage, waits;
        boolean visited, closed;

        Footprint(GameTestHelper helper, int z, boolean parkedCase) {
            this.helper = helper; this.parkedCase = parkedCase;
            earth = helper.getLevel();
            moon = earth.getServer().getLevel(CelestialIds.MOON_LEVEL);
            helper.assertTrue(moon != null, "Moon Level is unavailable");
            pacer = new GameTestTickPacer(earth.getServer());
            origin = new BlockPos(BASE, ARRIVAL_Y, z);
            originColumn = new ChunkPos(origin);
            farColumn = new ChunkPos(origin.offset(FAR_BLOCK, 0, 0));
        }

        void start() { helper.onEachTick(this::tick); }

        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < (parkedCase ? 560 : 360), "Arrival footprint fixture exceeded its bounded wait at stage " + stage);
                switch (stage) {
                    case 0 -> begin();
                    case 1 -> park();
                    case 2 -> awaitParkedUnload();
                    case 3 -> awaitOrigin();
                    case 4 -> { if (parkedCase) { flyIntoParked(); } else { flyIntoFree(); } }
                    case 5 -> awaitPlacement();
                    default -> { }
                }
                if (!closed) { pacer.pace(); }
            } catch (IOException failure) { close(); throw new java.io.UncheckedIOException(failure); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        void begin() {
            helper.assertTrue(originColumn.x + 3 == farColumn.x && originColumn.z == farColumn.z, "Fixture columns moved");
            if (parkedCase) { addTicket(farColumn, 0); stage = 1; }
            else { addTicket(originColumn, 2); addTicket(farColumn, 0); stage = 3; }
        }

        /** The far column's entity sections are loaded only by this FULL fixture ticket; the rocket parks there. */
        void park() {
            if (!moon.areEntitiesLoaded(farColumn.toLong())) { return; }
            var iron = new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of());
            var blocks = new ArrayList<RocketBlock>();
            for (int x = -10; x <= 0; x++) { blocks.add(new RocketBlock(new RocketPosition(x, 0, 0), iron)); }
            var at = origin.offset(FAR_BLOCK + 8, 0, 0);
            var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), moon.dimension().location(), position(at), blocks,
                    List.of(), new RocketStats(blocks.size(), blocks.size(), 0, 0, 0, 0, 0, 0), moon.getGameTime());
            var parked = ModEntities.ROCKET.get().create(moon);
            helper.assertTrue(parked != null, "Rocket entity creation failed");
            parked.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID());
            RocketTransferEntities.positionAtOrigin(parked, snapshot.sourceOrigin());
            helper.assertTrue(moon.addFreshEntity(parked) && new ChunkPos(parked.blockPosition()).equals(farColumn),
                    "The parked rocket was not admitted in the far footprint column");
            var arrival = RocketRegion.fromSnapshot(arrivalSnapshot());
            helper.assertTrue(RocketRegion.fromSnapshot(snapshot).overlaps(arrival), "The parked structure misses the arrival");
            helper.assertTrue(!parked.getBoundingBox().intersects(new AABB(arrival.minimum().x(), arrival.minimum().y(),
                    arrival.minimum().z(), arrival.maximum().x() + 1, arrival.maximum().y() + 1, arrival.maximum().z() + 1)),
                    "The parked entity box overlaps the arrival, so the census would not be exercised");
            parkedId = parked.getUUID();
            removeTicket(farColumn, 0);
            stage = 2;
        }

        void awaitParkedUnload() {
            if (moon.areEntitiesLoaded(farColumn.toLong()) || moon.getEntity(parkedId) != null) { return; }
            addTicket(originColumn, 2);
            stage = 3;
        }

        void awaitOrigin() throws IOException {
            var destinationSnapshot = arrivalSnapshot();
            if (!RocketTransferEntities.destinationEntityChunkReady(moon, destinationSnapshot)) { return; }
            if (parkedCase) {
                helper.assertTrue(!moon.areEntitiesLoaded(farColumn.toLong()) && moon.getEntity(parkedId) == null,
                        "The origin fixture loaded the parked column");
            } else if (RocketTransferEntities.entitiesLoadedColumns(RocketTransferEntities.destinationTicketSet(destinationSnapshot),
                    moon::areEntitiesLoaded) < 4) {
                return;
            }
            helper.assertTrue(moon.getBlockState(origin).isAir(), "The arrival origin is not air");
            setUpFlight(destinationSnapshot);
            stage = 4;
        }

        void setUpFlight(RocketStructureSnapshot destinationSnapshot) throws IOException {
            var columns = RocketDiscoveryColumns.of(destinationSnapshot);
            helper.assertTrue(columns.admissible() && columns.count() == 4, "The arrival's checked set is not 4 columns: " + columns);
            directory = Files.createTempDirectory("arce-arrival-footprint-");
            var file = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
            long now = earth.getGameTime(), due = now - RocketFlightLimits.TRANSIT_TICKS;
            var propulsion = propulsion();
            var sourceSnapshot = destinationSnapshot.relocated(UUID.randomUUID(), earth.dimension().location(),
                    position(helper.absolutePos(new BlockPos(3, 2, 3))), due);
            var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    earth.dimension().location(), moon.dimension().location(), 500, due);
            var fuel = RocketFuelState.empty(propulsion).fill(1_000).state();
            var transit = RocketFlightData.initial(logical, 1_000, 1, plan.sourceBody(), plan.sourceDimension(),
                    sourceSnapshot.sourceOrigin(), due).withFuel(fuel, due).withPlan(plan).startCountdown(due)
                    .completeCountdown(due).beginTransit(transfer, due);
            var arrival = transit.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(), plan.destinationDimension(),
                    destinationSnapshot.sourceOrigin(), now);
            source = ModEntities.ROCKET.get().create(earth);
            helper.assertTrue(source != null, "No source entity");
            record = RocketTransferRecord.create(transfer, logical, owner, source.getUUID(), sourceSnapshot, destinationSnapshot,
                    transit, arrival, 500, now);
            source.initializeTransferred(sourceSnapshot, logical, owner, transit);
            RocketTransferEntities.positionAtAltitude(source, sourceSnapshot.sourceOrigin());
            helper.assertTrue(earth.addFreshEntity(source), "Source publication failed");
            journal.put(record);
            RocketJournalGameTestSupport.confirmDetached(helper, journal, file);
            RocketFailBackAdmissionGameTests.live(service).add(transfer);
            log = new AuditLogCapture("ARCE_");
        }

        /** R32 item 1: the first due visit waits with the new reason; the tickets then load the parked column. */
        void flyIntoParked() {
            var server = earth.getServer();
            journal.pollWriter(server.getTickCount());
            var current = journal.find(transfer).orElse(null);
            assertNeverPlaced(current);
            if (current != null && journal.actionable(logical)) {
                boolean farLoaded = moon.areEntitiesLoaded(farColumn.toLong());
                int before = lines("ARCE_ARRIVAL_ENTITIES_LOADING transfer=" + transfer).size();
                service.tickLive(server, journal, current);
                var after = journal.find(transfer).orElse(null);
                if (!visited) {
                    visited = true;
                    var loading = lines("ARCE_ARRIVAL_ENTITIES_LOADING transfer=" + transfer);
                    helper.assertTrue(!farLoaded && after == current && loading.size() == 1
                                    && loading.get(0).contains(" reason=entities_loading")
                                    && loading.get(0).contains(" level=" + moon.dimension().location())
                                    && loading.get(0).matches(".* columns=[0-3]/4 .*"),
                            "The first due visit did not wait for the unloaded footprint column: " + loading);
                    helper.assertTrue(destinationFlightTickets().containsAll(expectedDestinationTickets()),
                            "The live visit did not ticket the destination's checked set");
                } else if (after == current) {
                    helper.assertTrue(lines("ARCE_ARRIVAL_ENTITIES_LOADING transfer=" + transfer).size() == before,
                            "A continuing wait logged again");
                }
            }
            journal.retryChecked(server);
            if (current == null && journal.clean()) { finishParked(); }
        }

        void finishParked() {
            helper.assertTrue(lines("ARCE_ARRIVAL_ENTITIES_LOADING transfer=" + transfer).size() == 1
                    && lines("ARCE_ARRIVAL_ENTITIES_READY transfer=" + transfer).size() == 1, "The wait did not log one line per transition");
            helper.assertTrue(lines("ARCE_PAD_OCCUPANCY_REFUSED reason=occupied").stream().anyMatch(line -> line.contains("rocket=" + parkedId)),
                    "The arrival was not refused by the parked rocket: " + lines("ARCE_PAD_OCCUPANCY_REFUSED"));
            helper.assertTrue(lines("ARCE_TRANSFER_RETURNED_TO_SOURCE transfer=" + transfer).stream()
                    .anyMatch(line -> line.contains("reason=destination_pad_blocked")), "The refused arrival did not return to its source");
            var parked = moon.getEntity(parkedId);
            helper.assertTrue(parked instanceof RocketEntity rocket && rocket.isAlive() && new ChunkPos(rocket.blockPosition()).equals(farColumn),
                    "The parked rocket was not reloaded in place");
            var flight = source.flightData().orElseThrow();
            helper.assertTrue(!source.isRemoved() && flight.state() == RocketFlightState.FUELED
                    && flight.fuel().equals(record.sourceFlightData().fuel()) && flight.activeTransferId().isEmpty(),
                    "The refused arrival did not keep its undebited source");
            succeed();
        }

        /** The ready, free footprint: no wait, and DESTINATION_SPAWNED on the first due visit, as before W83b. */
        void flyIntoFree() {
            var server = earth.getServer();
            service.tickLive(server, journal, record);
            var spawned = journal.find(transfer).orElse(null);
            helper.assertTrue(spawned != null, "A ready, free footprint returned to its source: " + lines("ARCE_"));
            helper.assertTrue(spawned.phase() == RocketTransferPhase.DESTINATION_SPAWNED && spawned.destinationEntityId().isPresent(),
                    "A ready, free footprint did not acquire destination authority on its first due visit");
            helper.assertTrue(lines("ARCE_ARRIVAL_ENTITIES_").stream().noneMatch(line -> line.contains("transfer=" + transfer)),
                    "A ready footprint waited");
            record = spawned; journal.retryChecked(server); stage = 5;
        }

        void awaitPlacement() {
            var server = earth.getServer();
            journal.pollWriter(server.getTickCount()); journal.retryChecked(server);
            if (!(moon.getEntity(record.destinationEntityId().orElseThrow()) instanceof RocketEntity placed)) { return; }
            destination = placed;
            helper.assertTrue(placed.snapshot().orElseThrow().equals(record.destinationSnapshot())
                    && placed.getX() == origin.getX() + 0.5D && placed.getZ() == origin.getZ() + 0.5D,
                    "The arrival was not placed at its reserved origin");
            helper.assertTrue(lines("ARCE_ARRIVAL_ENTITIES_").stream().noneMatch(line -> line.contains("transfer=" + transfer)),
                    "A ready footprint logged a wait");
            succeed();
        }

        void assertNeverPlaced(RocketTransferRecord current) {
            helper.assertTrue(current == null || current.phase() == RocketTransferPhase.PREPARED,
                    "The arrival acquired destination authority over the parked rocket");
            var area = new AABB(origin.getX() - 4, moon.getMinBuildHeight(), origin.getZ() - 4,
                    origin.getX() + FAR_BLOCK + 16, moon.getMaxBuildHeight(), origin.getZ() + 4);
            helper.assertTrue(moon.getEntitiesOfClass(RocketEntity.class, area).stream()
                    .noneMatch(rocket -> rocket.assemblyTransactionId().filter(logical::equals).isPresent()),
                    "An arrival rocket was placed over the parked rocket");
        }

        TreeSet<String> expectedDestinationTickets() {
            var expected = new TreeSet<String>();
            RocketTransferEntities.destinationTicketSet(record.destinationSnapshot())
                    .forEach(column -> expected.add(RocketMarginGameTestSupport.column(moon.dimension().location(), column)));
            return expected;
        }

        TreeSet<String> destinationFlightTickets() {
            var found = new TreeSet<String>();
            for (var column : RocketMarginGameTestSupport.flightTicketColumns(earth.getServer(), transfer)) {
                if (column.startsWith(moon.dimension().location() + "@")) { found.add(column); }
            }
            return found;
        }

        RocketStructureSnapshot arrivalSnapshot() {
            var iron = new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of());
            var blocks = List.of(new RocketBlock(new RocketPosition(0, 0, 0), iron), new RocketBlock(new RocketPosition(FAR_BLOCK, 0, 0), iron));
            return RocketStructureSnapshot.create(new UUID(origin.getZ(), FAR_BLOCK), moon.dimension().location(), position(origin), blocks,
                    List.of(new RocketPosition(0, 0, 0)), new RocketStats(2, 100, 1_000, 1_000, 1, 1, 1, 0), 0, propulsion());
        }

        List<String> lines(String prefix) {
            return log == null ? List.of() : log.lines().stream().filter(line -> line.startsWith(prefix)).toList();
        }

        void addTicket(ChunkPos column, int distance) {
            moon.getChunkSource().addRegionTicket(FIXTURE, column, distance, ticket);
            fixtureTickets.add(Map.entry(column, distance));
        }

        void removeTicket(ChunkPos column, int distance) {
            moon.getChunkSource().removeRegionTicket(FIXTURE, column, distance, ticket);
            fixtureTickets.remove(Map.entry(column, distance));
        }

        void succeed() { close(); helper.succeed(); }

        void close() {
            if (closed) { return; }
            closed = true;
            if (log != null) { log.close(); }
            journal.abort(); service.clear();
            if (destination != null) { destination.discard(); }
            if (source != null) { source.discard(); }
            if (parkedId != null && moon.getEntity(parkedId) != null) { moon.getEntity(parkedId).discard(); }
            if (record != null) { RocketTransferEntities.releaseFlightTickets(earth.getServer(), record); }
            for (var entry : List.copyOf(fixtureTickets)) { removeTicket(entry.getKey(), entry.getValue()); }
            if (directory != null) {
                try (var paths = Files.walk(directory)) {
                    for (var path : paths.sorted(Comparator.reverseOrder()).toList()) { Files.delete(path); }
                } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        }
    }

    /** The production runtime, a fresh station and a three-wide legacy rocket whose station pad footprint crosses x = 16k. */
    private static final class BorderFlight {
        final GameTestHelper helper;
        final ServerLevel earth;
        final ServerLevel space;
        final GameTestTickPacer pacer;
        final BlockPos sourceOrigin;
        final StationState station;
        final UUID transfer = UUID.randomUUID();
        RocketEntity source;
        RocketEntity landed;
        RocketTransferRecord record;
        int stage, waits;
        boolean closed;

        BorderFlight(GameTestHelper helper) {
            this.helper = helper;
            earth = helper.getLevel();
            space = earth.getServer().getLevel(CelestialIds.SPACE_LEVEL);
            helper.assertTrue(space != null, "Space Level is unavailable");
            pacer = new GameTestTickPacer(earth.getServer());
            sourceOrigin = helper.absolutePos(new BlockPos(3, 2, 3));
            station = new StationCreationService(new StationPlatformGenerator(), id -> true)
                    .create(earth.getServer(), UUID.randomUUID(), "Footprint Border Test", CelestialIds.EARTH_ID, false)
                    .station().orElseThrow();
        }

        void start() { helper.onEachTick(this::tick); }

        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < 650, "Border flight exceeded its bounded wait at stage " + stage);
                switch (stage) {
                    case 0 -> launch();
                    case 1 -> awaitAcknowledgement();
                    case 2 -> awaitLanding();
                    default -> { }
                }
                if (!closed) { pacer.pace(); }
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        void launch() {
            if (!earth.isPositionEntityTicking(sourceOrigin)) { return; }
            source = addSource();
            helper.assertTrue(noPlayerNearPad(), "A player is near the destination pad");
            var result = RocketRuntime.requestAdminStationFlight(source, station.stationId(), transfer);
            RocketJournalGameTestSupport.assertPendingLaunch(helper, result, source);
            stage = 1;
        }

        /** R32 item 2: from the PREPARED acknowledgement, the source origin and every destination column are ticketed. */
        void awaitAcknowledgement() {
            record = RocketTransferSavedData.get(earth.getServer()).find(transfer).orElse(null);
            if (record == null || source.flightData().orElseThrow().state() == RocketFlightState.FUELED) { return; }
            helper.assertTrue(record.phase() == RocketTransferPhase.PREPARED && record.nativeTransfer().isEmpty(),
                    "The flight is not a legacy PREPARED record");
            var region = RocketRegion.fromSnapshot(record.destinationSnapshot());
            helper.assertTrue(region.minimum().x() >> 4 != region.maximum().x() >> 4,
                    "The station pad footprint does not cross a chunk border: " + region.minimum().x() + ".." + region.maximum().x());
            var columns = RocketTransferEntities.destinationTicketSet(record.destinationSnapshot());
            helper.assertTrue(columns.size() == 4 && RocketDiscoveryColumns.of(record.destinationSnapshot()).admissible(),
                    "The border footprint's checked set is not 2 x 2: " + columns);
            var expected = new TreeSet<String>();
            var sourceAt = record.sourceSnapshot().sourceOrigin();
            expected.add(RocketMarginGameTestSupport.column(earth.dimension().location(), new ChunkPos(sourceAt.x() >> 4, sourceAt.z() >> 4)));
            columns.forEach(column -> expected.add(RocketMarginGameTestSupport.column(space.dimension().location(), column)));
            var actual = RocketMarginGameTestSupport.flightTicketColumns(earth.getServer(), transfer);
            helper.assertTrue(actual.equals(expected), "The acknowledged flight's tickets are not the source origin and the "
                    + "destination's checked set: expected " + expected + ", found " + actual);
            stage = 2;
        }

        void awaitLanding() {
            var current = RocketTransferSavedData.get(earth.getServer()).find(transfer).orElse(null);
            if (current == null || current.destinationEntityId().isEmpty()) { return; }
            record = current;
            if (!(space.getEntity(current.destinationEntityId().orElseThrow()) instanceof RocketEntity arrival)
                    || arrival.flightData().orElseThrow().state() != RocketFlightState.LANDED) { return; }
            landed = arrival;
            helper.assertTrue(RocketRegion.fromSnapshot(arrival.snapshot().orElseThrow()).equals(RocketRegion.fromSnapshot(current.destinationSnapshot()))
                    && arrival.assemblyTransactionId().equals(source.assemblyTransactionId()), "The landing changed its footprint or identity");
            helper.assertTrue(source.isRemoved() && noPlayerNearPad(), "The source survived, or a player reached the pad");
            close(); helper.succeed();
        }

        boolean noPlayerNearPad() {
            var pad = station.landingPad();
            return space.getNearestPlayer(pad.x() + 0.5D, pad.y(), pad.z() + 0.5D, 128.0D, false) == null;
        }

        /** West tank, motor, east tank, seat and guidance: x -1..1, so the station pad's x = 16k splits the footprint. */
        RocketEntity addSource() {
            var level = earth;
            level.setBlock(sourceOrigin, ModBlocks.ROCKET_MOTOR.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(sourceOrigin.above(), ModBlocks.ROCKET_SEAT.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(sourceOrigin.above(2), ModBlocks.GUIDANCE_COMPUTER.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(sourceOrigin.east(), ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(sourceOrigin.west(), ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState(), Block.UPDATE_ALL);
            var task = new RocketStructureScanTask(new ServerLevelRocketScanWorld(level, RocketBlockEntityAdapters.defaults()),
                    level.dimension().location(), position(sourceOrigin), UUID.randomUUID(), level.getGameTime());
            var result = task.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
            for (int i = 1; i < 64 && result.status() == RocketScanResult.Status.RUNNING; i++) {
                result = task.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
            }
            helper.assertTrue(result.snapshot().isPresent(), "Legal border fixture scan failed: " + result.issues());
            var snapshot = result.snapshot().orElseThrow();
            for (var block : snapshot.blocks()) {
                var at = snapshot.sourceOrigin().add(block.position());
                level.removeBlock(new BlockPos(at.x(), at.y(), at.z()), false);
            }
            var rocket = ModEntities.ROCKET.get().create(level);
            helper.assertTrue(rocket != null, "Rocket entity creation failed");
            rocket.initialize(snapshot, UUID.randomUUID(), station.ownerId());
            var flight = rocket.flightData().orElseThrow();
            rocket.updateFlightData(flight.withFuel(flight.fuel().fill(flight.fuel().capacity()).state(), level.getGameTime()));
            helper.assertTrue(level.addFreshEntity(rocket), "Source spawn failed");
            return rocket;
        }

        void close() {
            if (closed) { return; }
            closed = true;
            if (landed != null) { landed.discard(); }
            if (source != null && !source.isRemoved()) { source.discard(); }
            var journal = RocketTransferSavedData.get(earth.getServer());
            var retained = journal.find(transfer).orElse(record);
            if (retained != null) {
                retained.destinationEntityId().map(space::getEntity).ifPresent(net.minecraft.world.entity.Entity::discard);
                journal.remove(transfer);
                RocketTransferEntities.releaseFlightTickets(earth.getServer(), retained);
            }
            StationRegistrySavedData.get(earth.getServer()).delete(station.stationId());
            new StationPlatformGenerator().removeTemplate(space, station.cell());
        }
    }

    private static RocketPropulsion propulsion() {
        return new RocketPropulsion(RocketPropulsion.Mode.ABSTRACT, 1_000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS);
    }

    private static RocketPosition position(BlockPos at) { return new RocketPosition(at.getX(), at.getY(), at.getZ()); }
}
