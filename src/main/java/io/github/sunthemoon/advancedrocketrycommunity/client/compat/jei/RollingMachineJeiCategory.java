package io.github.sunthemoon.advancedrocketrycommunity.client.compat.jei;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Optional JEI view of the Rolling Machine's server-synchronized recipe definition. */
final class RollingMachineJeiCategory extends AbstractRecipeCategory<RollingMachineRecipe> {
    static final RecipeType<RollingMachineRecipe> TYPE = RecipeType.create(
            AdvancedRocketryCommunity.MOD_ID, "rolling", RollingMachineRecipe.class
    );

    private final IDrawable arrow;

    RollingMachineJeiCategory(IGuiHelper helper) {
        super(TYPE, Component.translatable("menu.advancedrocketrycommunity.rolling_machine"),
                helper.createDrawableItemLike(ModItems.ROLLING_MACHINE.get()), 146, 55);
        arrow = helper.getRecipeArrow();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RollingMachineRecipe recipe, IFocusGroup focuses) {
        MachineRecipeView view = MachineRecipeView.from(recipe.processDefinition());
        if (view.itemInputs().size() != 1 || view.fluidInputs().size() != 1
                || view.itemOutputs().size() != 1) {
            throw new IllegalArgumentException("Rolling Machine display has an unexpected port shape");
        }
        builder.addInputSlot(5, 10).setStandardSlotBackground()
                .addItemStacks(view.itemInputs().get(0).alternatives());
        MachineRecipeView.FluidInput fluid = view.fluidInputs().get(0);
        builder.addInputSlot(33, 10).setStandardSlotBackground()
                .setFluidRenderer(fluid.amount(), true, 16, 16)
                .addFluidStack(fluid.fluid(), fluid.amount());
        builder.addOutputSlot(118, 10).setStandardSlotBackground()
                .addItemStack(view.itemOutputs().get(0));
    }

    @Override
    public void draw(
            RollingMachineRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics graphics,
            double mouseX,
            double mouseY
    ) {
        arrow.draw(graphics, 78, 9);
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable("jei.advancedrocketrycommunity.process_cost",
                        recipe.processDefinition().durationTicks(),
                        recipe.processDefinition().energyPerTick()),
                5, 37, 0xFF344D54, false);
    }

    @Override
    public ResourceLocation getRegistryName(RollingMachineRecipe recipe) {
        return recipe.getId();
    }
}
