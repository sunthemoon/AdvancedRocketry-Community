package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Registry checks for a torch pair (ADR-066 section 3.2): native torch shapes with only the supported orientation
 * property, no block entity, and one standing-and-wall item that both blocks name. A tag alone never qualifies a block.
 */
final class TorchShapes {
    private TorchShapes() {
    }

    static boolean registered(TorchPair pair) {
        Block standing = block(pair.standingBlock());
        Block wall = block(pair.wallBlock());
        Item item = item(pair.item());
        return standing != null && wall != null && supports(standing, false) && supports(wall, true)
                && item instanceof StandingAndWallBlockItem placer && placer.getBlock() == standing
                && standing.asItem() == item && wall.asItem() == item;
    }

    /** A plain standing torch has no properties; a wall torch has exactly the native horizontal facing. */
    static boolean supports(Block block, boolean wall) {
        if (block instanceof EntityBlock || CombustionTorches.isThermite(block)) {
            return false;
        }
        var properties = block.getStateDefinition().getProperties();
        return wall ? block instanceof WallTorchBlock && properties.size() == 1 && properties.contains(WallTorchBlock.FACING)
                : block instanceof TorchBlock && !(block instanceof WallTorchBlock) && properties.isEmpty();
    }

    static Block block(ResourceLocation id) {
        Block block = ForgeRegistries.BLOCKS.containsKey(id) ? ForgeRegistries.BLOCKS.getValue(id) : null;
        return block == null || block == Blocks.AIR ? null : block;
    }

    static Item item(ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
        return item == null || item == Items.AIR ? null : item;
    }
}
