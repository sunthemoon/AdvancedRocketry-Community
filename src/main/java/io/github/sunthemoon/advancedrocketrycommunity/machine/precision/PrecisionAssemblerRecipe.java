package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import com.google.gson.JsonArray;
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
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.BoundedItemIngredientCodec;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRecipes;
import io.netty.buffer.Unpooled;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Exact-slot, multi-input recipe adapter for the shared process transaction kernel. */
public final class PrecisionAssemblerRecipe implements Recipe<SimpleContainer> {
    public static final int MIN_INPUTS = 2;
    public static final int MAX_INPUTS = PrecisionAssemblerChannels.INPUT_COUNT;
    public static final int MIN_OUTPUTS = 1;
    public static final int MAX_OUTPUTS = PrecisionAssemblerChannels.OUTPUT_COUNT;
    public static final int MAX_ITEM_COUNT = 64;
    // Reserve nine journal entries for currently stored foreign Items and output identities.
    public static final int MAX_TOTAL_ALTERNATIVES = ProcessResourceSnapshot.MAX_ENTRIES - 9;

    private static final Set<String> ROOT_FIELDS = Set.of(
            "type", "schema_version", "inputs", "outputs", "processing_time", "energy_per_tick"
    );
    private static final Set<String> INPUT_FIELDS = Set.of("ingredient", "count");
    private static final Set<String> OUTPUT_FIELDS = Set.of("item", "count");

    private final ResourceLocation id;
    private final List<Input> inputs;
    private final List<ItemStack> outputs;
    private final List<List<String>> alternatives;
    private final ProcessDefinition processDefinition;
    private final String signature;

    public PrecisionAssemblerRecipe(
            ResourceLocation id,
            List<Input> inputs,
            List<ItemStack> outputs,
            int processingTicks,
            int energyPerTick,
            int encodedSizeBytes
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        if (this.inputs.size() < MIN_INPUTS || this.inputs.size() > MAX_INPUTS) {
            throw new IllegalArgumentException("precision input count must be 2..5");
        }
        this.outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs")).stream()
                .map(ItemStack::copy)
                .toList();
        if (this.outputs.size() < MIN_OUTPUTS || this.outputs.size() > MAX_OUTPUTS) {
            throw new IllegalArgumentException("precision output count must be 1..2");
        }

        List<List<String>> resolved = new ArrayList<>(this.inputs.size());
        List<ProcessInput> processInputs = new ArrayList<>(this.inputs.size());
        int totalAlternatives = 0;
        for (int index = 0; index < this.inputs.size(); index++) {
            Input input = this.inputs.get(index);
            List<String> choices = BoundedItemIngredientCodec.resolveAlternatives(input.ingredient());
            totalAlternatives = Math.addExact(totalAlternatives, choices.size());
            if (totalAlternatives > MAX_TOTAL_ALTERNATIVES) {
                throw new IllegalArgumentException("precision recipe exceeds the 64-entry journal snapshot");
            }
            for (ItemStack variant : input.ingredient().getItems()) {
                if (input.count() > variant.getMaxStackSize()) {
                    throw new IllegalArgumentException("input count exceeds an ingredient stack limit");
                }
            }
            resolved.add(choices);
            processInputs.add(new ProcessInput(
                    ProcessResourceKind.ITEM,
                    PrecisionAssemblerChannels.input(index),
                    choices,
                    input.count()
            ));
        }
        this.alternatives = List.copyOf(resolved);

        List<ProcessOutput> processOutputs = new ArrayList<>(this.outputs.size());
        for (int index = 0; index < this.outputs.size(); index++) {
            ItemStack output = this.outputs.get(index);
            if (output.isEmpty() || output.hasTag() || output.getCount() < 1
                    || output.getCount() > Math.min(MAX_ITEM_COUNT, output.getMaxStackSize())) {
                throw new IllegalArgumentException("precision result must be a bounded untagged item");
            }
            processOutputs.add(new ProcessOutput(
                    new ProcessResourceKey(
                            ProcessResourceKind.ITEM,
                            PrecisionAssemblerChannels.output(index),
                            requireRegisteredId(output.getItem(), "result").toString()
                    ),
                    output.getCount()
            ));
        }
        this.processDefinition = new ProcessDefinition(
                id.toString(),
                ProcessDefinition.SCHEMA_VERSION,
                encodedSizeBytes,
                processingTicks,
                energyPerTick,
                processInputs,
                processOutputs
        );
        this.signature = sha256(canonicalPayload());
    }

