package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketScanResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Actual pre-authority call-site checks; the local journal is not a durability fixture. */
@net.minecraftforge.gametest.GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class RocketDestinationReadinessGameTests {
    private static final BlockPos MOON_ORIGIN = new BlockPos(8192, 200, 8192);
    private static final int READINESS_ATTEMPTS = 80;
    private static final TicketType<UUID> FIXTURE_TICKET = TicketType.create(
            "arce_gametest_destination_readiness", Comparator.<UUID>naturalOrder(), 300);

    private RocketDestinationReadinessGameTests() {
    }

    @GameTest(template = "rocket_test", batch = "destination_readiness", timeoutTicks = 300)
    public static void destinationSpawnWaitsForEntityReadinessAndRechecksTheReservedPad(GameTestHelper helper) {
        ServerLevel moon = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon Level is unavailable");
        Fixture fixture = new Fixture(helper, moon);
        fixture.guard(() -> awaitSource(fixture, 1));
    }

    private static void awaitSource(Fixture fixture, int attempt) {
        fixture.helper.runAfterDelay(1, () -> fixture.guard(() -> {
            BlockPos sourceOrigin = fixture.helper.absolutePos(new BlockPos(3, 2, 3));
            if (!fixture.earth.areEntitiesLoaded(new ChunkPos(sourceOrigin).toLong())
                    || !fixture.earth.isPositionEntityTicking(sourceOrigin)) {
                fixture.helper.assertTrue(attempt < READINESS_ATTEMPTS, "Source entity chunk did not become ready");
                fixture.pacer.pace();
                awaitSource(fixture, attempt + 1);
                return;
            }
            fixture.createSource(sourceOrigin);
            fixture.assertColdWait();
            // Radius zero retains a FULL block chunk without requesting ENTITY_TICKING.
            fixture.moon.getChunkSource().addRegionTicket(FIXTURE_TICKET, fixture.chunk, 0, fixture.ticket);
            fixture.ticketLevel = 0;
            fixture.moon.getChunkAt(MOON_ORIGIN);
            awaitLoadedButNotTicking(fixture, 1);
        }));
    }

    private static void awaitLoadedButNotTicking(Fixture fixture, int attempt) {
        fixture.helper.runAfterDelay(1, () -> fixture.guard(() -> {
            fixture.helper.assertTrue(!fixture.moon.isPositionEntityTicking(MOON_ORIGIN),
                    "The FULL-only remote fixture unexpectedly became entity-ticking");
            if (!fixture.moon.areEntitiesLoaded(fixture.chunk.toLong())) {
                fixture.helper.assertTrue(attempt < READINESS_ATTEMPTS, "Remote entity data did not load");
                fixture.pacer.pace();
                awaitLoadedButNotTicking(fixture, attempt + 1);
                return;
            }
            fixture.assertWaitUnchanged();
            fixture.moon.getChunkSource().removeRegionTicket(FIXTURE_TICKET, fixture.chunk, 0, fixture.ticket);
            fixture.ticketLevel = -1;
            fixture.moon.getChunkSource().addRegionTicket(FIXTURE_TICKET, fixture.chunk, 2, fixture.ticket);
            fixture.ticketLevel = 2;
            awaitReady(fixture, 1);
        }));
    }

    private static void awaitReady(Fixture fixture, int attempt) {
        fixture.helper.runAfterDelay(1, () -> fixture.guard(() -> {
            if (!RocketTransferEntities.destinationEntityChunkReady(fixture.moon, fixture.record.destinationSnapshot())) {
                fixture.helper.assertTrue(attempt < READINESS_ATTEMPTS, "Ticketed destination did not become ready");
                fixture.assertWaitUnchanged();
                fixture.pacer.pace();
                awaitReady(fixture, attempt + 1);
                return;
            }
            try {
                fixture.assertBlockedPadReturn();
                fixture.assertReadySpawn();
            } finally {
                fixture.close();
            }
            fixture.assertCleanup();
            fixture.helper.succeed();
        }));
    }

    private static final class Fixture {
        private final GameTestHelper helper;
        private final ServerLevel earth;
        private final ServerLevel moon;
        /** Retries wait for asynchronous chunk and entity loading; each lasts a server tick (GameTestTickPacer). */
        private final GameTestTickPacer pacer;
        private final ChunkPos chunk = new ChunkPos(MOON_ORIGIN);
        private final UUID ticket = UUID.randomUUID();
        private final UUID logical = UUID.randomUUID();
        private final UUID owner = UUID.randomUUID();
        private final RocketTransferService service = new RocketTransferService();
        private final RocketTransferSavedData journal = new RocketTransferSavedData();
        private final Map<BlockPos, BlockState> sourceBlocks = new LinkedHashMap<>();
        private RocketEntity source;
        private RocketEntity destination;
        private RocketStructureSnapshot sourceSnapshot;
        private RocketTransferRecord record;
        private BlockState obstructionBefore;
        private int ticketLevel = -1;
        private boolean closed;

        private Fixture(GameTestHelper helper, ServerLevel moon) {
            this.helper = helper;
            earth = helper.getLevel();
            this.moon = moon;
            pacer = new GameTestTickPacer(earth.getServer());
        }

        private void guard(Runnable action) {
            try {
                action.run();
            } catch (RuntimeException | Error exception) {
                try {
                    close();
                } catch (RuntimeException | Error cleanup) {
                    exception.addSuppressed(cleanup);
                }
                throw exception;
            }
        }

        private void createSource(BlockPos origin) {
            Map<BlockPos, BlockState> structure = new LinkedHashMap<>();
            structure.put(origin, ModBlocks.ROCKET_MOTOR.get().defaultBlockState());
            structure.put(origin.above(), ModBlocks.ROCKET_SEAT.get().defaultBlockState());
            structure.put(origin.above(2), ModBlocks.GUIDANCE_COMPUTER.get().defaultBlockState());
            structure.put(origin.east(), ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState());
            for (BlockPos position : structure.keySet()) {
                helper.assertTrue(earth.getBlockState(position).isAir() && earth.getBlockEntity(position) == null,
                        "Source fixture would overwrite a nonempty cell: " + position);
                sourceBlocks.put(position, earth.getBlockState(position));
            }
            structure.forEach((position, state) -> earth.setBlock(position, state, Block.UPDATE_ALL));
            RocketStructureScanTask scan = new RocketStructureScanTask(
                    new ServerLevelRocketScanWorld(earth, RocketBlockEntityAdapters.defaults()),
                    earth.dimension().location(), position(origin), UUID.randomUUID(), earth.getGameTime());
            RocketScanResult result = scan.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
            for (int step = 1; result.status() == RocketScanResult.Status.RUNNING && step < 64; step++) {
                result = scan.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
            }
            helper.assertTrue(result.status() == RocketScanResult.Status.SUCCESS,
                    "Registered source structure did not scan: " + result.status());
            sourceSnapshot = result.snapshot().orElseThrow();
            source = ModEntities.ROCKET.get().create(earth);
            helper.assertTrue(source != null, "Registered rocket entity type is unavailable");
            record = newRecord();
            source.initializeTransferred(sourceSnapshot, logical, owner, record.sourceFlightData());
            sourceBlocks.forEach((position, state) -> earth.setBlock(position, state, Block.UPDATE_ALL));
            helper.assertTrue(earth.addFreshEntity(source), "Source entity was not admitted");
            helper.assertTrue(earth.getEntity(source.getUUID()) == source,
                    "Ready source is not visible through the native UUID lookup");
            journal.put(record);
        }

        private RocketTransferRecord newRecord() {
            UUID transfer = UUID.randomUUID();
            long now = earth.getGameTime();
            RocketStructureSnapshot relocated = sourceSnapshot.relocated(
                    UUID.randomUUID(), moon.dimension().location(), position(MOON_ORIGIN), now);
            RocketFuelState fuel = RocketFuelState.empty(sourceSnapshot.stats().fuelCapacity())
                    .fill(sourceSnapshot.stats().fuelCapacity()).state();
            var planned = RocketFlightPlanner.plan(sourceSnapshot.stats(), fuel,
                    RocketFlightPlanner.EARTH, RocketFlightPlanner.MOON, transfer, now);
            helper.assertTrue(planned.success(), "Source fixture cannot plan a flight: " + planned.code());
            var plan = planned.plan();
            RocketFlightData transit = RocketFlightData.initial(logical, fuel.capacity(),
                    sourceSnapshot.stats().seatCount(), CelestialIds.EARTH_ID, earth.dimension().location(),
                    sourceSnapshot.sourceOrigin(), now)
                    .withFuel(fuel, now).withPlan(plan).startCountdown(now)
                    .completeCountdown(now).beginTransit(transfer, now);
            RocketFlightData arrival = transit.arriveAtDestination(fuel.debit(transfer, plan.requiredFuel()).state(),
                    CelestialIds.MOON_ID, moon.dimension().location(), relocated.sourceOrigin(), now);
            return RocketTransferRecord.create(transfer, logical, owner, source.getUUID(), sourceSnapshot,
                    relocated, transit, arrival, plan.requiredFuel(), now);
        }

        private void assertColdWait() {
            helper.assertTrue(moon.getChunkSource().getChunkNow(chunk.x, chunk.z) == null,
                    "The fixed remote destination was not cold");
            helper.assertTrue(!moon.areEntitiesLoaded(chunk.toLong())
                            && !moon.isPositionEntityTicking(MOON_ORIGIN),
                    "The cold fixture already has ready entity state");
            assertWaitUnchanged();
            helper.assertTrue(moon.getChunkSource().getChunkNow(chunk.x, chunk.z) == null,
                    "The pre-authority wait loaded destination blocks");
        }

        private void assertWaitUnchanged() {
            CompoundTag journalBefore = journal.save(new CompoundTag());
            RocketFlightData flightBefore = source.flightData().orElseThrow();
            var positionBefore = source.position();
            service.spawnDestination(earth.getServer(), journal, record, source);
            helper.assertTrue(journal.find(record.transferId()).orElseThrow() == record
                            && record.phase() == RocketTransferPhase.PREPARED
                            && record.destinationEntityId().isEmpty()
                            && journal.save(new CompoundTag()).equals(journalBefore),
                    "Readiness wait changed the PREPARED journal/reservation");
            helper.assertTrue(source.flightData().orElseThrow() == flightBefore
                            && source.position().equals(positionBefore) && !source.isRemoved(),
                    "Readiness wait changed source authority, position or fuel");
            helper.assertTrue(moon.getEntitiesOfClass(RocketEntity.class,
                            source.getBoundingBox().move(MOON_ORIGIN.getX() - source.getX(),
                                    MOON_ORIGIN.getY() - source.getY(), MOON_ORIGIN.getZ() - source.getZ())
                                    .inflate(4, 64, 4)).stream()
                            .noneMatch(entity -> entity.assemblyTransactionId().filter(logical::equals).isPresent()),
                    "Readiness wait created a destination rocket");
        }

        private void assertBlockedPadReturn() {
            for (var block : record.destinationSnapshot().blocks()) {
                RocketPosition absolute = record.destinationSnapshot().sourceOrigin().add(block.position());
                BlockPos at = new BlockPos(absolute.x(), absolute.y(), absolute.z());
                helper.assertTrue(moon.getBlockState(at).isAir() && moon.getBlockEntity(at) == null,
                        "Destination fixture would overwrite a nonempty cell: " + at);
            }
            obstructionBefore = moon.getBlockState(MOON_ORIGIN);
            moon.setBlock(MOON_ORIGIN, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            service.spawnDestination(earth.getServer(), journal, record, source);
            helper.assertTrue(journal.find(record.transferId()).isEmpty(), "Blocked pad retained an active transfer");
            helper.assertTrue(!source.isRemoved() && source.flightData().orElseThrow().state() == RocketFlightState.FUELED
                            && source.flightData().orElseThrow().fuel().equals(record.sourceFlightData().fuel())
                            && source.flightData().orElseThrow().activeTransferId().isEmpty(),
                    "Blocked pad did not return the original source with undebited fuel");
            helper.assertTrue(source.position().x == sourceSnapshot.sourceOrigin().x() + 0.5D
                            && source.position().y == sourceSnapshot.sourceOrigin().y()
                            && source.position().z == sourceSnapshot.sourceOrigin().z() + 0.5D,
                    "Blocked pad did not return to the exact source origin");
            moon.setBlock(MOON_ORIGIN, obstructionBefore, Block.UPDATE_ALL);
        }

        private void assertReadySpawn() {
            record = newRecord();
            source.updateFlightData(record.sourceFlightData());
            journal.put(record);
            service.spawnDestination(earth.getServer(), journal, record, source);
            RocketTransferRecord spawned = journal.find(record.transferId()).orElseThrow();
            helper.assertTrue(spawned.phase() == RocketTransferPhase.DESTINATION_SPAWNED,
                    "Ready free pad did not acquire destination authority");
            destination = (RocketEntity) moon.getEntity(spawned.destinationEntityId().orElseThrow());
            helper.assertTrue(destination != null && destination.operational(),
                    "Ready destination is not visible through the native UUID lookup");
            helper.assertTrue(destination.flightData().orElseThrow().fuel().equals(record.destinationFlightData().fuel())
                            && destination.flightData().orElseThrow().fuel().amount()
                            == source.flightData().orElseThrow().fuel().amount() - record.requiredFuel()
                            && source.flightData().orElseThrow().equals(record.sourceFlightData()) && !source.isRemoved(),
                    "Ready spawn changed the exact debit or removed source before passenger transfer");
            helper.assertTrue(destination.snapshot().orElseThrow().equals(record.destinationSnapshot())
                            && destination.getX() == MOON_ORIGIN.getX() + 0.5D
                            && destination.getZ() == MOON_ORIGIN.getZ() + 0.5D,
                    "Ready spawn changed the reserved destination origin/snapshot");
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            ArrayList<Runnable> cleanup = new ArrayList<>();
            if (destination != null) {
                cleanup.add(destination::discard);
            } else if (record != null) {
                cleanup.add(() -> journal.find(record.transferId())
                        .flatMap(RocketTransferRecord::destinationEntityId)
                        .map(moon::getEntity).ifPresent(entity -> entity.discard()));
            }
            if (source != null) {
                cleanup.add(source::discard);
            }
            if (obstructionBefore != null) {
                cleanup.add(() -> moon.setBlock(MOON_ORIGIN, obstructionBefore, Block.UPDATE_ALL));
            }
            sourceBlocks.forEach((position, state) -> cleanup.add(
                    () -> earth.setBlock(position, state, Block.UPDATE_ALL)));
            if (ticketLevel >= 0) {
                cleanup.add(() -> moon.getChunkSource().removeRegionTicket(FIXTURE_TICKET, chunk, ticketLevel, ticket));
            }
            Throwable failure = null;
            for (Runnable action : cleanup) {
                try {
                    action.run();
                } catch (RuntimeException | Error exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
            if (failure instanceof RuntimeException exception) {
                throw exception;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            // The fixture journal is never cached. Production flush may save other cached SavedData,
            // but cannot persist this local instance; this test does not assert transaction durability.
        }

        private void assertCleanup() {
            helper.assertTrue(source.isRemoved() && destination.isRemoved(), "Fixture entities were not removed");
            sourceBlocks.forEach((position, state) -> helper.assertTrue(earth.getBlockState(position).equals(state),
                    "Source fixture cell was not restored: " + position));
            helper.assertTrue(moon.getBlockState(MOON_ORIGIN).equals(obstructionBefore),
                    "Destination obstruction was not restored");
        }

        private static RocketPosition position(BlockPos position) {
            return new RocketPosition(position.getX(), position.getY(), position.getZ());
        }
    }
}
