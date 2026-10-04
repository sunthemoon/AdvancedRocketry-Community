package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.LegacyKernelRecipeProof;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.netty.buffer.Unpooled;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RollingMachineRecipeTest {
    private static final ResourceLocation ID = ResourceLocation.tryBuild(
            "advancedrocketrycommunity",
            "rolling_iron_bars"
    );

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void strictJsonBuildsTheBoundedProcessDefinitionAndStableSignature() {
        RollingMachineRecipe.Serializer serializer = new RollingMachineRecipe.Serializer();
        RollingMachineRecipe first = serializer.fromJson(ID, validJson());
        RollingMachineRecipe second = serializer.fromJson(ID, validJson());

        assertEquals(2, first.inputCount());
        assertEquals(Fluids.WATER, first.fluid());
        assertEquals(100, first.fluidAmount());
        assertEquals(Items.IRON_BARS, first.result().getItem());
        assertEquals(8, first.result().getCount());
        assertEquals(100, first.processDefinition().durationTicks());
        assertEquals(20, first.processDefinition().energyPerTick());
        assertEquals(2_000, first.processDefinition().totalEnergy());
        assertEquals(first.signature(), second.signature());
        assertTrue(first.signature().matches("[0-9a-f]{64}"));
        assertEquals(ProcessResourceKind.ITEM, first.processDefinition().inputs().get(0).kind());
        assertEquals(ProcessResourceKind.FLUID, first.processDefinition().inputs().get(1).kind());
    }

    @Test
    void boundedNetworkRoundTripPreservesSemanticSignature() {
        RollingMachineRecipe.Serializer serializer = new RollingMachineRecipe.Serializer();
        RollingMachineRecipe original = serializer.fromJson(ID, validJson());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        serializer.toNetwork(buffer, original);
        RollingMachineRecipe decoded = serializer.fromNetwork(ID, buffer);

        assertEquals(original.signature(), decoded.signature());
        assertEquals(original.ingredientAlternatives(), decoded.ingredientAlternatives());
        assertEquals(original.processDefinition().durationTicks(), decoded.processDefinition().durationTicks());
        assertEquals(original.processDefinition().energyPerTick(), decoded.processDefinition().energyPerTick());
        assertEquals(original.result().getItem(), decoded.result().getItem());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void unknownFieldsInvalidFluidAndUnboundedCountsAreRejected() {
        RollingMachineRecipe.Serializer serializer = new RollingMachineRecipe.Serializer();
        JsonObject unknown = validJson();
        unknown.addProperty("unexpected", true);
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, unknown));

        JsonObject lava = validJson();
        lava.getAsJsonObject("fluid").addProperty("fluid", "minecraft:lava");
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, lava));

        JsonObject count = validJson();
        count.addProperty("input_count", 65);
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, count));

        JsonObject fractional = validJson();
        fractional.addProperty("processing_time", 100.5);
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, fractional));

        JsonObject customIngredient = validJson();
        customIngredient.getAsJsonObject("ingredient").addProperty("nbt", "{}");
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, customIngredient));
    }

    /** C16a-01 supersedes the temporary item-only restriction; parsing and sync do not expand tags. */
    @Test
    void tagIngredientsArePreservedFromJsonAndFromTheNetworkWithoutBinding() {
        RollingMachineRecipe.Serializer serializer = new RollingMachineRecipe.Serializer();
        JsonObject tagged = validJson();
        tagged.add("ingredient", JsonParser.parseString("{\"tag\": \"forge:ingots/iron\"}"));
        RollingMachineRecipe parsed = serializer.fromJson(ID, tagged);
        assertTrue(parsed.hasTagIngredients());
        assertEquals(tagged.get("ingredient"), parsed.jsonPayload().get("ingredient"));

        JsonObject mixed = validJson();
        mixed.add("ingredient", JsonParser.parseString(
                "[{\"item\": \"minecraft:iron_ingot\"}, {\"tag\": \"forge:ingots/iron\"}]"));
        assertTrue(serializer.fromJson(ID, mixed).hasTagIngredients());

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        serializer.toNetwork(buffer, serializer.fromJson(ID, validJson()));
        byte[] written = new byte[buffer.readableBytes()];
        buffer.readBytes(written);
        String wire = new String(written, java.nio.charset.StandardCharsets.UTF_8);
        String item = "{\"item\":\"minecraft:iron_ingot\"}";
        String tag = "{\"tag\":\"forge:ingots/iron_abc\"}";
        assertTrue(wire.contains(item) && item.length() == tag.length(), "the probe swaps the ingredient in place");
        FriendlyByteBuf swapped = new FriendlyByteBuf(Unpooled.wrappedBuffer(
                wire.replace(item, tag).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        RollingMachineRecipe decoded = serializer.fromNetwork(ID, swapped);
        assertTrue(decoded.hasTagIngredients());
        assertEquals(JsonParser.parseString(tag), decoded.jsonPayload().get("ingredient"));
        swapped.release();
        buffer.release();
    }

    @Test
    void recipeRemainsOutsideTheVanillaRecipeBookContract() {
        RollingMachineRecipe recipe = new RollingMachineRecipe.Serializer().fromJson(ID, validJson());
        assertTrue(recipe.isSpecial());
    }

    @Test
    void processedCurrentTagRecipeAndHistoricalItemRecipeKeepDistinctSignatures() throws Exception {
        JsonObject currentJson;
        try (InputStream stream = getClass().getResourceAsStream(
                "/data/advancedrocketrycommunity/recipes/rolling_iron_bars.json")) {
            assertNotNull(stream, "the processed current Rolling recipe must be available");
            currentJson = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
        assertEquals(JsonParser.parseString("{\"tag\":\"forge:ingots/iron\"}"), currentJson.get("ingredient"));
        JsonObject historicalJson = LegacyKernelRecipeProof.payload(ID, "advancedrocketrycommunity:rolling");
        assertNotNull(historicalJson);
        RollingMachineRecipe.Serializer serializer = new RollingMachineRecipe.Serializer();
        RollingMachineRecipe current = serializer.fromJson(ID, currentJson);
        RollingMachineRecipe historical = serializer.fromJson(ID, historicalJson);
        assertEquals(current.getId(), historical.getId());
        assertTrue(current.hasTagIngredients());
        assertFalse(historical.hasTagIngredients());
        assertNotEquals(current.signature(), historical.signature(), "authored tag JSON is not historical item JSON");
        JsonObject currentFields = current.jsonPayload();
        JsonObject historicalFields = historical.jsonPayload();
        currentFields.remove("ingredient");
        historicalFields.remove("ingredient");
        assertEquals(historicalFields, currentFields, "the current selector changes no other recipe field");
    }

    private static JsonObject validJson() {
        return JsonParser.parseString("""
                {
                  "type": "advancedrocketrycommunity:rolling",
                  "schema_version": 1,
                  "ingredient": {"item": "minecraft:iron_ingot"},
                  "input_count": 2,
                  "fluid": {"fluid": "minecraft:water", "amount": 100},
                  "result": {"item": "minecraft:iron_bars", "count": 8},
                  "processing_time": 100,
                  "energy_per_tick": 20
                }
                """).getAsJsonObject();
    }
}
