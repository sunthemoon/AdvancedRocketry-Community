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

/** Original blueprints; the pinned classic layouts and production numbers are behavioral facts. */
public final class V180ArcCuttingData implements DataProvider {
    public static final String NS = "advancedrocketrycommunity";
    public static final List<String> BLOCKS = List.of("arc_furnace", "cutting_machine", "blast_brick", "saw_blade");
    public static final Map<String, String> WOODS = woods();
    public static final String CLEARANCE_TAG = NS + ":machine_clearance";
    /** Air plus the non-solid decoration without a block entity that legacy null cells ignored; data packs extend it. */
    public static final List<String> CLEARANCE = List.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air",
            "minecraft:snow", "minecraft:torch", "minecraft:wall_torch", "minecraft:soul_torch", "minecraft:soul_wall_torch",
            "minecraft:redstone_torch", "minecraft:redstone_wall_torch", "minecraft:redstone_wire", "#minecraft:rails",
            "#minecraft:wool_carpets", "minecraft:moss_carpet", "minecraft:vine", "minecraft:glow_lichen", "minecraft:ladder",
            "minecraft:lever", "#minecraft:buttons", "#minecraft:pressure_plates", "minecraft:tripwire", "minecraft:tripwire_hook");
    private final PackOutput output;
    private final boolean client, server;
    public V180ArcCuttingData(PackOutput output, boolean client, boolean server) {
        this.output = output; this.client = client; this.server = server;
    }
    private static Map<String, String> woods() {
        var woods = new LinkedHashMap<String, String>();
        for (String wood : List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry")) {
            woods.put("minecraft:" + wood + "_log", "minecraft:" + wood + "_planks");
        }
        for (String wood : List.of("crimson", "warped")) { woods.put("minecraft:" + wood + "_stem", "minecraft:" + wood + "_planks"); }
        woods.put(NS + ":lightwood_log", NS + ":lightwood_planks");
        return java.util.Collections.unmodifiableMap(woods);
    }
    public static Map<String, JsonObject> clientFiles() {
        var files = new LinkedHashMap<String, JsonObject>();
        for (String block : BLOCKS) {
            boolean controller = block.equals("arc_furnace") || block.equals("cutting_machine");
            var model = object("parent", controller ? "minecraft:block/orientable" : "minecraft:block/cube_all");
            var textures = new JsonObject();
            if (controller) {
                String shell = block.equals("arc_furnace") ? "blast_brick" : "machine_casing";
                textures.addProperty("front", NS + ":block/" + block);
                // The machine casing ships separate side and top textures; blast brick has one texture.
                for (String face : List.of("side", "top")) {
                    textures.addProperty(face, NS + ":block/" + shell + (shell.equals("machine_casing") ? "_" + face : ""));
                }
            } else { textures.addProperty("all", NS + ":block/" + block); }
            model.add("textures", textures); files.put(asset("models/block/" + block), model);
            files.put(asset("models/item/" + block), object("parent", NS + ":block/" + block));
            var variants = new JsonObject();
            if (controller) {
                String[] facings = {"north", "east", "south", "west"};
                for (int i = 0; i < 4; i++) { var row = object("model", NS + ":block/" + block); row.addProperty("y", i * 90); variants.add("facing=" + facings[i], row); }
            } else { variants.add("", object("model", NS + ":block/" + block)); }
            var state = new JsonObject(); state.add("variants", variants); files.put(asset("blockstates/" + block), state);
        }
        var saw = object("parent", "minecraft:item/generated"); saw.add("textures", object("layer0", NS + ":item/iron_saw_blade"));
        files.put(asset("models/item/iron_saw_blade"), saw);
        return files;
    }
    public static Map<String, JsonObject> serverFiles() {
        var files = new LinkedHashMap<String, JsonObject>();
        files.put(data("machine_patterns/arc_furnace"), arcPattern());
        files.put(data("machine_patterns/cutting_machine"), cuttingPattern());
        var shell = new JsonObject(); shell.addProperty("replace", false);
        shell.add("values", strings(NS + ":blast_brick", NS + ":machine_casing", NS + ":item_input_hatch", NS + ":item_output_hatch",
                NS + ":fluid_input_hatch", NS + ":fluid_output_hatch", NS + ":power_input_plug"));
        files.put(data("tags/blocks/arc_furnace_shell_parts"), shell);
        var clearance = new JsonObject(); clearance.addProperty("replace", false);
        clearance.add("values", strings(CLEARANCE.toArray(String[]::new)));
        files.put(data("tags/blocks/machine_clearance"), clearance);
        files.put(data("recipes/arc_furnace_steel"), process("arc_furnace", 6_000, 1, NS + ":steel_ingot", 1,
                input("tag", "forge:ingots/iron", 1), input("item", "minecraft:charcoal", 1)));
        files.put(data("recipes/arc_furnace_silicon"), process("arc_furnace", 12_000, 1, NS + ":silicon_ingot", 1,
                input("item", "minecraft:sand", 1)));
        // Modern rutile drops a raw item; both that drop and the legacy ore enter the same production route.
        var rutile = input("tag", "forge:ores/rutile", 1); var alternatives = new JsonArray();
        alternatives.add(object("tag", "forge:ores/rutile")); alternatives.add(object("tag", "forge:raw_materials/rutile"));
        rutile.add("ingredient", alternatives);
        files.put(data("recipes/arc_furnace_titanium"), process("arc_furnace", 100, 10, NS + ":titanium_ingot", 1, rutile));
        files.put(data("recipes/arc_furnace_titanium_aluminide"), process("arc_furnace", 9_000, 20, NS + ":titanium_aluminide_ingot", 3,
                input("tag", "forge:ingots/aluminum", 7), input("tag", "forge:ingots/titanium", 3)));
        files.put(data("recipes/arc_furnace_titanium_iridium"), process("arc_furnace", 3_000, 20, NS + ":titanium_iridium_ingot", 2,
                input("tag", "forge:ingots/iridium", 1), input("tag", "forge:ingots/titanium", 1)));
        files.put(data("recipes/cutting_silicon_wafer"), process("cutting", 300, 100, NS + ":silicon_wafer", 4,
                input("tag", NS + ":boules/silicon", 1)));
        for (String tier : List.of("basic", "advanced")) {
            files.put(data("recipes/cutting_" + tier + "_circuit"), process("cutting", 300, 100, NS + ":" + tier + "_circuit", 4,
                    input("item", NS + ":" + tier + "_circuit_plate", 1)));
        }
        WOODS.forEach((log, planks) -> {
            var recipe = process("cutting", 80, 10, planks, 6, input("item", log, 1)); recipe.addProperty("sawmill", true);
            files.put(data("recipes/cutting_" + log.substring(log.indexOf(':') + 1)), recipe);
        });
        files.put(data("recipes/blast_brick"), shapeless("blast_brick", 16, object("item", "minecraft:bricks"), object("item", "minecraft:magma_cream")));
        files.put(data("recipes/iron_saw_blade"), shaped("iron_saw_blade", List.of(" R ", "RPR", " R "), Map.of(
                "R", object("tag", "forge:rods/iron"), "P", object("tag", "forge:plates/iron"))));
        files.put(data("recipes/saw_blade"), shaped("saw_blade", List.of("R R", "PBP", "P P"), Map.of(
                "R", object("tag", "forge:rods/iron"), "P", object("tag", "forge:plates/iron"), "B", object("item", NS + ":iron_saw_blade"))));
        // No steel, motor or circuit dependency precedes the first steel-producing machine.
        files.put(data("recipes/arc_furnace"), shaped("arc_furnace", List.of("BRB", "PCP", "BIB"), Map.of(
                "B", object("item", NS + ":blast_brick"), "R", object("item", "minecraft:redstone"),
                "P", object("tag", "forge:plates/iron"), "C", object("item", NS + ":machine_casing"), "I", object("tag", "forge:ingots/copper"))));
        files.put(data("recipes/cutting_machine"), shaped("cutting_machine", List.of("PRP", "MCM", "PSP"), Map.of(
                "P", object("tag", "forge:plates/steel"), "R", object("item", "minecraft:redstone"), "M", object("item", NS + ":motor"),
                "C", object("item", NS + ":machine_casing"), "S", object("item", NS + ":iron_saw_blade"))));
        for (String block : BLOCKS) {
            var entry = object("type", "minecraft:item"); entry.addProperty("name", NS + ":" + block);
            var pool = new JsonObject(); pool.addProperty("rolls", 1); var entries = new JsonArray(); entries.add(entry); pool.add("entries", entries);
            var condition = object("condition", "minecraft:survives_explosion"); var conditions = new JsonArray(); conditions.add(condition); pool.add("conditions", conditions);
            var pools = new JsonArray(); pools.add(pool); var loot = object("type", "minecraft:block"); loot.add("pools", pools);
            files.put(data("loot_tables/blocks/" + block), loot);
        }
        for (String id : List.of("arc_furnace", "cutting_machine", "blast_brick", "saw_blade", "iron_saw_blade")) {
            var root = object("parent", "minecraft:recipes/root");
            var criterion = object("trigger", "minecraft:inventory_changed"); var conditions = new JsonObject();
            var predicate = new JsonObject(); predicate.add("items", strings("minecraft:iron_ingot"));
            var items = new JsonArray(); items.add(predicate); conditions.add("items", items); criterion.add("conditions", conditions);
            var criteria = new JsonObject(); criteria.add("has_iron", criterion); root.add("criteria", criteria);
            var requirements = new JsonArray(); requirements.add(strings("has_iron")); root.add("requirements", requirements);
            var rewards = new JsonObject(); rewards.add("recipes", strings(NS + ":" + id)); root.add("rewards", rewards);
            files.put(data("advancements/recipes/misc/" + id), root);
        }
        return files;
    }
    public static JsonObject arcPattern() {
        String[][] layers = {
                {".....", ".PBP.", ".BBB.", ".BPB.", "....."},
                {".BBB.", "BKAKB", "BAAAB", "BAKAB", ".BBB."},
                {"BBBBB", "BAAAB", "BAAAB", "BAAAB", "BBBBB"},
                {"B*C*B", "*BBB*", "*BBB*", "*BBB*", "B***B"},
                {"BBBBB", "BBBBB", "BBBBB", "BBBBB", "BBBBB"}};
        return pattern("arc_furnace", 5, 5, 5, 2, 1, 0, layers);
    }
    public static JsonObject cuttingPattern() {
        return pattern("cutting_machine", 3, 1, 2, 1, 0, 0, new String[][]{{"ICO", "MSP"}});
    }
    private static JsonObject pattern(String id, int xSize, int ySize, int zSize, int x, int y, int z, String[][] topDown) {
        var root = object("id", NS + ":" + id); root.addProperty("schema_version", 1);
        root.add("size", position(xSize, ySize, zSize)); root.add("controller_anchor", position(x, y, z));
        var rotations = new JsonArray(); for (int angle : new int[]{0, 90, 180, 270}) { rotations.add(angle); }
        root.add("allowed_rotations", rotations); root.addProperty("allow_mirror_local_x", false);
        var cells = new JsonArray();
        for (int layer = 0; layer < ySize; layer++) {
            for (int row = 0; row < zSize; row++) {
                for (int col = 0; col < xSize; col++) {
                    var cell = position(col, ySize - layer - 1, row);
                    var matcher = switch (topDown[layer][row].charAt(col)) {
                        case 'B' -> object("block", NS + ":blast_brick");
                        case '*' -> object("tag", NS + ":arc_furnace_shell_parts");
                        case 'K' -> object("tag", NS + ":coils");
                        case 'M' -> object("tag", NS + ":motors");
                        case 'S' -> object("block", NS + ":saw_blade");
                        case 'I' -> object("port", "item_input"); case 'O' -> object("port", "item_output");
                        case 'P' -> object("port", "energy_input");
                        // ADR-016's closed matcher set has no ignored cell: legacy null cells ('.') take the
                        // clearance tag, so snow, torches or redstone wire on the rim do not unform it. The optional
                        // wrapper also keeps the earlier air matcher's acceptance of any block that reports isAir()
                        // (W01G review L2). Other mods' cables usually have a block entity and still unform it, as do
                        // solid blocks; legacy air stays air.
                        case 'C' -> flag("controller"); case 'A' -> flag("air"); case '.' -> clearance();
                        default -> throw new IllegalArgumentException("Unknown layout cell");
                    };
                    cell.add("matcher", matcher); cells.add(cell);
                }
            }
        }
        root.add("cells", cells); return root;
    }
    private static JsonObject process(String type, int ticks, int energy, String result, int count, JsonObject... inputs) {
        var root = object("type", NS + ":" + type); root.addProperty("schema_version", 1);
        var in = new JsonArray(); for (var row : inputs) { in.add(row); } root.add("item_inputs", in);
        var out = object("item", result); out.addProperty("count", count); var outputs = new JsonArray(); outputs.add(out); root.add("item_outputs", outputs);
        root.add("fluid_inputs", new JsonArray()); root.add("fluid_outputs", new JsonArray());
        root.addProperty("processing_time", ticks); root.addProperty("energy_per_tick", energy); return root;
    }
    private static JsonObject input(String key, String id, int count) { var row = new JsonObject(); row.add("ingredient", object(key, id)); row.addProperty("count", count); return row; }
    private static JsonObject shaped(String id, List<String> pattern, Map<String, JsonObject> key) {
        var root = object("type", "minecraft:crafting_shaped"); root.addProperty("category", "misc"); root.add("pattern", strings(pattern.toArray(String[]::new)));
        var keys = new JsonObject(); key.forEach(keys::add); root.add("key", keys); root.add("result", object("item", NS + ":" + id)); return root;
    }
    private static JsonObject shapeless(String id, int count, JsonObject... ingredients) {
        var root = object("type", "minecraft:crafting_shapeless"); var in = new JsonArray(); for (var ingredient : ingredients) { in.add(ingredient); }
        root.add("ingredients", in); var result = object("item", NS + ":" + id); result.addProperty("count", count); root.add("result", result); return root;
    }
    public static Map<String, String> language(boolean chinese) {
        String[] en = {"Electric Arc Furnace", "Cutting Machine", "Blast Brick", "Saw Blade"};
        String[] zh = {"电弧炉", "切割机", "高炉砖", "锯片组件"};
        var result = new LinkedHashMap<String, String>();
        for (int i = 0; i < BLOCKS.size(); i++) { result.put("block." + NS + "." + BLOCKS.get(i), chinese ? zh[i] : en[i]); }
        result.put("item." + NS + ".iron_saw_blade", chinese ? "铁锯片" : "Iron Saw Blade"); return result;
    }
    private static JsonObject position(int x, int y, int z) { var root = new JsonObject(); root.addProperty("x", x); root.addProperty("y", y); root.addProperty("z", z); return root; }
    private static JsonObject flag(String key) { var root = new JsonObject(); root.addProperty(key, true); return root; }
    static JsonObject clearance() { var root = new JsonObject(); root.add("optional", object("tag", CLEARANCE_TAG)); return root; }
    private static JsonObject object(String key, String value) { var root = new JsonObject(); root.addProperty(key, value); return root; }
    private static JsonArray strings(String... values) { var root = new JsonArray(); for (String value : values) { root.add(value); } return root; }
    private static String asset(String path) { return "assets/" + NS + "/" + path + ".json"; }
    private static String data(String path) { return "data/" + NS + "/" + path + ".json"; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        if (client) { clientFiles().forEach((path, json) -> writes.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path)))); }
        if (server) { serverFiles().forEach((path, json) -> writes.add(DataProvider.saveStable(cache, json, output.getOutputFolder().resolve(path)))); }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 arc furnace and cutting blueprints"; }
}
