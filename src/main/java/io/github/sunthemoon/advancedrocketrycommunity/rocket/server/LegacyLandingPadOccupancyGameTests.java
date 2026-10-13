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
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketScanResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketAssemblyTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegionLockManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.NamedLandingPadStaging;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W24: R1's wide occupant at the legacy station pad, its pre-spawn recheck and the fixed pads. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LegacyLandingPadOccupancyGameTests {
    private static final TicketType<UUID> FIXTURE_TICKET = TicketType.create(
            "arce_gametest_legacy_pad_occupancy", Comparator.<UUID>naturalOrder(), 180);
    /** The fixed pads lie within 64 blocks of their base; this ticket keeps them and a margin FULL. */
    private static final int SURFACE_TICKET_RADIUS = 6;

    private LegacyLandingPadOccupancyGameTests() { }

    @GameTest(template = "rocket_test", batch = "legacy_pad_occupancy", timeoutTicks = 180)
    public static void wideParkedStructureRefusesUnnamedStationPadButNearMissIsAccepted(GameTestHelper helper) {
        StationFixture f = new StationFixture(helper);
        f.awaitReady(() -> {
            var source = f.source();
            var parked = f.parkWideRocket();
            f.assertReviewGeometry(parked, f.arrival());
            UUID transfer = UUID.randomUUID();
            var before = source.flightData().orElseThrow();
            var result = new RocketTransferService().prepareLaunch(source, f.countdown(source, transfer));
            helper.assertTrue(result.code() == RocketFlightRequestCode.LANDING_PAD_UNAVAILABLE
                    && result.detailKey() != null && result.detailKey().endsWith("occupied"),
                    "Wide occupant was not a stated unnamed-pad refusal: " + result.code() + ":" + result.detailKey());
            f.assertNothingCreated(transfer, 1);
            helper.assertTrue(source.flightData().orElseThrow().equals(before), "Refusal changed source authority/fuel");

            // The same structure ending one block before the arrival is discovered but does not overlap.
            var nearMiss = parked.snapshot().orElseThrow().relocated(UUID.randomUUID(), f.space.dimension().location(),
                    position(f.pad.offset(-17, 0, 0)), f.space.getGameTime());
            helper.assertTrue(RocketRegion.fromSnapshot(nearMiss).maximum().x() == f.arrival().minimum().x() - 1,
                    "Near-miss fixture is not adjacent to the arrival");
            parked.discard();
            f.addRocket(f.space, nearMiss);
            var accepted = new RocketLandingPadSelector().selectStation(f.space, source.snapshot().orElseThrow(),
                    UUID.randomUUID(), f.station, List.of(), f.space.getGameTime());
            helper.assertTrue(accepted.success(), "Non-overlapping station pad refused: " + accepted.detail());
            helper.assertTrue(RocketRegion.fromSnapshot(accepted.destinationSnapshot()).equals(f.arrival()),
                    "Accepted arrival is not the default station pad footprint");
            f.close(); helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", batch = "legacy_pad_occupancy", timeoutTicks = 180)
    public static void wideParkedStructureAppearingAfterPreparationRefusesUnnamedPreSpawn(GameTestHelper helper) {
        StationFixture f = new StationFixture(helper);
        f.awaitReady(() -> {
            var source = f.source(); UUID transfer = UUID.randomUUID();
            var service = new RocketTransferService();
            helper.assertTrue(service.prepareLaunch(source, f.countdown(source, transfer)).success(),
                    "Empty unnamed station pad preparation failed");
            var journal = RocketTransferSavedData.get(f.space.getServer());
            var record = journal.find(transfer).orElseThrow();
            helper.assertTrue(RocketRegion.fromSnapshot(record.destinationSnapshot()).equals(f.arrival()),
                    "Prepared arrival is not the default station pad footprint");
            var parked = f.parkWideRocket();
            f.assertReviewGeometry(parked, f.arrival());
            var selector = new RocketLandingPadSelector();
            String reason = selector.unavailable(f.space, record.destinationSnapshot(), null, false);
            helper.assertTrue(reason != null && reason.endsWith("occupied"), "Pre-spawn occupancy reason was not stated: " + reason);
            helper.assertTrue(!selector.available(f.space, record.destinationSnapshot(), null, false),
                    "Pre-spawn recheck accepted the wide occupant");
            source.updateFlightData(record.sourceFlightData());
            service.spawnDestination(f.space.getServer(), journal, record, source);
            f.assertNothingCreated(transfer, 1);
            helper.assertTrue(!source.isRemoved() && source.flightData().orElseThrow().state() == RocketFlightState.FUELED
                    && source.flightData().orElseThrow().fuel().equals(record.sourceFlightData().fuel()),
                    "Occupied destination did not retain undebited source authority");
            helper.assertTrue(record.destinationEntityId().isEmpty(), "Pre-spawn refusal created a destination identity");
            f.close(); helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", batch = "legacy_pad_occupancy", timeoutTicks = 180)
    public static void unnamedStationDiscoveryAcceptsItsLimitAndRefusesOverflowWithoutLoading(GameTestHelper helper) {
        StationFixture f = new StationFixture(helper);
        f.awaitReady(() -> {
            var source = f.source(); var snapshot = source.snapshot().orElseThrow();
            var clear = snapshot.relocated(UUID.randomUUID(), f.space.dimension().location(),
                    position(f.pad.offset(20, 0, 0)), f.space.getGameTime());
            for (int i = 0; i < NamedLandingPadStaging.MAX_OCCUPANCY_ENTITY_INSPECTIONS; i++) {
                f.addRocket(f.space, clear);
            }
            int coldX = (f.station.region().maximumX() - 8) >> 4;
            int coldZ = (f.station.region().maximumZ() - 8) >> 4;
            helper.assertTrue(f.space.getChunkSource().getChunkNow(coldX, coldZ) == null, "Discovery fixture was not cold");
            var accepted = new RocketLandingPadSelector().selectStation(f.space, snapshot, UUID.randomUUID(), f.station,
                    List.of(), f.space.getGameTime());
            helper.assertTrue(accepted.success(), "Exactly 64 non-overlapping rockets exceeded the bound: " + accepted.detail());
            f.addRocket(f.space, clear);
            UUID refused = UUID.randomUUID();
            var before = source.flightData().orElseThrow();
            var result = new RocketTransferService().prepareLaunch(source, f.countdown(source, refused));
            helper.assertTrue(result.code() == RocketFlightRequestCode.LANDING_PAD_UNAVAILABLE
                    && result.detailKey() != null && result.detailKey().endsWith("entity_budget"),
                    "Discovery overflow was not a stated refusal: " + result.code() + ":" + result.detailKey());
            f.assertNothingCreated(refused, NamedLandingPadStaging.MAX_OCCUPANCY_ENTITY_INSPECTIONS + 1);
            helper.assertTrue(source.flightData().orElseThrow().equals(before), "Refusal changed source authority/fuel");
            helper.assertTrue(f.space.getChunkSource().getChunkNow(coldX, coldZ) == null, "Discovery forced a cold chunk");
            f.close(); helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", batch = "legacy_pad_occupancy", timeoutTicks = 180)
    public static void fixedPadSelectionSkipsAWideOccupantAndStopsAtTheDiscoveryBudget(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        ServerLevel earth = helper.getLevel();
        BlockPos base = earth.getSharedSpawnPos();
        UUID ticket = UUID.randomUUID();
        List<RocketEntity> spawned = new ArrayList<>();
        // Test setup, not admission: every fixed pad's chunks stay FULL so their entity sections are accessible.
        earth.getChunkSource().addRegionTicket(FIXTURE_TICKET, new ChunkPos(base), SURFACE_TICKET_RADIUS, ticket);
        Runnable close = () -> {
            spawned.forEach(RocketEntity::discard);
            spawned.clear();
            earth.getChunkSource().removeRegionTicket(FIXTURE_TICKET, new ChunkPos(base), SURFACE_TICKET_RADIUS, ticket);
        };
        BlockPos sourceOrigin = helper.absolutePos(new BlockPos(3, 2, 3));
        var snapshot = sourceSnapshot(helper, sourceOrigin);
        awaitSurface(helper, driver, earth, base, sourceOrigin, new GameTestTickPacer(earth.getServer()), () -> {
            try {
                var selector = new RocketLandingPadSelector();
                var owner = UUID.randomUUID();
                var dry = selector.select(earth, snapshot, UUID.randomUUID(), List.of(), earth.getGameTime());
                helper.assertTrue(dry.success(), "No fixed pad was available before the occupant: " + dry.detail());
                var first = RocketRegion.fromSnapshot(dry.destinationSnapshot());
                int centerX = first.minimum().x() - snapshot.bounds().minimum().x();
                var parked = addRocket(helper, earth, wideSnapshot(earth, new RocketPosition(
                        centerX - 10, first.minimum().y(), first.minimum().z())), owner);
                spawned.add(parked);
                var parkedRegion = RocketRegion.fromSnapshot(parked.snapshot().orElseThrow());
                helper.assertTrue(parkedRegion.overlaps(first), "Wide structure did not cover the first fixed pad");
                helper.assertTrue(!parked.getBoundingBox().intersects(box(first)),
                        "Fixed-pad geometry accidentally overlaps the occupant entity AABB");
                String reason = selector.unavailable(earth, dry.destinationSnapshot(), null, false);
                helper.assertTrue(reason != null && reason.endsWith("occupied"),
                        "Fixed-pad recheck did not state the occupant: " + reason);

                UUID transfer = UUID.randomUUID();
                var next = selector.select(earth, snapshot, transfer, List.of(), earth.getGameTime());
                helper.assertTrue(next.success(), "No later fixed pad accepted: " + next.detail());
                helper.assertTrue(next.candidatesChecked() > dry.candidatesChecked(),
                        "Occupied fixed pad was not skipped to a later candidate");
                helper.assertTrue(!RocketRegion.fromSnapshot(next.destinationSnapshot()).overlaps(parkedRegion),
                        "Next fixed pad overlaps the wide occupant");
                helper.assertTrue(RocketTransferSavedData.get(earth.getServer()).find(transfer).isEmpty(),
                        "Fixed-pad selection created a journal record");

                // At least 65 discovered rockets: selection stops with the stated budget instead of trying on.
                var clear = snapshot.relocated(UUID.randomUUID(), earth.dimension().location(),
                        new RocketPosition(base.getX() + 32, first.minimum().y(), base.getZ() + 32), earth.getGameTime());
                for (int i = 0; i < NamedLandingPadStaging.MAX_OCCUPANCY_ENTITY_INSPECTIONS; i++) {
                    spawned.add(addRocket(helper, earth, clear, owner));
                }
                var budget = selector.select(earth, snapshot, UUID.randomUUID(), List.of(), earth.getGameTime());
                helper.assertTrue(!budget.success() && budget.detail().endsWith("entity_budget")
                        && budget.refusalKey() != null, "Fixed-pad discovery overflow was not a stated refusal: " + budget.detail());
                helper.assertTrue(budget.candidatesChecked() <= next.candidatesChecked(),
                        "Fixed-pad selection continued past the discovery budget");
                helper.succeed();
            } finally {
                close.run();
            }
        }, close);
    }

    private static void awaitSurface(GameTestHelper helper, GameTestTickDriver driver, ServerLevel level, BlockPos base, BlockPos sourceOrigin,
            GameTestTickPacer pacer, Runnable action, Runnable close) {
        boolean ready = level.areEntitiesLoaded(new ChunkPos(sourceOrigin).toLong());
        for (int x = (base.getX() - 72) >> 4; x <= (base.getX() + 72) >> 4; x++) {
            for (int z = (base.getZ() - 72) >> 4; z <= (base.getZ() + 72) >> 4; z++) {
                ready &= level.areEntitiesLoaded(ChunkPos.asLong(x, z));
            }
        }
        if (ready || helper.getTick() >= 80) {
            if (!ready) { close.run(); }
            helper.assertTrue(ready, "Fixed-pad entity chunks did not become ready");
            action.run();
        } else {
            pacer.pace(); driver.after(1, () -> awaitSurface(helper, driver, level, base, sourceOrigin, pacer, action, close));
        }
    }

    private static final class StationFixture {
        private final GameTestHelper helper;
        private final GameTestTickDriver driver;
        private final ServerLevel space;
        private final StationState station;
        private final BlockPos pad;
        private final BlockPos parkedOrigin;
        private final BlockPos sourceOrigin;
        private final List<RocketEntity> rockets = new ArrayList<>();
        private final GameTestTickPacer pacer;
        private final UUID ticket = UUID.randomUUID();
        private boolean closed;

        private StationFixture(GameTestHelper helper) {
            this.helper = helper;
            this.driver = new GameTestTickDriver(helper);
            space = helper.getLevel().getServer().getLevel(CelestialIds.SPACE_LEVEL);
            helper.assertTrue(space != null, "Space Level unavailable");
            station = new StationCreationService(new StationPlatformGenerator(), id -> true)
                    .create(space.getServer(), UUID.randomUUID(), "Legacy Occupancy Test", CelestialIds.EARTH_ID, false)
                    .station().orElseThrow();
            // The unnamed path lands on the station's own pad; R1's occupant origin is ten blocks west of it.
            pad = new BlockPos(station.landingPad().x(), station.landingPad().y(), station.landingPad().z());
            parkedOrigin = pad.offset(-10, 0, 0);
            sourceOrigin = helper.absolutePos(new BlockPos(3, 2, 3));
            pacer = new GameTestTickPacer(space.getServer());
            // Test setup, not admission: reach the real pre-spawn branch instead of its readiness wait.
            space.getChunkSource().addRegionTicket(FIXTURE_TICKET, new ChunkPos(pad), 2, ticket);
            for (int x = (pad.getX() - 17) >> 4; x <= (pad.getX() + 21) >> 4; x++) { space.getChunk(x, pad.getZ() >> 4); }
        }

        private void awaitReady(Runnable action) {
            boolean ready = helper.getLevel().areEntitiesLoaded(new ChunkPos(sourceOrigin).toLong())
                    && space.isPositionEntityTicking(pad);
            for (int x = (pad.getX() - 17) >> 4; x <= (pad.getX() + 21) >> 4; x++) {
                ready &= space.areEntitiesLoaded(ChunkPos.asLong(x, pad.getZ() >> 4));
            }
            if (ready || helper.getTick() >= 80) {
                helper.assertTrue(ready, "Legacy occupancy fixture entity chunks did not become ready");
                try { action.run(); } finally { close(); }
            } else {
                pacer.pace(); driver.after(1, () -> awaitReady(action));
            }
        }

        private RocketRegion arrival() {
            return new RocketRegion(space.dimension().location(), position(pad.offset(-1, 0, 0)), position(pad.offset(1, 2, 0)));
        }

        private RocketEntity source() {
            var snapshot = sourceSnapshot(helper, sourceOrigin);
            var source = addRocket(helper.getLevel(), snapshot);
            var flight = source.flightData().orElseThrow();
            source.updateFlightData(flight.withFuel(flight.fuel().fill(flight.fuel().capacity()).state(), helper.getLevel().getGameTime()));
            return source;
        }

        private RocketEntity parkWideRocket() {
            placeComponents(space, parkedOrigin);
            for (int x = 2; x <= 15; x++) { space.setBlock(parkedOrigin.offset(x, 0, 0), Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL); }
            var snapshot = scan(helper, space, parkedOrigin);
            var assembled = new RocketAssemblyTransaction(new ServerLevelRocketTransactionWorld(space,
                    RocketBlockEntityAdapters.defaults(), station.ownerId()), new RocketRegionLockManager(),
                    new RocketOperationLedger(), RocketTransactionJournal.NO_OP).execute(UUID.randomUUID(), snapshot);
            helper.assertTrue(assembled.success(), "Wide parked assembly failed: " + assembled.code());
            var parked = (RocketEntity) space.getEntity(assembled.rocketEntityId().orElseThrow());
            helper.assertTrue(parked != null, "Assembled occupant not visible in loaded entity index");
            rockets.add(parked); return parked;
        }

        private void assertReviewGeometry(RocketEntity parked, RocketRegion arrival) {
            helper.assertTrue(RocketRegion.fromSnapshot(parked.snapshot().orElseThrow()).overlaps(arrival),
                    "Wide structure did not cover the station pad");
            helper.assertTrue(!parked.getBoundingBox().intersects(box(arrival)),
                    "Review geometry accidentally overlaps the occupant entity AABB");
            for (int x = -1; x <= 1; x++) {
                helper.assertTrue(space.getBlockState(pad.offset(x, 0, 0)).isAir(), "Assembly did not clear pad terrain");
            }
            helper.assertTrue(parked.flightData().orElseThrow().state() == RocketFlightState.ASSEMBLED
                    && parked.flightData().orElseThrow().plan().isEmpty(), "Occupant had flown or acquired a reservation");
        }

        /** An untargeted station flight: no named pad, so the legacy selector chooses the station pad. */
        private RocketFlightData countdown(RocketEntity source, UUID transfer) {
            var plan = new RocketFlightPlan(3, transfer, CelestialIds.EARTH_ID, station.orbitBody(),
                    helper.getLevel().dimension().location(), space.dimension().location(), station.stationId(),
                    100, helper.getLevel().getGameTime(), new TravelTarget.Station(station.stationId()));
            helper.assertTrue(plan.landingPad() == null, "Fixture plan targeted a named pad");
            return source.flightData().orElseThrow().withPlan(plan).startCountdown(helper.getLevel().getGameTime());
        }

        private RocketEntity addRocket(ServerLevel level, RocketStructureSnapshot snapshot) {
            var rocket = LegacyLandingPadOccupancyGameTests.addRocket(helper, level, snapshot, station.ownerId());
            rockets.add(rocket); return rocket;
        }

        private List<RocketEntity> spaceRockets() {
            return space.getEntitiesOfClass(RocketEntity.class, new AABB(station.region().minimumX(), space.getMinBuildHeight(),
                    station.region().minimumZ(), (double) station.region().maximumX() + 1, space.getMaxBuildHeight(),
                    (double) station.region().maximumZ() + 1));
        }

        private void assertNothingCreated(UUID transfer, int expectedRockets) {
            helper.assertTrue(RocketTransferSavedData.get(space.getServer()).find(transfer).isEmpty(), "Refused transfer retained a journal");
            helper.assertTrue(spaceRockets().size() == expectedRockets, "Occupancy refusal created or removed a destination rocket");
        }

        private void close() {
            if (closed) { return; }
            closed = true;
            rockets.forEach(RocketEntity::discard);
            space.getChunkSource().removeRegionTicket(FIXTURE_TICKET, new ChunkPos(pad), 2, ticket);
            // A failed assembly leaves its placed blocks; a successful one already removed them.
            for (int x = 0; x <= 15; x++) { for (int y = 0; y <= 2; y++) { space.removeBlock(parkedOrigin.offset(x, y, 0), false); } }
            StationRegistrySavedData.get(space.getServer()).delete(station.stationId());
            new StationPlatformGenerator().removeTemplate(space, station.cell());
        }
    }

    /** R1's three-wide, one-deep, three-tall arrival: motor, seat and computer with a tank on each side. */
    private static RocketStructureSnapshot sourceSnapshot(GameTestHelper helper, BlockPos origin) {
        placeComponents(helper.getLevel(), origin);
        helper.getLevel().setBlock(origin.west(), ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState(), Block.UPDATE_ALL);
        var snapshot = scan(helper, helper.getLevel(), origin);
        for (var block : snapshot.blocks()) {
            var at = snapshot.sourceOrigin().add(block.position());
            helper.getLevel().removeBlock(new BlockPos(at.x(), at.y(), at.z()), false);
        }
        return snapshot;
    }

    /** A sixteen-block-long occupant whose origin is its west end, like R1's X=0..15 structure. */
    private static RocketStructureSnapshot wideSnapshot(ServerLevel level, RocketPosition origin) {
        var iron = new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of());
        List<RocketBlock> blocks = new ArrayList<>();
        for (int x = 0; x <= 15; x++) { blocks.add(new RocketBlock(new RocketPosition(x, 0, 0), iron)); }
        blocks.add(new RocketBlock(new RocketPosition(0, 1, 0), iron));
        blocks.add(new RocketBlock(new RocketPosition(0, 2, 0), iron));
        return RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(), origin, blocks, List.of(),
                new RocketStats(blocks.size(), blocks.size(), 0, 0, 0, 0, 0, 0), level.getGameTime());
    }

    private static RocketEntity addRocket(GameTestHelper helper, ServerLevel level, RocketStructureSnapshot snapshot, UUID owner) {
        var rocket = ModEntities.ROCKET.get().create(level);
        helper.assertTrue(rocket != null, "Rocket entity creation failed");
        rocket.initialize(snapshot, UUID.randomUUID(), owner);
        helper.assertTrue(level.addFreshEntity(rocket), "Fixture rocket spawn failed");
        return rocket;
    }

    private static RocketStructureSnapshot scan(GameTestHelper helper, ServerLevel level, BlockPos origin) {
        var task = new RocketStructureScanTask(new ServerLevelRocketScanWorld(level, RocketBlockEntityAdapters.defaults()),
                level.dimension().location(), position(origin), UUID.randomUUID(), level.getGameTime());
        var result = task.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        for (int i = 1; i < 64 && result.status() == RocketScanResult.Status.RUNNING; i++) {
            result = task.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        }
        helper.assertTrue(result.snapshot().isPresent(), "Legal fixture scan failed: " + result.issues());
        return result.snapshot().orElseThrow();
    }

    private static void placeComponents(ServerLevel level, BlockPos origin) {
        level.setBlock(origin, ModBlocks.ROCKET_MOTOR.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(origin.above(), ModBlocks.ROCKET_SEAT.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(origin.above(2), ModBlocks.GUIDANCE_COMPUTER.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(origin.east(), ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState(), Block.UPDATE_ALL);
    }

    private static AABB box(RocketRegion region) {
        return new AABB(region.minimum().x(), region.minimum().y(), region.minimum().z(),
                region.maximum().x() + 1.0D, region.maximum().y() + 1.0D, region.maximum().z() + 1.0D);
    }

    private static RocketPosition position(BlockPos at) { return new RocketPosition(at.getX(), at.getY(), at.getZ()); }
}
