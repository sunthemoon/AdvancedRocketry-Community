package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessInput;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessOutput;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.BoundedItemIngredientCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.DeferredProcessDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeJsonSignature;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRecipes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
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
    private static final int MAX_INGREDIENT_JSON_CHARS = BoundedItemIngredientCodec.MAX_INGREDIENT_JSON_CHARS;
    private static final int MAX_RESOURCE_ID_CHARS = 128;
    private final ResourceLocation id;
    private final Ingredient ingredient;
    private final Fluid fluid;
    private final ItemStack hydrogenResult;
    private final ItemStack oxygenResult;
    private final ElectrolyzerRecipeSpec spec;
    private final JsonElement ingredientJson;
    private final DeferredProcessDefinition definition;
    private final String signature;

    public ElectrolyzerRecipe(
            ResourceLocation id,
            Ingredient ingredient,
            Fluid fluid,
            ItemStack hydrogenResult,
            ItemStack oxygenResult,
            ElectrolyzerRecipeSpec spec
    ) {
        this(
                id,
                ingredient,
                fluid,
                hydrogenResult,
                oxygenResult,
                spec,
                canonicalEncodedSize(id, ingredient, fluid, hydrogenResult, oxygenResult, spec)
        );
    }

    private ElectrolyzerRecipe(
            ResourceLocation id,
            Ingredient ingredient,
            Fluid fluid,
            ItemStack hydrogenResult,
            ItemStack oxygenResult,
            ElectrolyzerRecipeSpec spec,
            int encodedSizeBytes
    ) {
        this(id, ingredient, fluid, hydrogenResult, oxygenResult, spec, encodedSizeBytes, ingredient.toJson());
    }

    private ElectrolyzerRecipe(ResourceLocation id, Ingredient ingredient, Fluid fluid,
            ItemStack hydrogenResult, ItemStack oxygenResult, ElectrolyzerRecipeSpec spec,
            int encodedSizeBytes, JsonElement ingredientJson) {
        this.id = Objects.requireNonNull(id, "id");
        this.ingredient = Objects.requireNonNull(ingredient, "ingredient");
        this.fluid = Objects.requireNonNull(fluid, "fluid");
        this.hydrogenResult = Objects.requireNonNull(hydrogenResult, "hydrogenResult").copy();
        this.oxygenResult = Objects.requireNonNull(oxygenResult, "oxygenResult").copy();
        this.spec = Objects.requireNonNull(spec, "spec");
        BoundedItemIngredientCodec.validateOnly(ingredientJson);
        this.ingredientJson = ingredientJson.deepCopy();
        validateContent();
        new ProcessDefinition(id.toString(), ProcessDefinition.SCHEMA_VERSION, encodedSizeBytes,
                spec.processingTicks(), spec.energyPerTick(), List.of(), List.of());
        definition = new DeferredProcessDefinition(id.toString(), () -> new ProcessDefinition(
                id.toString(),
                ProcessDefinition.SCHEMA_VERSION,
                encodedSizeBytes,
                spec.processingTicks(),
                spec.energyPerTick(),
                List.of(
                        new ProcessInput(
                                ProcessResourceKind.ITEM,
                                ElectrolyzerPortPolicy.ITEM_INPUT_CHANNEL,
                                BoundedItemIngredientCodec.resolveAlternatives(this.ingredientJson),
                                spec.inputCount()
                        ),
                        new ProcessInput(
                                ProcessResourceKind.FLUID,
                                ElectrolyzerPortPolicy.FLUID_INPUT_CHANNEL,
                                List.of(fluidId()),
                                spec.waterAmount()
                        )
                ),
                List.of(
                        output(ElectrolyzerPortPolicy.ITEM_OUTPUT_CHANNEL, hydrogenResult),
                        output(ElectrolyzerPortPolicy.ITEM_OUTPUT_CHANNEL, oxygenResult)
                )
        ));
        signature = RecipeJsonSignature.signature(jsonPayload());
    }

    private void validateContent() {
        if (fluid != Fluids.WATER || ForgeRegistries.FLUIDS.getKey(fluid) == null) {
            throw new IllegalArgumentException("Electrolyzer fluid must be registered water");
        }
        validateOutput(
                "hydrogen_result",
                hydrogenResult,
                ModItems.HYDROGEN_CANISTER.get(),
                spec.hydrogenOutputCount()
        );
        validateOutput(
                "oxygen_result",
                oxygenResult,
                ModItems.OXYGEN_CANISTER.get(),
                spec.oxygenOutputCount()
        );
    }

    private static void validateOutput(String name, ItemStack result, Item expectedItem, int expectedCount) {
        if (!result.is(expectedItem) || result.getCount() != expectedCount) {
            throw new IllegalArgumentException(name + " must use its stable canister and bounded output count");
        }
        if (result.hasTag()) {
            throw new IllegalArgumentException(name + " cannot carry NBT");
        }
    }

    @Override
    public boolean matches(SimpleContainer container, Level level) {
        ItemStack stack = container.getItem(0);
        return available() && !stack.hasTag() && !stack.isEmpty()
                && ingredientAlternatives().contains(itemId(stack)) && stack.getCount() >= spec.inputCount();
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

    @Override
    public boolean isSpecial() {
        return true;
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

    public List<String> ingredientAlternatives() {
        return processDefinition().inputs().get(0).alternatives();
    }

    public ProcessDefinition processDefinition() {
        return definition.get();
    }

    public String signature() {
        return signature;
    }

    public boolean available() { return definition.available(); }

    public boolean hasTagIngredients() { return BoundedItemIngredientCodec.hasTags(ingredientJson); }

   public String legacySignature() { return signature(canonicalPayload()); }

    public ElectrolyzerRecipe retainedPlan(io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceSnapshot before) {
        JsonObject json = jsonPayload();
        json.add("ingredient", io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RetainedRecipePlan
                .ingredient(before, ElectrolyzerPortPolicy.ITEM_INPUT_CHANNEL));
        return new Serializer().fromJson(id, json);
    }

    public JsonObject jsonPayload() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "advancedrocketrycommunity:electrolyzing");
        json.addProperty("schema_version", spec.schemaVersion());
        json.add("ingredient", ingredientJson.deepCopy());
        json.addProperty("input_count", spec.inputCount());
        JsonObject water = new JsonObject();
        water.addProperty("fluid", fluidId()); water.addProperty("amount", spec.waterAmount()); json.add("fluid", water);
        json.add("hydrogen_result", resultJson(hydrogenResult));
        json.add("oxygen_result", resultJson(oxygenResult));
        json.addProperty("processing_time", spec.processingTicks());
        json.addProperty("energy_per_tick", spec.energyPerTick());
        return json;
    }

    private static JsonObject resultJson(ItemStack stack) {
        JsonObject json = new JsonObject();
        json.addProperty("item", itemId(stack)); json.addProperty("count", stack.getCount());
        return json;
    }

    public static final class Serializer implements RecipeSerializer<ElectrolyzerRecipe> {
        private static final String FLUID_FIELD = "fluid";

        @Override
        public ElectrolyzerRecipe fromJson(ResourceLocation id, JsonObject json) {
            try {
                int encodedSizeBytes = RecipeJsonSignature.canonical(json).getBytes(StandardCharsets.UTF_8).length;
                if (encodedSizeBytes < 1 || encodedSizeBytes > ProcessDefinition.MAX_DEFINITION_BYTES) {
                    throw new IllegalArgumentException("recipe JSON exceeds the 64 KiB limit");
                }
                if (!Set.of("type", "schema_version", "ingredient", "input_count", "fluid",
                        "hydrogen_result", "oxygen_result", "processing_time", "energy_per_tick").equals(json.keySet())
                        || !"advancedrocketrycommunity:electrolyzing".equals(GsonHelper.getAsString(json, "type"))) {
                    throw new IllegalArgumentException("recipe has missing or unexpected top-level fields");
                }
                JsonElement ingredientJson = GsonHelper.getNonNull(json, "ingredient");
                Ingredient ingredient = BoundedItemIngredientCodec.decode(ingredientJson);
                JsonObject fluidObject = GsonHelper.getAsJsonObject(json, FLUID_FIELD);
                Fluid fluid = requireFluid(GsonHelper.getAsString(fluidObject, FLUID_FIELD));
                if (!Set.of("fluid", "amount").equals(fluidObject.keySet())) {
                    throw new IllegalArgumentException("fluid has missing or unexpected fields");
                }
                ItemStack hydrogen = resultFromJson(GsonHelper.getAsJsonObject(json, "hydrogen_result"));
                ItemStack oxygen = resultFromJson(GsonHelper.getAsJsonObject(json, "oxygen_result"));
                ElectrolyzerRecipeSpec spec = new ElectrolyzerRecipeSpec(
                        RecipeJsonSignature.requireInt(json, "schema_version"),
                        RecipeJsonSignature.requireInt(json, "input_count"),
                        RecipeJsonSignature.requireInt(fluidObject, "amount"),
                        RecipeJsonSignature.requireInt(json, "processing_time"),
                        RecipeJsonSignature.requireInt(json, "energy_per_tick"),
                        hydrogen.getCount(),
                        oxygen.getCount()
                );
                return new ElectrolyzerRecipe(
                        id,
                        ingredient,
                        fluid,
                        hydrogen,
                        oxygen,
                        spec,
                        encodedSizeBytes,
                        ingredientJson
                );
            } catch (IllegalArgumentException exception) {
                throw new JsonSyntaxException("Invalid Electrolyzer recipe " + id + ": " + exception.getMessage(), exception);
            }
        }

        @Override
        public ElectrolyzerRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            try {
                String ingredientJson = buffer.readUtf(MAX_INGREDIENT_JSON_CHARS);
                JsonElement parsed = JsonParser.parseString(ingredientJson);
                Ingredient ingredient = BoundedItemIngredientCodec.decode(parsed);
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
                return new ElectrolyzerRecipe(id, ingredient, fluid, hydrogen, oxygen, spec,
                        canonicalEncodedSize(id, ingredient, fluid, hydrogen, oxygen, spec), parsed);
            } catch (RuntimeException exception) {
                throw new JsonParseException("Invalid network Electrolyzer recipe " + id, exception);
            }
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ElectrolyzerRecipe recipe) {
            String ingredientJson = recipe.ingredientJson.toString();
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

        private static ItemStack resultFromJson(JsonObject json) {
            if (!Set.of("item").equals(json.keySet()) && !Set.of("item", "count").equals(json.keySet())) {
                throw new IllegalArgumentException("result has missing or unexpected fields");
            }
            String rawId = GsonHelper.getAsString(json, "item");
            ResourceLocation id = ResourceLocation.tryParse(rawId);
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            int count = json.has("count") ? RecipeJsonSignature.requireInt(json, "count") : 1;
            if (rawId.length() > MAX_RESOURCE_ID_CHARS || item == null || item.getDefaultInstance().isEmpty()
                    || count < 1 || count > Math.min(64, item.getMaxStackSize())) {
                throw new IllegalArgumentException("result has an invalid item/count");
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

    private ProcessOutput output(String channel, ItemStack stack) {
        return new ProcessOutput(
                new ProcessResourceKey(ProcessResourceKind.ITEM, channel, itemId(stack)),
                stack.getCount()
        );
    }

    private String canonicalPayload() {
        return String.join(
                "|",
                Integer.toString(ProcessDefinition.SCHEMA_VERSION),
                id.toString(),
                String.join(",", ingredientAlternatives()),
                Integer.toString(spec.inputCount()),
                fluidId(),
                Integer.toString(spec.waterAmount()),
                itemId(hydrogenResult),
                Integer.toString(hydrogenResult.getCount()),
                itemId(oxygenResult),
                Integer.toString(oxygenResult.getCount()),
                Integer.toString(spec.processingTicks()),
                Integer.toString(spec.energyPerTick())
        );
    }

    private String fluidId() {
        ResourceLocation registered = ForgeRegistries.FLUIDS.getKey(fluid);
        if (registered == null) {
            throw new IllegalArgumentException("Electrolyzer fluid has no registered ID");
        }
        return registered.toString();
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation registered = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (registered == null) {
            throw new IllegalArgumentException("Electrolyzer item has no registered ID");
        }
        return registered.toString();
    }

    private static int canonicalEncodedSize(
            ResourceLocation id,
            Ingredient ingredient,
            Fluid fluid,
            ItemStack hydrogen,
            ItemStack oxygen,
            ElectrolyzerRecipeSpec spec
    ) {
        String payload = id + "|" + ingredient.toJson() + "|" + ForgeRegistries.FLUIDS.getKey(fluid)
                + "|" + ForgeRegistries.ITEMS.getKey(hydrogen.getItem()) + "|" + hydrogen.getCount()
                + "|" + ForgeRegistries.ITEMS.getKey(oxygen.getItem()) + "|" + oxygen.getCount()
                + "|" + spec;
        return Math.max(1, payload.getBytes(StandardCharsets.UTF_8).length);
    }

    private static String signature(String canonical) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
