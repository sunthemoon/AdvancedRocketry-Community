package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Original additive damage definitions and passive armor tag membership. */
public final class PlanetaryExposureProvider implements DataProvider {
    private final Path root;

    public PlanetaryExposureProvider(PackOutput output) {
        root = output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve(ModIdentity.MOD_ID);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (String hazard : List.of("cold", "heat", "pressure", "solar")) {
            JsonObject json = new JsonObject();
            json.addProperty("message_id", ModIdentity.MOD_ID + ".planetary_" + hazard);
            json.addProperty("scaling", "never");
            json.addProperty("exhaustion", 0);
            json.addProperty("effects", "hurt");
            writes.add(DataProvider.saveStable(output, json, root.resolve("damage_type/planetary_" + hazard + ".json")));
        }
        for (String protection : List.of("thermal", "pressure", "solar")) {
            JsonObject json = new JsonObject();
            json.addProperty("replace", false);
            JsonArray items = new JsonArray();
            for (String slot : List.of("helmet", "chestplate", "leggings", "boots")) {
                items.add(ModIdentity.id("space_suit_" + slot).toString());
            }
            json.add("values", items);
            writes.add(DataProvider.saveStable(output, json, root.resolve("tags/items/" + protection + "_protection.json")));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "ARCE planetary exposure and protection"; }
}
