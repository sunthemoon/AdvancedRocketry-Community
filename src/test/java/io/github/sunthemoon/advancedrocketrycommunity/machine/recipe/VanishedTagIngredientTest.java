package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicMachineRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TagsUpdatedEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W193 (W179-F5): a tag that the data of a reload no longer defines keeps its named set with the old items in vanilla's
 * and Forge's registries, while no item is a member any more. The shared ingredient codec treats such a tag as absent,
 * which is what it and vanilla already see after a restart.
 */
class VanishedTagIngredientTest {
    private static final String NO_ALTERNATIVES = "machine ingredient has no bound alternatives";
    /** Created after the bootstrap, which a tag key needs. */
    private static TagKey<Item> VANISHING;
    private static TagKey<Item> KEPT;

    @BeforeAll static void bootstrap() {
        MinecraftBootstrap.initialize();
        VANISHING = tag("w193_vanishing");
        KEPT = tag("w193_kept");
    }

    @Test void aTagTheReloadNoLongerDefinesResolvesToNothingAsAfterARestart() {
        Map<TagKey<Item>, List<Holder<Item>>> saved = saved();
        try {
            bind(saved, Map.of(VANISHING, holders(Items.IRON_INGOT, Items.GOLD_INGOT), KEPT, holders(Items.COPPER_INGOT)));
            assertEquals(List.of("minecraft:gold_ingot", "minecraft:iron_ingot"), resolve("{\"tag\":\"arce_test:w193_vanishing\"}"));

            // A reload whose data no longer defines the tag: the registry keeps the named set, and no item is a member.
            bind(saved, Map.of(KEPT, holders(Items.COPPER_INGOT)));
            assertEquals(2, BuiltInRegistries.ITEM.getTag(VANISHING).orElseThrow().size(), "the named set is stale");
            assertFalse(Items.IRON_INGOT.builtInRegistryHolder().is(VANISHING));
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> resolve("{\"tag\":\"arce_test:w193_vanishing\"}"));
            assertEquals(NO_ALTERNATIVES, refused.getMessage());
            assertEquals(List.of("minecraft:stone"),
                    resolve("[{\"tag\":\"arce_test:w193_vanishing\"},{\"item\":\"minecraft:stone\"}]"));
            assertEquals(List.of("minecraft:copper_ingot"), resolve("{\"tag\":\"arce_test:w193_kept\"}"));

            // Defined again, the tag resolves again.
            bind(saved, Map.of(VANISHING, holders(Items.IRON_INGOT)));
            assertEquals(List.of("minecraft:iron_ingot"), resolve("{\"tag\":\"arce_test:w193_vanishing\"}"));
        } finally { restore(saved); }
    }

    @Test void theVariantBoundStillAppliesToADefinedTagButNotToAVanishedOne() {
        Map<TagKey<Item>, List<Holder<Item>>> saved = saved();
        try {
            List<Holder<Item>> tooMany = new ArrayList<>();
            BuiltInRegistries.ITEM.stream().filter(item -> !item.getDefaultInstance().isEmpty()).limit(33)
                    .forEach(item -> tooMany.add(item.builtInRegistryHolder()));
            bind(saved, Map.of(VANISHING, tooMany));
            assertEquals("machine ingredient must resolve to 1..32 variants", assertThrows(IllegalArgumentException.class,
                    () -> resolve("{\"tag\":\"arce_test:w193_vanishing\"}")).getMessage());
            bind(saved, Map.of());
            assertEquals(NO_ALTERNATIVES, assertThrows(IllegalArgumentException.class,
                    () -> resolve("{\"tag\":\"arce_test:w193_vanishing\"}")).getMessage());
        } finally { restore(saved); }
    }

    @Test void classicAndRollingRecipesLoseAVanishedTagAtTheReload() {
        Map<TagKey<Item>, List<Holder<Item>>> saved = saved();
        try {
            bind(saved, Map.of(VANISHING, holders(Items.IRON_INGOT)));
            ClassicMachineRecipe classic = new ClassicMachineRecipe.Serializer().fromJson(
                    new ResourceLocation("arce_test", "w193_lathe"), JsonParser.parseString(
                            "{\"type\":\"advancedrocketrycommunity:lathe\",\"schema_version\":1,\"item_inputs\":"
                            + "[{\"ingredient\":{\"tag\":\"arce_test:w193_vanishing\"},\"count\":1}],\"item_outputs\":"
                            + "[{\"item\":\"minecraft:iron_nugget\",\"count\":1}],\"fluid_inputs\":[],\"fluid_outputs\":[],"
                            + "\"processing_time\":100,\"energy_per_tick\":10}").getAsJsonObject());
            RollingMachineRecipe rolling = new RollingMachineRecipe.Serializer().fromJson(
                    new ResourceLocation("arce_test", "w193_rolling"), JsonParser.parseString(
                            "{\"type\":\"advancedrocketrycommunity:rolling\",\"schema_version\":1,\"ingredient\":"
                            + "{\"tag\":\"arce_test:w193_vanishing\"},\"input_count\":2,\"fluid\":{\"fluid\":"
                            + "\"minecraft:water\",\"amount\":100},\"result\":{\"item\":\"minecraft:iron_bars\",\"count\":8},"
                            + "\"processing_time\":100,\"energy_per_tick\":20}").getAsJsonObject());
            assertEquals(List.of("minecraft:iron_ingot"), classic.definition().inputs().get(0).alternatives());
            assertTrue(rolling.available());

            bind(saved, Map.of());
            assertEquals(NO_ALTERNATIVES, assertThrows(IllegalArgumentException.class, classic::definition).getMessage());
            assertFalse(rolling.available(), "the rolling recipe still resolves a tag that the reload dropped");
        } finally { restore(saved); }
    }

    private static List<String> resolve(String json) {
        JsonElement ingredient = JsonParser.parseString(json);
        return BoundedItemIngredientCodec.resolveAlternatives(ingredient);
    }

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, new ResourceLocation("arce_test", path));
    }

    private static List<Holder<Item>> holders(Item... items) {
        List<Holder<Item>> holders = new ArrayList<>();
        for (Item item : items) { holders.add(item.builtInRegistryHolder()); }
        return holders;
    }

    private static Map<TagKey<Item>, List<Holder<Item>>> saved() {
        Map<TagKey<Item>, List<Holder<Item>>> saved = new HashMap<>();
        BuiltInRegistries.ITEM.getTags().forEach(pair -> saved.put(pair.getFirst(), pair.getSecond().stream().toList()));
        saved.remove(VANISHING);
        saved.remove(KEPT);
        return saved;
    }

    /** Binds the saved tags plus {@code extra}, as a reload's data would, and advances the recipe tag generation. */
    private static void bind(Map<TagKey<Item>, List<Holder<Item>>> saved, Map<TagKey<Item>, List<Holder<Item>>> extra) {
        Map<TagKey<Item>, List<Holder<Item>>> tags = new HashMap<>(saved);
        tags.putAll(extra);
        BuiltInRegistries.ITEM.bindTags(tags);
        RecipeTagGeneration.tagsUpdated(new TagsUpdatedEvent(RegistryAccess.EMPTY, false, false));
    }

    private static void restore(Map<TagKey<Item>, List<Holder<Item>>> saved) { bind(saved, Map.of()); }
}
