package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedDisassembly;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedSupersessionGameTests {
    @GameTest(template = "rocket_test", batch = "w152_hardening", timeoutTicks = 400)
    public static void changedCarrierInvalidatesItsPendingNativeProofBeforeElection(GameTestHelper helper) {
        var level = helper.getLevel(); var server = level.getServer(); var at = helper.absolutePos(new BlockPos(1, 2, 1));
        var position = new RocketPosition(at.getX(), at.getY(), at.getZ());
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(), position,
                List.of(new RocketBlock(new RocketPosition(0, 0, 0), RocketBlockStateAdapter.capture(Blocks.IRON_BLOCK.defaultBlockState()))),
                List.of(new RocketPosition(0, 0, 0)), new RocketStats(1, 100, 1000, 1000, 1, 1, 1, 0), level.getGameTime());
        level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
        var rocket = ModEntities.ROCKET.get().create(level); helper.assertTrue(rocket != null, "Missing rocket type");
        rocket.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID()); RocketTransferEntities.positionAtOrigin(rocket, position);
        helper.assertTrue(level.addFreshEntity(rocket), "Fixture D was not installed");
        var id = new UUID[1]; var original = new CompoundTag[1]; var changedAt = new long[] {-1};
        var repaired = new boolean[1]; var completed = new SealedDisassembly.Result[1];
        helper.onEachTick(() -> {
            if (id[0] == null) {
                if (!level.areEntitiesLoaded(new ChunkPos(at).toLong())) { return; }
                id[0] = RocketRuntime.beginSealedForReleaseTest(rocket, result -> completed[0] = result);
                helper.assertTrue(id[0] != null, "Fixture request was refused");
            }
            var diagnostic = RocketRuntime.sealedDiagnosticForReleaseTest(server, id[0]);
            if (original[0] == null && diagnostic.startsWith("PROVE:")) {
                var carrier = (SealedRocketBlockEntity) level.getBlockEntity(at);
                original[0] = carrier.saveWithFullMetadata(); changedAt[0] = server.getTickCount();
                level.setBlock(at, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
                helper.assertTrue(RocketRuntime.sealedDiagnosticForReleaseTest(server, id[0]).contains("no-batch"),
                        "Carrier replacement retained an earlier native terrain proof");
            }
            if (original[0] != null && !repaired[0]) {
                helper.assertTrue(level.getBlockState(at).is(Blocks.GOLD_BLOCK) && rocket.isAlive()
                        && RocketTransactionSavedData.get(server).entries().stream().anyMatch(entry -> entry.record().transactionId().equals(id[0])),
                        "Foreign terrain was overwritten or the unresolved election retired");
                if (server.getTickCount() - changedAt[0] < 20) { return; }
                // The fixture restores only the exact carrier image it replaced, never deletes U to bypass recovery.
                var state = ModBlocks.SEALED_ROCKET_BLOCK.get().defaultBlockState(); level.setBlock(at, state, 2);
                var restored = new SealedRocketBlockEntity(at, state); restored.load(original[0]); level.setBlockEntity(restored); restored.setChanged();
                repaired[0] = true;
            }
            if (completed[0] != null) {
                helper.assertTrue(repaired[0] && completed[0] == SealedDisassembly.Result.SEALED, "Repaired native proof did not complete");
                helper.succeed();
            }
        });
    }
    private SealedSupersessionGameTests() { }
}
