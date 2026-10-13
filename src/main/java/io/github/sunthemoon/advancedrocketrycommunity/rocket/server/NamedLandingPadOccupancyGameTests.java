package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickDriver;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketTransactionWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketScanResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketAssemblyTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegionLockManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.NamedLandingPadStaging;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadEntry;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadIndex;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** R1's assembled, never-flown wide occupant at both named-pad admission boundaries. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NamedLandingPadOccupancyGameTests {
    private static final TicketType<UUID> FIXTURE_TICKET = TicketType.create(
            "arce_gametest_named_pad_occupancy", Comparator.<UUID>naturalOrder(), 180);

    private NamedLandingPadOccupancyGameTests() { }

    @GameTest(template = "rocket_test", batch = "named_pad_occupancy", timeoutTicks = 180)
    public static void wideParkedStructureRefusesPreparationButClearPadStillAccepts(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.awaitReady(() -> {
            var source = f.source();
            var parked = f.parkWideRocket();
            f.assertReviewGeometry(parked);
            UUID transfer = UUID.randomUUID();
            var before = source.flightData().orElseThrow();
            var result = new RocketTransferService().prepareLaunch(source, f.countdown(source, transfer));
            helper.assertTrue(result.code() == RocketFlightRequestCode.LANDING_PAD_UNAVAILABLE
                    && result.detailKey().endsWith("occupied"), "Wide occupant was not a stated preparation refusal");
            f.assertNoClaimOrDestination(transfer);
            helper.assertTrue(source.flightData().orElseThrow().equals(before), "Refusal changed source authority/fuel");

            UUID clearTransfer = UUID.randomUUID();
            var accepted = new NamedLandingPadStaging().select(f.space, source.snapshot().orElseThrow(),
                    clearTransfer, f.station, f.clearEntry.reference(), List.of(), f.space.getGameTime());
            helper.assertTrue(accepted.success(), "Non-overlapping named pad refused: " + accepted.detail());
            helper.assertTrue(accepted.chunksLoaded() == 0, "Occupancy discovery loaded chunks");
            LandingPadIndex.get(f.space.getServer()).release(f.station.stationId(), f.clearEntry.reference(), clearTransfer);
            f.close(); helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", batch = "named_pad_occupancy", timeoutTicks = 180)
    public static void wideParkedStructureAppearingAfterPreparationRefusesPreSpawn(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.awaitReady(() -> {
            var source = f.source(); UUID transfer = UUID.randomUUID();
            var service = new RocketTransferService();
            helper.assertTrue(service.prepareLaunch(source, f.countdown(source, transfer)).success(),
                    "Empty named pad preparation failed");
            var journal = RocketTransferSavedData.get(f.space.getServer());
            var record = journal.find(transfer).orElseThrow();
            var parked = f.parkWideRocket();
            f.assertReviewGeometry(parked);
            var staging = new NamedLandingPadStaging();
            helper.assertTrue(staging.unavailable(f.space, record.destinationSnapshot(), f.station).endsWith("occupied"),
                    "Pre-spawn occupancy reason was not stated");
            helper.assertTrue(!staging.recheck(f.space, record.destinationSnapshot(), f.station.stationId(),
                    f.entry.reference(), transfer), "Pre-spawn recheck accepted the wide occupant");
            helper.assertTrue(transfer.equals(f.currentEntry().claim()), "Recheck created/replaced the prepared claim");
            source.updateFlightData(record.sourceFlightData());
            service.spawnDestination(f.space.getServer(), journal, record, source);
            f.assertNoClaimOrDestination(transfer);
            helper.assertTrue(!source.isRemoved() && source.flightData().orElseThrow().state() == RocketFlightState.FUELED
                    && source.flightData().orElseThrow().fuel().equals(record.sourceFlightData().fuel()),
                    "Occupied destination did not retain undebited source authority");
            helper.assertTrue(record.destinationEntityId().isEmpty(), "Pre-spawn refusal created a destination identity");
            f.close(); helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", batch = "named_pad_occupancy", timeoutTicks = 180)
    public static void loadedEntityDiscoveryAcceptsItsLimitAndRefusesOverflowWithoutLoading(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.awaitReady(() -> {
            var source = f.source(); var snapshot = source.snapshot().orElseThrow();
            var parkedSnapshot = snapshot.relocated(UUID.randomUUID(), f.space.dimension().location(),
                    position(f.clearPad.above()), f.space.getGameTime());
            for (int i = 0; i < NamedLandingPadStaging.MAX_OCCUPANCY_ENTITY_INSPECTIONS; i++) {
                f.addRocket(f.space, parkedSnapshot);
            }
            int coldX = (f.station.region().maximumX() - 8) >> 4;
            int coldZ = (f.station.region().maximumZ() - 8) >> 4;
            helper.assertTrue(f.space.getChunkSource().getChunkNow(coldX, coldZ) == null, "Discovery fixture was not cold");
            var staging = new NamedLandingPadStaging(); UUID acceptedTransfer = UUID.randomUUID();
            var accepted = staging.select(f.space, snapshot, acceptedTransfer, f.station, f.entry.reference(),
                    List.of(), f.space.getGameTime());
            helper.assertTrue(accepted.success(), "Exactly 64 non-overlapping rockets exceeded the bound: " + accepted.detail());
            LandingPadIndex.get(f.space.getServer()).release(f.station.stationId(), f.entry.reference(), acceptedTransfer);
            f.addRocket(f.space, parkedSnapshot);
            UUID refusedTransfer = UUID.randomUUID();
            var refused = staging.select(f.space, snapshot, refusedTransfer, f.station, f.entry.reference(),
                    List.of(), f.space.getGameTime());
            helper.assertTrue(!refused.success() && refused.detail().endsWith("entity_budget"),
                    "Discovery overflow was silently accepted/truncated");
            helper.assertTrue(refused.chunksLoaded() == 0 && f.currentEntry().claim() == null
                    && RocketTransferSavedData.get(f.space.getServer()).find(refusedTransfer).isEmpty(),
                    "Discovery overflow loaded chunks or created a claim/journal");
            helper.assertTrue(f.space.getChunkSource().getChunkNow(coldX, coldZ) == null, "Discovery forced a cold chunk");
            helper.assertTrue(f.spaceRockets().size() == NamedLandingPadStaging.MAX_OCCUPANCY_ENTITY_INSPECTIONS + 1,
                    "Discovery overflow created or removed a rocket");
            f.close(); helper.succeed();
        });
    }

    private static final class Fixture {
        private final GameTestHelper helper;
        private final GameTestTickDriver driver;
        private final ServerLevel space;
        private final StationState station;
        private final BlockPos origin;
        private final BlockPos pad;
        private final BlockPos clearPad;
        private final BlockPos sourceOrigin;
        private final LandingPadEntry entry;
        private final LandingPadEntry clearEntry;
        private final List<RocketEntity> rockets = new ArrayList<>();
        private final GameTestTickPacer pacer;
        private final UUID ticket = UUID.randomUUID();
        private boolean closed;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.driver = new GameTestTickDriver(helper);
            space = helper.getLevel().getServer().getLevel(CelestialIds.SPACE_LEVEL);
            helper.assertTrue(space != null, "Space Level unavailable");
            station = new StationCreationService(new StationPlatformGenerator(), id -> true)
                    .create(space.getServer(), UUID.randomUUID(), "Occupancy Test", CelestialIds.EARTH_ID, false)
                    .station().orElseThrow();
            origin = new BlockPos(station.landingPad().x(), 80, station.landingPad().z());
            pad = origin.offset(10, -1, 0); clearPad = origin.offset(20, -1, 0);
            sourceOrigin = helper.absolutePos(new BlockPos(3, 2, 3));
            pacer = new GameTestTickPacer(space.getServer());
            // Test setup, not admission: reach the real pre-spawn branch instead of its readiness wait.
            space.getChunkSource().addRegionTicket(FIXTURE_TICKET, new ChunkPos(pad), 2, ticket);
            for (int x = -1; x <= 21; x++) { for (int z = -1; z <= 1; z++) {
                BlockPos at = origin.offset(x, -1, z);
                space.getChunk(at); space.setBlock(at, Blocks.GRAY_CONCRETE.defaultBlockState(), Block.UPDATE_ALL);
            } }
            space.setBlock(pad, ModBlocks.LANDING_PAD.get().defaultBlockState(), Block.UPDATE_ALL);
            space.setBlock(clearPad, ModBlocks.LANDING_PAD.get().defaultBlockState(), Block.UPDATE_ALL);
            entry = LandingPadService.register(space, pad, station.ownerId(), false, "Occupied Dock");
            clearEntry = LandingPadService.register(space, clearPad, station.ownerId(), false, "Clear Dock");
        }

        private void awaitReady(Runnable action) {
            boolean ready = helper.getLevel().areEntitiesLoaded(new ChunkPos(sourceOrigin).toLong())
                    && space.isPositionEntityTicking(pad.above());
            for (int x = (origin.getX() - 1) >> 4; x <= (origin.getX() + 21) >> 4; x++) {
                for (int z = (origin.getZ() - 1) >> 4; z <= (origin.getZ() + 1) >> 4; z++) {
                    ready &= space.areEntitiesLoaded(ChunkPos.asLong(x, z));
                }
            }
            if (ready || helper.getTick() >= 80) {
                helper.assertTrue(ready, "Occupancy fixture entity chunks did not become ready");
                try { action.run(); } finally { close(); }
            } else {
                pacer.pace(); driver.after(1, () -> awaitReady(action));
            }
        }

        private RocketEntity source() {
            placeComponents(helper.getLevel(), sourceOrigin);
            helper.getLevel().setBlock(sourceOrigin.west(), ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState(), Block.UPDATE_ALL);
            var snapshot = scan(helper.getLevel(), sourceOrigin);
            for (var block : snapshot.blocks()) {
                var at = snapshot.sourceOrigin().add(block.position());
                helper.getLevel().removeBlock(new BlockPos(at.x(), at.y(), at.z()), false);
            }
            var source = addRocket(helper.getLevel(), snapshot);
            var flight = source.flightData().orElseThrow();
            source.updateFlightData(flight.withFuel(flight.fuel().fill(flight.fuel().capacity()).state(), helper.getLevel().getGameTime()));
            return source;
        }

        private RocketEntity parkWideRocket() {
            placeComponents(space, origin);
            for (int x = 2; x <= 15; x++) { space.setBlock(origin.offset(x, 0, 0), Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL); }
            var snapshot = scan(space, origin);
            var assembled = new RocketAssemblyTransaction(new ServerLevelRocketTransactionWorld(space,
                    RocketBlockEntityAdapters.defaults(), station.ownerId()), new RocketRegionLockManager(),
                    new RocketOperationLedger(), RocketTransactionJournal.NO_OP).execute(UUID.randomUUID(), snapshot);
            helper.assertTrue(assembled.success(), "Wide parked assembly failed: " + assembled.code());
            var parked = (RocketEntity) space.getEntity(assembled.rocketEntityId().orElseThrow());
            helper.assertTrue(parked != null, "Assembled occupant not visible in loaded entity index");
            rockets.add(parked); return parked;
        }

        private void assertReviewGeometry(RocketEntity parked) {
            var region = new RocketRegion(space.dimension().location(), position(pad.offset(-1, 1, 0)), position(pad.offset(1, 3, 0)));
            helper.assertTrue(RocketRegion.fromSnapshot(parked.snapshot().orElseThrow()).overlaps(region), "Wide structure did not cover X=9..11");
            helper.assertTrue(!parked.getBoundingBox().intersects(new AABB(pad.offset(-1, 1, 0), pad.offset(2, 4, 1))),
                    "Review geometry accidentally overlaps the occupant entity AABB");
            for (int x = 9; x <= 11; x++) {
                helper.assertTrue(space.getBlockState(origin.offset(x, 0, 0)).isAir(), "Assembly did not clear pad terrain");
            }
            helper.assertTrue(parked.flightData().orElseThrow().state() == RocketFlightState.ASSEMBLED
                    && parked.flightData().orElseThrow().plan().isEmpty(), "Occupant had flown or acquired a reservation");
        }

        private RocketFlightData countdown(RocketEntity source, UUID transfer) {
            var plan = new RocketFlightPlan(3, transfer, CelestialIds.EARTH_ID, station.orbitBody(),
                    helper.getLevel().dimension().location(), space.dimension().location(), station.stationId(),
                    100, helper.getLevel().getGameTime(), new TravelTarget.Station(station.stationId()));
            return source.flightData().orElseThrow().withPlan(plan.withLandingPad(entry.reference()))
                    .startCountdown(helper.getLevel().getGameTime());
        }

        private RocketEntity addRocket(ServerLevel level, RocketStructureSnapshot snapshot) {
            var rocket = ModEntities.ROCKET.get().create(level);
            helper.assertTrue(rocket != null, "Rocket entity creation failed");
            rocket.initialize(snapshot, UUID.randomUUID(), station.ownerId());
            helper.assertTrue(level.addFreshEntity(rocket), "Fixture rocket spawn failed");
            rockets.add(rocket); return rocket;
        }

        private RocketStructureSnapshot scan(ServerLevel level, BlockPos origin) {
            var task = new RocketStructureScanTask(new ServerLevelRocketScanWorld(level, RocketBlockEntityAdapters.defaults()),
                    level.dimension().location(), position(origin), UUID.randomUUID(), level.getGameTime());
            var result = task.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
            for (int i = 1; i < 64 && result.status() == RocketScanResult.Status.RUNNING; i++) {
                result = task.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
            }
            helper.assertTrue(result.snapshot().isPresent(), "Legal fixture scan failed: " + result.issues());
            return result.snapshot().orElseThrow();
        }

        private LandingPadEntry currentEntry() { return LandingPadIndex.get(space.getServer()).find(station.stationId(), entry.reference()).orElseThrow(); }

        private List<RocketEntity> spaceRockets() {
            return space.getEntitiesOfClass(RocketEntity.class, new AABB(station.region().minimumX(), space.getMinBuildHeight(),
                    station.region().minimumZ(), (double) station.region().maximumX() + 1, space.getMaxBuildHeight(),
                    (double) station.region().maximumZ() + 1));
        }

        private void assertNoClaimOrDestination(UUID transfer) {
            helper.assertTrue(currentEntry().claim() == null, "Occupancy refusal retained/acquired a claim");
            helper.assertTrue(RocketTransferSavedData.get(space.getServer()).find(transfer).isEmpty(), "Refused transfer retained a journal");
            helper.assertTrue(spaceRockets().size() == 1, "Occupancy refusal created or removed a destination rocket");
        }

        private void close() {
            if (closed) { return; }
            closed = true;
            rockets.forEach(RocketEntity::discard);
            space.getChunkSource().removeRegionTicket(FIXTURE_TICKET, new ChunkPos(pad), 2, ticket);
            var index = LandingPadIndex.get(space.getServer());
            for (var registered : index.entries(station.stationId())) {
                index.remove(station.stationId(), registered.reference(), registered.physicalId());
            }
            for (int x = -1; x <= 21; x++) { for (int z = -1; z <= 1; z++) { space.removeBlock(origin.offset(x, -1, z), false); } }
            StationRegistrySavedData.get(space.getServer()).delete(station.stationId());
            new StationPlatformGenerator().removeTemplate(space, station.cell());
        }
    }

    private static void placeComponents(ServerLevel level, BlockPos origin) {
        level.setBlock(origin, ModBlocks.ROCKET_MOTOR.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(origin.above(), ModBlocks.ROCKET_SEAT.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(origin.above(2), ModBlocks.GUIDANCE_COMPUTER.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(origin.east(), ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState(), Block.UPDATE_ALL);
    }

    private static RocketPosition position(BlockPos at) { return new RocketPosition(at.getX(), at.getY(), at.getZ()); }
}
