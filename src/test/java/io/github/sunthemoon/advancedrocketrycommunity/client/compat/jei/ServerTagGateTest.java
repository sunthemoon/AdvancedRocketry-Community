package io.github.sunthemoon.advancedrocketrycommunity.client.compat.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicRecipeCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicMachineRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.BoundedItemIngredientCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeTagGeneration;
import io.github.sunthemoon.advancedrocketrycommunity.material.press.SmallPlatePressRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.event.TagsUpdatedEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W188: a recipe that names a tag reaches JEI only once the client holds this connection's tags. */
class ServerTagGateTest {
    private static final Predicate<String> NAMES_TAG = recipe -> recipe.startsWith("tag");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void tagRecipesRegisteredBeforeTheTagsArriveReachJeiWhenTheyArrive() {
        ServerTagGate<Added> gate = new ServerTagGate<>();
        gate.connectionChanged();
        assertEquals(List.of("item"), gate.admit("lathe", List.of("item", "tag_a", "tag_b"), NAMES_TAG,
                recipe -> true, into("lathe")));
        assertEquals(2, gate.held("lathe"));

        Added added = new Added();
        assertEquals(List.of(), gate.release(added), "nothing is released before the tags arrive");
        assertTrue(added.recipes.isEmpty());

        gate.tagsArrived();
        assertEquals(List.of(new ServerTagGate.Released("lathe", 3)), gate.release(added));
        assertEquals(Map.of("lathe", List.of("tag_a", "tag_b")), added.recipes);
        assertEquals(0, gate.held("lathe"));
        assertEquals(List.of(), gate.release(added), "each held recipe is released once");
    }

    @Test
    void afterTheTagsArriveARegistrationHoldsNothing() {
        ServerTagGate<Added> gate = new ServerTagGate<>();
        gate.connectionChanged();
        gate.tagsArrived();
        assertEquals(List.of("item", "tag_a"), gate.admit("lathe", List.of("item", "tag_a"), NAMES_TAG,
                recipe -> true, into("lathe")));
        assertEquals(0, gate.held("lathe"));
        assertEquals(List.of(), gate.release(new Added()));
    }

    @Test
    void aNewConnectionDoesNotTrustTagsBoundBeforeIt() {
        ServerTagGate<Added> gate = new ServerTagGate<>();
        gate.connectionChanged();
        gate.tagsArrived();
        // A single-player world or an earlier server bound these tags, not the server joined next.
        gate.connectionChanged();
        assertEquals(List.of(), gate.admit("lathe", List.of("tag_a"), NAMES_TAG, recipe -> true, into("lathe")));
        assertEquals(1, gate.held("lathe"));
        assertEquals(List.of(), gate.release(new Added()));
    }

    @Test
    void leavingTheServerOrAStoppedJeiDropsHeldRecipes() {
        ServerTagGate<Added> gate = new ServerTagGate<>();
        gate.connectionChanged();
        gate.admit("lathe", List.of("tag_a"), NAMES_TAG, recipe -> true, into("lathe"));
        gate.runtimeStopped();
        assertEquals(0, gate.held("lathe"), "JEI's next start registers every recipe again");
        gate.tagsArrived();
        assertEquals(List.of(), gate.release(new Added()));

        gate.connectionChanged();
        gate.admit("lathe", List.of("tag_a"), NAMES_TAG, recipe -> true, into("lathe"));
        gate.connectionChanged();
        assertEquals(0, gate.held("lathe"));
    }

    @Test
    void aNewRegistrationReplacesTheCategoryItHeldBefore() {
        ServerTagGate<Added> gate = new ServerTagGate<>();
        gate.connectionChanged();
        gate.admit("lathe", List.of("tag_a", "tag_b"), NAMES_TAG, recipe -> true, into("lathe"));
        gate.admit("lathe", List.of("item", "tag_c"), NAMES_TAG, recipe -> true, into("lathe"));
        gate.admit("cutting", List.of("tag_d"), NAMES_TAG, recipe -> true, into("cutting"));
        gate.tagsArrived();
        Added added = new Added();
        assertEquals(List.of(new ServerTagGate.Released("lathe", 2), new ServerTagGate.Released("cutting", 1)),
                gate.release(added));
        assertEquals(Map.of("lathe", List.of("tag_c"), "cutting", List.of("tag_d")), added.recipes);
    }

    @Test
    void heldRecipesKeepTheCategoryFilterAndTheTotalCountsOnlyShownRecipes() {
        ServerTagGate<Added> gate = new ServerTagGate<>();
        gate.connectionChanged();
        Predicate<String> shown = recipe -> !recipe.endsWith("broken");
        assertEquals(List.of("item"), gate.admit("lathe", List.of("item", "item_broken", "tag_a", "tag_broken"),
                NAMES_TAG, shown, into("lathe")));
        gate.tagsArrived();
        Added added = new Added();
        assertEquals(List.of(new ServerTagGate.Released("lathe", 2)), gate.release(added));
        assertEquals(Map.of("lathe", List.of("tag_a")), added.recipes);

        gate.admit("arc_furnace", List.of("tag_broken"), NAMES_TAG, shown, into("arc_furnace"));
        assertEquals(0, gate.held("arc_furnace"), "tags have arrived, so the filter applies at once");
    }

