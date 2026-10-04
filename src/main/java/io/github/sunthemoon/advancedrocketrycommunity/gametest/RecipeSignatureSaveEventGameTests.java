package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Synthetic outgoing snapshots test registered event consumers, not native disk persistence. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeSignatureSaveEventGameTests {
    private static final String REASON = "Refusing chunk save with oversized recipe input; back up and repair first";
    private static final String[] IDS = {"rolling_machine", "precision_assembler", "electrolyzer"};
    private static final String[] ROOTS = {"arce_recipe_signature", "arce_process", "arce_process_journal", "arce_machine"};

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registeredRecipeGuardRetainsDenialAfterEveryOutgoingRootDisappears(GameTestHelper helper) {
        var chunk = helper.getLevel().getChunkAt(helper.absolutePos(net.minecraft.core.BlockPos.ZERO));
        for (String id : IDS) {
            for (String root : ROOTS) {
                try {
                    CompoundTag carrier = new CompoundTag(); carrier.putString("id", ModIdentity.id(id).toString());
                    CompoundTag oversized = new CompoundTag();
                    oversized.putByteArray("payload", new byte[root.equals("arce_recipe_signature") ? 1_024 : 65_536]);
                    carrier.put(root, oversized);
                    ListTag entities = new ListTag(); entities.add(carrier);
                    CompoundTag outgoing = new CompoundTag(); outgoing.put("block_entities", entities);
                    chunk.setUnsaved(false);
                    rejected(helper, new ChunkDataEvent.Save(chunk, helper.getLevel(), outgoing));
                    helper.assertTrue(chunk.isUnsaved(), "Initial recipe refusal lost retry flag: " + id + "/" + root);
                    chunk.setUnsaved(false);
                    rejected(helper, new ChunkDataEvent.Save(chunk, helper.getLevel(), new CompoundTag()));
                    helper.assertTrue(chunk.isUnsaved(), "Empty recipe retry lost Level-owned denial: " + id + "/" + root);
                } finally {
                    // No live BE/resource was changed: only this synthetic outgoing tag existed.
                    GuardedChunkSaves.restoreGameTestFixture(helper.getLevel(), chunk.getPos());
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void anUnrelatedUndeniedOutgoingChunkStillSaves(GameTestHelper helper) {
        // Unregistered object, never inserted in the chunk source or used to load world data.
        var chunk = new LevelChunk(helper.getLevel(), new ChunkPos(1_000_000, 1_000_000));
        CompoundTag outgoing = new CompoundTag();
        MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, helper.getLevel(), outgoing));
        helper.assertTrue(outgoing.isEmpty(), "Recipe guard fabricated unrelated resource data");
        helper.succeed();
    }

    private static void rejected(GameTestHelper helper, ChunkDataEvent.Save event) {
        try { MinecraftForge.EVENT_BUS.post(event); helper.fail("Oversized recipe/empty retry was admitted"); }
        catch (IllegalStateException refused) {
            helper.assertTrue(REASON.equals(refused.getMessage()), "Different failure masked recipe save denial");
        }
    }
    private RecipeSignatureSaveEventGameTests() { }
}
