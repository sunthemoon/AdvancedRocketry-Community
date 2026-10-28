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
import java.util.function.Predicate;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Optional client adapter; core initialization never references JEI classes. */
@JeiPlugin
public final class ArceJeiPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_ID = ModIdentity.id("jei_plugin");

    // Render thread only: JEI's callbacks and the client network events below all run there.
    private final ServerTagGate<IRecipeManager> gate = new ServerTagGate<>();
    private IJeiRuntime runtime;

    public ArceJeiPlugin() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ClientPlayerNetworkEvent.LoggingIn.class,
                event -> gate.connectionChanged());
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ClientPlayerNetworkEvent.LoggingOut.class,
                event -> gate.connectionChanged());
        // Low, so RecipeTagGeneration has already advanced the generation that deferred definitions check.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, TagsUpdatedEvent.class, this::onTagsUpdated);
    }

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
        register(registration, level, ModRecipes.ELECTROLYZING_TYPE.get(), ElectrolyzerJeiCategory.TYPE,
                ElectrolyzerRecipe::hasTagIngredients, recipe -> true);
        register(registration, level, ModRecipes.ROLLING_TYPE.get(), RollingMachineJeiCategory.TYPE,
                RollingMachineRecipe::hasTagIngredients, recipe -> true);
        register(registration, level, ModRecipes.PRECISION_ASSEMBLING_TYPE.get(), PrecisionAssemblerJeiCategory.TYPE,
                PrecisionAssemblerRecipe::hasTagIngredients, recipe -> true);
        register(registration, level, MaterialContent.SMALL_PLATE_PRESS_TYPE.get(), SmallPlatePressJeiCategory.TYPE,
                SmallPlatePressRecipe::hasTagIngredients, recipe -> true);
        registerClassic(registration, level, ModRecipes.LATHE_TYPE.get(), ClassicMachineJeiCategory.LATHE);
        registerClassic(registration, level, ModRecipes.ARC_FURNACE_TYPE.get(), ClassicMachineJeiCategory.ARC_FURNACE);
        registerClassic(registration, level, ModRecipes.CUTTING_TYPE.get(), ClassicMachineJeiCategory.CUTTING);
        registerClassic(registration, level, ModRecipes.CRYSTALLIZER_TYPE.get(), ClassicMachineJeiCategory.CRYSTALLIZER);
        registerClassic(registration, level, ModRecipes.CHEMICAL_REACTOR_TYPE.get(), ClassicMachineJeiCategory.CHEMICAL_REACTOR);
        registerClassic(registration, level, ModRecipes.PRECISION_LASER_ETCHER_TYPE.get(), ClassicMachineJeiCategory.PRECISION_LASER_ETCHER);
        registerClassic(registration, level, ModRecipes.CENTRIFUGE_TYPE.get(), ClassicMachineJeiCategory.CENTRIFUGE);
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

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        release();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        gate.runtimeStopped();
    }

    private void onTagsUpdated(TagsUpdatedEvent event) {
        // The integrated server's own data load runs on the server thread and is not this connection's tags packet.
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) {
            gate.tagsArrived();
            release();
        }
    }

    private void release() {
        if (runtime != null) {
            gate.release(runtime.getRecipeManager())
                    .forEach(released -> receipt("registered", released.category(), released.total()));
        }
    }

    private void registerClassic(IRecipeRegistration registration, ClientLevel level,
            net.minecraft.world.item.crafting.RecipeType<ClassicMachineRecipe> type,
            mezz.jei.api.recipe.RecipeType<ClassicMachineRecipe> category) {
        register(registration, level, type, category, ClassicMachineRecipe::hasTagIngredients, recipe -> {
            try { recipe.definition(); return true; } catch (IllegalArgumentException invalid) { return false; }
        });
    }

    private <C extends Container, T extends Recipe<C>> void register(IRecipeRegistration registration,
            ClientLevel level, net.minecraft.world.item.crafting.RecipeType<T> type,
            mezz.jei.api.recipe.RecipeType<T> category, Predicate<T> namesTag, Predicate<T> shown) {
        List<T> recipes = level == null ? List.of() : level.getRecipeManager().getAllRecipesFor(type);
        String id = category.getUid().toString();
        List<T> now = gate.admit(id, recipes, namesTag, shown, (manager, later) -> manager.addRecipes(category, later));
        registration.addRecipes(category, now);
        // One bounded receipt per category per registration, after any recipe filtering.
        receipt("registered", id, now.size());
        if (gate.held(id) > 0) {
            receipt("held", id, gate.held(id));
        }
    }

    private static void receipt(String status, String category, int recipes) {
        AdvancedRocketryCommunity.LOGGER.info("{} optional_compat=jei status={} category={} recipes={}",
                BetaDiagnosticId.OPTIONAL_COMPATIBILITY.code(), status, category, recipes);
    }
}
