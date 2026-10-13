package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UnlitTorchFixtureCleanupGameTests {
    @GameTest(template = "rocket_test", batch = "unlit_torch_cleanup", timeoutTicks = 100)
    public static void constructorFailureReleasesOnlyItsOwnedMoonForce(GameTestHelper helper) {
        var moon = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon Level absent");
        ChunkPos chunk = new ChunkPos(helper.absolutePos(new BlockPos(8, 8, 8)));
        BlockPos origin = new BlockPos(chunk.getMinBlockX() + 8, 180, chunk.getMinBlockZ() + 8);
        helper.assertTrue(!moon.getForcedChunks().contains(chunk.toLong()), "Cleanup fixture chunk is already forced");
        var before = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(moon.getForcedChunks());
        boolean failed = false;
        try {
            // Deliberately fail listener installation after the real force/load and drop snapshot.
            new UnlitTorchFixture(new GameTestHelper(null), moon, origin, new AABB(origin).inflate(2), true);
        } catch (NullPointerException expected) {
            failed = true;
        }
        helper.assertTrue(failed && moon.getForcedChunks().equals(before), "Constructor failure leaked or removed another Moon force");
        helper.succeed();
    }

    private UnlitTorchFixtureCleanupGameTests() { }
}
