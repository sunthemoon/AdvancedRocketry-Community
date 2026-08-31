package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRecipes;
import java.util.Objects;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;

/** Bounded two-output recipe decoded before it can be exposed to the machine. */
public final class ElectrolyzerRecipe implements Recipe<SimpleContainer> {
    private static final int MAX_INGREDIENT_VARIANTS = 16;
    private static final int MAX_INGREDIENT_JSON_CHARS = 2_048;
    private static final int MAX_RESOURCE_ID_CHARS = 128;
    private final ResourceLocation id;
    private final Ingredient ingredient;
    private final Fluid fluid;
    private final ItemStack hydrogenResult;
    private final ItemStack oxygenResult;
    private final ElectrolyzerRecipeSpec spec;

    public ElectrolyzerRecipe(
            ResourceLocation id,
            Ingredient ingredient,
            Fluid fluid,
            ItemStack hydrogenResult,
            ItemStack oxygenResult,
            ElectrolyzerRecipeSpec spec
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.ingredient = Objects.requireNonNull(ingredient, "ingredient");
        this.fluid = Objects.requireNonNull(fluid, "fluid");
        this.hydrogenResult = Objects.requireNonNull(hydrogenResult, "hydrogenResult").copy();
        this.oxygenResult = Objects.requireNonNull(oxygenResult, "oxygenResult").copy();
        this.spec = Objects.requireNonNull(spec, "spec");
        validateContent();
    }

    private void validateContent() {
        if (ingredient.isEmpty()) {
            throw new IllegalArgumentException("Electrolyzer ingredient cannot be empty");
        }
        ItemStack[] variants = ingredient.getItems();
        if (variants.length < 1 || variants.length > MAX_INGREDIENT_VARIANTS) {
            throw new IllegalArgumentException("Electrolyzer ingredient must resolve to 1-16 bounded variants");
        }
        for (ItemStack variant : variants) {
            if (variant.hasTag()) {
                throw new IllegalArgumentException("Electrolyzer ingredients cannot carry NBT");
            }
        }
        if (fluid != Fluids.WATER || ForgeRegistries.FLUIDS.getKey(fluid) == null) {
            throw new IllegalArgumentException("Electrolyzer fluid must be registered water");
        }
        validateOutput("hydrogen_result", hydrogenResult, spec.hydrogenOutputCount());
        validateOutput("oxygen_result", oxygenResult, spec.oxygenOutputCount());
    }

    private static void validateOutput(String name, ItemStack result, int expectedCount) {
        if (result.isEmpty() || result.getCount() != expectedCount) {
            throw new IllegalArgumentException(name + " must be non-empty and match its bounded output count");
        }
        if (result.hasTag()) {
            throw new IllegalArgumentException(name + " cannot carry NBT");
        }
    }

    @Override
    public boolean matches(SimpleContainer container, Level level) {
        ItemStack stack = container.getItem(0);
        return ingredient.test(stack) && stack.getCount() >= spec.inputCount();
    }

