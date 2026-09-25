package io.github.sunthemoon.advancedrocketrycommunity.client.compat.jei;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRecipe;
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

/** Fixed-slot JEI view; omitted inputs remain empty in the actual machine. */
final class PrecisionAssemblerJeiCategory extends AbstractRecipeCategory<PrecisionAssemblerRecipe> {
    static final RecipeType<PrecisionAssemblerRecipe> TYPE = RecipeType.create(
            AdvancedRocketryCommunity.MOD_ID, "precision_assembling", PrecisionAssemblerRecipe.class
    );

    private final IDrawable arrow;

    PrecisionAssemblerJeiCategory(IGuiHelper helper) {
        super(TYPE, Component.translatable("menu.advancedrocketrycommunity.precision_assembler"),
                helper.createDrawableItemLike(ModItems.PRECISION_ASSEMBLER.get()), 190, 61);
        arrow = helper.getRecipeArrow();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, PrecisionAssemblerRecipe recipe, IFocusGroup focuses) {
        MachineRecipeView view = MachineRecipeView.from(recipe.processDefinition());
        if (!view.fluidInputs().isEmpty() || view.itemInputs().size() < 2
                || view.itemInputs().size() > 5 || view.itemOutputs().isEmpty()
                || view.itemOutputs().size() > 2) {
            throw new IllegalArgumentException("Precision Assembler display has an unexpected port shape");
        }
        for (int index = 0; index < view.itemInputs().size(); index++) {
            builder.addInputSlot(5 + index * 22, 10).setStandardSlotBackground()
                    .addItemStacks(view.itemInputs().get(index).alternatives());
        }
        for (int index = 0; index < view.itemOutputs().size(); index++) {
            builder.addOutputSlot(145 + index * 22, 10).setStandardSlotBackground()
                    .addItemStack(view.itemOutputs().get(index));
        }
    }

    @Override
    public void draw(
            PrecisionAssemblerRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics graphics,
            double mouseX,
            double mouseY
    ) {
        arrow.draw(graphics, 115, 9);
        for (int index = 0; index < recipe.inputs().size(); index++) {
            graphics.drawString(Minecraft.getInstance().font, Integer.toString(index),
                    11 + index * 22, 32, 0xFF52636D, false);
        }
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable("jei.advancedrocketrycommunity.process_cost",
                        recipe.processDefinition().durationTicks(),
                        recipe.processDefinition().energyPerTick()),
                5, 48, 0xFF344D54, false);
    }

    @Override
    public ResourceLocation getRegistryName(PrecisionAssemblerRecipe recipe) {
        return recipe.getId();
    }
}
