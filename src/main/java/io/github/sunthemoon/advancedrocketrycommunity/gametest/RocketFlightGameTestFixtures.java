package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketTransactionWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketScanResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketAssemblyTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegionLockManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketWorldBlock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Shared world setup and assertion helpers for rocket-flight GameTests. */
final class RocketFlightGameTestFixtures {
    static final int ASSEMBLY_WAIT_TICKS = 20;
    /** W166: the usual paced bound for the primed pads' entity sections, which load from disk within a few ticks. */
    static final int PAD_LOAD_TICKS = 100;
    private static final int PAD_SPACING = 64;
    private static final int[][] PAD_OFFSETS = {
            {0, 0},
            {PAD_SPACING, 0},
            {-PAD_SPACING, 0},
            {0, PAD_SPACING},
            {0, -PAD_SPACING},
            {PAD_SPACING, PAD_SPACING},
            {-PAD_SPACING, PAD_SPACING},
            {PAD_SPACING, -PAD_SPACING}
    };
    private static final TicketType<ChunkPos> PAD_CLEANUP_TICKET = TicketType.create(
            "arce_gametest_pad_cleanup",
            Comparator.comparingLong(chunk -> chunk.toLong()),
            200
    );

    private RocketFlightGameTestFixtures() {
    }

    static void assembleFueledRocket(
            GameTestHelper helper, GameTestTickDriver driver,
            BlockPos origin,
            UUID owner,
            Consumer<RocketEntity> ready
    ) {
        assembleFueledRocket(helper, driver, origin, owner, () -> {}, ready);
    }

    static void assembleFueledRocket(GameTestHelper helper, GameTestTickDriver driver, BlockPos origin, UUID owner,
            Runnable cleanupOnFailure, Consumer<RocketEntity> ready) {
        assembleFueledRocket(helper, driver, origin, owner, cleanupOnFailure, helper.getTick() + ASSEMBLY_WAIT_TICKS, ready);
    }

    static void assembleFueledRocket(GameTestHelper helper, GameTestTickDriver driver, BlockPos origin, UUID owner,
            Runnable cleanupOnFailure, long deadline, Consumer<RocketEntity> ready) {
        assembleShapedFueledRocket(helper, driver, origin, owner, at -> placeDefaultRocket(helper, at),
                cleanupOnFailure, deadline, ready);
    }

    /** As {@link #assembleFueledRocket}, with {@code shape} placing the rocket's blocks around {@code origin}. */
    static void assembleShapedFueledRocket(GameTestHelper helper, GameTestTickDriver driver, BlockPos origin, UUID owner,
            Consumer<BlockPos> shape, Runnable cleanupOnFailure, long deadline, Consumer<RocketEntity> ready) {
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(helper.absolutePos(origin));
        // One shared budget for origin readiness and visibility, inside even the 40-tick callers.
        driver.after(1, () -> awaitPaced(helper, driver, deadline, () -> level.areEntitiesLoaded(chunk.toLong()), () -> {
            UUID entityId;
            try {
                helper.assertTrue(level.areEntitiesLoaded(chunk.toLong()), "Flight test origin entities did not load");
                entityId = assembleRocket(helper, origin, owner, shape);
            } catch (RuntimeException | Error failure) {
                cleanupOnFailure.run();
                throw failure;
            }
            awaitPaced(helper, driver, deadline, () -> level.getEntity(entityId) != null, () -> {
                RocketEntity rocket;
                try {
                    rocket = (RocketEntity) level.getEntity(entityId);
                    helper.assertTrue(rocket != null, "Flight test assembly did not spawn a rocket");
                    var full = rocket.flightData().orElseThrow().fuel()
                            .fill(rocket.flightData().orElseThrow().fuel().capacity()).state();
                    rocket.updateFlightData(rocket.flightData().orElseThrow().withFuel(full, level.getGameTime()));
                } catch (RuntimeException | Error failure) {
                    cleanupOnFailure.run();
                    throw failure;
                }
                ready.accept(rocket);
            });
        }));
    }

    private static void placeDefaultRocket(GameTestHelper helper, BlockPos origin) {
        placeLegalRocket(helper, origin);
        helper.setBlock(origin.west(), ModBlocks.ROCKET_FUEL_TANK.get());
    }

    private static UUID assembleRocket(GameTestHelper helper, BlockPos origin, UUID owner, Consumer<BlockPos> shape) {
        shape.accept(origin);
        RocketStructureSnapshot snapshot = successfulSnapshot(helper, origin);
        ServerLevel level = helper.getLevel();
        RocketTransactionResult assembled = new RocketAssemblyTransaction(
                new ServerLevelRocketTransactionWorld(
                        level,
                        RocketBlockEntityAdapters.defaults(),
                        owner
                ),
                new RocketRegionLockManager(),
                new RocketOperationLedger(),
                RocketTransactionJournal.NO_OP
        ).execute(UUID.randomUUID(), snapshot);
        helper.assertTrue(assembled.success(), "Flight test rocket assembly failed: " + assembled.code());
        return assembled.rocketEntityId().orElseThrow();
    }

