package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketRoot;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedLazyGameTests {
    @GameTest(template = "rocket_test", batch = "w152_lazy", timeoutTicks = 400)
    public static void loadedDiscoveryFindsCarriersBeyondTheHintBoundWithoutReplayingNativeCells(GameTestHelper helper) {
        var level = helper.getLevel(); var server = level.getServer();
        var carriers = new ArrayList<SealedRocketBlockEntity>();
        var state = ModBlocks.SEALED_ROCKET_BLOCK.get().defaultBlockState();
        // W129aM transition (ADR-074 item 8): the former checkpoint after installation is awaited before it, so no
        // discovery attempt meets the pending write; the installation changes no journal entry.
        SealedProductionTestAccess.afterJournalAcknowledged(helper, () -> {
            for (int index = 0; index < 70; index++) {
                var at = helper.absolutePos(new BlockPos(1 + index % 8, 2 + index / 64, 1 + index / 8 % 8));
                var member = new SealedRocketMember(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "0".repeat(64),
                        level.dimension().location(), new RocketPosition(at.getX(), at.getY(), at.getZ()), 0,
                        RocketBlockStateAdapter.capture(Blocks.IRON_BLOCK.defaultBlockState()), null);
                level.setBlock(at, state, 2);
                var carrier = new SealedRocketBlockEntity(at, state); carrier.initialize(SealedRocketRoot.stage(member));
                level.setBlockEntity(carrier); carrier.setChanged(); carriers.add(carrier);
            }
            helper.assertTrue(carriers.stream().allMatch(c -> level.getBlockEntity(c.getBlockPos()) == c), "onLoad published before coordinator authority");
            try {
                int hints = (int) RocketRuntime.class.getMethod("sealedHintCountForReleaseTest", MinecraftServer.class).invoke(null, server);
                helper.assertTrue(hints == 64, "Hint queue did not retain exactly its existing bound");
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Lazy coordinator unavailable", failure); }
        });
        var replaced = new boolean[1]; var finished = new boolean[1];
        helper.onEachTick(() -> {
            if (finished[0] || carriers.isEmpty()) { return; }
            var first = carriers.get(0).getBlockPos();
            if (!replaced[0] && level.getBlockState(first).is(Blocks.IRON_BLOCK)) {
                level.setBlock(first, Blocks.GOLD_BLOCK.defaultBlockState(), 2); replaced[0] = true;
            }
            if (!replaced[0]) { return; }
            helper.assertTrue(level.getBlockState(first).is(Blocks.GOLD_BLOCK), "Rediscovery overwrote a native replacement");
            for (int index = 1; index < carriers.size(); index++) {
                if (!level.getBlockState(carriers.get(index).getBlockPos()).is(Blocks.IRON_BLOCK)) { return; }
            }
            helper.assertTrue(carriers.stream().allMatch(SealedRocketBlockEntity::isRemoved), "Published cells retain live C references");
            finished[0] = true; helper.succeed();
        });
    }
    private SealedLazyGameTests() { }
}
