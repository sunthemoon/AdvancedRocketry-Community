package io.github.sunthemoon.advancedrocketrycommunity.registry;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerRecipe;
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

    private ModRecipes() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        SERIALIZERS.register(modBus);
    }
}
