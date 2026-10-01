package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.SingularityContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleCodec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
 * ADR-057 example content: the Cygnus X-1 body, its singularity profile (500 FE per tick) and the {@code default}
 * fuel table with the legacy values (stone, cobblestone, dirt and netherrack 1 tick, everything else 500 ticks).
 * Every file is decoded by the strict runtime codecs before it is written.
 */
public final class V170BlackHoleProvider implements DataProvider {
    private final PackOutput output;

    public V170BlackHoleProvider(PackOutput output) {
        this.output = output;
    }

    static JsonObject singularity() {
        JsonObject profile = new JsonObject();
        profile.addProperty("schema_version", 1);
        profile.addProperty("body", SingularityContent.CYGNUS_X1.toString());
        profile.addProperty("output_fe_per_tick", 500);
        profile.addProperty("fuel_table", ModIdentity.id("default").toString());
        return profile;
    }

    static JsonObject defaultFuel() {
        JsonObject table = new JsonObject();
        table.addProperty("schema_version", 1);
        table.addProperty("default_burn_ticks", 500);
        JsonArray entries = new JsonArray();
        for (Item item : List.of(Items.STONE, Items.COBBLESTONE, Items.DIRT, Items.NETHERRACK)) {
            JsonObject entry = new JsonObject();
            entry.addProperty("item", ForgeRegistries.ITEMS.getKey(item).toString());
            entry.addProperty("burn_ticks", 1);
            entries.add(entry);
        }
        table.add("entries", entries);
        return table;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Predicate<ResourceLocation> items = id -> ForgeRegistries.ITEMS.containsKey(id)
                && ForgeRegistries.ITEMS.getValue(id) != Items.AIR;
        List<CompletableFuture<?>> writes = new ArrayList<>();
        var bodies = output.createPathProvider(PackOutput.Target.DATA_PACK, "celestial_bodies");
        for (CelestialBodyDefinition body : SingularityContent.definitions()) {
            writes.add(DataProvider.saveStable(cache, CelestialBodyDefinition.CODEC.encodeStart(JsonOps.INSTANCE, body)
                    .getOrThrow(false, message -> { }), bodies.json(body.id())));
        }
        JsonObject singularity = singularity();
        BlackHoleCodec.decodeSingularity(SingularityContent.CYGNUS_X1, bytes(singularity));
        writes.add(DataProvider.saveStable(cache, singularity, output.createPathProvider(PackOutput.Target.DATA_PACK,
                BlackHoleCodec.SINGULARITY_DIRECTORY).json(SingularityContent.CYGNUS_X1)));
        JsonObject fuel = defaultFuel();
        BlackHoleCodec.decodeFuelTable(ModIdentity.id("default"), bytes(fuel), items);
        writes.add(DataProvider.saveStable(cache, fuel, output.createPathProvider(PackOutput.Target.DATA_PACK,
                BlackHoleCodec.FUEL_DIRECTORY).json(ModIdentity.id("default"))));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "ARCE v1.7 black-hole example system and fuel";
    }

    private static byte[] bytes(JsonObject json) {
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }
}
