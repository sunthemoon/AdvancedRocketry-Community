package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Exercises vanilla's failed-wait notification without registering an intentionally failing suite test. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LaserPhysicalCleanupGameTests {
    private LaserPhysicalCleanupGameTests() { }

    @GameTest(template = "empty", batch = "endgame_laser_physical_counters", timeoutTicks = 40)
    public static void aFailedWaitRemovesTheConnectedOwnerAndClosesTheFixture(GameTestHelper helper) {
        // A private GameTestInfo has no reporting listener: only its enclosing, required test reports a result.
        var function = new TestFunction("endgame_laser_physical_counters", "laser_cleanup_failed_wait", "empty",
                2, -1L, true, child -> child.startSequence().thenWaitUntil(() -> child.fail("Injected wait")));
        var child = new GameTestInfo(function, Rotation.NONE, helper.getLevel());
        var childHelper = new GameTestHelper(child);
        var fixture = new OrbitalLaserDrillGameTests.Fixture(helper, CelestialIds.EARTH_ID);
        var cleanup = new LaserPhysicalCleanup(helper, fixture);
        cleanup.attach(childHelper);
        BlockPos marker = helper.absolutePos(new BlockPos(8, 4, -1024));
        cleanup.forceTargetChunk(helper.getLevel(), marker);
        helper.getLevel().setBlockAndUpdate(marker, ModBlocks.LASER_TARGET.get().defaultBlockState());
        var target = (LaserTargetBlockEntity) helper.getLevel().getBlockEntity(marker);
        target.buffer().setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
        cleanup.layer(java.util.List.of(marker.below()));
        helper.getLevel().setBlockAndUpdate(marker.below(), Blocks.STONE.defaultBlockState());
        var owner = ConnectedTestPlayers.join(fixture.server, fixture.owner, "cleanupOwner", fixture.space,
                fixture.controller.south(4).above(2), new ArrayList<>());
        cleanup.joined.add(owner);
        try {
            var start = GameTestInfo.class.getDeclaredMethod("startExecution");
            start.setAccessible(true);
            start.invoke(child);
        } catch (ReflectiveOperationException failure) {
            cleanup.close();
            throw new IllegalStateException("Could not start the private cleanup probe", failure);
        }
        helper.onEachTick(() -> {
            child.tick();
            if (!child.isDone()) { return; }
            try {
                helper.assertTrue(child.hasFailed() && "Injected wait".equals(child.getError().getMessage()),
                        "The probe did not fail its pending wait");
                helper.assertTrue(fixture.server.getPlayerList().getPlayer(fixture.owner) == null,
                        "A failed laser wait left its owner connected");
                helper.assertTrue(helper.getLevel().getBlockState(marker).isAir()
                                && helper.getLevel().getBlockState(marker.below()).isAir()
                                && target.buffer().getStackInSlot(0).isEmpty()
                                && !helper.getLevel().getForcedChunks().contains(new ChunkPos(marker).toLong()),
                        "A failed laser wait left its target, drops, layer or force ticket");
                helper.assertTrue(fixture.space.getBlockState(fixture.controller).isAir()
                                && fixture.stations.find(fixture.stationId).isEmpty()
                                && !fixture.space.getForcedChunks().contains(new ChunkPos(fixture.controller).toLong()),
                        "A failed laser wait left its drill, station or force ticket");
                helper.getLevel().setBlockAndUpdate(marker, Blocks.STONE.defaultBlockState());
                cleanup.close();
                cleanup.close();
                helper.assertTrue(helper.getLevel().getBlockState(marker).is(Blocks.STONE),
                        "Repeated cleanup changed a position it no longer owns");
                helper.getLevel().setBlockAndUpdate(marker, Blocks.AIR.defaultBlockState());
                helper.assertTrue(child.getError().getSuppressed().length == 0, "Cleanup failed on the private test");
                helper.assertTrue(fixture.server.getPlayerList().getPlayer(fixture.owner) == null,
                        "Repeated cleanup reconnected the owner");
                helper.succeed();
            } finally { cleanup.close(); }
        });
    }

    @GameTest(template = "empty", batch = "endgame_laser_physical_counters", timeoutTicks = 40)
    public static void aForeignForceFringeIsNeverBorrowedOrCleared(GameTestHelper helper) {
        var fixture = new OrbitalLaserDrillGameTests.Fixture(helper, CelestialIds.EARTH_ID);
        var cleanup = new LaserPhysicalCleanup(helper, fixture);
        var level = helper.getLevel();
        BlockPos marker = helper.absolutePos(new BlockPos(8, 4, -2048));
        ChunkPos targetChunk = new ChunkPos(marker);
        ChunkPos foreignChunk = new ChunkPos(targetChunk.x - 2, targetChunk.z);
        boolean ownsForeign = level.setChunkForced(foreignChunk.x, foreignChunk.z, true);
        try {
            helper.assertTrue(ownsForeign, "The ownership probe borrowed an existing force ticket");
            level.setBlockAndUpdate(marker, Blocks.STONE.defaultBlockState());
            boolean rejected = false;
            try { cleanup.forceTargetChunk(level, marker); }
            catch (IllegalStateException expected) { rejected = true; }
            helper.assertTrue(rejected, "The laser borrowed another fixture's FULL fringe");
            cleanup.close();
            helper.assertTrue(level.getForcedChunks().contains(foreignChunk.toLong())
                            && !level.getForcedChunks().contains(targetChunk.toLong())
                            && level.getBlockState(marker).is(Blocks.STONE),
                    "Rejected setup or cleanup changed a foreign ticket or target position");
        } finally {
            try { cleanup.close(); }
            finally {
                level.setBlockAndUpdate(marker, Blocks.AIR.defaultBlockState());
                if (ownsForeign) { level.setChunkForced(foreignChunk.x, foreignChunk.z, false); }
            }
        }
        helper.succeed();
    }
}
