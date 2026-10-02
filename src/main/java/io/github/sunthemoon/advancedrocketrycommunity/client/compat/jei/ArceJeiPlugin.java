package io.github.sunthemoon.advancedrocketrycommunity.client.compat.jei;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.client.ElectrolyzerScreen;
import io.github.sunthemoon.advancedrocketrycommunity.client.PrecisionAssemblerScreen;
import io.github.sunthemoon.advancedrocketrycommunity.client.RollingMachineScreen;
import io.github.sunthemoon.advancedrocketrycommunity.diagnostics.BetaDiagnosticId;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import io.github.sunthemoon.advancedrocketrycommunity.material.press.SmallPlatePressRecipe;
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
        var helper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new ElectrolyzerJeiCategory(helper),
                new RollingMachineJeiCategory(helper),
                new PrecisionAssemblerJeiCategory(helper),
                new SmallPlatePressJeiCategory(helper)
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ClientLevel level = Minecraft.getInstance().level;
        List<ElectrolyzerRecipe> electrolyzerRecipes = level == null
                ? List.of()
                : level.getRecipeManager().getAllRecipesFor(ModRecipes.ELECTROLYZING_TYPE.get());
        List<RollingMachineRecipe> rollingRecipes = level == null
                ? List.of()
                : level.getRecipeManager().getAllRecipesFor(ModRecipes.ROLLING_TYPE.get());
        List<PrecisionAssemblerRecipe> precisionRecipes = level == null
                ? List.of()
                : level.getRecipeManager().getAllRecipesFor(ModRecipes.PRECISION_ASSEMBLING_TYPE.get());
        registration.addRecipes(ElectrolyzerJeiCategory.TYPE, electrolyzerRecipes);
        registration.addRecipes(RollingMachineJeiCategory.TYPE, rollingRecipes);
        registration.addRecipes(PrecisionAssemblerJeiCategory.TYPE, precisionRecipes);
        List<SmallPlatePressRecipe> pressRecipes = level == null ? List.of()
                : level.getRecipeManager().getAllRecipesFor(MaterialContent.SMALL_PLATE_PRESS_TYPE.get());
        registration.addRecipes(SmallPlatePressJeiCategory.TYPE, pressRecipes);
        AdvancedRocketryCommunity.LOGGER.info(
                "{} optional_compat=jei status=registered recipes={}",
                BetaDiagnosticId.OPTIONAL_COMPATIBILITY.code(),
                electrolyzerRecipes.size()
        );
        AdvancedRocketryCommunity.LOGGER.info(
                "{} optional_compat=jei machine_recipes rolling={} precision={} small_plate_press={}",
                BetaDiagnosticId.OPTIONAL_COMPATIBILITY.code(),
                rollingRecipes.size(), precisionRecipes.size(), pressRecipes.size()
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModItems.ELECTROLYZER.get(), ElectrolyzerJeiCategory.TYPE);
        registration.addRecipeCatalyst(ModItems.ROLLING_MACHINE.get(), RollingMachineJeiCategory.TYPE);
        registration.addRecipeCatalyst(ModItems.PRECISION_ASSEMBLER.get(), PrecisionAssemblerJeiCategory.TYPE);
        registration.addRecipeCatalyst(MaterialContent.SMALL_PLATE_PRESS_ITEM.get(), SmallPlatePressJeiCategory.TYPE);
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
        registration.addRecipeClickArea(
                RollingMachineScreen.class, 96, 43, 47, 12,
                RollingMachineJeiCategory.TYPE
        );
        registration.addRecipeClickArea(
                PrecisionAssemblerScreen.class, 18, 71, 212, 11,
                PrecisionAssemblerJeiCategory.TYPE
        );
    }
}
