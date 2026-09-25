package io.github.sunthemoon.advancedrocketrycommunity.registry;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(
            ForgeRegistries.RECIPE_TYPES,
            AdvancedRocketryCommunity.MOD_ID
    );
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(
            ForgeRegistries.RECIPE_SERIALIZERS,
            AdvancedRocketryCommunity.MOD_ID
    );

    public static final RegistryObject<RecipeType<ElectrolyzerRecipe>> ELECTROLYZING_TYPE = TYPES.register(
            "electrolyzing",
            () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return AdvancedRocketryCommunity.MOD_ID + ":electrolyzing";
                }
            }
    );
    public static final RegistryObject<RecipeSerializer<ElectrolyzerRecipe>> ELECTROLYZING_SERIALIZER =
            SERIALIZERS.register("electrolyzing", ElectrolyzerRecipe.Serializer::new);
    public static final RegistryObject<RecipeType<RollingMachineRecipe>> ROLLING_TYPE = TYPES.register(
            "rolling",
            () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return AdvancedRocketryCommunity.MOD_ID + ":rolling";
                }
            }
    );
    public static final RegistryObject<RecipeSerializer<RollingMachineRecipe>> ROLLING_SERIALIZER =
            SERIALIZERS.register("rolling", RollingMachineRecipe.Serializer::new);
    public static final RegistryObject<RecipeType<PrecisionAssemblerRecipe>> PRECISION_ASSEMBLING_TYPE =
            TYPES.register("precision_assembling", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return AdvancedRocketryCommunity.MOD_ID + ":precision_assembling";
                }
            });
    public static final RegistryObject<RecipeSerializer<PrecisionAssemblerRecipe>> PRECISION_ASSEMBLING_SERIALIZER =
            SERIALIZERS.register("precision_assembling", PrecisionAssemblerRecipe.Serializer::new);

    private ModRecipes() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        SERIALIZERS.register(modBus);
    }
}
