package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * C15aR1-M2 (ADR-061 section 2.2, revision 7): kernel machine recipes name items. Minecraft parses recipes and sends
 * them to joining clients before tags are bound, and kernel recipes resolve their ingredient when built, so they
 * reject tag entries until the C16a machine family resolves tags after binding; the small plate press keeps tags.
 */
class BoundedItemIngredientCodecTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void itemsOnlyAcceptsItemsAndRejectsAnyTagEntry() {
        Ingredient one = BoundedItemIngredientCodec.decodeItemsOnly(
                JsonParser.parseString("{\"item\": \"minecraft:iron_ingot\"}"));
        assertEquals(Items.IRON_INGOT, one.getItems()[0].getItem());
        assertDoesNotThrow(() -> BoundedItemIngredientCodec.requireItemsOnly(JsonParser.parseString(
                "[{\"item\": \"minecraft:iron_ingot\"}, {\"item\": \"minecraft:gold_ingot\"}]")));
        assertThrows(IllegalArgumentException.class, () -> BoundedItemIngredientCodec.decodeItemsOnly(
                JsonParser.parseString("{\"tag\": \"forge:ingots/iron\"}")));
        assertThrows(IllegalArgumentException.class, () -> BoundedItemIngredientCodec.requireItemsOnly(
                JsonParser.parseString("[{\"item\": \"minecraft:iron_ingot\"}, {\"tag\": \"forge:ingots/iron\"}]")));
        // The general decoder (used by the small plate press) still accepts tags.
        assertDoesNotThrow(() -> BoundedItemIngredientCodec.decode(
                JsonParser.parseString("{\"tag\": \"forge:ingots/iron\"}")));
    }

    @Test
    void theElectrolyzerRejectsATagIngredientBeforeAnythingElse() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> new ElectrolyzerRecipe.Serializer()
                .fromJson(new ResourceLocation("advancedrocketrycommunity", "probe"), JsonParser.parseString("""
                        {
                          "type": "advancedrocketrycommunity:electrolyzing",
                          "schema_version": 1,
                          "ingredient": {"tag": "forge:ingots/iron"},
                          "input_count": 1,
                          "fluid": {"fluid": "minecraft:water", "amount": 100},
                          "hydrogen_result": {"item": "minecraft:stone"},
                          "oxygen_result": {"item": "minecraft:stone"},
                          "processing_time": 100,
                          "energy_per_tick": 10
                        }
                        """).getAsJsonObject()));
        assertEquals(true, String.valueOf(exception.getMessage()).contains("tag ingredients are not supported"),
                exception.getMessage());
    }
}
