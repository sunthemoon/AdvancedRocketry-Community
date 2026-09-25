package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

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
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRecipes;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Strict datapack recipe adapter for the first journaled multiblock process. */
public final class RollingMachineRecipe implements Recipe<SimpleContainer> {
    public static final int MAX_INGREDIENT_JSON_CHARS = BoundedItemIngredientCodec.MAX_INGREDIENT_JSON_CHARS;
    public static final int MAX_RESOURCE_ID_CHARS = ProcessResourceKey.MAX_RESOURCE_ID_CHARS;
    public static final int MAX_ITEM_COUNT = 64;
    public static final int MAX_FLUID_AMOUNT = RollingMachinePortBlockEntity.FLUID_CAPACITY;

    private static final Set<String> TOP_LEVEL_FIELDS = Set.of(
            "type",
            "schema_version",
            "ingredient",
            "input_count",
            "fluid",
            "result",
            "processing_time",
            "energy_per_tick"
    );
    private static final Set<String> FLUID_FIELDS = Set.of("fluid", "amount");
    private static final Set<String> RESULT_FIELDS = Set.of("item", "count");

    private final ResourceLocation id;
    private final Ingredient ingredient;
    private final int inputCount;
    private final Fluid fluid;
    private final int fluidAmount;
    private final ItemStack result;
    private final ProcessDefinition processDefinition;
    private final List<String> ingredientAlternatives;
    private final String signature;

    public RollingMachineRecipe(
            ResourceLocation id,
            Ingredient ingredient,
            int inputCount,
            Fluid fluid,
            int fluidAmount,
            ItemStack result,
            int processingTicks,
            int energyPerTick,
            int encodedSizeBytes
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.ingredient = Objects.requireNonNull(ingredient, "ingredient");
        this.inputCount = requireRange("input_count", inputCount, 1, MAX_ITEM_COUNT);
        this.fluid = Objects.requireNonNull(fluid, "fluid");
        this.fluidAmount = requireRange("fluid amount", fluidAmount, 1, MAX_FLUID_AMOUNT);
        this.result = Objects.requireNonNull(result, "result").copy();
        this.ingredientAlternatives = BoundedItemIngredientCodec.resolveAlternatives(ingredient);
        validateFluid();
        validateResult();
        this.processDefinition = new ProcessDefinition(
                id.toString(),
                ProcessDefinition.SCHEMA_VERSION,
                encodedSizeBytes,
                processingTicks,
                energyPerTick,
                List.of(
                        new ProcessInput(
                                ProcessResourceKind.ITEM,
                                RollingMachinePortType.ITEM_INPUT.channel(),
                                ingredientAlternatives,
                                inputCount
                        ),
                        new ProcessInput(
                                ProcessResourceKind.FLUID,
                                RollingMachinePortType.FLUID_INPUT.channel(),
                                List.of(fluidId()),
                                fluidAmount
                        )
                ),
                List.of(new ProcessOutput(
                        new ProcessResourceKey(
                                ProcessResourceKind.ITEM,
                                RollingMachinePortType.ITEM_OUTPUT.channel(),
                                resultId()
                        ),
                        result.getCount()
                ))
        );
        this.signature = signature(canonicalPayload());
    }

    @Override
    public boolean matches(SimpleContainer container, Level level) {
        ItemStack stack = container.getItem(0);
        return ingredient.test(stack) && stack.getCount() >= inputCount;
    }