    /** Runs the original assertions on readiness or expiry; a timeout never silently succeeds. */
    static void awaitPaced(GameTestHelper helper, GameTestTickDriver driver, long deadline, BooleanSupplier ready, Runnable assertions) {
        awaitPaced(helper, driver, deadline, ready, assertions, new GameTestTickPacer(helper.getLevel().getServer()));
    }

    private static void awaitPaced(GameTestHelper helper, GameTestTickDriver driver, long deadline, BooleanSupplier ready,
            Runnable assertions, GameTestTickPacer pacer) {
        if (ready.getAsBoolean() || helper.getTick() >= deadline) {
            assertions.run();
            return;
        }
        pacer.pace();
        driver.after(1, () -> awaitPaced(helper, driver, deadline, ready, assertions, pacer));
    }

    static void awaitFlightState(GameTestHelper helper, GameTestTickDriver driver, ServerLevel destination, UUID logical,
            RocketFlightState state,
            int ticks, Runnable assertions) {
        awaitPaced(helper, driver, helper.getTick() + ticks, () -> hasFlightState(destination, logical, state)
                && RocketTransferSavedData.get(destination.getServer()).acknowledgedCurrent(), assertions);
    }

    /**
     * W98 (P1-N3 N3-A1): an ordinary source return keeps its record until the returned rocket's own entity slot is read
     * back, so the record leaves, and its acknowledged effects run, a few ticks after the rocket turns FUELED. The same
     * bounded wait as {@link #awaitFlightState}, which also waits for that retirement; the assertions decide as before.
     */
    static void awaitSourceReturned(GameTestHelper helper, GameTestTickDriver driver, ServerLevel source, UUID logical,
            int ticks, Runnable assertions) {
        awaitPaced(helper, driver, helper.getTick() + ticks, () -> hasFlightState(source, logical, RocketFlightState.FUELED)
                && sourceRetired(source.getServer(), logical), assertions);
    }

    /**
     * W166: the destination identity of a record the fixture expects to have landed. A fail-back before the destination
     * spawned, such as a DESTINATION_PAD_BLOCKED return to the source, never names one, so instead of "No value present"
     * the failure names the record's phase and the transfer service's last branch for it.
     */
    static UUID landedDestinationId(GameTestHelper helper, RocketTransferRecord record) {
        if (record.destinationEntityId().isEmpty()) {
            Matcher branch = Pattern.compile("target_branch=(\\S+)")
                    .matcher(RocketRuntime.transferFailureDiagnostics(record.transferId()));
            helper.fail("The fixture flight did not land: transfer " + record.transferId() + " is " + record.phase()
                    + " with no destination identity, last service branch " + (branch.find() ? branch.group(1) : "UNOBSERVED")
                    + " (DESTINATION_UNAVAILABLE is a DESTINATION_PAD_BLOCKED fail-back)");
        }
        return record.destinationEntityId().orElseThrow();
    }

    /** No record names {@code logical}, its removal is acknowledged and no continuation for it is pending. */
    public static boolean sourceRetired(net.minecraft.server.MinecraftServer server, UUID logical) {
        var journal = RocketTransferSavedData.get(server);
        return journal.acknowledgedCurrent() && journal.findByLogicalRocket(logical).isEmpty() && journal.actionable(logical);
    }

    static void assertPendingLaunch(GameTestHelper helper,
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult result,
            RocketEntity rocket) {
        io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport
                .assertPendingLaunch(helper, result, rocket);
    }

    static boolean hasFlightState(ServerLevel level, UUID logical, RocketFlightState state) {
        RocketEntity rocket = loadedLogicalRocket(level, logical);
        return rocket != null && rocket.flightData().map(data -> data.state() == state).orElse(false);
    }

    static RocketEntity findLogicalRocket(ServerLevel level, UUID logical) {
        primePadChunks(level);
        return loadedLogicalRocket(level, logical);
    }

