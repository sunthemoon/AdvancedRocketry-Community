package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.ResourceTableCodec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * ADR-052 section 8 built-in resource data: four asteroid types with the legacy numbers as reference (weights
 * 20, 15, 2, 1; masses 200, 200, 75, 50; richness 30, 20, 20, 20; mass variability 50; richness variability 50,
 * 50, 30, 50) with cobblestone as the base and vanilla ores in place of LibVulpes items, and one gas table that
 * maps the gas giant to hydrogen canisters. The ore lists are v1.6 balance decisions. Every file is decoded by
 * the strict runtime codec before it is written.
 */
public final class V160ResourceTableProvider implements DataProvider {
    private final PackOutput.PathProvider asteroidPaths;
    private final PackOutput.PathProvider gasPaths;

    public V160ResourceTableProvider(PackOutput output) {
        asteroidPaths = output.createPathProvider(PackOutput.Target.DATA_PACK, ResourceTableCodec.ASTEROID_DIRECTORY);
        gasPaths = output.createPathProvider(PackOutput.Target.DATA_PACK, ResourceTableCodec.GAS_DIRECTORY);
    }

    static Map<String, JsonObject> asteroidTypes() {
        Map<String, JsonObject> types = new LinkedHashMap<>();
        types.put("small_asteroid", type("small_asteroid", 20, 200, 30, 50, 100,
                ores(Items.IRON_ORE, 10, Items.COPPER_ORE, 10, Items.COAL_ORE, 5)));
        types.put("light_asteroid", type("light_asteroid", 15, 200, 20, 50, 100,
                ores(Items.IRON_ORE, 5, Items.GOLD_ORE, 5, Items.REDSTONE_ORE, 5, Items.LAPIS_ORE, 3)));
        types.put("rich_asteroid", type("rich_asteroid", 2, 75, 20, 30, 150,
                ores(Items.GOLD_ORE, 5, Items.DIAMOND_ORE, 4, Items.EMERALD_ORE, 1)));
        types.put("strange_asteroid", type("strange_asteroid", 1, 50, 20, 50, 200,
                ores(Items.NETHER_QUARTZ_ORE, 10, Items.NETHER_GOLD_ORE, 5, Items.ANCIENT_DEBRIS, 1)));
        return types;
    }

    static Map<String, JsonObject> gasTables() {
        JsonObject table = new JsonObject();
        table.addProperty("schema_version", 1);
        table.addProperty("body", ModIdentity.id("gas_giant").toString());
        JsonArray products = new JsonArray();
        JsonObject hydrogen = new JsonObject();
        hydrogen.addProperty("item", key(ModItems.HYDROGEN_CANISTER.get()).toString());
        hydrogen.addProperty("amount_per_1000_ticks", 8);
        products.add(hydrogen);
        table.add("products", products);
        return Map.of("gas_giant", table);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        Predicate<ResourceLocation> items = id -> ForgeRegistries.ITEMS.containsKey(id)
                && ForgeRegistries.ITEMS.getValue(id) != Items.AIR;
        List<CompletableFuture<?>> writes = new ArrayList<>();
        asteroidTypes().forEach((name, json) -> {
            ResourceTableCodec.decodeAsteroidType(ModIdentity.id(name), bytes(json), items);
            writes.add(DataProvider.saveStable(output, json, asteroidPaths.json(ModIdentity.id(name))));
        });
        gasTables().forEach((name, json) -> {
            ResourceTableCodec.decodeGasTable(ModIdentity.id(name), bytes(json), items);
            writes.add(DataProvider.saveStable(output, json, gasPaths.json(ModIdentity.id(name))));
        });
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "ARCE v1.6 resource tables";
    }

    private static JsonObject type(String name, int weight, int mass, int richness, int richnessVariability,
                                   int timeMultiplier, JsonArray ores) {
        JsonObject type = new JsonObject();
        type.addProperty("schema_version", 1);
        type.addProperty("id", ModIdentity.id(name).toString());
        type.addProperty("weight", weight);
        type.add("systems", new JsonArray());
        type.addProperty("mass", mass);
        type.addProperty("mass_variability_pct", 50);
        type.addProperty("richness_pct", richness);
        type.addProperty("richness_variability_pct", richnessVariability);
        type.addProperty("base_item", key(Items.COBBLESTONE).toString());
        type.add("ores", ores);
        type.addProperty("time_multiplier_pct", timeMultiplier);
        return type;
    }

    private static JsonArray ores(Object... itemsAndWeights) {
        JsonArray ores = new JsonArray();
        for (int index = 0; index < itemsAndWeights.length; index += 2) {
            JsonObject ore = new JsonObject();
            ore.addProperty("item", key((Item) itemsAndWeights[index]).toString());
            ore.addProperty("weight", (Integer) itemsAndWeights[index + 1]);
            ores.add(ore);
        }
        return ores;
    }

    private static ResourceLocation key(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }

    private static byte[] bytes(JsonObject json) {
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }
}
