package io.github.sunthemoon.advancedrocketrycommunity.client.compat.jei;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import io.github.sunthemoon.advancedrocketrycommunity.material.press.SmallPlatePressRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Optional JEI view of the small plate press (ADR-063 sections 3 and 9): the block pressed on obsidian. */
final class SmallPlatePressJeiCategory extends AbstractRecipeCategory<SmallPlatePressRecipe> {
    static final RecipeType<SmallPlatePressRecipe> TYPE = RecipeType.create(
            AdvancedRocketryCommunity.MOD_ID, "small_plate_press", SmallPlatePressRecipe.class
    );

    private final IDrawable arrow;

    SmallPlatePressJeiCategory(IGuiHelper helper) {
        super(TYPE, Component.translatable("block.advancedrocketrycommunity.small_plate_press"),
                helper.createDrawableItemLike(MaterialContent.SMALL_PLATE_PRESS_ITEM.get()), 120, 62);
        arrow = helper.getRecipeArrow();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SmallPlatePressRecipe recipe, IFocusGroup focuses) {
        SmallPlatePressView view = SmallPlatePressView.of(recipe);
        builder.addSlot(RecipeIngredientRole.CATALYST, 5, 5).setStandardSlotBackground()
                .addItemStack(new ItemStack(MaterialContent.SMALL_PLATE_PRESS_ITEM.get()));
        builder.addInputSlot(5, 24).setStandardSlotBackground().addItemStacks(view.inputs());
        builder.addSlot(RecipeIngredientRole.CATALYST, 5, 43).setStandardSlotBackground()
                .addItemStack(view.anvil());
        builder.addOutputSlot(90, 24).setStandardSlotBackground().addItemStack(view.output());
    }

    @Override
    public void draw(SmallPlatePressRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        arrow.draw(graphics, 60, 22);
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable("jei.advancedrocketrycommunity.small_plate_press.redstone"),
                28, 47, 0xFF344D54, false);
    }

    @Override
    public ResourceLocation getRegistryName(SmallPlatePressRecipe recipe) {
        return recipe.getId();
    }
}
