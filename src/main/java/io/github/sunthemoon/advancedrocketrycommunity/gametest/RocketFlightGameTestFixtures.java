package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
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
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketWorldBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Shared world setup and assertion helpers for rocket-flight GameTests. */
final class RocketFlightGameTestFixtures {
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

    static RocketEntity assembleFueledRocket(
            GameTestHelper helper,
            BlockPos origin,
            UUID owner
    ) {
        placeLegalRocket(helper, origin);
        helper.setBlock(origin.west(), ModBlocks.ROCKET_FUEL_TANK.get());
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
        RocketEntity rocket = (RocketEntity) level.getEntity(assembled.rocketEntityId().orElseThrow());
        helper.assertTrue(rocket != null, "Flight test assembly did not spawn a rocket");
        var full = rocket.flightData().orElseThrow().fuel()
                .fill(rocket.flightData().orElseThrow().fuel().capacity()).state();
        rocket.updateFlightData(rocket.flightData().orElseThrow().withFuel(full, level.getGameTime()));
        return rocket;
    }

    static RocketEntity findLogicalRocket(ServerLevel level, UUID logical) {
        primePadChunks(level);
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
        BlockPos base = level.dimension().equals(CelestialIds.MOON_LEVEL)
                ? SafeCelestialTravel.FIXED_FEET_POSITION
                : level.getSharedSpawnPos();
        for (int[] offset : PAD_OFFSETS) {
            BlockPos pad = base.offset(offset[0], 0, offset[1]);
            ChunkPos chunk = new ChunkPos(pad);
            level.getChunkSource().addRegionTicket(PAD_CLEANUP_TICKET, chunk, 2, chunk);
            level.getChunkAt(pad);
        }
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