    static RocketEntity loadedLogicalRocket(ServerLevel level, UUID logical) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof RocketEntity rocket
                    && rocket.operational()
                    && rocket.assemblyTransactionId().filter(logical::equals).isPresent()) {
                return rocket;
            }
        }
        return null;
    }

    static void assertSnapshotBlocks(
            GameTestHelper helper,
            ServerLevelRocketTransactionWorld world,
            RocketStructureSnapshot snapshot
    ) {
        for (var block : snapshot.blocks()) {
            RocketPosition absolute = snapshot.sourceOrigin().add(block.position());
            helper.assertTrue(
                    world.readBlock(absolute).filter(RocketWorldBlock.fromSnapshotBlock(block)::equals).isPresent(),
                    "Disassembly changed relocated block at " + absolute
            );
        }
    }

    static void clearSnapshotBlocks(ServerLevel level, RocketStructureSnapshot snapshot) {
        for (var block : snapshot.blocks()) {
            RocketPosition absolute = snapshot.sourceOrigin().add(block.position());
            level.setBlock(
                    new BlockPos(absolute.x(), absolute.y(), absolute.z()),
                    Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL
            );
        }
    }

    static void clearTransferJournal(ServerLevel level) {
        RocketTransferSavedData data = RocketTransferSavedData.get(level.getServer());
        if (!data.operational()) {
            throw new IllegalStateException("Transfer journal is blocked during GameTest");
        }
        data.entries().forEach(record -> data.remove(record.transferId()));
        data.flush(level.getServer());
    }

    static void primePadChunks(ServerLevel level) {
        BlockPos base = padBase(level);
        for (int[] offset : PAD_OFFSETS) {
            BlockPos pad = base.offset(offset[0], 0, offset[1]);
            ChunkPos chunk = new ChunkPos(pad);
            level.getChunkSource().addRegionTicket(PAD_CLEANUP_TICKET, chunk, 2, chunk);
            level.getChunkAt(pad);
        }
    }

    /**
     * W166: {@link #primePadChunks} only queues the pads' entity sections, which load asynchronously, so a
     * {@link #clearPadRockets} straight after it missed a rocket still on disk, and that rocket later blocked a pad. This
     * primes the pads of {@code levels}, waits paced for at most {@code ticks} until every pad chunk and its neighbours
     * have their entity sections loaded, discards every loaded rocket in those Levels and then runs {@code then}. When
     * the wait expires it fails instead of continuing on pads it could not clear.
     */
    static void clearPadRocketsWhenLoaded(GameTestHelper helper, GameTestTickDriver driver, int ticks, Runnable then,
            ServerLevel... levels) {
        for (ServerLevel level : levels) {
            primePadChunks(level);
        }
        BooleanSupplier loaded = () -> Arrays.stream(levels).allMatch(RocketFlightGameTestFixtures::padEntitiesLoaded);
        awaitPaced(helper, driver, helper.getTick() + ticks, loaded, () -> {
            for (ServerLevel level : levels) {
                helper.assertTrue(padEntitiesLoaded(level), "The fixed pads' entity sections did not load within "
                        + ticks + " ticks in " + level.dimension().location());
                clearPadRockets(level);
            }
            then.run();
        });
    }

    /** Every fixed pad's chunk and its eight neighbours have loaded entity sections, so no rocket there is still on disk. */
    static boolean padEntitiesLoaded(ServerLevel level) {
        for (int[] offset : PAD_OFFSETS) {
            ChunkPos chunk = new ChunkPos(padBase(level).offset(offset[0], 0, offset[1]));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!level.areEntitiesLoaded(ChunkPos.asLong(chunk.x + dx, chunk.z + dz))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static BlockPos padBase(ServerLevel level) {
        return level.dimension().equals(CelestialIds.MOON_LEVEL)
                ? SafeCelestialTravel.FIXED_FEET_POSITION
                : level.getSharedSpawnPos();
    }

    static void clearPadRockets(ServerLevel level) {
        ArrayList<RocketEntity> stale = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof RocketEntity rocket) {
                stale.add(rocket);
            }
        }
        stale.forEach(Entity::discard);
    }

    static void placeLegalRocket(GameTestHelper helper, BlockPos origin) {
        helper.setBlock(origin, ModBlocks.ROCKET_MOTOR.get());
        helper.setBlock(origin.above(), ModBlocks.ROCKET_SEAT.get());
        helper.setBlock(origin.above(2), ModBlocks.GUIDANCE_COMPUTER.get());
    }

    static RocketStructureSnapshot successfulSnapshot(GameTestHelper helper, BlockPos origin) {
        RocketScanResult result = scan(helper, origin);
        helper.assertTrue(
                result.status() == RocketScanResult.Status.SUCCESS,
                "Legal rocket scan failed: "
                        + (result.issues().isEmpty() ? "unknown" : result.issues().get(0).code())
        );
        return result.snapshot().orElseThrow();
    }

    static RocketScanResult scan(GameTestHelper helper, BlockPos relativeOrigin) {
        ServerLevel level = helper.getLevel();
        BlockPos absoluteOrigin = helper.absolutePos(relativeOrigin);
        RocketStructureScanTask task = new RocketStructureScanTask(
                new ServerLevelRocketScanWorld(level, RocketBlockEntityAdapters.defaults()),
                level.dimension().location(),
                new RocketPosition(absoluteOrigin.getX(), absoluteOrigin.getY(), absoluteOrigin.getZ()),
                UUID.randomUUID(),
                level.getGameTime()
        );
        RocketScanResult result = task.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        int steps = 1;
        while (result.status() == RocketScanResult.Status.RUNNING && steps++ < 64) {
            result = task.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        }
        return result;
    }
}
