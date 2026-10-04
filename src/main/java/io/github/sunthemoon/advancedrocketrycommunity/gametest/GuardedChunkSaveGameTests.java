package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native byte preservation is checked separately; these exercise the real Level-owned event bridge. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuardedChunkSaveGameTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rejectedChunkRefusesLaterEmptySerialization(GameTestHelper helper) {
        var chunk = helper.getLevel().getChunkAt(helper.absolutePos(net.minecraft.core.BlockPos.ZERO));
        String reason = "Guarded-save fixture rejection";
        try {
            expectRefusal(helper, reason, () -> GuardedChunkSaves.refuse(
                    new ChunkDataEvent.Save(chunk, helper.getLevel(), new CompoundTag()), reason));
            chunk.setUnsaved(false);
            expectRefusal(helper, reason, () -> GuardedChunkSaves.beforeSave(
                    new ChunkDataEvent.Save(chunk, helper.getLevel(), new CompoundTag())));
            helper.assertTrue(chunk.isUnsaved(), "Empty retry erased the original denial/dirty flag");
        } finally {
            GuardedChunkSaves.restoreGameTestFixture(helper.getLevel(), chunk.getPos());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void denialSurvivesReplacementOfTheLiveChunkObject(GameTestHelper helper) {
        var chunk = helper.getLevel().getChunkAt(helper.absolutePos(net.minecraft.core.BlockPos.ZERO));
        String reason = "Guarded-save chunk-identity fixture";
        try {
            expectRefusal(helper, reason, () -> GuardedChunkSaves.refuse(
                    new ChunkDataEvent.Save(chunk, helper.getLevel(), new CompoundTag()), reason));
            // Disjoint unregistered Java object, never inserted into the Level or used to load a chunk.
            var replacement = new LevelChunk(helper.getLevel(), chunk.getPos());
            replacement.setUnsaved(false);
            expectRefusal(helper, reason, () -> GuardedChunkSaves.beforeSave(
                    new ChunkDataEvent.Save(replacement, helper.getLevel(), new CompoundTag())));
            helper.assertTrue(replacement.isUnsaved(), "Changed BE/chunk lifetime bypassed refusal identity");
        } finally {
            GuardedChunkSaves.restoreGameTestFixture(helper.getLevel(), chunk.getPos());
        }
        helper.succeed();
    }

    private static void expectRefusal(GameTestHelper helper, String reason, Runnable action) {
        try { action.run(); helper.fail("Rejected chunk save was admitted"); }
        catch (IllegalStateException refused) {
            helper.assertTrue(reason.equals(refused.getMessage()), "Different failure masked the denial check");
        }
    }

    private GuardedChunkSaveGameTests() { }
}
