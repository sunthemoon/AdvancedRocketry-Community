package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Bounded subset checks; the explicit item-only decoder remains available for legacy proof. */
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
    void anUnboundTagDoesNotMaskInvalidElectrolyzerOutputCounts() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> new ElectrolyzerRecipe.Serializer()
                .fromJson(new ResourceLocation("advancedrocketrycommunity", "probe"), JsonParser.parseString("""
                        {
                          "type": "advancedrocketrycommunity:electrolyzing",
                          "schema_version": 1,
                          "ingredient": {"tag": "forge:ingots/iron"},
                          "input_count": 1,
                          "fluid": {"fluid": "minecraft:water", "amount": 100},
                          "hydrogen_result": {"item": "minecraft:stone", "count": 0},
                          "oxygen_result": {"item": "minecraft:stone"},
                          "processing_time": 100,
                          "energy_per_tick": 10
                        }
                        """).getAsJsonObject()));
        assertEquals(true, String.valueOf(exception.getMessage()).contains("invalid item/count"),
                exception.getMessage());
    }

    /** A tag is decoded without binding, but output and packet bounds still apply. */
    @Test
    void anUnboundNetworkTagDoesNotMaskAnInvalidOutputCount() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUtf("{\"tag\":\"forge:ingots/iron\"}");
            buffer.writeUtf("minecraft:water");
            for (int value : new int[] {1, 1, 100, 100, 10}) {
                buffer.writeVarInt(value);
            }
            buffer.writeUtf("minecraft:stone"); buffer.writeVarInt(0);
            RuntimeException refused = assertThrows(RuntimeException.class, () -> new ElectrolyzerRecipe.Serializer()
                    .fromNetwork(new ResourceLocation("advancedrocketrycommunity", "probe"), buffer));
            Throwable cause = refused;
            while (cause.getCause() != null && !String.valueOf(cause.getMessage()).contains("hydrogen_result")) {
                cause = cause.getCause();
            }
            assertEquals(true, String.valueOf(cause.getMessage()).contains("Invalid hydrogen_result item/count"),
                    refused.toString());
        } finally {
            buffer.release();
        }
    }
}