    @Override
    public ItemStack assemble(SimpleContainer container, RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return result.copy();
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
        return ModRecipes.ROLLING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.ROLLING_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public Ingredient ingredient() {
        return ingredient;
    }

    public int inputCount() {
        return inputCount;
    }

    public Fluid fluid() {
        return fluid;
    }

    public int fluidAmount() {
        return fluidAmount;
    }

    public ItemStack result() {
        return result.copy();
    }

    public ProcessDefinition processDefinition() {
        return processDefinition;
    }

    public List<String> ingredientAlternatives() {
        return ingredientAlternatives;
    }

    public String signature() {
        return signature;
    }

    private void validateFluid() {
        ResourceLocation registered = BuiltInRegistries.FLUID.getKey(fluid);
        if (fluid != Fluids.WATER || registered == null) {
            throw new IllegalArgumentException("Rolling Machine fluid must be registered water");
        }
    }

    private void validateResult() {
        if (result.isEmpty()
                || result.hasTag()
                || result.getCount() < 1
                || result.getCount() > Math.min(MAX_ITEM_COUNT, result.getMaxStackSize())
                || BuiltInRegistries.ITEM.getKey(result.getItem()) == null) {
            throw new IllegalArgumentException("Rolling Machine result must be a bounded registered item without NBT");
        }
    }

    private String canonicalPayload() {
        return String.join(
                "|",
                Integer.toString(ProcessDefinition.SCHEMA_VERSION),
                id.toString(),
                String.join(",", ingredientAlternatives),
                Integer.toString(inputCount),
                fluidId(),
                Integer.toString(fluidAmount),
                resultId(),
                Integer.toString(result.getCount()),
                Integer.toString(processDefinition.durationTicks()),
                Integer.toString(processDefinition.energyPerTick())
        );
    }

    private String fluidId() {
        return requireRegisteredId(BuiltInRegistries.FLUID.getKey(fluid), "fluid").toString();
    }

    private String resultId() {
        return requireRegisteredId(BuiltInRegistries.ITEM.getKey(result.getItem()), "result").toString();
    }

    private static ResourceLocation requireRegisteredId(ResourceLocation id, String field) {
        if (id == null || id.toString().length() > MAX_RESOURCE_ID_CHARS) {
            throw new IllegalArgumentException("Rolling Machine " + field + " has an invalid resource id");
        }
        return id;
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

    private static int requireRange(String field, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(field + " is outside the bounded range");
        }
        return value;
    }

    public static final class Serializer implements RecipeSerializer<RollingMachineRecipe> {
        @Override
        public RollingMachineRecipe fromJson(ResourceLocation id, JsonObject json) {
            try {
                int encodedBytes = json.toString().getBytes(StandardCharsets.UTF_8).length;
                if (encodedBytes < 1 || encodedBytes > ProcessDefinition.MAX_DEFINITION_BYTES) {
                    throw new IllegalArgumentException("recipe JSON exceeds the 64 KiB limit");
                }
                if (!TOP_LEVEL_FIELDS.equals(json.keySet())
                        || !RollingMachineIds.RECIPE.toString().equals(GsonHelper.getAsString(json, "type"))) {
                    throw new IllegalArgumentException("recipe has missing or unexpected top-level fields");
                }
                if (requireInt(json, "schema_version") != ProcessDefinition.SCHEMA_VERSION) {
                    throw new IllegalArgumentException("unsupported recipe schema");
                }
                JsonElement ingredientJson = GsonHelper.getNonNull(json, "ingredient");
                Ingredient ingredient = BoundedItemIngredientCodec.decode(ingredientJson);
                JsonObject fluidJson = exactObject(json, "fluid", FLUID_FIELDS);
                JsonObject resultJson = exactObject(json, "result", RESULT_FIELDS);
                return new RollingMachineRecipe(
                        id,
                        ingredient,
                        requireInt(json, "input_count"),
                        requireWater(GsonHelper.getAsString(fluidJson, "fluid")),
                        requireInt(fluidJson, "amount"),
                        new ItemStack(
                                requireItem(GsonHelper.getAsString(resultJson, "item")),
                                requireInt(resultJson, "count")
                        ),
                        requireInt(json, "processing_time"),
                        requireInt(json, "energy_per_tick"),
                        encodedBytes
                );
            } catch (RuntimeException exception) {
                throw new JsonSyntaxException(
                        "Invalid Rolling Machine recipe " + id + ": " + exception.getMessage(),
                        exception
                );
            }
        }

        @Override
        public RollingMachineRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            try {
                int schemaVersion = buffer.readVarInt();
                if (schemaVersion != ProcessDefinition.SCHEMA_VERSION) {
                    throw new IllegalArgumentException("unsupported recipe schema");
                }
                String ingredientText = buffer.readUtf(MAX_INGREDIENT_JSON_CHARS);
                JsonElement ingredientJson = JsonParser.parseString(ingredientText);
                Ingredient ingredient = BoundedItemIngredientCodec.decode(ingredientJson);
                int inputCount = buffer.readVarInt();
                Fluid fluid = requireWater(buffer.readUtf(MAX_RESOURCE_ID_CHARS));
                int fluidAmount = buffer.readVarInt();
                Item resultItem = requireItem(buffer.readUtf(MAX_RESOURCE_ID_CHARS));
                int resultCount = buffer.readVarInt();
                int processingTicks = buffer.readVarInt();
                int energyPerTick = buffer.readVarInt();
                int encodedBytes = canonicalNetworkSize(
                        id,
                        ingredientText,
                        inputCount,
                        fluid,
                        fluidAmount,
                        resultItem,
                        resultCount,
                        processingTicks,
                        energyPerTick
                );
                return new RollingMachineRecipe(
                        id,
                        ingredient,
                        inputCount,
                        fluid,
                        fluidAmount,
                        new ItemStack(resultItem, resultCount),
                        processingTicks,
                        energyPerTick,
                        encodedBytes
                );
            } catch (RuntimeException exception) {
                throw new JsonParseException("Invalid network Rolling Machine recipe " + id, exception);
            }
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, RollingMachineRecipe recipe) {
            String ingredientJson = recipe.ingredient.toJson().toString();
            BoundedItemIngredientCodec.validateOnly(JsonParser.parseString(ingredientJson));
            buffer.writeVarInt(ProcessDefinition.SCHEMA_VERSION);
            buffer.writeUtf(ingredientJson, MAX_INGREDIENT_JSON_CHARS);
            buffer.writeVarInt(recipe.inputCount);
            buffer.writeUtf(recipe.fluidId(), MAX_RESOURCE_ID_CHARS);
            buffer.writeVarInt(recipe.fluidAmount);
            buffer.writeUtf(recipe.resultId(), MAX_RESOURCE_ID_CHARS);
            buffer.writeVarInt(recipe.result.getCount());
            buffer.writeVarInt(recipe.processDefinition.durationTicks());
            buffer.writeVarInt(recipe.processDefinition.energyPerTick());
        }

        private static JsonObject exactObject(JsonObject parent, String field, Set<String> fields) {
            JsonObject object = GsonHelper.getAsJsonObject(parent, field);
            if (!fields.equals(object.keySet())) {
                throw new IllegalArgumentException(field + " has missing or unexpected fields");
            }
            return object;
        }

        private static int requireInt(JsonObject parent, String field) {
            JsonElement element = parent.get(field);
            if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException(field + " must be an integer");
            }
            try {
                return new BigDecimal(element.getAsString()).intValueExact();
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new IllegalArgumentException(field + " must be a 32-bit integer", exception);
            }
        }

