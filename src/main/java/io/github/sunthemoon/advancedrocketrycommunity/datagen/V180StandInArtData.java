package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Texture-only replacements for historical models. Never writes into an earlier generated root. */
public final class V180StandInArtData implements DataProvider {
    public static final String ASSETS = "assets/advancedrocketrycommunity/";
    private static final String NS = "advancedrocketrycommunity:";
    public static final List<String> PORTS = List.of("rolling_machine_item_input_port", "rolling_machine_item_output_port",
            "rolling_machine_fluid_input_port", "rolling_machine_energy_input_port",
            "precision_assembler_item_input_port", "precision_assembler_item_output_port",
            "precision_assembler_energy_input_port");
    private final PackOutput output;

    public V180StandInArtData(PackOutput output) { this.output = output; }

    public static Map<String, JsonObject> models() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        JsonObject machine = model("minecraft:block/orientable_with_bottom");
        JsonObject faces = machine.getAsJsonObject("textures");
        faces.addProperty("front", NS + "block/rolling_machine_front");
        faces.addProperty("side", NS + "block/machine_panel_side");
        faces.addProperty("top", NS + "block/machine_casing_top");
        faces.addProperty("bottom", NS + "block/machine_casing_top");
        files.put(ASSETS + "models/block/rolling_machine.json", machine);
        for (String port : PORTS) {
            String kind = port.substring(port.indexOf("_", port.indexOf("_") + 1) + 1);
            JsonObject json = model("minecraft:block/cube_bottom_top");
            JsonObject textures = json.getAsJsonObject("textures");
            textures.addProperty("side", NS + "block/machine_panel_side");
            textures.addProperty("bottom", NS + "block/machine_casing_top");
            textures.addProperty("top", NS + "block/machine_" + kind);
            files.put(ASSETS + "models/block/" + port + ".json", json);
        }
        for (String id : List.of("empty_canister", "oxygen_canister", "hydrogen_canister")) {
            JsonObject json = model("minecraft:item/generated");
            json.getAsJsonObject("textures").addProperty("layer0", NS + "item/" + id + "_cylinder");
            files.put(ASSETS + "models/item/" + id + ".json", json);
        }
        return files;
    }

    private static JsonObject model(String parent) {
        JsonObject json = new JsonObject();
        json.addProperty("parent", parent);
        json.add("textures", new JsonObject());
        return json;
    }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        models().forEach((path, json) -> writes.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path))));
        for (String name : V180StandInArt.textures()) {
            byte[] png = V180StandInArt.png(name);
            var path = output.getOutputFolder().resolve(ASSETS + "textures/" + name + ".png");
            writes.add(CompletableFuture.runAsync(() -> {
                try { cache.writeIfNeeded(path, png, Hashing.sha1().hashBytes(png)); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
            }));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "v1.8 W275 original machine and cylinder art"; }
}
