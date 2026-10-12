package io.github.sunthemoon.advancedrocketrycommunity.registry;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicMachineRecipe;

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

    public static final RegistryObject<RecipeType<ClassicMachineRecipe>> LATHE_TYPE = TYPES.register("lathe", () -> new RecipeType<>() {
        @Override public String toString() { return AdvancedRocketryCommunity.MOD_ID + ":lathe"; }
    });
    public static final RegistryObject<RecipeSerializer<ClassicMachineRecipe>> LATHE_SERIALIZER =
            SERIALIZERS.register("lathe", ClassicMachineRecipe.Serializer::new);
    public static final RegistryObject<RecipeType<ClassicMachineRecipe>> ARC_FURNACE_TYPE = classicType("arc_furnace");
    public static final RegistryObject<RecipeType<ClassicMachineRecipe>> CUTTING_TYPE = classicType("cutting");
    public static final RegistryObject<RecipeSerializer<ClassicMachineRecipe>> ARC_FURNACE_SERIALIZER =
            SERIALIZERS.register("arc_furnace", () -> new ClassicMachineRecipe.Serializer(AdvancedRocketryCommunity.MOD_ID + ":arc_furnace"));
    public static final RegistryObject<RecipeSerializer<ClassicMachineRecipe>> CUTTING_SERIALIZER =
            SERIALIZERS.register("cutting", () -> new ClassicMachineRecipe.Serializer(AdvancedRocketryCommunity.MOD_ID + ":cutting"));
    public static final RegistryObject<RecipeType<ClassicMachineRecipe>> CRYSTALLIZER_TYPE = classicType("crystallizing");
    public static final RegistryObject<RecipeSerializer<ClassicMachineRecipe>> CRYSTALLIZER_SERIALIZER =
            SERIALIZERS.register("crystallizing", () -> new ClassicMachineRecipe.Serializer(AdvancedRocketryCommunity.MOD_ID + ":crystallizing"));
    public static final RegistryObject<RecipeType<ClassicMachineRecipe>> CHEMICAL_REACTOR_TYPE = classicType("chemical_reacting");
    public static final RegistryObject<RecipeSerializer<ClassicMachineRecipe>> CHEMICAL_REACTOR_SERIALIZER =
            SERIALIZERS.register("chemical_reacting", () -> new ClassicMachineRecipe.Serializer(AdvancedRocketryCommunity.MOD_ID + ":chemical_reacting"));
    public static final RegistryObject<RecipeType<ClassicMachineRecipe>> PRECISION_LASER_ETCHER_TYPE = classicType("laser_etching");
    public static final RegistryObject<RecipeSerializer<ClassicMachineRecipe>> PRECISION_LASER_ETCHER_SERIALIZER =
            SERIALIZERS.register("laser_etching", () -> new ClassicMachineRecipe.Serializer(AdvancedRocketryCommunity.MOD_ID + ":laser_etching"));
    public static final RegistryObject<RecipeType<ClassicMachineRecipe>> CENTRIFUGE_TYPE = classicType("centrifuging");
    public static final RegistryObject<RecipeSerializer<ClassicMachineRecipe>> CENTRIFUGE_SERIALIZER =
            SERIALIZERS.register("centrifuging", () -> new ClassicMachineRecipe.Serializer(AdvancedRocketryCommunity.MOD_ID + ":centrifuging"));
    private static RegistryObject<RecipeType<ClassicMachineRecipe>> classicType(String name) {
        return TYPES.register(name, () -> new RecipeType<>() {
            @Override public String toString() { return AdvancedRocketryCommunity.MOD_ID + ":" + name; }
        });
    }

    private ModRecipes() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        SERIALIZERS.register(modBus);
    }
}
