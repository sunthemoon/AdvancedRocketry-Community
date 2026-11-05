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

/** W277 texture-only copies of still-live historical models. Existing v1.8 providers own their models. */
public final class V180RemainingStandInArtData implements DataProvider {
    public static final String ASSETS = "assets/advancedrocketrycommunity/";
    private static final String NS = "advancedrocketrycommunity:";
    private final PackOutput output;

    public V180RemainingStandInArtData(PackOutput output) { this.output = output; }

    public static Map<String, JsonObject> models() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        block(files, "machine_casing", "orientable_with_bottom", "bottom", "machine_casing_top",
                "front", "machine_casing_front", "side", "machine_panel_side", "top", "machine_casing_top");
        for (String id : List.of("oxygen_vent", "oxygen_vent_active", "guidance_computer", "rocket_assembler",
                "fuel_loader", "satellite_terminal")) {
            String top = switch (id) {
                case "oxygen_vent" -> "machine_casing_top";
                case "oxygen_vent_active" -> "oxygen_vent_grille_active";
                default -> id + "_top";
            };
            block(files, id, "cube_bottom_top", "bottom", "machine_panel_side", "side", "machine_panel_side", "top", top);
        }
        block(files, "rocket_seat", "cube_bottom_top", "bottom", "machine_panel_side",
                "side", "rocket_seat_frame", "top", "rocket_seat_cushion");
        block(files, "microwave_receiver", "cube_bottom_top", "bottom", "machine_casing_top",
                "side", "machine_panel_side", "top", "microwave_receiver_top");
        block(files, "black_hole_generator", "orientable", "front", "black_hole_generator_front",
                "side", "machine_panel_side", "top", "black_hole_generator_top");
        block(files, "elevator_anchor", "cube_bottom_top", "bottom", "elevator_anchor_bottom",
                "side", "machine_panel_side", "top", "machine_casing_top");
        block(files, "elevator_terminal", "cube_bottom_top", "bottom", "machine_casing_top",
                "side", "machine_casing_front", "top", "elevator_terminal_top");
        block(files, "endgame_casing", "cube_column", "end", "endgame_casing_end", "side", "machine_panel_side");
        block(files, "gravity_field_controller", "cube_bottom_top", "bottom", "machine_casing_top",
                "side", "machine_casing_front", "top", "gravity_field_controller_top");
        block(files, "laser_target", "cube_bottom_top", "bottom", "laser_target_bottom",
                "side", "machine_panel_side", "top", "machine_casing_top");
        JsonObject kit = new JsonObject();
        kit.addProperty("parent", "minecraft:item/generated");
        JsonObject icon = new JsonObject();
        icon.addProperty("layer0", NS + "item/station_deployment_kit");
        kit.add("textures", icon);
        files.put(ASSETS + "models/item/station_deployment_kit.json", kit);
        return files;
    }

    private static void block(Map<String, JsonObject> files, String id, String parent, String... pairs) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/" + parent);
        JsonObject faces = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) { faces.addProperty(pairs[i], NS + "block/" + pairs[i + 1]); }
        model.add("textures", faces);
        files.put(ASSETS + "models/block/" + id + ".json", model);
    }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        models().forEach((path, json) -> writes.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path))));
        for (String name : V180RemainingStandInArt.textures()) {
            byte[] png = V180RemainingStandInArt.png(name);
            var path = output.getOutputFolder().resolve(ASSETS + "textures/" + name + ".png");
            writes.add(CompletableFuture.runAsync(() -> {
                try { cache.writeIfNeeded(path, png, Hashing.sha1().hashBytes(png)); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
            }));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "v1.8 W277 remaining original stand-in art"; }
}
