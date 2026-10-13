package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Original resources; recipe facts follow the audited 1.12.2 trackingcircuit.json. */
public final class V180TrackingCircuitData implements DataProvider {
    public static final String ITEM_ID = "advancedrocketrycommunity:tracking_circuit";
    public static final String RECIPE_ID = "advancedrocketrycommunity:precision_tracking_circuit";
    public static final String TEXTURE = "assets/advancedrocketrycommunity/textures/item/tracking_circuit.png";
    private final PackOutput output;
    private final boolean client, server;

    public V180TrackingCircuitData(PackOutput output, boolean client, boolean server) {
        this.output = output; this.client = client; this.server = server;
    }

    public static Map<String, JsonObject> clientFiles() {
        return Map.of("assets/advancedrocketrycommunity/models/item/tracking_circuit.json", json("""
                {"parent":"minecraft:item/generated","textures":{"layer0":"advancedrocketrycommunity:item/tracking_circuit"}}
                """));
    }

    public static Map<String, JsonObject> serverFiles() {
        return Map.of("data/advancedrocketrycommunity/recipes/precision_tracking_circuit.json", json("""
                {"type":"advancedrocketrycommunity:precision_assembling","schema_version":1,
                 "inputs":[{"ingredient":{"tag":"forge:dusts/redstone"},"count":1},
                  {"ingredient":{"item":"minecraft:ender_eye"},"count":1},
                  {"ingredient":{"item":"advancedrocketrycommunity:basic_circuit_plate"},"count":1}],
                 "outputs":[{"item":"advancedrocketrycommunity:tracking_circuit","count":1}],
                 "processing_time":900,"energy_per_tick":50}
                """));
    }

    public static Map<String, String> language(boolean chinese) {
        return Map.of("item.advancedrocketrycommunity.tracking_circuit", chinese ? "追踪电路" : "Tracking Circuit");
    }

    /** Original crosshair PCB with an offset target and three edge contacts. */
    public static String[] grid() {
        return new String[] {
                "................", "..333333333333..", "..355555555553..", "..357777755553..",
                ".9937557757553..", "..357522275553..", "..357521275553..", "..35722222777366",
                ".993752125755366", "..357522275553..", "..357557755553..", "..357777755553..",
                ".9935555555553..", "..333333333333..", "................", "................"
        };
    }

    public static byte[] texture() { return V180MaterialArt.png(grid(), 0x4AAE99); }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        if (client) {
            clientFiles().forEach((path, json) -> writes.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path))));
            writes.add(CompletableFuture.runAsync(() -> {
                byte[] png = texture();
                try { cache.writeIfNeeded(output.getOutputFolder().resolve(TEXTURE), png, Hashing.sha1().hashBytes(png)); }
                catch (IOException failed) { throw new UncheckedIOException(failed); }
            }));
        }
        if (server) {
            serverFiles().forEach((path, json) -> writes.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path))));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "v1.8 tracking circuit original resources"; }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
