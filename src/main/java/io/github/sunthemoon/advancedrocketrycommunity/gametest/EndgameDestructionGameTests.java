package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.EndgameModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * ADR-054 section 9.1 destruction bounds (C13): every endgame endpoint block, found through the endpoint block entity
 * types the service observes, is wither- and dragon-immune, listed in the common movers' non-movable tags, refuses
 * pistons and has blast resistance 1,200; a charged-creeper explosion next to each leaves it and its block entity.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EndgameDestructionGameTests {
    private static final TagKey<Block> CREATE_NON_MOVABLE = TagKey.create(Registries.BLOCK,
            ResourceLocation.tryBuild("create", "non_movable"));
    private static final TagKey<Block> CARRYON_BLACKLIST = TagKey.create(Registries.BLOCK,
            ResourceLocation.tryBuild("carryon", "block_blacklist"));

    private EndgameDestructionGameTests() {
    }

    /** The registered blocks of the endpoint block entity types. */
    private static List<Block> endpointBlocks(GameTestHelper helper) {
        Set<String> types = EndgameModule.endgameBlockEntityIds();
        List<Block> blocks = new ArrayList<>();
        for (String id : types) {
            BlockEntityType<?> type = ForgeRegistries.BLOCK_ENTITY_TYPES.getValue(ResourceLocation.tryParse(id));
            helper.assertTrue(type != null, "Unknown endpoint block entity type " + id);
            int found = 0;
            for (Block block : ForgeRegistries.BLOCKS) {
                if (type.isValid(block.defaultBlockState())) {
                    blocks.add(block);
                    found++;
                }
            }
            helper.assertTrue(found > 0, "No block carries the endpoint type " + id);
        }
        return blocks;
    }

    @GameTest(template = "empty", batch = "endgame_destruction", timeoutTicks = 100)
    public static void everyEndpointBlockHasTheRemovalDefences(GameTestHelper helper) {
        List<Block> blocks = endpointBlocks(helper);
        helper.assertTrue(blocks.size() == EndgameModule.endgameBlockEntityIds().size(),
                "Endpoint blocks differ from their types: " + blocks);
        for (Block block : blocks) {
            BlockState state = block.defaultBlockState();
            String name = String.valueOf(ForgeRegistries.BLOCKS.getKey(block));
            helper.assertTrue(state.is(BlockTags.WITHER_IMMUNE) && state.is(BlockTags.DRAGON_IMMUNE),
                    name + " is not wither- and dragon-immune");
            helper.assertTrue(state.is(CREATE_NON_MOVABLE) && state.is(CARRYON_BLACKLIST),
                    name + " is missing from a mover's non-movable tag");
            helper.assertTrue(state.getPistonPushReaction() == PushReaction.BLOCK, name + " can be pushed");
            helper.assertTrue(block.getExplosionResistance() >= 1200.0F, name + " has a lower blast resistance");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "endgame_destruction_blast", timeoutTicks = 100)
    public static void aChargedCreeperBlastLeavesEveryEndpoint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Block> blocks = endpointBlocks(helper);
        List<BlockPos> placed = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            // High above the test origin and apart, so no blast reaches another endpoint's neighbourhood first.
            BlockPos pos = helper.absolutePos(new BlockPos(2 + 8 * i, 12, 2));
            level.setBlockAndUpdate(pos, blocks.get(i).defaultBlockState());
            helper.assertTrue(level.getBlockEntity(pos) != null, "No block entity at " + pos);
            placed.add(pos);
        }
        for (BlockPos pos : placed) {
            level.explode(null, pos.getX() + 1.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 6.0F,
                    Level.ExplosionInteraction.TNT);
        }
        for (int i = 0; i < placed.size(); i++) {
            BlockPos pos = placed.get(i);
            helper.assertTrue(level.getBlockState(pos).is(blocks.get(i)) && level.getBlockEntity(pos) != null
                            && !level.getBlockEntity(pos).isRemoved(),
                    "A blast removed " + ForgeRegistries.BLOCKS.getKey(blocks.get(i)));
            level.removeBlock(pos, false);
        }
        helper.succeed();
    }
}
