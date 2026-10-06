package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Event-bridge admission only; native final-save and restart evidence is separate. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClassicSaveAdmissionGameTests {
    @GameTest(template = "empty", batch = "classic_save_admission", timeoutTicks = 20)
    public static void observedDenialRejectsTheFirstEmptySave(GameTestHelper helper) {
        var chunk = helper.getLevel().getChunkAt(helper.absolutePos(BlockPos.ZERO));
        String reason = "Observed rejection before serialization";
        try {
            chunk.setUnsaved(false);
            GuardedChunkSaves.recordObservedDenial(helper.getLevel(), chunk.getPos(), reason);
            helper.assertTrue(!chunk.isUnsaved(), "Observation changed the live chunk dirty flag");
            expectRefusal(helper, reason, () -> GuardedChunkSaves.beforeSave(
                    new ChunkDataEvent.Save(chunk, helper.getLevel(), new CompoundTag())));
            helper.assertTrue(chunk.isUnsaved(), "First empty save did not remain dirty");
        } finally {
            GuardedChunkSaves.restoreGameTestFixture(helper.getLevel(), chunk.getPos());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "classic_save_admission", timeoutTicks = 20)
    public static void repeatedObservationsRetainTheFirstReason(GameTestHelper helper) {
        var chunk = helper.getLevel().getChunkAt(helper.absolutePos(BlockPos.ZERO));
        String reason = "First observed rejection";
        try {
            GuardedChunkSaves.recordObservedDenial(helper.getLevel(), chunk.getPos(), reason);
            GuardedChunkSaves.recordObservedDenial(helper.getLevel(), chunk.getPos(), "Later rejection");
            expectRefusal(helper, reason, () -> GuardedChunkSaves.beforeSave(
                    new ChunkDataEvent.Save(chunk, helper.getLevel(), new CompoundTag())));
        } finally {
            GuardedChunkSaves.restoreGameTestFixture(helper.getLevel(), chunk.getPos());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "classic_save_admission", timeoutTicks = 20)
    public static void temporaryDeferralDoesNotDenyTheNextSave(GameTestHelper helper) {
        var chunk = helper.getLevel().getChunkAt(helper.absolutePos(BlockPos.ZERO));
        var data = new CompoundTag();
        data.putString("fixture", "preserved");
        var before = data.copy();
        try {
            chunk.setUnsaved(false);
            expectRefusal(helper, "Held coherent operation", () -> GuardedChunkSaves.defer(
                    new ChunkDataEvent.Save(chunk, helper.getLevel(), data), "Held coherent operation"));
            helper.assertTrue(chunk.isUnsaved(), "Deferred attempt lost dirty eligibility");
            helper.assertTrue(data.equals(before), "Deferral rewrote the outgoing data");
            chunk.setUnsaved(false);
            GuardedChunkSaves.beforeSave(new ChunkDataEvent.Save(chunk, helper.getLevel(), new CompoundTag()));
            helper.assertTrue(!chunk.isUnsaved(), "Temporary deferral became a permanent denial");
        } finally {
            GuardedChunkSaves.restoreGameTestFixture(helper.getLevel(), chunk.getPos());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "classic_save_admission", timeoutTicks = 20)
    public static void permanentDenialPrecedesTemporaryReasonValidation(GameTestHelper helper) {
        var chunk = helper.getLevel().getChunkAt(helper.absolutePos(BlockPos.ZERO));
        String reason = "Original permanent denial";
        try {
            GuardedChunkSaves.recordObservedDenial(helper.getLevel(), chunk.getPos(), reason);
            chunk.setUnsaved(false);
            expectRefusal(helper, reason, () -> GuardedChunkSaves.defer(
                    new ChunkDataEvent.Save(chunk, helper.getLevel(), new CompoundTag()), null));
            helper.assertTrue(chunk.isUnsaved(), "Temporary reason bypassed permanent dirty eligibility");
            expectRefusal(helper, reason, () -> GuardedChunkSaves.beforeSave(
                    new ChunkDataEvent.Save(chunk, helper.getLevel(), new CompoundTag())));
        } finally {
            GuardedChunkSaves.restoreGameTestFixture(helper.getLevel(), chunk.getPos());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "classic_save_admission", timeoutTicks = 20)
    public static void invalidReasonsDoNotAcquireDenials(GameTestHelper helper) {
        var chunk = helper.getLevel().getChunkAt(helper.absolutePos(BlockPos.ZERO));
        try {
            for (String reason : new String[] { null, " ", "x".repeat(257) }) {
                chunk.setUnsaved(false);
                expectInvalid(helper, () -> GuardedChunkSaves.recordObservedDenial(
                        helper.getLevel(), chunk.getPos(), reason));
                expectInvalid(helper, () -> GuardedChunkSaves.defer(
                        new ChunkDataEvent.Save(chunk, helper.getLevel(), new CompoundTag()), reason));
                GuardedChunkSaves.beforeSave(new ChunkDataEvent.Save(chunk, helper.getLevel(), new CompoundTag()));
                helper.assertTrue(!chunk.isUnsaved(), "Invalid reason mutated save admission");
            }
            String exact = "x".repeat(256);
            expectRefusal(helper, exact, () -> GuardedChunkSaves.defer(
                    new ChunkDataEvent.Save(chunk, helper.getLevel(), new CompoundTag()), exact));
            GuardedChunkSaves.beforeSave(new ChunkDataEvent.Save(chunk, helper.getLevel(), new CompoundTag()));
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

    private static void expectInvalid(GameTestHelper helper, Runnable action) {
        try { action.run(); helper.fail("Invalid bounded reason was accepted"); }
        catch (IllegalArgumentException expected) {
            helper.assertTrue(expected.getMessage().startsWith("Invalid bounded chunk-save"),
                    "Different failure masked reason validation");
        }
    }

    private ClassicSaveAdmissionGameTests() { }
}
