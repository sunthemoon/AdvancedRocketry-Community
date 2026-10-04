package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TagsUpdatedEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DeferredRecipeTagTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void tagParsingBeforeBindingReloadAndPerRecipeOverflowAreBounded() {
        Map<TagKey<Item>, List<Holder<Item>>> saved = new HashMap<>();
        BuiltInRegistries.ITEM.getTags().forEach(pair -> saved.put(pair.getFirst(), pair.getSecond().stream().toList()));
        TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation("arce_test", "inputs"));
        try {
            BuiltInRegistries.ITEM.bindTags(Map.of()); reload();
            RollingMachineRecipe tagged = rolling("{\"tag\":\"arce_test:inputs\"}");
            RollingMachineRecipe other = rolling("{\"item\":\"minecraft:iron_ingot\"}");
            String signature = tagged.signature();
            assertFalse(tagged.available()); assertTrue(other.available());
            BuiltInRegistries.ITEM.bindTags(Map.of(tag, List.of(Items.IRON_INGOT.builtInRegistryHolder()))); reload();
            assertTrue(tagged.available());
            assertTrue(tagged.matches(new SimpleContainer(new ItemStack(Items.IRON_INGOT, 2)), null));
            List<Holder<Item>> tooMany = new ArrayList<>();
            BuiltInRegistries.ITEM.stream().filter(item -> !item.getDefaultInstance().isEmpty()).limit(33)
                    .forEach(item -> tooMany.add(item.builtInRegistryHolder()));
            BuiltInRegistries.ITEM.bindTags(Map.of(tag, tooMany)); reload();
            assertFalse(tagged.available()); assertTrue(other.available());
            BuiltInRegistries.ITEM.bindTags(Map.of(tag, List.of(Items.GOLD_INGOT.builtInRegistryHolder())));
            assertFalse(tagged.available(), "disabled recipes retry only after a tag update");
            reload(); assertTrue(tagged.available()); assertEquals(signature, tagged.signature());
            assertFalse(tagged.matches(new SimpleContainer(new ItemStack(Items.IRON_INGOT, 2)), null));
            assertTrue(tagged.matches(new SimpleContainer(new ItemStack(Items.GOLD_INGOT, 2)), null));
        } finally { BuiltInRegistries.ITEM.bindTags(saved); reload(); }
    }

    @Test void sameBuiltinIdAndResolvedOldHashCannotWitnessDifferentAuthoredJson() {
        ResourceLocation id = new ResourceLocation("advancedrocketrycommunity", "rolling_iron_bars");
        Map<TagKey<Item>, List<Holder<Item>>> saved = new HashMap<>();
        BuiltInRegistries.ITEM.getTags().forEach(pair -> saved.put(pair.getFirst(), pair.getSecond().stream().toList()));
        TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation("arce_test", "historical_alias"));
        try {
            BuiltInRegistries.ITEM.bindTags(Map.of(tag, List.of(Items.IRON_INGOT.builtInRegistryHolder()))); reload();
            RollingMachineRecipe old = new RollingMachineRecipe.Serializer().fromJson(id,
                    LegacyKernelRecipeProof.payload(id, "advancedrocketrycommunity:rolling"));
            RollingMachineRecipe override = rolling("{\"tag\":\"arce_test:historical_alias\"}");
            assertEquals(old.getId(), override.getId());
            assertEquals(old.legacySignature(), override.legacySignature());
            assertNotEquals(old.jsonPayload(), override.jsonPayload());
            assertNotEquals(old.signature(), override.signature());
        } finally { BuiltInRegistries.ITEM.bindTags(saved); reload(); }
    }

    private static void reload() { RecipeTagGeneration.tagsUpdated(new TagsUpdatedEvent(RegistryAccess.EMPTY, false, false)); }
    private static RollingMachineRecipe rolling(String ingredient) {
        return new RollingMachineRecipe.Serializer().fromJson(new ResourceLocation("advancedrocketrycommunity", "rolling_iron_bars"),
                JsonParser.parseString("{\"type\":\"advancedrocketrycommunity:rolling\",\"schema_version\":1,\"ingredient\":"
                        + ingredient + ",\"input_count\":2,\"fluid\":{\"fluid\":\"minecraft:water\",\"amount\":100},"
                        + "\"result\":{\"item\":\"minecraft:iron_bars\",\"count\":8},\"processing_time\":100,\"energy_per_tick\":20}")
                        .getAsJsonObject());
    }
}
