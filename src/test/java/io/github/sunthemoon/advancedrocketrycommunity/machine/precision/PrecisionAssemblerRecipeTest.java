package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PrecisionAssemblerRecipeTest {
    private static final ResourceLocation ID = ResourceLocation.tryBuild(
            "advancedrocketrycommunity", "precision_test"
    );
    private final PrecisionAssemblerRecipe.Serializer serializer = new PrecisionAssemblerRecipe.Serializer();

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void twoInputTwoOutputRecipeBuildsStableDistinctChannels() {
        PrecisionAssemblerRecipe recipe = serializer.fromJson(ID, twoInputJson());
        ProcessDefinition definition = recipe.processDefinition();

        assertEquals(2, definition.inputs().size());
        assertEquals(2, definition.outputs().size());
        assertEquals("item_input_0", definition.inputs().get(0).channel());
        assertEquals("item_input_1", definition.inputs().get(1).channel());
        assertEquals("item_output_0", definition.outputs().get(0).key().channel());
        assertEquals("item_output_1", definition.outputs().get(1).key().channel());
        assertEquals(ProcessResourceKind.ITEM, definition.inputs().get(0).kind());
        assertEquals(2_000, definition.totalEnergy());
        assertEquals(2, definition.outputs().get(0).amount());
        assertTrue(recipe.signature().matches("[0-9a-f]{64}"));
        assertEquals(recipe.signature(), serializer.fromJson(ID, twoInputJson()).signature());
        assertTrue(recipe.isSpecial());

        List<ItemStack> returned = recipe.outputs();
        returned.get(0).shrink(1);
        assertEquals(2, recipe.outputs().get(0).getCount());
    }

    @Test
    void fiveInputDefinitionAndExactSlotSelectionDoNotDependOnCandidateOrder() {
        PrecisionAssemblerRecipe shortRecipe = serializer.fromJson(ID, twoInputJson());
        PrecisionAssemblerRecipe longRecipe = serializer.fromJson(
                ResourceLocation.tryBuild("advancedrocketrycommunity", "precision_five"),
                fiveInputJson()
        );
        SimpleContainer two = slots(
                new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.REDSTONE),
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
        );
        SimpleContainer five = slots(
                new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.REDSTONE),
                new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.DIAMOND),
                new ItemStack(Items.STICK)
        );

        assertTrue(shortRecipe.matches(two, null));
        assertFalse(shortRecipe.matches(five, null));
        assertTrue(longRecipe.matches(five, null));
        assertFalse(longRecipe.matches(two, null));
        assertEquals(shortRecipe, PrecisionAssemblerRecipeResolver.select(
                List.of(longRecipe, shortRecipe), two
        ).recipe().orElseThrow());
        assertEquals(longRecipe, PrecisionAssemblerRecipeResolver.select(
                List.of(shortRecipe, longRecipe), five
        ).recipe().orElseThrow());
        assertEquals(5, longRecipe.processDefinition().inputs().size());
        assertEquals("item_input_4", longRecipe.processDefinition().inputs().get(4).channel());
    }

    /** C16a-01 preserves tag JSON without reading unsynchronized client tags. */
    @Test
    void aTagIngredientFromTheNetworkIsPreserved() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            serializer.toNetwork(buffer, serializer.fromJson(ID, twoInputJson()));
            byte[] written = new byte[buffer.readableBytes()];
            buffer.readBytes(written);
            String wire = new String(written, java.nio.charset.StandardCharsets.ISO_8859_1);
            String item = "{\"item\":\"minecraft:iron_ingot\"}";
            String tag = "{\"tag\":\"forge:ingots/iron_abc\"}";
            assertTrue(wire.contains(item) && item.length() == tag.length(), "the probe swaps the ingredient in place");
            FriendlyByteBuf swapped = new FriendlyByteBuf(Unpooled.wrappedBuffer(
                    wire.replace(item, tag).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)));
            try {
                PrecisionAssemblerRecipe decoded = serializer.fromNetwork(ID, swapped);
                assertTrue(decoded.hasTagIngredients());
                assertEquals(JsonParser.parseString(tag), decoded.inputs().get(0).json());
            } finally { swapped.release(); }
        } finally {
            buffer.release();
        }
    }

    @Test
    void networkRoundTripRetainsDefinitionAndSemanticSignature() {
        PrecisionAssemblerRecipe original = serializer.fromJson(ID, fiveInputJson());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            serializer.toNetwork(buffer, original);
            PrecisionAssemblerRecipe decoded = serializer.fromNetwork(ID, buffer);

            assertEquals(original.signature(), decoded.signature());
            assertEquals(original.ingredientAlternatives(), decoded.ingredientAlternatives());
            assertEquals(original.processDefinition().inputs(), decoded.processDefinition().inputs());
            assertEquals(original.processDefinition().outputs(), decoded.processDefinition().outputs());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void ingredientArrayOrderAffectsJsonSignatureAndTaggedInputIsNotConsumed() {
        JsonObject firstJson = twoInputJson();
        JsonObject secondJson = twoInputJson();
        firstJson.getAsJsonArray("inputs").get(0).getAsJsonObject().add("ingredient",
                JsonParser.parseString("[{\"item\":\"minecraft:iron_ingot\"},"
                        + "{\"item\":\"minecraft:gold_ingot\"}]"));
        secondJson.getAsJsonArray("inputs").get(0).getAsJsonObject().add("ingredient",
                JsonParser.parseString("[{\"item\":\"minecraft:gold_ingot\"},"
                        + "{\"item\":\"minecraft:iron_ingot\"}]"));
        PrecisionAssemblerRecipe first = serializer.fromJson(ID, firstJson);
        PrecisionAssemblerRecipe second = serializer.fromJson(ID, secondJson);
        assertNotEquals(first.signature(), second.signature());
        assertEquals(first.legacySignature(), second.legacySignature());

        ItemStack tagged = new ItemStack(Items.IRON_INGOT, 2);
        tagged.getOrCreateTag().putString("owner", "test");
        assertFalse(first.matches(slots(tagged, new ItemStack(Items.REDSTONE),
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY), null));
    }

    @Test
    void invalidShapeValuesAndOversizedPayloadFailBeforeUse() {
        JsonObject unknown = twoInputJson();
        unknown.addProperty("extra", true);
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, unknown));

        JsonObject oneInput = twoInputJson();
        oneInput.getAsJsonArray("inputs").remove(1);
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, oneInput));

        JsonObject sixInputs = fiveInputJson();
        sixInputs.getAsJsonArray("inputs").add(sixInputs.getAsJsonArray("inputs").get(0));
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, sixInputs));

        JsonObject unknownIngredientField = twoInputJson();
        unknownIngredientField.getAsJsonArray("inputs").get(0).getAsJsonObject()
                .getAsJsonObject("ingredient").addProperty("nbt", "{}");
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, unknownIngredientField));

        JsonObject fractional = twoInputJson();
        fractional.getAsJsonArray("outputs").get(0).getAsJsonObject().addProperty("count", 1.5);
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, fractional));

        JsonObject stackOverflow = twoInputJson();
        stackOverflow.getAsJsonArray("inputs").get(0).getAsJsonObject().addProperty("count", 65);
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, stackOverflow));

        JsonObject unstackableResult = twoInputJson();
        unstackableResult.getAsJsonArray("outputs").get(0).getAsJsonObject()
                .addProperty("item", "minecraft:diamond_sword");
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, unstackableResult));

        JsonObject oversized = twoInputJson();
        oversized.addProperty("padding", "x".repeat(ProcessDefinition.MAX_DEFINITION_BYTES));
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, oversized));

        JsonObject invalidEnergy = twoInputJson();
        invalidEnergy.addProperty("energy_per_tick", ProcessDefinition.MAX_ENERGY_PER_TICK + 1);
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, invalidEnergy));

        // Tag binding is deferred; malformed ingredient shapes remain rejected above.
        JsonObject tagged = twoInputJson();
        tagged.getAsJsonArray("inputs").get(0).getAsJsonObject()
                .add("ingredient", JsonParser.parseString("{\"tag\": \"forge:ingots/iron\"}"));
        assertTrue(serializer.fromJson(ID, tagged).hasTagIngredients());
    }

    @Test
    void ambiguousAndOverBudgetCandidateSetsFailClosed() {
        PrecisionAssemblerRecipe first = serializer.fromJson(ID, twoInputJson());
        PrecisionAssemblerRecipe duplicate = serializer.fromJson(
                ResourceLocation.tryBuild("advancedrocketrycommunity", "precision_duplicate"),
                twoInputJson()
        );
        SimpleContainer input = slots(
                new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.REDSTONE),
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
        );
        assertEquals(PrecisionAssemblerRecipeResolver.SelectionProblem.AMBIGUOUS,
                PrecisionAssemblerRecipeResolver.select(List.of(first, duplicate), input).problem());
        assertTrue(PrecisionAssemblerRecipeResolver.select(List.of(first, duplicate), input)
                .recipe().isEmpty());
        assertEquals(PrecisionAssemblerRecipeResolver.SelectionProblem.TOO_MANY,
                PrecisionAssemblerRecipeResolver.select(
                        Collections.nCopies(PrecisionAssemblerRecipeResolver.MAX_RECIPES + 1, first), input
                ).problem());

        FriendlyByteBuf malformed = new FriendlyByteBuf(Unpooled.buffer());
        try {
            malformed.writeVarInt(ProcessDefinition.SCHEMA_VERSION);
            malformed.writeVarInt(6);
            assertThrows(RuntimeException.class, () -> serializer.fromNetwork(ID, malformed));
        } finally {
            malformed.release();
        }
    }

    @Test
    void totalIngredientAlternativesFitTheJournalSnapshot() {
        List<Item> variants = BuiltInRegistries.ITEM.stream()
                .filter(item -> !item.getDefaultInstance().isEmpty()
                        && item.getDefaultInstance().getMaxStackSize() >= 2)
                .limit(56).toList();
        assertEquals(56, variants.size());
        JsonObject json = twoInputJson();
        JsonArray first = alternatives(variants.subList(0, 32));
        JsonArray second = alternatives(variants.subList(32, 55));
        json.getAsJsonArray("inputs").get(0).getAsJsonObject().add("ingredient", first);
        json.getAsJsonArray("inputs").get(1).getAsJsonObject().add("ingredient", second);
        PrecisionAssemblerRecipe accepted = serializer.fromJson(ID, json);
        assertEquals(ProcessResourceSnapshot.MAX_ENTRIES - 9,
                accepted.ingredientAlternatives().stream().mapToInt(List::size).sum());

        JsonObject extra = new JsonObject();
        extra.addProperty("item", BuiltInRegistries.ITEM.getKey(variants.get(55)).toString());
        second.add(extra);
        PrecisionAssemblerRecipe overBudget = serializer.fromJson(ID, json);
        assertFalse(overBudget.available());
        assertThrows(RuntimeException.class, overBudget::processDefinition);
    }

    private static JsonArray alternatives(List<Item> items) {
        JsonArray variants = new JsonArray();
        for (Item item : items) {
            JsonObject value = new JsonObject();
            value.addProperty("item", BuiltInRegistries.ITEM.getKey(item).toString());
            variants.add(value);
        }
        return variants;
    }

    private static SimpleContainer slots(ItemStack... values) {
        return new SimpleContainer(values);
    }

    private static JsonObject twoInputJson() {
        return JsonParser.parseString("""
                {
                  "type": "advancedrocketrycommunity:precision_assembling",
                  "schema_version": 1,
                  "inputs": [
                    {"ingredient": {"item": "minecraft:iron_ingot"}, "count": 2},
                    {"ingredient": {"item": "minecraft:redstone"}, "count": 1}
                  ],
                  "outputs": [
                    {"item": "minecraft:iron_bars", "count": 2},
                    {"item": "minecraft:lever", "count": 1}
                  ],
                  "processing_time": 100,
                  "energy_per_tick": 20
                }
                """).getAsJsonObject();
    }

    private static JsonObject fiveInputJson() {
        JsonObject json = twoInputJson();
        JsonArray inputs = json.getAsJsonArray("inputs");
        for (String item : List.of("minecraft:gold_ingot", "minecraft:diamond", "minecraft:stick")) {
            JsonObject ingredient = new JsonObject();
            ingredient.addProperty("item", item);
            JsonObject input = new JsonObject();
            input.add("ingredient", ingredient);
            input.addProperty("count", 1);
            inputs.add(input);
        }
        return json;
    }
}