    @Override
    public ItemStack assemble(SimpleContainer container, RegistryAccess registryAccess) {
        return hydrogenResult.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return hydrogenResult.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, ingredient);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.ELECTROLYZING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.ELECTROLYZING_TYPE.get();
    }

    public Ingredient ingredient() {
        return ingredient;
    }

    public Fluid fluid() {
        return fluid;
    }

    public ItemStack hydrogenResult() {
        return hydrogenResult.copy();
    }

    public ItemStack oxygenResult() {
        return oxygenResult.copy();
    }

    public ElectrolyzerRecipeSpec spec() {
        return spec;
    }

    public static final class Serializer implements RecipeSerializer<ElectrolyzerRecipe> {
        private static final String FLUID_FIELD = "fluid";

        @Override
        public ElectrolyzerRecipe fromJson(ResourceLocation id, JsonObject json) {
            try {
                Ingredient ingredient = Ingredient.fromJson(GsonHelper.getNonNull(json, "ingredient"));
                JsonObject fluidObject = GsonHelper.getAsJsonObject(json, FLUID_FIELD);
                Fluid fluid = requireFluid(GsonHelper.getAsString(fluidObject, FLUID_FIELD));
                ItemStack hydrogen = ShapedRecipe.itemStackFromJson(
                        GsonHelper.getAsJsonObject(json, "hydrogen_result")
                );
                ItemStack oxygen = ShapedRecipe.itemStackFromJson(
                        GsonHelper.getAsJsonObject(json, "oxygen_result")
                );
                ElectrolyzerRecipeSpec spec = new ElectrolyzerRecipeSpec(
                        GsonHelper.getAsInt(json, "schema_version"),
                        GsonHelper.getAsInt(json, "input_count"),
                        GsonHelper.getAsInt(fluidObject, "amount"),
                        GsonHelper.getAsInt(json, "processing_time"),
                        GsonHelper.getAsInt(json, "energy_per_tick"),
                        hydrogen.getCount(),
                        oxygen.getCount()
                );
                return new ElectrolyzerRecipe(id, ingredient, fluid, hydrogen, oxygen, spec);
            } catch (IllegalArgumentException exception) {
                throw new JsonSyntaxException("Invalid Electrolyzer recipe " + id + ": " + exception.getMessage(), exception);
            }
        }

        @Override
        public ElectrolyzerRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            try {
                String ingredientJson = buffer.readUtf(MAX_INGREDIENT_JSON_CHARS);
                Ingredient ingredient = Ingredient.fromJson(JsonParser.parseString(ingredientJson));
                Fluid fluid = requireFluid(buffer.readUtf(MAX_RESOURCE_ID_CHARS));
                int schemaVersion = buffer.readVarInt();
                int inputCount = buffer.readVarInt();
                int fluidAmount = buffer.readVarInt();
                int processingTicks = buffer.readVarInt();
                int energyPerTick = buffer.readVarInt();
                ItemStack hydrogen = readResult(buffer, "hydrogen_result");
                ItemStack oxygen = readResult(buffer, "oxygen_result");
                ElectrolyzerRecipeSpec spec = new ElectrolyzerRecipeSpec(
                        schemaVersion,
                        inputCount,
                        fluidAmount,
                        processingTicks,
                        energyPerTick,
                        hydrogen.getCount(),
                        oxygen.getCount()
                );
                return new ElectrolyzerRecipe(id, ingredient, fluid, hydrogen, oxygen, spec);
            } catch (RuntimeException exception) {
                throw new JsonParseException("Invalid network Electrolyzer recipe " + id, exception);
            }
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ElectrolyzerRecipe recipe) {
            String ingredientJson = recipe.ingredient.toJson().toString();
            buffer.writeUtf(ingredientJson, MAX_INGREDIENT_JSON_CHARS);
            ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(recipe.fluid);
            if (fluidId == null) {
                throw new IllegalStateException("Cannot synchronize an unregistered Electrolyzer fluid");
            }
            buffer.writeUtf(fluidId.toString(), MAX_RESOURCE_ID_CHARS);
            buffer.writeVarInt(recipe.spec.schemaVersion());
            buffer.writeVarInt(recipe.spec.inputCount());
            buffer.writeVarInt(recipe.spec.waterAmount());
            buffer.writeVarInt(recipe.spec.processingTicks());
            buffer.writeVarInt(recipe.spec.energyPerTick());
            writeResult(buffer, recipe.hydrogenResult);
            writeResult(buffer, recipe.oxygenResult);
        }

        private static ItemStack readResult(FriendlyByteBuf buffer, String field) {
            String rawId = buffer.readUtf(MAX_RESOURCE_ID_CHARS);
            ResourceLocation id = ResourceLocation.tryParse(rawId);
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            int count = buffer.readVarInt();
            if (item == null || item.getDefaultInstance().isEmpty() || count < 1 || count > 64) {
                throw new IllegalArgumentException("Invalid " + field + " item/count");
            }
            return new ItemStack(item, count);
        }

        private static void writeResult(FriendlyByteBuf buffer, ItemStack result) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(result.getItem());
            if (itemId == null || result.hasTag()) {
                throw new IllegalStateException("Cannot synchronize an invalid Electrolyzer result");
            }
            buffer.writeUtf(itemId.toString(), MAX_RESOURCE_ID_CHARS);
            buffer.writeVarInt(result.getCount());
        }

        private static Fluid requireFluid(String rawId) {
            ResourceLocation id = ResourceLocation.tryParse(rawId);
            if (id == null) {
                throw new IllegalArgumentException("Invalid fluid id: " + rawId);
            }
            return requireFluid(id);
        }

        private static Fluid requireFluid(ResourceLocation id) {
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(id);
            if (fluid == null || fluid == Fluids.EMPTY) {
                throw new IllegalArgumentException("Unknown or empty fluid: " + id);
            }
            return fluid;
        }
    }
}
