package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillTableCodec;
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
 * ADR-055 section 2 built-in laser drill tables: Earth, Moon, Mars, Venus and one default. Every table keeps the
 * legacy proportion of one ore hit in ten; the default is the legacy drill (nine in ten five cobblestone, ore hits of
 * one to five items) and equals the C10 reference table. Only vanilla raw ores and minerals are used; the body lists
 * are v1.7 balance decisions. Every file is decoded by the strict runtime codec before it is written.
 */
public final class V170LaserDrillTableProvider implements DataProvider {
    private final PackOutput.PathProvider paths;

    public V170LaserDrillTableProvider(PackOutput output) {
        paths = output.createPathProvider(PackOutput.Target.DATA_PACK, LaserDrillTableCodec.DIRECTORY);
    }

    static Map<String, JsonObject> tables() {
        Map<String, JsonObject> tables = new LinkedHashMap<>();
        tables.put("default", table(null, Items.COBBLESTONE,
                Items.RAW_IRON, 5, 30, Items.RAW_COPPER, 5, 30, Items.RAW_GOLD, 5, 15, Items.REDSTONE, 5, 20,
                Items.DIAMOND, 1, 5));
        tables.put("earth", table(CelestialIds.EARTH_ID, Items.COBBLESTONE,
                Items.COAL, 5, 25, Items.RAW_IRON, 5, 25, Items.RAW_COPPER, 5, 20, Items.RAW_GOLD, 3, 10,
                Items.LAPIS_LAZULI, 5, 10, Items.DIAMOND, 1, 5, Items.EMERALD, 1, 5));
        tables.put("moon", table(CelestialIds.MOON_ID, Items.COBBLESTONE,
                Items.RAW_IRON, 5, 40, Items.QUARTZ, 5, 30, Items.RAW_GOLD, 2, 15, Items.RAW_COPPER, 5, 10,
                Items.DIAMOND, 1, 5));
        tables.put("mars", table(PlanetaryContent.MARS, Items.RED_SAND,
                Items.RAW_IRON, 5, 45, Items.REDSTONE, 5, 25, Items.RAW_COPPER, 5, 20, Items.RAW_GOLD, 2, 5,
                Items.DIAMOND, 1, 5));
        tables.put("venus", table(PlanetaryContent.VENUS, Items.BASALT,
                Items.RAW_GOLD, 5, 30, Items.QUARTZ, 5, 30, Items.RAW_COPPER, 5, 20, Items.REDSTONE, 5, 15,
                Items.DIAMOND, 1, 5));
        return tables;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        Predicate<ResourceLocation> items = id -> ForgeRegistries.ITEMS.containsKey(id)
                && ForgeRegistries.ITEMS.getValue(id) != Items.AIR;
        List<CompletableFuture<?>> writes = new ArrayList<>();
        tables().forEach((name, json) -> {
            LaserDrillTableCodec.decode(ModIdentity.id(name), json.toString().getBytes(StandardCharsets.UTF_8), items);
            writes.add(DataProvider.saveStable(output, json, paths.json(ModIdentity.id(name))));
        });
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "ARCE v1.7 laser drill tables";
    }

    /** Nine in ten hits are five of {@code filler}; the ore entries ({@code item, count, weight} triples) sum to 100. */
    private static JsonObject table(ResourceLocation body, Item filler, Object... ores) {
        JsonObject table = new JsonObject();
        table.addProperty("schema_version", 1);
        JsonArray bodies = new JsonArray();
        if (body != null) {
            bodies.add(body.toString());
        }
        table.add("bodies", bodies);
        table.addProperty("default", body == null);
        JsonArray entries = new JsonArray();
        entries.add(entry(filler, 5, 900));
        int oreWeight = 0;
        for (int index = 0; index < ores.length; index += 3) {
            entries.add(entry((Item) ores[index], (Integer) ores[index + 1], (Integer) ores[index + 2]));
            oreWeight += (Integer) ores[index + 2];
        }
        if (oreWeight != 100) {
            throw new IllegalStateException("Ore entries must weigh 100 so one hit in ten is an ore");
        }
        table.add("entries", entries);
        return table;
    }

    private static JsonObject entry(Item item, int count, int weight) {
        JsonObject entry = new JsonObject();
        entry.addProperty("item", ForgeRegistries.ITEMS.getKey(item).toString());
        entry.addProperty("count", count);
        entry.addProperty("weight", weight);
        return entry;
    }
}
