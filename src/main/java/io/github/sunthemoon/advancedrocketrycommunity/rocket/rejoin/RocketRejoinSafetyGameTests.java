package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
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
        var level = helper.getLevel();
        var chunk = new net.minecraft.world.level.chunk.ProtoChunk(new net.minecraft.world.level.ChunkPos(0, 0),
                net.minecraft.world.level.chunk.UpgradeData.EMPTY, level,
                level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME), null);
        var type = net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
        helper.assertTrue(!chunk.hasPrimedHeightmap(type), "Fixture unexpectedly has a primed heightmap");
        helper.assertTrue(RocketRejoinWorld.candidateHeight(chunk, 0, 0).isEmpty(), "Unknown heightmap supplied a candidate");
        helper.assertTrue(!chunk.hasPrimedHeightmap(type), "Candidate lookup primed a missing heightmap by scanning blocks");
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
        for (var block : java.util.List.of(Blocks.LAVA, Blocks.WATER, Blocks.FIRE, Blocks.WITHER_ROSE, Blocks.CACTUS,
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
