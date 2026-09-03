package io.github.sunthemoon.advancedrocketrycommunity.client.compat.jei;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.Arrays;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Client-only JEI projection of the existing server-synchronized recipe. */
final class ElectrolyzerJeiCategory extends AbstractRecipeCategory<ElectrolyzerRecipe> {
    static final RecipeType<ElectrolyzerRecipe> TYPE = RecipeType.create(
            AdvancedRocketryCommunity.MOD_ID,
            "electrolyzing",
            ElectrolyzerRecipe.class
    );

    private static final int WIDTH = 132;
    private static final int HEIGHT = 42;
    private final IDrawable arrow;

    ElectrolyzerJeiCategory(IGuiHelper guiHelper) {
        super(
                TYPE,
                Component.translatable("menu.advancedrocketrycommunity.electrolyzer"),
                guiHelper.createDrawableItemLike(ModItems.ELECTROLYZER.get()),
                WIDTH,
                HEIGHT
        );
        arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public void setRecipe(
            IRecipeLayoutBuilder builder,
            ElectrolyzerRecipe recipe,
            IFocusGroup focuses
    ) {
        List<ItemStack> inputs = Arrays.stream(recipe.ingredient().getItems())
                .map(ItemStack::copy)
                .peek(stack -> stack.setCount(recipe.spec().inputCount()))
                .toList();
        builder.addInputSlot(5, 10)
                .setStandardSlotBackground()
                .addItemStacks(inputs);
        builder.addInputSlot(31, 10)
                .setStandardSlotBackground()
                .setFluidRenderer(recipe.spec().waterAmount(), true, 16, 16)
                .addFluidStack(recipe.fluid(), recipe.spec().waterAmount());
        builder.addOutputSlot(87, 10)
                .setStandardSlotBackground()
                .addItemStack(recipe.hydrogenResult());
        builder.addOutputSlot(113, 10)
                .setStandardSlotBackground()
                .addItemStack(recipe.oxygenResult());
    }

    @Override
    public void draw(
            ElectrolyzerRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics graphics,
            double mouseX,
            double mouseY
    ) {
        arrow.draw(graphics, 57, 9);
    }

    @Override
    public ResourceLocation getRegistryName(ElectrolyzerRecipe recipe) {
        return recipe.getId();
    }
}
