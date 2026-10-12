package io.github.sunthemoon.advancedrocketrycommunity.client.compat.jei;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.client.ElectrolyzerScreen;
import io.github.sunthemoon.advancedrocketrycommunity.client.PrecisionAssemblerScreen;
import io.github.sunthemoon.advancedrocketrycommunity.client.RollingMachineScreen;
import io.github.sunthemoon.advancedrocketrycommunity.diagnostics.BetaDiagnosticId;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicMachineRecipe;
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
                new SmallPlatePressJeiCategory(helper),
                new ClassicMachineJeiCategory(helper, ClassicMachineJeiCategory.LATHE, "lathe", ModItems.LATHE.get()),
                new ClassicMachineJeiCategory(helper, ClassicMachineJeiCategory.ARC_FURNACE, "arc_furnace", ModItems.ARC_FURNACE.get()),
                new ClassicMachineJeiCategory(helper, ClassicMachineJeiCategory.CUTTING, "cutting_machine", ModItems.CUTTING_MACHINE.get()),
                new ClassicMachineJeiCategory(helper, ClassicMachineJeiCategory.CRYSTALLIZER, "crystallizer", ModItems.CRYSTALLIZER.get()),
                new ClassicMachineJeiCategory(helper, ClassicMachineJeiCategory.CHEMICAL_REACTOR, "chemical_reactor", ModItems.CHEMICAL_REACTOR.get()),
                new ClassicMachineJeiCategory(helper, ClassicMachineJeiCategory.PRECISION_LASER_ETCHER, "precision_laser_etcher", ModItems.PRECISION_LASER_ETCHER.get()),
                new ClassicMachineJeiCategory(helper, ClassicMachineJeiCategory.CENTRIFUGE, "centrifuge", ModItems.CENTRIFUGE.get())
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(List.of(new net.minecraft.world.item.ItemStack(ModItems.SOLAR_ARRAY.get()),
                        new net.minecraft.world.item.ItemStack(ModItems.SOLAR_ARRAY_PANEL.get()),
                        new net.minecraft.world.item.ItemStack(ModItems.POWER_OUTPUT_PLUG.get())),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                net.minecraft.network.chat.Component.translatable("jei.advancedrocketrycommunity.solar_array"));
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
        registerClassic(registration, level, ModRecipes.LATHE_TYPE.get(), ClassicMachineJeiCategory.LATHE);
        registerClassic(registration, level, ModRecipes.ARC_FURNACE_TYPE.get(), ClassicMachineJeiCategory.ARC_FURNACE);
        registerClassic(registration, level, ModRecipes.CUTTING_TYPE.get(), ClassicMachineJeiCategory.CUTTING);
        registerClassic(registration, level, ModRecipes.CRYSTALLIZER_TYPE.get(), ClassicMachineJeiCategory.CRYSTALLIZER);
        registerClassic(registration, level, ModRecipes.CHEMICAL_REACTOR_TYPE.get(), ClassicMachineJeiCategory.CHEMICAL_REACTOR);
        registerClassic(registration, level, ModRecipes.PRECISION_LASER_ETCHER_TYPE.get(), ClassicMachineJeiCategory.PRECISION_LASER_ETCHER);
        registerClassic(registration, level, ModRecipes.CENTRIFUGE_TYPE.get(), ClassicMachineJeiCategory.CENTRIFUGE);
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
        registration.addRecipeCatalyst(ModItems.LATHE.get(), ClassicMachineJeiCategory.LATHE);
        registration.addRecipeCatalyst(ModItems.ARC_FURNACE.get(), ClassicMachineJeiCategory.ARC_FURNACE);
        registration.addRecipeCatalyst(ModItems.CUTTING_MACHINE.get(), ClassicMachineJeiCategory.CUTTING);
        registration.addRecipeCatalyst(ModItems.CRYSTALLIZER.get(), ClassicMachineJeiCategory.CRYSTALLIZER);
        registration.addRecipeCatalyst(ModItems.CHEMICAL_REACTOR.get(), ClassicMachineJeiCategory.CHEMICAL_REACTOR);
        registration.addRecipeCatalyst(ModItems.PRECISION_LASER_ETCHER.get(), ClassicMachineJeiCategory.PRECISION_LASER_ETCHER);
        registration.addRecipeCatalyst(ModItems.CENTRIFUGE.get(), ClassicMachineJeiCategory.CENTRIFUGE);
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

    private static void registerClassic(IRecipeRegistration registration, ClientLevel level,
            net.minecraft.world.item.crafting.RecipeType<ClassicMachineRecipe> type,
            mezz.jei.api.recipe.RecipeType<ClassicMachineRecipe> category) {
        var recipes = level == null ? List.<ClassicMachineRecipe>of()
                : level.getRecipeManager().getAllRecipesFor(type);
        registration.addRecipes(category, recipes.stream().filter(recipe -> {
            try { recipe.definition(); return true; } catch (IllegalArgumentException invalid) { return false; }
        }).toList());
    }
}
