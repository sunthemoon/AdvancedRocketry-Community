package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorContent;
import io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** C16a-07: actual registered motor tags, drops and accepted crafting multisets. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MotorGameTests {
    private MotorGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void everyTierHasBothTagsAndDropsItsOwnItem(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        for (MotorDefinition tier : MotorDefinition.values()) {
            Block block = MotorContent.block(tier).get();
            Item item = MotorContent.item(tier).get();
            var state = block.defaultBlockState();
            helper.assertTrue(BuiltInRegistries.BLOCK.getKey(block).equals(MotorContent.id(tier))
                            && BuiltInRegistries.ITEM.getKey(item).equals(MotorContent.id(tier)),
                    "Motor block/item identity differs: " + tier);
            helper.assertTrue(state.is(MotorContent.BLOCK_TAG) && new ItemStack(item).is(MotorContent.ITEM_TAG),
                    "Motor tier missing from a tag: " + tier);
            helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE) && state.is(BlockTags.NEEDS_STONE_TOOL),
                    "Motor tool tags missing: " + tier);
            helper.getLevel().setBlock(position, state, Block.UPDATE_CLIENTS);
            helper.assertTrue(helper.getLevel().getBlockEntity(position) == null,
                    "A crafting motor unexpectedly owns machine state");
            List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), position, null, null,
                    new ItemStack(Items.IRON_PICKAXE));
            helper.assertTrue(drops.size() == 1 && ItemStack.matches(drops.get(0), new ItemStack(item)),
                    "Motor drops differ: " + tier + " -> " + drops);
        }
        helper.assertTrue(BuiltInRegistries.BLOCK.getKey(ModBlocks.ENDGAME_CASING.get()).toString()
                        .equals("advancedrocketrycommunity:endgame_casing")
                        && BuiltInRegistries.ITEM.getKey(ModItems.ENDGAME_CASING.get()).toString()
                        .equals("advancedrocketrycommunity:endgame_casing"),
                "Advanced casing was given a different saved identity");
        helper.assertTrue(ModBlocks.ENDGAME_CASING.get().getDescriptionId()
                        .equals("block.advancedrocketrycommunity.advanced_machine_casing")
                        && ModItems.ENDGAME_CASING.get().getDescriptionId()
                        .equals("block.advancedrocketrycommunity.advanced_machine_casing"),
                "Casing block/item still depend on duplicate-key namespace iteration order");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void acceptedMultisetsCraftAllFourTiersInAnyOrder(GameTestHelper helper) {
        for (MotorDefinition tier : MotorDefinition.values()) {
            Item[] accepted = ingredients(tier);
            TransientCraftingContainer grid = grid(accepted);
            CraftingRecipe recipe = recipe(helper, tier);
            helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Accepted motor ingredients refuse: " + tier);
            var result = recipe.assemble(grid, helper.getLevel().registryAccess());
            helper.assertTrue(ItemStack.matches(result, new ItemStack(MotorContent.item(tier).get())),
                    "Motor crafting result differs: " + tier);
            for (int i = 0; i < accepted.length; i++) {
                helper.assertTrue(grid.getItem(i).is(accepted[i]) && grid.getItem(i).getCount() == 1,
                        "Crafting simulation mutated an input");
            }
            Item[] reversed = accepted.clone();
            for (int i = 0; i < accepted.length; i++) { reversed[i] = accepted[accepted.length - i - 1]; }
            helper.assertTrue(recipe.matches(grid(reversed), helper.getLevel()), "Shapeless motor requires an order");
            for (int i = 0; i < accepted.length; i++) {
                var missing = grid(accepted);
                missing.setItem(i, ItemStack.EMPTY);
                helper.assertFalse(recipe.matches(missing, helper.getLevel()), "Motor accepted a missing ingredient");
            }
            var extra = grid(accepted);
            extra.setItem(accepted.length, new ItemStack(Items.DIRT));
            helper.assertFalse(recipe.matches(extra, helper.getLevel()), "Motor accepted an extra ingredient");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void upgradesRequireTheExactPrecedingTierNotAnyMotor(GameTestHelper helper) {
        for (MotorDefinition tier : List.of(MotorDefinition.ADVANCED, MotorDefinition.ENHANCED, MotorDefinition.ELITE)) {
            CraftingRecipe recipe = recipe(helper, tier);
            for (MotorDefinition supplied : MotorDefinition.values()) {
                Item[] input = ingredients(tier);
                input[0] = MotorContent.item(supplied).get();
                helper.assertTrue(recipe.matches(grid(input), helper.getLevel()) == (supplied == tier.previous()),
                        "Motor upgrade accepts the wrong tier: " + tier + "/" + supplied);
            }
        }
        helper.succeed();
    }

    private static Item[] ingredients(MotorDefinition tier) {
        return switch (tier) {
            case MOTOR -> new Item[] {MaterialContent.item("copper_coil"), MaterialContent.item("steel_plate"),
                    MaterialContent.item("steel_plate"), MaterialContent.item("iron_rod"),
                    MaterialContent.item("iron_rod"), MaterialContent.item("steel_ingot")};
            case ADVANCED -> new Item[] {MotorContent.item(MotorDefinition.MOTOR).get(), MaterialContent.item("gold_coil"),
                    MaterialContent.item("gold_plate"), MaterialContent.item("gold_plate")};
            case ENHANCED -> new Item[] {MotorContent.item(MotorDefinition.ADVANCED).get(), MaterialContent.item("titanium_coil"),
                    MaterialContent.item("titanium_plate"), MaterialContent.item("titanium_plate")};
            case ELITE -> new Item[] {MotorContent.item(MotorDefinition.ENHANCED).get(), MaterialContent.item("iridium_coil"),
                    MaterialContent.item("iridium_plate"), MaterialContent.item("iridium_plate")};
        };
    }

    private static CraftingRecipe recipe(GameTestHelper helper, MotorDefinition tier) {
        var loaded = helper.getLevel().getRecipeManager().byKey(new ResourceLocation(tier.fullId()));
        helper.assertTrue(loaded.isPresent() && loaded.orElseThrow().getType() == RecipeType.CRAFTING,
                "Motor crafting recipe missing: " + tier);
        return (CraftingRecipe) loaded.orElseThrow();
    }

    private static TransientCraftingContainer grid(Item[] items) {
        var grid = new TransientCraftingContainer(new NoMenu(), 3, 3);
        for (int i = 0; i < items.length; i++) { grid.setItem(i, new ItemStack(items[i])); }
        return grid;
    }

    private static final class NoMenu extends AbstractContainerMenu {
        private NoMenu() { super(null, -1); }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return false; }
    }
}
