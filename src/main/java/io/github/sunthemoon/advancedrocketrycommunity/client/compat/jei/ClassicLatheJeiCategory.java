package io.github.sunthemoon.advancedrocketrycommunity.client.compat.jei;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicMachineRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.ArrayList;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Optional client-only view of synced authored rows, using the client's own bounded tags. */
final class ClassicLatheJeiCategory extends AbstractRecipeCategory<ClassicMachineRecipe> {
    static final RecipeType<ClassicMachineRecipe> TYPE = RecipeType.create(ModIdentity.MOD_ID, "lathe", ClassicMachineRecipe.class);
    private final IDrawable arrow;
    ClassicLatheJeiCategory(IGuiHelper helper) {
        super(TYPE, Component.translatable("block.advancedrocketrycommunity.lathe"),
                helper.createDrawableItemLike(ModItems.LATHE.get()), 156, 104); arrow = helper.getRecipeArrow();
    }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, ClassicMachineRecipe recipe, IFocusGroup focuses) {
        var alternatives = recipe.resolveInputs();
        for (int index = 0; index < alternatives.size(); index++) {
            var stacks = new ArrayList<ItemStack>();
            for (String id : alternatives.get(index)) {
                stacks.add(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id)), recipe.rows().itemInputs().get(index).count()));
            }
            builder.addInputSlot(4, 4 + index * 18).setStandardSlotBackground().addItemStacks(stacks);
        }
        for (int index = 0; index < recipe.rows().itemOutputs().size(); index++) {
            builder.addOutputSlot(132, 4 + index * 18).setStandardSlotBackground().addItemStack(recipe.output(index));
        }
        for (int index = 0; index < recipe.rows().fluidInputs().size(); index++) {
            var row = recipe.rows().fluidInputs().get(index);
            builder.addInputSlot(30, 4 + index * 18).setStandardSlotBackground().setFluidRenderer(16_000, true, 16, 16)
                    .addFluidStack(BuiltInRegistries.FLUID.get(row.fluidId()), row.amount());
        }
        for (int index = 0; index < recipe.rows().fluidOutputs().size(); index++) {
            var row = recipe.rows().fluidOutputs().get(index);
            builder.addOutputSlot(106, 4 + index * 18).setStandardSlotBackground().setFluidRenderer(16_000, true, 16, 16)
                    .addFluidStack(BuiltInRegistries.FLUID.get(row.fluidId()), row.amount());
        }
    }
    @Override public void draw(ClassicMachineRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, 63, 25);
        graphics.drawString(Minecraft.getInstance().font, recipe.rows().processingTicks() + " ticks / "
                + recipe.rows().energyPerTick() + " FE/t", 4, 84, 0xFF555555, false);
    }
    @Override public ResourceLocation getRegistryName(ClassicMachineRecipe recipe) { return recipe.getId(); }
}
