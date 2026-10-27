package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketMember;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedTransientRetryGameTests {
    @GameTest(template = "rocket_test", batch = "w152f_transient", timeoutTicks = 350)
    public static void readinessHoldRetriesAfter200TicksWithoutLatching(GameTestHelper helper) {
        var level = helper.getLevel(); var server = level.getServer();
        var at = helper.absolutePos(new BlockPos(4, 2, 4));
        var snapshot = SealedUnsealGameTests.snapshot(level, at, Blocks.IRON_BLOCK.defaultBlockState(), null);
        var member = new SealedRocketMember(UUID.randomUUID(), snapshot.snapshotId(), UUID.randomUUID(), snapshot.contentHash(),
                level.dimension().location(), SealedUnsealGameTests.position(at), 0, snapshot.blocks().get(0).state(), null);
        boolean wasOff = level.noSave;
        var installed = new SealedRocketBlockEntity[1];
        long[] deadline = {-1}; boolean[] finished = {false};
        Runnable cleanup = () -> { level.noSave = wasOff; level.setBlock(at, Blocks.AIR.defaultBlockState(), 2); finished[0] = true; };
        helper.runAtTickTime(349, cleanup);
        // W129aM transition (ADR-074 item 8): the former checkpoint after installation is awaited before it, so the
        // first attempt is held by readiness, not by the pending write; the installation changes no journal entry.
        SealedProductionTestAccess.afterJournalAcknowledged(helper, () -> {
            installed[0] = SealedUnsealGameTests.install(level, at, member);
            level.noSave = true;
        });
        helper.onEachTick(() -> {
            if (finished[0] || installed[0] == null) { return; }
            var carrier = installed[0];
            try {
                long now = server.getTickCount();
                helper.assertTrue(!carrier.preparationFailed(), "Readiness was treated as terminal preparation failure");
                if (deadline[0] < 0) {
                    if (carrier.unsealAttemptDue(now)) { return; }
                    long due = now;
                    while (due <= now + 200 && !carrier.unsealAttemptDue(due)) { due++; }
                    helper.assertTrue(due - now >= 199 && due - now <= 200, "Transient hold did not keep the 200-tick retry");
                    deadline[0] = due; level.noSave = wasOff;
                }
                if (now < deadline[0]) {
                    helper.assertTrue(level.getBlockEntity(at) == carrier, "Transient hold retried before its deadline"); return;
                }
                if (!level.getBlockState(at).is(Blocks.IRON_BLOCK)) { return; }
                helper.assertTrue(carrier.isRemoved(), "Publication retained the old carrier");
                cleanup.run(); helper.succeed();
            } catch (RuntimeException | Error failure) { cleanup.run(); throw failure; }
        });
    }
    private SealedTransientRetryGameTests() { }
}
