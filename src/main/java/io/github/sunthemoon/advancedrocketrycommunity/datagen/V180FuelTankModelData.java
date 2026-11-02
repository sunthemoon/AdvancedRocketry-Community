package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** W260: one standalone middle mesh shared by the three chemical fuel tanks and their block items. */
public final class V180FuelTankModelData implements DataProvider {
    private static final String NS = ModIdentity.MOD_ID;
    private final PackOutput output;

    public V180FuelTankModelData(PackOutput output) { this.output = output; }

    public static JsonObject block() {
        JsonObject model = new JsonObject();
        model.addProperty("loader", "forge:obj");
        model.addProperty("model", NS + ":models/block/models/middletank.obj");
        model.addProperty("flip_v", false);
        model.addProperty("automatic_culling", false);
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", NS + ":block/tank");
        model.add("textures", textures);
        return model;
    }

    public static JsonObject item(String id) {
        if (!id.equals("rocket_fuel_tank") && !id.equals("bipropellant_fuel_tank") && !id.equals("oxidizer_fuel_tank")) {
            throw new IllegalArgumentException("Not a chemical fuel tank: " + id);
        }
        JsonObject model = V180MotorModelData.item("rocket_motor");
        model.addProperty("parent", NS + ":block/" + id);
        return model;
    }

    /** Higher tiers use V180PropulsionData; replace only the basic tank's two historical models here. */
    public static Map<String, JsonObject> files() {
        String root = "assets/" + NS + "/models/";
        return Map.of(root + "block/rocket_fuel_tank.json", block(),
                root + "item/rocket_fuel_tank.json", item("rocket_fuel_tank"));
    }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        return CompletableFuture.allOf(files().entrySet().stream()
                .map(entry -> DataProvider.saveStable(cache, entry.getValue(), output.getOutputFolder().resolve(entry.getKey())))
                .toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "v1.8 standalone classic fuel tank models"; }
}