    @Test
    void aCategoryWhoseHeldRecipesAllFailStillReportsItsTotal() {
        ServerTagGate<Added> gate = new ServerTagGate<>();
        gate.connectionChanged();
        gate.admit("lathe", List.of("item", "tag_broken"), NAMES_TAG, recipe -> !recipe.endsWith("broken"),
                into("lathe"));
        gate.tagsArrived();
        Added added = new Added();
        assertEquals(List.of(new ServerTagGate.Released("lathe", 1)), gate.release(added));
        assertTrue(added.recipes.isEmpty(), "JEI is not called with an empty list");
    }

    @Test
    void aClassicRecipeThatCannotResolveBeforeTheTagsIsHeldAndShownAfterThem() {
        Map<TagKey<Item>, List<Holder<Item>>> saved = savedTags();
        TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation("arce_test", "w188_ingots"));
        try {
            BuiltInRegistries.ITEM.bindTags(Map.of());
            reload();
            ClassicMachineRecipe tagged = lathe("{\"tag\":\"arce_test:w188_ingots\"}");
            ClassicMachineRecipe plain = lathe("{\"item\":\"minecraft:iron_ingot\"}");
            assertTrue(tagged.hasTagIngredients());
            assertFalse(plain.hasTagIngredients());
            // The client between the recipe packet and the tags packet: the old filter dropped this recipe.
            assertThrows(IllegalArgumentException.class, tagged::definition);

            ServerTagGate<Added> gate = new ServerTagGate<>();
            gate.connectionChanged();
            assertEquals(List.of(plain), gate.admit("lathe", List.of(plain, tagged),
                    ClassicMachineRecipe::hasTagIngredients, ServerTagGateTest::resolves, into("lathe")));

            BuiltInRegistries.ITEM.bindTags(Map.of(tag, List.of(Items.IRON_INGOT.builtInRegistryHolder())));
            reload();
            gate.tagsArrived();
            Added added = new Added();
            assertEquals(List.of(new ServerTagGate.Released("lathe", 2)), gate.release(added));
            assertEquals(Map.of("lathe", List.of(tagged)), added.recipes);
            assertEquals(List.of(List.of("minecraft:iron_ingot")), tagged.resolveInputs());
        } finally {
            BuiltInRegistries.ITEM.bindTags(saved);
            reload();
        }
    }

    @Test
    void aPressIngredientReadBeforeTheTagsKeepsForgesEmptyTagBarrier() {
        Map<TagKey<Item>, List<Holder<Item>>> saved = savedTags();
        TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation("arce_test", "w188_blocks"));
        String json = "{\"tag\":\"arce_test:w188_blocks\"}";
        try {
            BuiltInRegistries.ITEM.bindTags(Map.of());
            reload();
            Ingredient early = BoundedItemIngredientCodec.decode(JsonParser.parseString(json));
            Ingredient held = BoundedItemIngredientCodec.decode(JsonParser.parseString(json));
            assertTrue(early.getItems()[0].is(Items.BARRIER));

            BuiltInRegistries.ITEM.bindTags(Map.of(tag, List.of(Items.IRON_BLOCK.builtInRegistryHolder())));
            reload();
            // Forge caches getItems() and does not reset it for new tags, so the press must wait as well.
            assertTrue(early.getItems()[0].is(Items.BARRIER));
            assertTrue(held.getItems()[0].is(Items.IRON_BLOCK));

            assertTrue(new SmallPlatePressRecipe(new ResourceLocation("arce_test", "w188_press"), held,
                    new ItemStack(Items.IRON_INGOT)).hasTagIngredients());
            assertFalse(new SmallPlatePressRecipe(new ResourceLocation("arce_test", "w188_press_items"),
                    Ingredient.of(Items.IRON_BLOCK), new ItemStack(Items.IRON_INGOT)).hasTagIngredients());
        } finally {
            BuiltInRegistries.ITEM.bindTags(saved);
            reload();
        }
    }

    private static boolean resolves(ClassicMachineRecipe recipe) {
        try {
            recipe.definition();
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static ClassicMachineRecipe lathe(String ingredient) {
        return new ClassicMachineRecipe(ClassicRecipeCodec.decode(new ResourceLocation("arce_test", "w188_lathe"),
                "{\"type\":\"advancedrocketrycommunity:lathe\",\"schema_version\":1,\"item_inputs\":[{\"ingredient\":"
                        + ingredient + ",\"count\":1}],\"item_outputs\":[{\"item\":\"minecraft:stick\",\"count\":2}],"
                        + "\"fluid_inputs\":[],\"fluid_outputs\":[],\"processing_time\":300,\"energy_per_tick\":20}"));
    }

    private static Map<TagKey<Item>, List<Holder<Item>>> savedTags() {
        Map<TagKey<Item>, List<Holder<Item>>> saved = new HashMap<>();
        BuiltInRegistries.ITEM.getTags().forEach(pair -> saved.put(pair.getFirst(), pair.getSecond().stream().toList()));
        return saved;
    }

    private static void reload() {
        RecipeTagGeneration.tagsUpdated(new TagsUpdatedEvent(RegistryAccess.EMPTY, false, false));
    }

    private static <T> BiConsumer<Added, List<T>> into(String category) {
        return (added, recipes) -> added.recipes.computeIfAbsent(category, key -> new ArrayList<>()).addAll(recipes);
    }

    /** Stands in for JEI's runtime recipe manager: what each category received after the tags arrived. */
    private static final class Added {
        private final Map<String, List<Object>> recipes = new LinkedHashMap<>();
    }
}
