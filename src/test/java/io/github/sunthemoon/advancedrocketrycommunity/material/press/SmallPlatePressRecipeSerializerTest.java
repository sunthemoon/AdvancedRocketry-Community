package io.github.sunthemoon.advancedrocketrycommunity.material.press;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.BoundedItemIngredientCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * C15aR1-M3: the small plate press recipe serializer is the slice's data-pack and network entry point (ADR-063
 * section 3, bounded fields). Every bound is exercised from JSON and from the network.
 */
class SmallPlatePressRecipeSerializerTest {
    private static final ResourceLocation ID = new ResourceLocation("advancedrocketrycommunity", "press_test");
    private final SmallPlatePressRecipe.Serializer serializer = new SmallPlatePressRecipe.Serializer();

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void aValidRecipeDecodesItemsAndTags() {
        SmallPlatePressRecipe recipe = serializer.fromJson(ID, valid());
        assertTrue(recipe.ingredient().test(new ItemStack(Items.IRON_BLOCK)));
        assertEquals(Items.IRON_INGOT, recipe.result().getItem());
        assertEquals(4, recipe.result().getCount());
        JsonObject tagged = valid();
        tagged.add("ingredient", JsonParser.parseString("{\"tag\": \"forge:storage_blocks/iron\"}"));
        assertEquals(4, serializer.fromJson(ID, tagged).result().getCount());
    }

    @Test
    void fieldsSchemaAndSizeAreBounded() {
        // An ingredient just inside its own bounds (at most 32 entries, at most 4,096 characters) in a recipe whose
        // whole JSON exceeds 4,096 bytes: only the recipe's byte limit can refuse it.
        JsonArray longTags = new JsonArray();
        String path = "x".repeat(110);
        while (longTags.toString().length() < 3_990) {
            longTags.add(JsonParser.parseString("{\"tag\": \"forge:" + path + longTags.size() + "\"}"));
        }
        BoundedItemIngredientCodec.validateOnly(longTags);
        JsonObject oversized = valid();
        oversized.add("ingredient", longTags);
        oversized.getAsJsonObject("result").addProperty("item", "minecraft:iron_ingot");
        assertTrue(oversized.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 4_096);
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, oversized), "more than 4,096 bytes");

        JsonObject extra = valid();
        extra.addProperty("unexpected", true);
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, extra));
        JsonObject missing = valid();
        missing.remove("result");
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, missing));
        JsonObject resultExtra = valid();
        resultExtra.getAsJsonObject("result").addProperty("nbt", "{}");
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, resultExtra));
        JsonObject resultMissing = valid();
        resultMissing.getAsJsonObject("result").remove("count");
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, resultMissing));

        JsonObject schema = valid();
        schema.addProperty("schema_version", 2);
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, schema));
        JsonObject fractional = valid();
        fractional.addProperty("schema_version", 1.5);
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, fractional));
        JsonObject text = valid();
        text.addProperty("schema_version", "1");
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, text));
    }

    @Test
    void resultsAndIngredientsAreBounded() {
        for (int count : new int[] {0, -1, 65}) {
            JsonObject json = valid();
            json.getAsJsonObject("result").addProperty("count", count);
            assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, json), "count " + count);
        }
        JsonObject overStack = valid();
        overStack.getAsJsonObject("result").addProperty("item", "minecraft:ender_pearl");
        overStack.getAsJsonObject("result").addProperty("count", 17);
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, overStack), "above the max stack");
        JsonObject unknown = valid();
        unknown.getAsJsonObject("result").addProperty("item", "advancedrocketrycommunity:no_such_item");
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, unknown));
        JsonObject air = valid();
        air.getAsJsonObject("result").addProperty("item", "minecraft:air");
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, air));

        JsonArray entries = new JsonArray();
        for (int i = 0; i < 33; i++) {
            entries.add(JsonParser.parseString("{\"item\": \"minecraft:iron_block\"}"));
        }
        JsonObject tooMany = valid();
        tooMany.add("ingredient", entries);
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, tooMany), "more than 32 entries");
        JsonObject empty = valid();
        empty.add("ingredient", new JsonArray());
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, empty));
        JsonObject custom = valid();
        custom.add("ingredient", JsonParser.parseString("{\"type\": \"forge:nbt\", \"item\": \"minecraft:iron_block\"}"));
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, custom));
    }

    @Test
    void theNetworkRoundTripKeepsTheIngredientAndTheCount() {
        for (String ingredient : new String[] {"{\"item\": \"minecraft:iron_block\"}",
                "{\"tag\": \"forge:storage_blocks/iron\"}"}) {
            JsonObject json = valid();
            json.add("ingredient", JsonParser.parseString(ingredient));
            json.getAsJsonObject("result").addProperty("count", 7);
            SmallPlatePressRecipe original = serializer.fromJson(ID, json);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            serializer.toNetwork(buffer, original);
            SmallPlatePressRecipe decoded = serializer.fromNetwork(ID, buffer);
            assertEquals(original.ingredient().toJson(), decoded.ingredient().toJson());
            assertEquals(7, decoded.result().getCount());
            assertEquals(Items.IRON_INGOT, decoded.result().getItem());
            assertEquals(0, buffer.readableBytes(), "trailing bytes");
        }
    }

    @Test
    void malformedNetworkDataIsRejected() {
        FriendlyByteBuf full = new FriendlyByteBuf(Unpooled.buffer());
        serializer.toNetwork(full, serializer.fromJson(ID, valid()));
        byte[] bytes = new byte[full.readableBytes()];
        full.readBytes(bytes);
        FriendlyByteBuf truncated = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes, 0, bytes.length - 2));
        assertThrows(RuntimeException.class, () -> serializer.fromNetwork(ID, truncated));

        FriendlyByteBuf schema = new FriendlyByteBuf(Unpooled.buffer());
        schema.writeVarInt(SmallPlatePressRecipe.SCHEMA_VERSION + 1);
        assertThrows(RuntimeException.class, () -> serializer.fromNetwork(ID, schema));

        FriendlyByteBuf oversized = new FriendlyByteBuf(Unpooled.buffer());
        oversized.writeVarInt(SmallPlatePressRecipe.SCHEMA_VERSION);
        oversized.writeUtf("x".repeat(5_000), 8_192);
        assertThrows(RuntimeException.class, () -> serializer.fromNetwork(ID, oversized));

        FriendlyByteBuf count = new FriendlyByteBuf(Unpooled.buffer());
        count.writeVarInt(SmallPlatePressRecipe.SCHEMA_VERSION);
        count.writeUtf("{\"item\":\"minecraft:iron_block\"}");
        count.writeUtf("minecraft:iron_ingot");
        count.writeVarInt(65);
        assertThrows(RuntimeException.class, () -> serializer.fromNetwork(ID, count));
    }

    private static JsonObject valid() {
        return JsonParser.parseString("""
                {
                  "type": "advancedrocketrycommunity:small_plate_press",
                  "schema_version": 1,
                  "ingredient": {"item": "minecraft:iron_block"},
                  "result": {"item": "minecraft:iron_ingot", "count": 4}
                }
                """).getAsJsonObject();
    }
}
