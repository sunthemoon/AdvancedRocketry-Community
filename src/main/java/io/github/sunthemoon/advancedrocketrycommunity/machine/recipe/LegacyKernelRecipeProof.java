package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Shipped v1.7 payload facts for fixtures; they do not prove a saved world's authored JSON lineage. */
public final class LegacyKernelRecipeProof {
    private static final Map<String, String> SHIPPED = Map.of(
            "rolling_iron_bars", """
            {"type":"advancedrocketrycommunity:rolling","schema_version":1,
            "ingredient":{"item":"minecraft:iron_ingot"},"input_count":2,
            "fluid":{"fluid":"minecraft:water","amount":100},
            "result":{"item":"minecraft:iron_bars","count":8},"processing_time":100,"energy_per_tick":20}
            """,
            "precision_control_circuit", """
            {"type":"advancedrocketrycommunity:precision_assembling","schema_version":1,
            "inputs":[{"count":2,"ingredient":{"item":"minecraft:iron_ingot"}},
            {"count":2,"ingredient":{"item":"minecraft:redstone"}}],
            "outputs":[{"count":1,"item":"advancedrocketrycommunity:advanced_circuit"},
            {"count":2,"item":"minecraft:redstone_torch"}],"processing_time":20,"energy_per_tick":40}
            """,
            "precision_guidance_module", """
            {"type":"advancedrocketrycommunity:precision_assembling","schema_version":1,
            "inputs":[{"count":2,"ingredient":{"item":"minecraft:iron_ingot"}},
            {"count":2,"ingredient":{"item":"minecraft:redstone"}},
            {"count":1,"ingredient":{"item":"minecraft:gold_ingot"}},
            {"count":1,"ingredient":{"item":"minecraft:quartz"}},
            {"count":1,"ingredient":{"item":"minecraft:copper_ingot"}}],
            "outputs":[{"count":1,"item":"minecraft:comparator"}],"processing_time":30,"energy_per_tick":50}
            """,
            "electrolyzer_water", """
            {"type":"advancedrocketrycommunity:electrolyzing","schema_version":1,
            "ingredient":{"item":"advancedrocketrycommunity:empty_canister"},"input_count":2,
            "fluid":{"fluid":"minecraft:water","amount":1000},
            "hydrogen_result":{"item":"advancedrocketrycommunity:hydrogen_canister"},
            "oxygen_result":{"item":"advancedrocketrycommunity:oxygen_canister"},
            "processing_time":100,"energy_per_tick":20}
            """
    );

    private LegacyKernelRecipeProof() { }

    /** Facts about the shipped JAR only; no persisted-world provenance is inferred from its ID/hash. */
    public static JsonObject payload(ResourceLocation id, String type) {
        if (id == null || !"advancedrocketrycommunity".equals(id.getNamespace())) { return null; }
        String raw = SHIPPED.get(id.getPath());
        if (raw == null) { return null; }
        JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
        return type.equals(json.get("type").getAsString()) ? json : null;
    }
}
