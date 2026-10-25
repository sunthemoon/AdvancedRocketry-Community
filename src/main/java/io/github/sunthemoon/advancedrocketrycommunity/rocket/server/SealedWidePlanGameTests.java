package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedDisassembly;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedWidePlanGameTests {
    @GameTest(template = "rocket_test", batch = "w152h_wide_plan", timeoutTicks = 100)
    public static void wideRequestIsRefusedWithoutClaimCarrierJournalOrEntityChange(GameTestHelper helper) {
        var level = helper.getLevel(); var server = level.getServer();
        var at = helper.absolutePos(new BlockPos(3, 2, 3));
        var origin = new RocketPosition((at.getX() & ~15) + 15, at.getY(), (at.getZ() & ~15) + 3);
        var blocks = new ArrayList<RocketBlock>();
        for (int x = 0; x < 160; x++) { blocks.add(new RocketBlock(new RocketPosition(x, 0, 0),
                RocketBlockStateAdapter.capture(Blocks.IRON_BLOCK.defaultBlockState()))); }
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(), origin, blocks,
                List.of(new RocketPosition(0, 0, 0)), new RocketStats(160, 200, 1000, 1000, 1, 1, 1, 0), level.getGameTime());
        var rocket = ModEntities.ROCKET.get().create(level); helper.assertTrue(rocket != null, "Rocket type unavailable");
        rocket.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID()); RocketTransferEntities.positionAtOrigin(rocket, origin);
        helper.assertTrue(level.addFreshEntity(rocket), "Fixture rocket was not published");
        var outcome = new SealedDisassembly.Result[1];
        helper.onEachTick(() -> {
            var before = rocket.saveWithoutId(new CompoundTag());
            var data = RocketTransactionSavedData.get(server); var entries = data.entries();
            UUID id = RocketRuntime.beginSealedForReleaseTest(rocket, result -> outcome[0] = result);
            if (outcome[0] == null) { helper.assertTrue(id == null, "Wide plan acquired a claim"); return; }
            helper.assertTrue(id == null && outcome[0] == SealedDisassembly.Result.PROOF_PLAN_LIMIT, "Wrong wide-plan refusal");
            helper.assertTrue(!RocketRuntime.sealedClaimed(rocket) && !RocketRuntime.sealedFrozen(rocket), "Wide refusal retained a RAM claim");
            helper.assertTrue(data.entries().equals(entries) && before.equals(rocket.saveWithoutId(new CompoundTag())),
                    "Wide refusal changed U or the entity image");
            rocket.discard(); helper.succeed();
        });
    }
    private SealedWidePlanGameTests() { }
}
