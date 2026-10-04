package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeTagGeneration;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TagsUpdatedEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PrecisionTagBudgetTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void fiveIndividuallyLegalTagsRespectExistingAggregateBudgetWithoutMutatingInputs() {
        Map<TagKey<Item>, List<Holder<Item>>> saved = new HashMap<>();
        BuiltInRegistries.ITEM.getTags().forEach(pair -> saved.put(pair.getFirst(), pair.getSecond().stream().toList()));
        TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation("arce_test", "precision_budget"));
        List<Holder<Item>> variants = BuiltInRegistries.ITEM.stream()
                .filter(item -> !item.getDefaultInstance().isEmpty() && item.getMaxStackSize() == 64)
                .limit(32).map(Item::builtInRegistryHolder).map(holder -> (Holder<Item>) holder).toList();
        assertEquals(32, variants.size());
        try {
            BuiltInRegistries.ITEM.bindTags(Map.of(tag, variants)); reload();
            PrecisionAssemblerRecipe recipe = recipe();
            String signature = recipe.signature();
            assertFalse(assertDoesNotThrow(recipe::available), "5 * 32 exceeds the frozen 55-alternative aggregate");
            assertThrows(IllegalArgumentException.class, recipe::processDefinition);
            SimpleContainer inputs = new SimpleContainer(5);
            for (int slot = 0; slot < 5; slot++) { inputs.setItem(slot, new ItemStack(variants.get(0).value(), 2)); }
            var selected = assertDoesNotThrow(() -> PrecisionAssemblerRecipeResolver.select(List.of(recipe), inputs));
            assertTrue(selected.recipe().isEmpty()); assertFalse(recipe.matches(inputs, null));
            for (int slot = 0; slot < 5; slot++) { assertEquals(2, inputs.getItem(slot).getCount()); }
            assertEquals(signature, recipe.signature());
            // 5 * 11 == 55, reserving nine entries for foreign/output identities: the accepted edge.
            BuiltInRegistries.ITEM.bindTags(Map.of(tag, variants.subList(0, 11))); reload();
            assertTrue(recipe.available()); assertEquals(55, recipe.ingredientAlternatives().stream().mapToInt(List::size).sum());
            assertEquals(signature, recipe.signature()); assertTrue(recipe.matches(inputs, null));
        } finally { BuiltInRegistries.ITEM.bindTags(saved); reload(); }
    }

    private static PrecisionAssemblerRecipe recipe() {
        JsonObject json = JsonParser.parseString("""
                {"type":"advancedrocketrycommunity:precision_assembling","schema_version":1,
                 "inputs":[],"outputs":[{"item":"minecraft:iron_bars","count":1}],
                 "processing_time":20,"energy_per_tick":40}
                """).getAsJsonObject();
        JsonArray inputs = json.getAsJsonArray("inputs");
        for (int index = 0; index < 5; index++) {
            inputs.add(JsonParser.parseString("{\"ingredient\":{\"tag\":\"arce_test:precision_budget\"},\"count\":2}"));
        }
        return new PrecisionAssemblerRecipe.Serializer().fromJson(new ResourceLocation("arce_test", "precision_budget"), json);
    }

    private static void reload() {
        RecipeTagGeneration.tagsUpdated(new TagsUpdatedEvent(RegistryAccess.EMPTY, false, false));
    }
}
