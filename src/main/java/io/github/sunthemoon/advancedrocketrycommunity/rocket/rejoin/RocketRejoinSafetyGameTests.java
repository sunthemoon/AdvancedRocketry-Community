package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketRejoinSafetyGameTests {
    private RocketRejoinSafetyGameTests() { }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_heightmap")
    public static void missingHeightmapIsNotPrimedByCandidateSelection(GameTestHelper helper) {
        var level = helper.getLevel(); var at = helper.absolutePos(new BlockPos(2, 4, 2));
        var chunk = level.getChunkAt(at); var type = net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
        var saved = chunk.getHeightmaps().stream().filter(e -> e.getKey() == type).findFirst().orElseThrow().getValue().getRawData().clone();
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "W159Heightmap"));
        var old = RocketRejoinGuardGameTests.root(player.getUUID()).batches().get(0);
        var pos = new RocketRejoinRoot.Position(at.getX(), at.getY(), at.getZ());
        var batch = new RocketRejoinRoot.Batch(old.release(), 0, old.transfer(), old.rocket(), old.authority(), old.hold(),
                old.dimension(), pos, pos, pos, old.resources(), old.before(), old.scrubbed(), old.released(),
                List.of(new RocketRejoinRoot.Slot(old.dimension(), at.getX() >> 4, at.getZ() >> 4)));
        try (var world = new RocketRejoinWorld(level.getServer())) {
            chunk.getHeightmaps().removeIf(e -> e.getKey() == type);
            world.choose(player, batch);
            helper.assertTrue(!chunk.hasPrimedHeightmap(type), "Candidate lookup primed a missing heightmap by scanning blocks");
        } finally { chunk.setHeightmap(type, saved); }
        helper.succeed();
    }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_safety")
    public static void loadedEnvelopeRejectsHazardsFluidsCollisionAndMissingSupport(GameTestHelper helper) {
        var level = helper.getLevel(); var at = helper.absolutePos(new BlockPos(2, 4, 2));
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "ARCESafety"));
        for (int x = -1; x <= 1; x++) { for (int z = -1; z <= 1; z++) { for (int y = -1; y <= 2; y++) {
            level.setBlockAndUpdate(at.offset(x, y, z), (y == -1 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
        } } }
        var pos = new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        helper.assertTrue(RocketRejoinWorld.safe(player, level, pos), "Plain supported air was refused");
        for (var block : List.of(Blocks.LAVA, Blocks.WATER, Blocks.FIRE, Blocks.WITHER_ROSE, Blocks.CACTUS,
                Blocks.MAGMA_BLOCK, Blocks.POWDER_SNOW, Blocks.SWEET_BERRY_BUSH, Blocks.CAMPFIRE, Blocks.NETHER_PORTAL)) {
            level.setBlockAndUpdate(at.east(), block.defaultBlockState());
            helper.assertTrue(!RocketRejoinWorld.safe(player, level, pos), "Hazard accepted: " + block);
            level.setBlockAndUpdate(at.east(), Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(at, Blocks.STONE.defaultBlockState());
        helper.assertTrue(!RocketRejoinWorld.safe(player, level, pos), "Collision accepted");
        level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState()); level.setBlockAndUpdate(at.below(), Blocks.AIR.defaultBlockState());
        helper.assertTrue(!RocketRejoinWorld.safe(player, level, pos), "Unsupported air accepted");
        helper.assertTrue(!RocketRejoinWorld.safe(player, level, new Vec3(pos.x, level.getMinBuildHeight(), pos.z)), "Build floor accepted");
        helper.assertTrue(!RocketRejoinWorld.safe(player, level, new Vec3(29_999_999.5, 64, 29_999_999.5)), "Unloaded/border position accepted");
        helper.succeed();
    }
}
