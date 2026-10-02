package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Material;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.OreKind;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Product;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialTags;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRecipes;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.material.Fluids;

/**
 * v1.8 recipes of the material set (ADR-063 section 2), with the legacy shapes (RecipeHandler 60-170) and on tags:
 * nuggets and blocks, rods (three ingots on a diagonal make four), gears, coils, the steel fan, smelting and blasting,
 * the rolling machine (ingot to plate at 20 FE/t, plate to sheet at 200 FE/t, 300 ticks and 100 mB of water each)
 * and the small plate press (block to four plates, ore to two dust; rutile neither smelts nor presses).
 *
 * <p>Rolling recipes name items, as the v1.2 {@code rolling_iron_bars} recipe does: the kernel recipe type resolves its
 * ingredient when recipes load, before tags are bound. Iron is the one rolling exception: {@code rolling_iron_bars}
 * already rolls iron ingots, and a second iron-ingot recipe would make the machine refuse both as ambiguous, so iron
 * plates come from the small plate press (ADR-063 revision 4).
 */
public final class V180MaterialRecipes extends RecipeProvider {
    public V180MaterialRecipes(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> output) {
        for (Material material : Material.values()) {
            String id = material.id();
            TagKey<Item> ingots = MaterialTags.item(material, Product.INGOT);
            boolean hasIngot = material.has(Product.INGOT) || material.vanillaIngot();
            Item ingotItem = ingotItem(material);
            if (material.has(Product.NUGGET) && hasIngot) {
                Item nugget = MaterialContent.item(id + "_nugget");
                ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, nugget, 9).requires(ingots)
                        .unlockedBy("has_ingot", has(ingots)).save(output, ModIdentity.id(id + "_nugget"));
                ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ingotItem)
                        .pattern("NNN").pattern("NNN").pattern("NNN")
                        .define('N', MaterialTags.item(material, Product.NUGGET))
                        .unlockedBy("has_nugget", has(nugget)).save(output, ModIdentity.id(id + "_ingot_from_nuggets"));
            }
            if (material.has(Product.BLOCK)) {
                Item block = MaterialContent.item(id + "_block");
                ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, block)
                        .pattern("III").pattern("III").pattern("III").define('I', ingots)
                        .unlockedBy("has_ingot", has(ingots)).save(output, ModIdentity.id(id + "_block"));
                ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ingotItem, 9)
                        .requires(MaterialTags.item(material, Product.BLOCK))
                        .unlockedBy("has_block", has(block)).save(output, ModIdentity.id(id + "_ingot_from_" + id + "_block"));
            }
            if (material.has(Product.ROD)) {
                ShapedRecipeBuilder.shaped(RecipeCategory.MISC, MaterialContent.item(id + "_rod"), 4)
                        .pattern("I  ").pattern(" I ").pattern("  I").define('I', ingots)
                        .unlockedBy("has_ingot", has(ingots)).save(output, ModIdentity.id(id + "_rod"));
            }
            if (material.has(Product.GEAR)) {
                ShapedRecipeBuilder.shaped(RecipeCategory.MISC, MaterialContent.item(id + "_gear"))
                        .pattern("SPS").pattern(" I ").pattern("SPS")
                        .define('S', MaterialTags.item(material, Product.ROD))
                        .define('P', MaterialTags.item(material, Product.PLATE))
                        .define('I', ingots)
                        .unlockedBy("has_plate", has(MaterialTags.item(material, Product.PLATE)))
                        .save(output, ModIdentity.id(id + "_gear"));
            }
            if (material.has(Product.COIL)) {
                ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, MaterialContent.item(id + "_coil"))
                        .pattern("III").pattern("I I").pattern("III").define('I', ingots)
                        .unlockedBy("has_ingot", has(ingots)).save(output, ModIdentity.id(id + "_coil"));
            }
            if (material.has(Product.FAN)) {
                ShapedRecipeBuilder.shaped(RecipeCategory.MISC, MaterialContent.item(id + "_fan"))
                        .pattern("P P").pattern(" R ").pattern("P P")
                        .define('P', MaterialTags.item(material, Product.PLATE))
                        .define('R', MaterialTags.item(material, Product.ROD))
                        .unlockedBy("has_plate", has(MaterialTags.item(material, Product.PLATE)))
                        .save(output, ModIdentity.id(id + "_fan"));
            }
            cooking(output, material, ingotItem);
            rolling(output, material);
            pressing(output, material);
        }
    }

    private static Item ingotItem(Material material) {
        return switch (material) {
            case COPPER -> Items.COPPER_INGOT;
            case IRON -> Items.IRON_INGOT;
            case GOLD -> Items.GOLD_INGOT;
            default -> material.has(Product.INGOT) ? MaterialContent.item(material.id() + "_ingot") : Items.AIR;
        };
    }

    /** Ore, raw item and dust to ingot (rutile excepted); the dilithium ore smelts to dilithium dust. */
    private static void cooking(Consumer<FinishedRecipe> output, Material material, Item ingot) {
        String id = material.id();
        if (material == Material.DILITHIUM) {
            Item dust = MaterialContent.item("dilithium_dust");
            cook(output, Ingredient.of(MaterialTags.oreItem(material)), dust, 1.0F, "dilithium_dust_from_ore",
                    has(MaterialTags.oreItem(material)));
            return;
        }
        if (ingot == Items.AIR) {
            return;
        }
        boolean ownSmeltableOre = material.hasOwnOre() && material != Material.TITANIUM;
        if (ownSmeltableOre) {
            cook(output, Ingredient.of(MaterialTags.oreItem(material)), ingot, 0.7F, id + "_ingot_from_ore",
                    has(MaterialTags.oreItem(material)));
            cook(output, Ingredient.of(MaterialTags.rawItem(material)), ingot, 0.7F, id + "_ingot_from_raw",
                    has(MaterialTags.rawItem(material)));
        }
        if (material.has(Product.DUST)) {
            TagKey<Item> dust = MaterialTags.item(material, Product.DUST);
            cook(output, Ingredient.of(dust), ingot, 0.35F, id + "_ingot_from_dust", has(dust));
        }
    }

    private static void cook(Consumer<FinishedRecipe> output, Ingredient input, Item result, float experience,
                             String name, InventoryChangeTrigger.TriggerInstance unlock) {
        SimpleCookingRecipeBuilder.smelting(input, RecipeCategory.MISC, result, experience, 200)
                .unlockedBy("has_input", unlock).save(output, ModIdentity.id(name + "_smelting"));
        SimpleCookingRecipeBuilder.blasting(input, RecipeCategory.MISC, result, experience, 100)
                .unlockedBy("has_input", unlock).save(output, ModIdentity.id(name + "_blasting"));
    }

    private static void rolling(Consumer<FinishedRecipe> output, Material material) {
        String id = material.id();
        if (material.has(Product.PLATE) && material != Material.IRON
                && (material.has(Product.INGOT) || material.vanillaIngot())) {
            output.accept(new Rolling(ModIdentity.id("rolling_" + id + "_plate"), ingotItem(material),
                    MaterialContent.item(id + "_plate"), 20));
        }
        if (material.has(Product.SHEET)) {
            output.accept(new Rolling(ModIdentity.id("rolling_" + id + "_sheet"), MaterialContent.item(id + "_plate"),
                    MaterialContent.item(id + "_sheet"), 200));
        }
    }

    private static void pressing(Consumer<FinishedRecipe> output, Material material) {
        String id = material.id();
        boolean hasBlock = material.has(Product.BLOCK) || material.vanillaIngot();
        if (material.has(Product.PLATE) && hasBlock) {
            output.accept(new Pressing(ModIdentity.id("pressing_" + id + "_plate"),
                    MaterialTags.item(material, Product.BLOCK), new ItemStack(MaterialContent.item(id + "_plate"), 4)));
        }
        // Ore to dust only for a material with its own ore and dust; titanium's ore is rutile, a separate legacy
        // material without dust, so rutile does not press: titanium waits for the electric arc furnace (C16b).
        boolean hasOre = (material.hasOwnOre() && material != Material.TITANIUM)
                || material.oreKind() == OreKind.VANILLA;
        if (material.has(Product.DUST) && hasOre) {
            output.accept(new Pressing(ModIdentity.id("pressing_" + id + "_dust"),
                    MaterialTags.oreItem(material), new ItemStack(MaterialContent.item(id + "_dust"), 2)));
        }
    }

    private static JsonObject itemJson(Item item, int count) {
        JsonObject json = new JsonObject();
        json.addProperty("item", BuiltInRegistries.ITEM.getKey(item).toString());
        json.addProperty("count", count);
        return json;
    }

    private record Rolling(ResourceLocation id, Item input, Item result, int energyPerTick)
            implements FinishedRecipe {
        @Override
        public void serializeRecipeData(JsonObject json) {
            json.addProperty("schema_version", ProcessDefinition.SCHEMA_VERSION);
            json.add("ingredient", Ingredient.of(input).toJson());
            json.addProperty("input_count", 1);
            JsonObject fluid = new JsonObject();
            fluid.addProperty("fluid", BuiltInRegistries.FLUID.getKey(Fluids.WATER).toString());
            fluid.addProperty("amount", 100);
            json.add("fluid", fluid);
            json.add("result", itemJson(result, 1));
            json.addProperty("processing_time", 300);
            json.addProperty("energy_per_tick", energyPerTick);
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public RecipeSerializer<?> getType() {
            return ModRecipes.ROLLING_SERIALIZER.get();
        }

        @Nullable
        @Override
        public JsonObject serializeAdvancement() {
            return null;
        }

        @Nullable
        @Override
        public ResourceLocation getAdvancementId() {
            return null;
        }
    }

    private record Pressing(ResourceLocation id, TagKey<Item> input, ItemStack result) implements FinishedRecipe {
        @Override
        public void serializeRecipeData(JsonObject json) {
            json.addProperty("schema_version", 1);
            json.add("ingredient", Ingredient.of(input).toJson());
            json.add("result", itemJson(result.getItem(), result.getCount()));
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public RecipeSerializer<?> getType() {
            return MaterialContent.SMALL_PLATE_PRESS_SERIALIZER.get();
        }

        @Nullable
        @Override
        public JsonObject serializeAdvancement() {
            return null;
        }

        @Nullable
        @Override
        public ResourceLocation getAdvancementId() {
            return null;
        }
    }
}
