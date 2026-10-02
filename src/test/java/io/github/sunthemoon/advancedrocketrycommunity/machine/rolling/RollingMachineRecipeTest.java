package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.netty.buffer.Unpooled;
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

    /** C15aR1-M2: kernel recipes name items until tags resolve after binding (ADR-061 section 2.2, revision 7). */
    @Test
    void tagIngredientsAreRejectedFromJsonAndFromTheNetwork() {
        RollingMachineRecipe.Serializer serializer = new RollingMachineRecipe.Serializer();
        JsonObject tagged = validJson();
        tagged.add("ingredient", JsonParser.parseString("{\"tag\": \"forge:ingots/iron\"}"));
        RuntimeException json = assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, tagged));
        assertTrue(String.valueOf(json.getMessage()).contains("tag ingredients are not supported"), json.getMessage());

        JsonObject mixed = validJson();
        mixed.add("ingredient", JsonParser.parseString(
                "[{\"item\": \"minecraft:iron_ingot\"}, {\"tag\": \"forge:ingots/iron\"}]"));
        assertThrows(RuntimeException.class, () -> serializer.fromJson(ID, mixed));

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
        assertThrows(RuntimeException.class, () -> serializer.fromNetwork(ID, swapped));
    }

    @Test
    void recipeRemainsOutsideTheVanillaRecipeBookContract() {
        RollingMachineRecipe recipe = new RollingMachineRecipe.Serializer().fromJson(ID, validJson());
        assertTrue(recipe.isSpecial());
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