    @Override
    public boolean matches(SimpleContainer container, Level level) {
        if (container.getContainerSize() != MAX_INPUTS) {
            return false;
        }
        for (int index = 0; index < MAX_INPUTS; index++) {
            ItemStack stack = container.getItem(index);
            if (index < inputs.size()) {
                Input input = inputs.get(index);
                if (stack.hasTag() || !input.ingredient().test(stack) || stack.getCount() < input.count()) {
                    return false;
                }
            } else if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(SimpleContainer container, RegistryAccess registryAccess) {
        return outputs.get(0).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return (long) width * height >= MAX_INPUTS;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return outputs.get(0).copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        inputs.forEach(input -> ingredients.add(input.ingredient()));
        return ingredients;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.PRECISION_ASSEMBLING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.PRECISION_ASSEMBLING_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public List<Input> inputs() {
        return inputs;
    }

    public List<ItemStack> outputs() {
        return outputs.stream().map(ItemStack::copy).toList();
    }

    public List<List<String>> ingredientAlternatives() {
        return alternatives;
    }

    public ProcessDefinition processDefinition() {
        return processDefinition;
    }

    public String signature() {
        return signature;
    }

    private String canonicalPayload() {
        StringBuilder canonical = new StringBuilder()
                .append(ProcessDefinition.SCHEMA_VERSION).append('|')
                .append(id).append('|').append(inputs.size());
        for (int index = 0; index < inputs.size(); index++) {
            canonical.append('|').append(PrecisionAssemblerChannels.input(index))
                    .append('|').append(String.join(",", alternatives.get(index)))
                    .append('|').append(inputs.get(index).count());
        }
        canonical.append('|').append(outputs.size());
        for (int index = 0; index < outputs.size(); index++) {
            canonical.append('|').append(PrecisionAssemblerChannels.output(index))
                    .append('|').append(requireRegisteredId(outputs.get(index).getItem(), "result"))
                    .append('|').append(outputs.get(index).getCount());
        }
        return canonical.append('|').append(processDefinition.durationTicks())
                .append('|').append(processDefinition.energyPerTick()).toString();
    }

    private static ResourceLocation requireRegisteredId(Item item, String field) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null || id.toString().length() > ProcessResourceKey.MAX_RESOURCE_ID_CHARS
                || item.getDefaultInstance().isEmpty()) {
            throw new IllegalArgumentException(field + " has an invalid item id");
        }
        return id;
    }

    private static Item requireItem(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (rawId.length() > ProcessResourceKey.MAX_RESOURCE_ID_CHARS || item == null
                || item.getDefaultInstance().isEmpty()) {
            throw new IllegalArgumentException("result item is unknown or empty");
        }
        return item;
    }

    private static int requireInt(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        try {
            return new BigDecimal(value.getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be a 32-bit integer", exception);
        }
    }

    private static JsonObject exactObject(JsonElement raw, Set<String> fields, String field) {
        if (raw == null || !raw.isJsonObject() || !fields.equals(raw.getAsJsonObject().keySet())) {
            throw new IllegalArgumentException(field + " has missing or unexpected fields");
        }
        return raw.getAsJsonObject();
    }

    private static JsonArray boundedArray(JsonObject root, String field, int minimum, int maximum) {
        JsonElement raw = root.get(field);
        if (raw == null || !raw.isJsonArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        JsonArray array = raw.getAsJsonArray();
        if (array.size() < minimum || array.size() > maximum) {
            throw new IllegalArgumentException(field + " has an unsupported entry count");
        }
        return array;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record Input(Ingredient ingredient, int count) {
        public Input {
            Objects.requireNonNull(ingredient, "ingredient");
            if (count < 1 || count > MAX_ITEM_COUNT) {
                throw new IllegalArgumentException("input count is outside the bounded range");
            }
        }
    }

    public static final class Serializer implements RecipeSerializer<PrecisionAssemblerRecipe> {
        @Override
        public PrecisionAssemblerRecipe fromJson(ResourceLocation id, JsonObject json) {
            try {
                int bytes = json.toString().getBytes(StandardCharsets.UTF_8).length;
                if (bytes < 1 || bytes > ProcessDefinition.MAX_DEFINITION_BYTES) {
                    throw new IllegalArgumentException("recipe JSON exceeds the 64 KiB limit");
                }
                if (!ROOT_FIELDS.equals(json.keySet())
                        || !PrecisionAssemblerIds.RECIPE.toString().equals(json.get("type").getAsString())
                        || requireInt(json, "schema_version") != ProcessDefinition.SCHEMA_VERSION) {
                    throw new IllegalArgumentException("unsupported precision recipe schema or fields");
                }
                JsonArray inputArray = boundedArray(json, "inputs", MIN_INPUTS, MAX_INPUTS);
                JsonArray outputArray = boundedArray(json, "outputs", MIN_OUTPUTS, MAX_OUTPUTS);
                List<Input> inputs = new ArrayList<>(inputArray.size());
                for (JsonElement element : inputArray) {
                    JsonObject input = exactObject(element, INPUT_FIELDS, "input");
                    inputs.add(new Input(
                            BoundedItemIngredientCodec.decode(input.get("ingredient")),
                            requireInt(input, "count")
                    ));
                }
                List<ItemStack> outputs = new ArrayList<>(outputArray.size());
                for (JsonElement element : outputArray) {
                    JsonObject output = exactObject(element, OUTPUT_FIELDS, "output");
                    outputs.add(new ItemStack(
                            requireItem(output.get("item").getAsString()),
                            requireInt(output, "count")
                    ));
                }
                return new PrecisionAssemblerRecipe(
                        id, inputs, outputs,
                        requireInt(json, "processing_time"),
                        requireInt(json, "energy_per_tick"),
                        bytes
                );
            } catch (RuntimeException exception) {
                throw new JsonSyntaxException("Invalid Precision Assembler recipe " + id, exception);
            }
        }

        @Override
        public PrecisionAssemblerRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            try {
                if (buffer.readVarInt() != ProcessDefinition.SCHEMA_VERSION) {
                    throw new IllegalArgumentException("unsupported precision recipe schema");
                }
                int inputCount = buffer.readVarInt();
                if (inputCount < MIN_INPUTS || inputCount > MAX_INPUTS) {
                    throw new IllegalArgumentException("network input count is outside 2..5");
                }
                List<Input> inputs = new ArrayList<>(inputCount);
                for (int index = 0; index < inputCount; index++) {
                    String ingredientJson = buffer.readUtf(BoundedItemIngredientCodec.MAX_INGREDIENT_JSON_CHARS);
                    inputs.add(new Input(
                            BoundedItemIngredientCodec.decode(JsonParser.parseString(ingredientJson)),
                            buffer.readVarInt()
                    ));
                }
                int outputCount = buffer.readVarInt();
                if (outputCount < MIN_OUTPUTS || outputCount > MAX_OUTPUTS) {
                    throw new IllegalArgumentException("network output count is outside 1..2");
                }
                List<ItemStack> outputs = new ArrayList<>(outputCount);
                for (int index = 0; index < outputCount; index++) {
                    outputs.add(new ItemStack(
                            requireItem(buffer.readUtf(ProcessResourceKey.MAX_RESOURCE_ID_CHARS)),
                            buffer.readVarInt()
                    ));
                }
                int duration = buffer.readVarInt();
                int energy = buffer.readVarInt();
                return new PrecisionAssemblerRecipe(
                        id, inputs, outputs, duration, energy,
                        canonicalNetworkSize(id, inputs, outputs, duration, energy)
                );
            } catch (RuntimeException exception) {
                throw new JsonParseException("Invalid network Precision Assembler recipe " + id, exception);
            }
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, PrecisionAssemblerRecipe recipe) {
            buffer.writeVarInt(ProcessDefinition.SCHEMA_VERSION);
            buffer.writeVarInt(recipe.inputs.size());
            for (Input input : recipe.inputs) {
                String json = input.ingredient().toJson().toString();
                BoundedItemIngredientCodec.validateOnly(JsonParser.parseString(json));
                buffer.writeUtf(json, BoundedItemIngredientCodec.MAX_INGREDIENT_JSON_CHARS);
                buffer.writeVarInt(input.count());
            }
            buffer.writeVarInt(recipe.outputs.size());
            for (ItemStack output : recipe.outputs) {
                buffer.writeUtf(requireRegisteredId(output.getItem(), "result").toString(),
                        ProcessResourceKey.MAX_RESOURCE_ID_CHARS);
                buffer.writeVarInt(output.getCount());
            }
            buffer.writeVarInt(recipe.processDefinition.durationTicks());
            buffer.writeVarInt(recipe.processDefinition.energyPerTick());
        }

        private static int canonicalNetworkSize(
                ResourceLocation id,
                List<Input> inputs,
                List<ItemStack> outputs,
                int duration,
                int energy
        ) {
            FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
            try {
                encoded.writeUtf(id.toString(), ProcessResourceKey.MAX_RESOURCE_ID_CHARS);
                encoded.writeVarInt(inputs.size());
                for (Input input : inputs) {
                    encoded.writeUtf(input.ingredient().toJson().toString(),
                            BoundedItemIngredientCodec.MAX_INGREDIENT_JSON_CHARS);
                    encoded.writeVarInt(input.count());
                }
                encoded.writeVarInt(outputs.size());
                for (ItemStack output : outputs) {
                    encoded.writeUtf(requireRegisteredId(output.getItem(), "result").toString(),
                            ProcessResourceKey.MAX_RESOURCE_ID_CHARS);
                    encoded.writeVarInt(output.getCount());
                }
                encoded.writeVarInt(duration);
                encoded.writeVarInt(energy);
                int bytes = encoded.readableBytes();
                if (bytes < 1 || bytes > ProcessDefinition.MAX_DEFINITION_BYTES) {
                    throw new IllegalArgumentException("network recipe exceeds the 64 KiB limit");
                }
                return bytes;
            } finally {
                encoded.release();
            }
        }
    }
}
