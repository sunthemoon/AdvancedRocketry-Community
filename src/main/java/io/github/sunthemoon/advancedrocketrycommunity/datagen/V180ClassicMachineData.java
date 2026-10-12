package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicWait;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Registry-independent original blueprints. Legacy layout/numbers are behavioral facts, not imported assets. */
public final class V180ClassicMachineData implements DataProvider {
    private static final String NS = "advancedrocketrycommunity";
    public static final List<String> BLOCKS = List.of("item_input_hatch", "item_output_hatch", "fluid_input_hatch",
            "fluid_output_hatch", "power_input_plug", "lathe");
    private final PackOutput output; private final boolean client, server;
    public V180ClassicMachineData(PackOutput output, boolean client, boolean server) { this.output = output; this.client = client; this.server = server; }
    public static Map<String, JsonObject> clientFiles() {
        var files = new LinkedHashMap<String, JsonObject>();
        for (String block : BLOCKS) {
            var model = object("parent", "minecraft:block/cube_all"); model.add("textures", object("all", NS + ":block/" + block));
            files.put(asset("models/block/" + block), model);
            files.put(asset("models/item/" + block), object("parent", NS + ":block/" + block));
            var variants = new JsonObject();
            if (block.equals("lathe")) {
                String[] directions = {"north", "east", "south", "west"};
                for (int index = 0; index < 4; index++) {
                    var row = object("model", NS + ":block/" + block); row.addProperty("y", index * 90);
                    variants.add("facing=" + directions[index], row);
                }
            } else { variants.add("", object("model", NS + ":block/" + block)); }
            var state = new JsonObject(); state.add("variants", variants); files.put(asset("blockstates/" + block), state);
        }
        return files;
    }
    public static Map<String, JsonObject> serverFiles() {
        var files = new LinkedHashMap<String, JsonObject>();
        for (String block : BLOCKS) {
            var recipe = object("type", "minecraft:crafting_shaped"); recipe.addProperty("category", "misc");
            recipe.add("pattern", strings("PPP", "BCB", "PPP"));
            var key = new JsonObject(); key.add("P", object("tag", "forge:plates/iron"));
            key.add("C", object("item", NS + ":machine_casing"));
            String part = block.startsWith("fluid") ? "minecraft:bucket" : block.startsWith("item") ? "minecraft:hopper"
                    : block.equals("lathe") ? NS + ":motor" : "minecraft:redstone";
            key.add("B", object("item", part)); recipe.add("key", key); recipe.add("result", object("item", NS + ":" + block));
            files.put(data("recipes/" + block), recipe);
            var entry = object("type", "minecraft:item"); entry.addProperty("name", NS + ":" + block);
            var pool = new JsonObject(); pool.addProperty("rolls", 1); var entries = new JsonArray(); entries.add(entry); pool.add("entries", entries);
            var pools = new JsonArray(); pools.add(pool); var loot = object("type", "minecraft:block"); loot.add("pools", pools);
            files.put(data("loot_tables/blocks/" + block), loot);
            files.put(data("advancements/recipes/misc/" + block), advancement(block));
        }
        files.put(data("machine_patterns/lathe"), lathePattern());
        for (var material : MaterialCatalog.Material.values()) {
            if (!material.has(MaterialCatalog.Product.ROD)) { continue; }
            files.put(data("recipes/lathe_" + material.id() + "_rod"), rodRecipe(material.id()));
        }
        return files;
    }
    public static JsonObject rodRecipe(String material) {
        var root = object("type", NS + ":lathe"); root.addProperty("schema_version", 1);
        var input = new JsonObject(); input.add("ingredient", object("tag", "forge:ingots/" + material)); input.addProperty("count", 1);
        var inputs = new JsonArray(); inputs.add(input); root.add("item_inputs", inputs);
        var output = object("item", NS + ":" + material + "_rod"); output.addProperty("count", 2);
        var outputs = new JsonArray(); outputs.add(output); root.add("item_outputs", outputs);
        root.add("fluid_inputs", new JsonArray()); root.add("fluid_outputs", new JsonArray());
        root.addProperty("processing_time", 300); root.addProperty("energy_per_tick", 20); return root;
    }
    public static JsonObject lathePattern() {
        return JsonParser.parseString("""
                {"schema_version":1,"id":"advancedrocketrycommunity:lathe","size":{"x":4,"y":2,"z":1},
                 "controller_anchor":{"x":0,"y":1,"z":0},"allowed_rotations":[0,90,180,270],"allow_mirror_local_x":false,
                 "cells":[
                  {"x":0,"y":1,"z":0,"matcher":{"controller":true}},
                  {"x":1,"y":1,"z":0,"matcher":{"tag":"advancedrocketrycommunity:motors"}},
                  {"x":2,"y":1,"z":0,"matcher":{"air":true}},
                  {"x":3,"y":1,"z":0,"matcher":{"port":"item_input"}},
                  {"x":0,"y":0,"z":0,"matcher":{"port":"energy_input"}},
                  {"x":1,"y":0,"z":0,"matcher":{"block":"advancedrocketrycommunity:machine_casing"}},
                  {"x":2,"y":0,"z":0,"matcher":{"block":"advancedrocketrycommunity:machine_casing"}},
                  {"x":3,"y":0,"z":0,"matcher":{"port":"item_output"}}]}
                """).getAsJsonObject();
    }
    private static JsonObject advancement(String block) {
        var conditions = new JsonObject(); var ingredient = object("tag", "forge:plates/iron");
        var ingredients = new JsonArray(); ingredients.add(ingredient); conditions.add("items", ingredients);
        var criterion = object("trigger", "minecraft:inventory_changed"); criterion.add("conditions", conditions);
        var unlocked = object("trigger", "minecraft:recipe_unlocked"); unlocked.add("conditions", object("recipe", NS + ":" + block));
        var criteria = new JsonObject(); criteria.add("has_plate", criterion); criteria.add("has_recipe", unlocked);
        var any = strings("has_plate", "has_recipe"); var requirements = new JsonArray(); requirements.add(any);
        var rewards = new JsonObject(); rewards.add("recipes", strings(NS + ":" + block));
        var root = object("parent", "minecraft:recipes/root"); root.add("criteria", criteria); root.add("requirements", requirements); root.add("rewards", rewards);
        return root;
    }
    public static Map<String, String> language(boolean chinese) {
        var result = new LinkedHashMap<String, String>();
        String[] en = {"Item Input Hatch", "Item Output Hatch", "Fluid Input Hatch", "Fluid Output Hatch", "Power Input Plug", "Lathe"};
        String[] zh = {"物品输入舱口", "物品输出舱口", "流体输入舱口", "流体输出舱口", "电力输入插头", "车床"};
        for (int i = 0; i < BLOCKS.size(); i++) { result.put("block." + NS + "." + BLOCKS.get(i), chinese ? zh[i] : en[i]); }
        result.put("screen." + NS + ".classic.formed", chinese ? "结构完整：%s" : "Formed: %s");
        result.put("screen." + NS + ".classic.progress", chinese ? "进度：%s / %s tick" : "Progress: %s / %s ticks");
        result.put("screen." + NS + ".classic.energy", chinese ? "电量：%s FE" : "Energy: %s FE");
        String[] english = {"Structure incomplete", "Checking recipes", "Waiting for inputs", "Output full", "Waiting for power",
                "Paused by redstone", "Waiting for loaded parts", "Hatch binding conflict", "Recipe missing", "Recipe changed",
                "Recipe tags unavailable", "Data needs administrator repair", "Resource or catalog limit", "Running", "Disabled by configuration"};
        String[] han = {"结构不完整", "正在检查配方", "等待输入", "输出已满", "等待电力", "红石暂停", "等待部件区块加载", "舱口绑定冲突",
                "配方缺失", "配方已改变", "配方标签不可用", "数据需要管理员修复", "资源或配方数量超限", "加工中", "配置已禁用"};
        for (var reason : ClassicWait.values()) { result.put(reason.key(), chinese ? han[reason.ordinal()] : english[reason.ordinal()]); }
        return result;
    }
    private static JsonObject object(String key, String value) { var object = new JsonObject(); object.addProperty(key, value); return object; }
    private static JsonArray strings(String... values) { var array = new JsonArray(); for (String value : values) { array.add(value); } return array; }
    private static String asset(String path) { return "assets/" + NS + "/" + path + ".json"; }
    private static String data(String path) { return "data/" + NS + "/" + path + ".json"; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        if (client) { clientFiles().forEach((path, json) -> writes.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path)))); }
        if (server) { serverFiles().forEach((path, json) -> writes.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path)))); }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 classic hatches and lathe blueprints"; }
}
