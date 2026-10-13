package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Original C16c blueprints. Only layout and production facts reference the pinned classic source. */
public final class V180C16cMachineData implements DataProvider {
    public static final String NS = "advancedrocketrycommunity";
    public static final List<String> MACHINES = List.of("crystallizer", "chemical_reactor", "precision_laser_etcher", "centrifuge");
    public static final List<String> BLOCKS = List.of("crystallizer", "chemical_reactor", "precision_laser_etcher", "centrifuge", "quartz_crucible");
    private final PackOutput output;
    private final boolean client, server;
    public V180C16cMachineData(PackOutput output, boolean client, boolean server) {
        this.output = output; this.client = client; this.server = server;
    }
    public static Map<String, JsonObject> clientFiles() {
        var files = new LinkedHashMap<String, JsonObject>();
        for (String block : BLOCKS) {
            boolean controller = MACHINES.contains(block);
            var model = object("parent", controller ? "minecraft:block/orientable" : "minecraft:block/cube_all");
            var textures = new JsonObject();
            if (controller) {
                textures.addProperty("front", NS + ":block/" + block);
                // The machine casing ships separate side and top textures (see V180ArcCuttingData).
                for (String face : List.of("side", "top")) { textures.addProperty(face, NS + ":block/machine_casing_" + face); }
            } else { textures.addProperty("all", NS + ":block/" + block); }
            model.add("textures", textures); files.put(asset("models/block/" + block), model);
            files.put(asset("models/item/" + block), object("parent", NS + ":block/" + block));
            var variants = new JsonObject();
            if (controller) {
                String[] facings = {"north", "east", "south", "west"};
                for (int i = 0; i < 4; i++) {
                    var row = object("model", NS + ":block/" + block); row.addProperty("y", i * 90);
                    variants.add("facing=" + facings[i], row);
                }
            } else { variants.add("", object("model", NS + ":block/" + block)); }
            var state = new JsonObject(); state.add("variants", variants); files.put(asset("blockstates/" + block), state);
        }
        var shell = object("parent", "minecraft:item/generated"); shell.add("textures", object("layer0", NS + ":item/quartz_crucible_shell"));
        files.put(asset("models/item/quartz_crucible_shell"), shell); return files;
    }
    public static Map<String, JsonObject> serverFiles() {
        var files = new LinkedHashMap<String, JsonObject>();
        MACHINES.forEach(id -> files.put(data("machine_patterns/" + id), pattern(id)));
        files.put(data("recipes/crystallizing_dilithium"), process("crystallizing", 300, 20,
                List.of(input("tag", "forge:dusts/dilithium", 1)), List.of(item(NS + ":dilithium_crystal", 1)), List.of(), List.of()));
        files.put(data("recipes/crystallizing_silicon_boule"), process("crystallizing", 300, 20,
                List.of(input("tag", "forge:ingots/silicon", 1), input("tag", "forge:nuggets/silicon", 1)),
                List.of(item(NS + ":silicon_boule", 1)), List.of(), List.of()));
        files.put(data("recipes/chemical_reacting_rocket_fuel"), process("chemical_reacting", 100, 10, List.of(), List.of(),
                List.of(fluid(NS + ":oxygen", 10), fluid(NS + ":hydrogen", 10)), List.of(fluid(NS + ":rocket_fuel", 20))));
        files.put(data("recipes/chemical_reacting_bone_meal"), process("chemical_reacting", 100, 1,
                List.of(input("item", "minecraft:bone", 1)), List.of(item("minecraft:bone_meal", 8)),
                List.of(fluid(NS + ":nitrogen", 10)), List.of()));
        for (String tier : List.of("basic", "advanced")) {
            boolean advanced = tier.equals("advanced");
            files.put(data("recipes/laser_etching_" + tier + "_circuit_plate"), process("laser_etching", 1_200, advanced ? 600 : 400,
                    List.of(input("tag", "forge:plates/gold", 1),
                            input(advanced ? "item" : "tag", advanced ? "minecraft:redstone_block" : "forge:dusts/redstone", 1),
                            input("item", NS + ":silicon_wafer", 4), input("item", NS + ":laser_lens", 1)),
                    List.of(item(NS + ":" + tier + "_circuit_plate", 2)), List.of(), List.of()));
        }
        var centrifuge = process("centrifuging", 200, 10, List.of(), List.of(),
                List.of(fluid(NS + ":enriched_lava", 1_000)), List.of(fluid("minecraft:lava", 1_000)));
        var chances = new JsonArray();
        String[] metals = {"copper", "iron", "tin", "gold", "iridium"}; int[] weights = {100, 100, 100, 75, 1};
        for (int i = 0; i < metals.length; i++) {
            String namespace = metals[i].equals("iron") || metals[i].equals("gold") ? "minecraft" : NS;
            var candidate = item(namespace + ":" + metals[i] + "_nugget", 1);
            candidate.addProperty("weight", weights[i]); chances.add(candidate);
        }
        centrifuge.add("chance_outputs", chances); files.put(data("recipes/centrifuging_enriched_lava"), centrifuge);
        files.put(data("recipes/quartz_crucible_shell"), shaped("quartz_crucible_shell", List.of("Q Q", "Q Q", "QQQ"),
                Map.of("Q", object("item", "minecraft:quartz"))));
        var crucible = object("type", "minecraft:crafting_shapeless");
        crucible.add("ingredients", array(List.of(object("item", NS + ":quartz_crucible_shell"))));
        crucible.add("result", item(NS + ":quartz_crucible", 1)); files.put(data("recipes/quartz_crucible"), crucible);
        for (String id : MACHINES) {
            String part = switch (id) {
                case "crystallizer" -> "quartz_crucible_shell"; case "precision_laser_etcher" -> "laser_lens";
                default -> "motor";
            };
            files.put(data("recipes/" + id), shaped(id, List.of("PRP", "BCB", "PMP"), Map.of(
                    "P", object("tag", "forge:plates/steel"), "R", object("item", "minecraft:redstone"),
                    "B", object("item", NS + ":" + part), "C", object("item", NS + ":machine_casing"),
                    "M", object("item", NS + ":motor"))));
        }
        for (String block : BLOCKS) {
            var entry = object("type", "minecraft:item"); entry.addProperty("name", NS + ":" + block);
            var pool = new JsonObject(); pool.addProperty("rolls", 1); pool.add("entries", array(List.of(entry)));
            pool.add("conditions", array(List.of(object("condition", "minecraft:survives_explosion"))));
            var loot = object("type", "minecraft:block"); loot.add("pools", array(List.of(pool)));
            files.put(data("loot_tables/blocks/" + block), loot);
        }
        var crafts = new ArrayList<>(BLOCKS); crafts.add("quartz_crucible_shell");
        for (String id : crafts) {
            var root = object("parent", "minecraft:recipes/root"); var criterion = object("trigger", "minecraft:inventory_changed");
            var predicate = new JsonObject(); predicate.add("items", strings("minecraft:quartz"));
            var conditions = new JsonObject(); conditions.add("items", array(List.of(predicate))); criterion.add("conditions", conditions);
            var criteria = new JsonObject(); criteria.add("has_quartz", criterion); root.add("criteria", criteria);
            var requirements = new JsonArray(); requirements.add(strings("has_quartz")); root.add("requirements", requirements);
            var rewards = new JsonObject(); rewards.add("recipes", strings(NS + ":" + id)); root.add("rewards", rewards);
            files.put(data("advancements/recipes/misc/" + id), root);
        }
        return files;
    }
    public static JsonObject pattern(String id) {
        String[][] layers = switch (id) {
            case "crystallizer" -> new String[][]{{"QQQ", "QQQ"}, {"OCI", "lPL"}};
            case "chemical_reactor" -> new String[][]{{".C.", "LIL"}, {"PMP", "lOl"}};
            case "precision_laser_etcher" -> new String[][]{{"SSS", "ASA", "SSS"}, {"BAB", "AEB", "BAB"}, {"BCI", "PMO", "PBB"}};
            case "centrifuge" -> new String[][]{{"ABl", "EEB", "EE."}, {"ABl", "EEB", "EEl"},
                    {"CBl", "EEB", "EEl"}, {"PLl", "MOB", "BBl"}};
            default -> throw new IllegalArgumentException("Unknown C16c pattern");
        };
        int x = id.equals("centrifuge") ? 0 : 1, y = id.equals("chemical_reactor") || id.equals("centrifuge") ? 1 : 0;
        var root = object("id", NS + ":" + id); root.addProperty("schema_version", 1);
        root.add("size", position(3, layers.length, layers[0].length)); root.add("controller_anchor", position(x, y, 0));
        var rotations = new JsonArray(); for (int angle : new int[]{0, 90, 180, 270}) { rotations.add(angle); }
        root.add("allowed_rotations", rotations); root.addProperty("allow_mirror_local_x", false);
        var cells = new JsonArray();
        for (int layer = 0; layer < layers.length; layer++) {
            for (int row = 0; row < layers[layer].length; row++) {
                for (int col = 0; col < 3; col++) {
                    var cell = position(col, layers.length - layer - 1, row);
                    var matcher = switch (layers[layer][row].charAt(col)) {
                        case 'Q' -> object("block", NS + ":quartz_crucible"); case 'B' -> object("block", NS + ":machine_casing");
                        case 'E' -> object("block", NS + ":endgame_casing"); case 'S' -> object("tag", "minecraft:slabs");
                        case 'M' -> object("tag", NS + ":motors"); case '.' -> object("tag", V180ArcCuttingData.CLEARANCE_TAG);
                        case 'I' -> object("port", "item_input"); case 'O' -> object("port", "item_output");
                        case 'L' -> object("port", "fluid_input"); case 'l' -> object("port", "fluid_output"); case 'P' -> object("port", "energy_input");
                        case 'C' -> flag("controller"); case 'A' -> flag("air"); default -> throw new IllegalArgumentException();
                    };
                    cell.add("matcher", matcher); cells.add(cell);
                }
            }
        }
        root.add("cells", cells); return root;
    }
    private static JsonObject process(String type, int ticks, int energy, List<JsonObject> inputs, List<JsonObject> outputs,
                                      List<JsonObject> fluidInputs, List<JsonObject> fluidOutputs) {
        var root = object("type", NS + ":" + type); root.addProperty("schema_version", 1);
        root.add("item_inputs", array(inputs)); root.add("item_outputs", array(outputs));
        root.add("fluid_inputs", array(fluidInputs)); root.add("fluid_outputs", array(fluidOutputs));
        root.addProperty("processing_time", ticks); root.addProperty("energy_per_tick", energy); return root;
    }
    private static JsonObject input(String key, String id, int count) {
        var row = new JsonObject(); row.add("ingredient", object(key, id)); row.addProperty("count", count); return row;
    }
    private static JsonObject item(String id, int count) { var row = object("item", id); row.addProperty("count", count); return row; }
    private static JsonObject fluid(String id, int amount) { var row = object("fluid", id); row.addProperty("amount", amount); return row; }
    private static JsonObject shaped(String id, List<String> pattern, Map<String, JsonObject> keys) {
        var root = object("type", "minecraft:crafting_shaped"); root.addProperty("category", "misc");
        root.add("pattern", strings(pattern.toArray(String[]::new))); var key = new JsonObject(); keys.forEach(key::add); root.add("key", key);
        root.add("result", item(NS + ":" + id, 1)); return root;
    }
    public static Map<String, String> language(boolean chinese) {
        String[] en = {"Crystallizer", "Chemical Reactor", "Precision Laser Etcher", "Centrifuge", "Quartz Crucible"};
        String[] zh = {"结晶器", "化学反应器", "精密激光蚀刻机", "离心机", "石英坩埚"};
        var result = new LinkedHashMap<String, String>();
        for (int i = 0; i < BLOCKS.size(); i++) { result.put("block." + NS + "." + BLOCKS.get(i), chinese ? zh[i] : en[i]); }
        result.put("item." + NS + ".quartz_crucible_shell", chinese ? "石英坩埚壳" : "Quartz Crucible Shell");
        result.put("jei." + NS + ".chance", chinese ? "独立概率：%s%%；最多四次成功" : "Independent chance: %s%%; at most four successes");
        result.put("jei." + NS + ".retained", chinese ? "在原输入槽中保留" : "Retained in its input slot");
        result.put("jei." + NS + ".iridium_cap", chinese ? "内置顺序下铱的实际概率：0.25%%" : "Built-in order: effective iridium chance 0.25%%");
        return result;
    }
    private static JsonObject position(int x, int y, int z) { var root = new JsonObject(); root.addProperty("x", x); root.addProperty("y", y); root.addProperty("z", z); return root; }
    private static JsonObject flag(String key) { var root = new JsonObject(); root.addProperty(key, true); return root; }
    private static JsonObject object(String key, String value) { var root = new JsonObject(); root.addProperty(key, value); return root; }
    private static JsonArray array(List<JsonObject> rows) { var array = new JsonArray(); rows.forEach(array::add); return array; }
    private static JsonArray strings(String... values) { var array = new JsonArray(); for (String value : values) { array.add(value); } return array; }
    private static String asset(String path) { return "assets/" + NS + "/" + path + ".json"; }
    private static String data(String path) { return "data/" + NS + "/" + path + ".json"; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        if (client) { clientFiles().forEach((path, json) -> writes.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path)))); }
        if (server) { serverFiles().forEach((path, json) -> writes.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path)))); }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 C16c machine blueprints"; }
}