        private static Fluid requireWater(String rawId) {
            ResourceLocation id = ResourceLocation.tryParse(rawId);
            Fluid fluid = id == null ? null : BuiltInRegistries.FLUID.getOptional(id).orElse(null);
            if (rawId.length() > MAX_RESOURCE_ID_CHARS || fluid != Fluids.WATER) {
                throw new IllegalArgumentException("fluid must be minecraft:water");
            }
            return fluid;
        }

        private static Item requireItem(String rawId) {
            ResourceLocation id = ResourceLocation.tryParse(rawId);
            Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (rawId.length() > MAX_RESOURCE_ID_CHARS
                    || item == null
                    || item.getDefaultInstance().isEmpty()) {
                throw new IllegalArgumentException("result item is unknown or empty");
            }
            return item;
        }

        private static int canonicalNetworkSize(
                ResourceLocation id,
                String ingredient,
                int inputCount,
                Fluid fluid,
                int fluidAmount,
                Item result,
                int resultCount,
                int processingTicks,
                int energyPerTick
        ) {
            String value = String.join(
                    "|",
                    id.toString(),
                    ingredient,
                    Integer.toString(inputCount),
                    String.valueOf(BuiltInRegistries.FLUID.getKey(fluid)),
                    Integer.toString(fluidAmount),
                    String.valueOf(BuiltInRegistries.ITEM.getKey(result)),
                    Integer.toString(resultCount),
                    Integer.toString(processingTicks),
                    Integer.toString(energyPerTick)
            );
            int bytes = value.getBytes(StandardCharsets.UTF_8).length;
            if (bytes < 1 || bytes > ProcessDefinition.MAX_DEFINITION_BYTES) {
                throw new IllegalArgumentException("network recipe exceeds the 64 KiB limit");
            }
            return bytes;
        }
    }
}
