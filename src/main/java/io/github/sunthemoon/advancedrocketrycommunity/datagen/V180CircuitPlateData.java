package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** NEW circuit substrates and original precision producers; etcher producers remain separate. */
public final class V180CircuitPlateData implements DataProvider {
    public static final String BASIC_ID = "advancedrocketrycommunity:basic_circuit_plate";
    public static final String ADVANCED_ID = "advancedrocketrycommunity:advanced_circuit_plate";
    public static final String BASIC_PRECISION_ID = "advancedrocketrycommunity:precision_basic_circuit_plate";
    public static final String ADVANCED_PRECISION_ID = "advancedrocketrycommunity:precision_advanced_circuit_plate";
    public static final String BASIC_TEXTURE = "assets/advancedrocketrycommunity/textures/item/basic_circuit_plate.png";
    public static final String ADVANCED_TEXTURE = "assets/advancedrocketrycommunity/textures/item/advanced_circuit_plate.png";
    private static final String DATA = "data/advancedrocketrycommunity/recipes/";
    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public V180CircuitPlateData(PackOutput output, boolean client, boolean server) {
        this.output = output;
        this.client = client;
        this.server = server;
    }

    public static Map<String, JsonObject> clientFiles() {
        return Map.of("assets/advancedrocketrycommunity/models/item/basic_circuit_plate.json", json("""
                {"parent":"minecraft:item/generated","textures":{"layer0":"advancedrocketrycommunity:item/basic_circuit_plate"}}
                """), "assets/advancedrocketrycommunity/models/item/advanced_circuit_plate.json", json("""
                {"parent":"minecraft:item/generated","textures":{"layer0":"advancedrocketrycommunity:item/advanced_circuit_plate"}}
                """));
    }

    public static Map<String, JsonObject> serverFiles() {
        return Map.of(DATA + "precision_basic_circuit_plate.json", json("""
                {"type":"advancedrocketrycommunity:precision_assembling","schema_version":1,
                 "inputs":[{"ingredient":{"tag":"forge:ingots/gold"},"count":1},
                  {"ingredient":{"tag":"forge:dusts/redstone"},"count":1},
                  {"ingredient":{"item":"advancedrocketrycommunity:silicon_wafer"},"count":1}],
                 "outputs":[{"item":"advancedrocketrycommunity:basic_circuit_plate","count":1}],
                 "processing_time":900,"energy_per_tick":100}
                """), DATA + "precision_advanced_circuit_plate.json", json("""
                {"type":"advancedrocketrycommunity:precision_assembling","schema_version":1,
                 "inputs":[{"ingredient":{"tag":"forge:ingots/gold"},"count":1},
                  {"ingredient":{"item":"minecraft:redstone_block"},"count":1},
                  {"ingredient":{"item":"advancedrocketrycommunity:silicon_wafer"},"count":1}],
                 "outputs":[{"item":"advancedrocketrycommunity:advanced_circuit_plate","count":1}],
                 "processing_time":900,"energy_per_tick":100}
                """));
    }

    /** Consumed by the existing sole language writer. */
    public static Map<String, String> language(boolean chinese) {
        return Map.of("item.advancedrocketrycommunity.basic_circuit_plate", chinese ? "基础电路板" : "Basic Circuit Plate",
                "item.advancedrocketrycommunity.advanced_circuit_plate", chinese ? "高级电路板" : "Advanced Circuit Plate");
    }

    /** Basic substrate: offset components, long edge fingers and orthogonal traces. */
    public static String[] basicGrid() {
        return new String[] {
                "................", "..333333333333..", ".35555555555553.", ".35777755577753.",
                ".35722755572253.", ".35721777771253.", ".35722755572253.", ".35777755577753.",
                ".35557555557553.", ".35777777777553.", ".35755555557553.", ".35779999777553.",
                ".35555555555553.", "..336636636633..", "....99.99.99....", "................"
        };
    }

    /** Advanced substrate: clipped corners, dual chips and crossing stepped traces. */
    public static String[] advancedGrid() {
        return new String[] {
                "................", ".....333333.....", "...3355555533...", "..355777777553..",
                ".35772222777553.", ".35772112755753.", ".35772222755753.", ".35577777577753.",
                ".35755777222273.", ".35755757211273.", ".35777777222273.", ".35577557777753.",
                "..355555555553..", "...3396996933...", ".....999999.....", "................"
        };
    }

    public static byte[] basicTexture() { return V180MaterialArt.png(basicGrid(), 0xB8A865); }
    public static byte[] advancedTexture() { return V180MaterialArt.png(advancedGrid(), 0x5B9FAB); }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        if (client) {
            clientFiles().forEach((path, json) -> writes.add(
                    DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path))));
            Map.of(BASIC_TEXTURE, basicTexture(), ADVANCED_TEXTURE, advancedTexture()).forEach((path, png) ->
                    writes.add(CompletableFuture.runAsync(() -> {
                        try { cache.writeIfNeeded(output.getOutputFolder().resolve(path), png, Hashing.sha1().hashBytes(png)); }
                        catch (IOException failed) { throw new UncheckedIOException(failed); }
                    })));
        }
        if (server) {
            serverFiles().forEach((path, json) -> writes.add(
                    DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path))));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "v1.8 precision circuit-plate original resources"; }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
