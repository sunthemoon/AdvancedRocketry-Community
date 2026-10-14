package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.SleepRefusal;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.SleepRespawnService;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetContent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SleepCompletedChunkGameTests {
    private SleepCompletedChunkGameTests() { }

    @GameTest(template = "atmosphere_test", timeoutTicks = 40)
    public static void unfinishedHeadAndFootChunksRefuseWithoutWaitingForCompletion(GameTestHelper helper) {
        var planet = NonPlayerAtmosphereGameTests.level(helper, ExoplanetContent.TAU_CETI_F_LEVEL);
        BlockPos column = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockPos head = new BlockPos((column.getX() + 15) & ~15, 200, column.getZ());
        Runnable bed = SleepRespawnGameTests.placeBedOnPlatform(planet, head);
        List<String> tokens = new ArrayList<>();
        var player = ConnectedTestPlayers.join(helper.getLevel().getServer(), UUID.randomUUID(), "W37FIncomplete",
                planet, head.east(), new ArrayList<>(), false, tokens);
        try {
            for (BlockPos cell : List.of(head, head.west())) {
                try (IncompleteFullChunk incomplete = new IncompleteFullChunk(planet, cell)) {
                    helper.assertTrue(planet.hasChunkAt(cell)
                                    && planet.getChunkSource().getChunkNow(cell.getX() >> 4, cell.getZ() >> 4) == null,
                            "The fixture did not distinguish an eligible holder from a completed FULL chunk");
                    tokens.clear();
                    var result = player.startSleepInBed(head);
                    helper.assertTrue(result.left().orElse(null) == Player.BedSleepingProblem.NOT_POSSIBLE_HERE
                                    && tokens.contains(ConnectedTestPlayers.ACTION_BAR + SleepRespawnService.sleepKey(SleepRefusal.UNAVAILABLE))
                                    && !incomplete.completed(),
                            "Native sleep admitted or forced completion of the unfinished " + cell + ": " + tokens);
                }
            }
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
            bed.run();
        }
        helper.succeed();
    }
}
