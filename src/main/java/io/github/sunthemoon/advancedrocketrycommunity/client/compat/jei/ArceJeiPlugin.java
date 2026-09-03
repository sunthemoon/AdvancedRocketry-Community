package io.github.sunthemoon.advancedrocketrycommunity.client.compat.jei;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.client.ElectrolyzerScreen;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRecipes;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;

/** Optional client adapter; core initialization never references JEI classes. */
@JeiPlugin
public final class ArceJeiPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_ID = ModIdentity.id("jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new ElectrolyzerJeiCategory(
                registration.getJeiHelpers().getGuiHelper()
        ));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ClientLevel level = Minecraft.getInstance().level;
        List<ElectrolyzerRecipe> recipes = level == null
                ? List.of()
                : level.getRecipeManager().getAllRecipesFor(ModRecipes.ELECTROLYZING_TYPE.get());
        registration.addRecipes(ElectrolyzerJeiCategory.TYPE, recipes);
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE-BETA-1100 optional_compat=jei recipes={}",
                recipes.size()
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModItems.ELECTROLYZER.get(), ElectrolyzerJeiCategory.TYPE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addRecipeClickArea(
                ElectrolyzerScreen.class,
                108,
                59,
                32,
                8,
                ElectrolyzerJeiCategory.TYPE
        );
    }
}
