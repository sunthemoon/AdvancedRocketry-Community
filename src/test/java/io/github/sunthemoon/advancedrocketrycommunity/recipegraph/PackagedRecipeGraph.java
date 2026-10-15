package io.github.sunthemoon.advancedrocketrycommunity.recipegraph;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.recipegraph.RecipeGraphReachability.Producer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Test-side ADR-066 §6.3 graph over the packaged resources (the merged {@code build/resources/main}, after the
 * build's superseded-file exclusions), for the evaluator in {@link RecipeGraphReachability}.
 *
 * <p>Node names: an item is its ID, an item tag {@code #ns:path}, a fluid {@code fluid:ns:path}, a formed machine
 * {@code machine:ns:path}, an enchantment {@code enchantment:ns:path}, plus {@code energy:fe},
 * {@code access:<level>} and {@code travel:rocket}. Survival
 * assumptions are explicit roots: the Overworld, vanilla items, water and lava, and vanilla tags that no packaged
 * file defines. Items of other mods are ignored. Unknown recipe types, ingredient forms and pattern matchers fail,
 * so a changed data shape cannot be skipped or misread.
 */
final class PackagedRecipeGraph {
    static final String NAMESPACE = "advancedrocketrycommunity";
    static final String OVERWORLD = "access:overworld";
    static final String ENERGY = "energy:fe";
    static final String ROCKET = "travel:rocket";
    /** ADR-063 revision 6: disable surface access, not discovery or the system's orbitable bodies. */
    static final Set<String> TAU_CETI_LEVELS = Set.of("access:tau_ceti_f", "access:tau_ceti_g");
    static final Set<String> TAU_CETI_ITEMS = Set.of(id("lightwood_log"), id("lightwood_leaves"),
            id("lightwood_sapling"), id("lightwood_planks"), id("electric_mushroom"),
            id("blue_crystal_block"), id("green_crystal_block"), id("orange_crystal_block"),
            id("red_crystal_block"), id("violet_crystal_block"), id("yellow_crystal_block"));
    /** ADR-066 §5.1: the chemical reactor's special recipes are the only source of this enchantment. */
    static final String SPACE_BREATHING = "enchantment:" + NAMESPACE + ":space_breathing";
    /** TASK-W19-ROOT-DECISION-01 (3c08ab5d): compatibility inputs, never vanilla/native roots. */
    static final Set<String> OPTIONAL_CROSS_MOD_INPUTS = Set.of(
            id("silicon_dust"), id("steel_dust"), id("titanium_dust"),
            id("titanium_aluminide_dust"), id("titanium_iridium_dust"),
            "#forge:dusts/silicon", "#forge:dusts/steel", "#forge:dusts/titanium",
            "#forge:dusts/titanium_aluminide", "#forge:dusts/titanium_iridium");
    /** Vanilla items that survival play cannot obtain; no recipe may use them as an input. */
    static final Set<String> NON_SURVIVAL = Set.of("minecraft:bedrock", "minecraft:barrier", "minecraft:command_block",
            "minecraft:chain_command_block", "minecraft:repeating_command_block", "minecraft:structure_block",
            "minecraft:structure_void", "minecraft:jigsaw", "minecraft:light", "minecraft:debug_stick",
            "minecraft:knowledge_book", "minecraft:spawner", "minecraft:budding_amethyst",
            "minecraft:reinforced_deepslate", "minecraft:end_portal_frame", "minecraft:petrified_oak_slab");
    /** Classic multiblocks that form through the shared lifecycle coordinator. */
    static final List<String> CLASSIC_MACHINES = List.of("lathe", "arc_furnace", "cutting_machine", "rolling_machine",
            "precision_assembler", "crystallizer", "chemical_reactor", "precision_laser_etcher", "centrifuge");
    private static final Map<String, String> HATCHES = Map.of("item_input", "item_input_hatch",
            "item_output", "item_output_hatch", "energy_input", "power_input_plug",
            "fluid_input", "fluid_input_hatch", "fluid_output", "fluid_output_hatch");

    final Path root;
    final Set<String> nodes = new TreeSet<>();
    final Set<String> roots = new TreeSet<>();
    final Map<String, Producer> producers = new TreeMap<>();
    /** Runtime source citations for non-recipe producers, included in the report. */
    final Map<String, String> codeSources = new TreeMap<>();
    final Set<String> nonSurvivalInputs = new TreeSet<>();
    /** Empty for a successfully loaded graph; unknown types reject loading instead of skipping recipes. */
    final Set<String> unmodelledTypes = new TreeSet<>();
    private final Map<String, Set<String>> oreDrops = new TreeMap<>();
    private int sequence;

    private PackagedRecipeGraph(Path root) {
        this.root = root;
    }

    static PackagedRecipeGraph load() throws IOException {
        return load(resourcesRoot());
    }

    static PackagedRecipeGraph load(Path root) throws IOException {
        PackagedRecipeGraph graph = new PackagedRecipeGraph(root);
        graph.add(OVERWORLD);
        graph.roots.add(OVERWORLD);
        graph.root("fluid:minecraft:water");
        graph.root("fluid:minecraft:lava");
        graph.recipes();
        graph.machines();
        graph.ores();
        graph.produce("energy", List.of(List.of(id("combustion_generator"), id("solar_generator"))), List.of(ENERGY));
        // A Moon or Space flight: assembler, engine, propulsion tank, seat, guidance and loaded fuel (ADR-033/057).
        graph.produce("rocket", List.of(List.of(id("rocket_assembler")),
                List.of(id("rocket_motor"), id("advanced_rocket_motor"), id("bipropellant_rocket_motor"),
                        id("advanced_bipropellant_rocket_motor"), id("nuclear_rocket_motor")),
                List.of(id("rocket_fuel_tank"), id("bipropellant_fuel_tank"), id("nuclear_fuel_tank")),
                List.of(id("rocket_seat")), List.of(id("guidance_computer")),
                List.of(id("fuel_loader")), List.of(id("rocket_fuel_cell"))), List.of(ROCKET));
        graph.produce("flight", List.of(List.of(ROCKET)), List.of("access:moon", "access:space"));
        CodeAcquisitionGraph.populate(graph);
        return graph;
    }

    static String id(String path) {
        return NAMESPACE + ":" + path;
    }

    /**
     * Adds a producer; members and outputs become nodes. A kind that already names one source (a recipe, machine
     * or ore) is the producer ID, any other kind gets a sequence number.
     */
    String produce(String kind, List<List<String>> slots, List<String> outputs) {
        // Taken first: adding a tag member below can add that tag's producers recursively.
        String producer = kind.contains(":") ? kind : kind + ":" + sequence++;
        if (producers.containsKey(producer)) {
            throw new IllegalStateException("Duplicate producer " + producer);
        }
        List<List<String>> ownedSlots = new ArrayList<>();
        Set<List<String>> seen = new LinkedHashSet<>();
        for (List<String> slot : slots) {
            List<String> members = List.copyOf(new LinkedHashSet<>(slot));
            if (members.isEmpty()) {
                throw new IllegalStateException(kind + " has an empty slot");
            }
            if (seen.add(members)) {
                members.forEach(this::add);
                ownedSlots.add(members);
            }
        }
        List<String> ownedOutputs = List.copyOf(new LinkedHashSet<>(outputs));
        ownedOutputs.forEach(this::add);
        producers.put(producer, new Producer(producer, ownedSlots, ownedOutputs));
        return producer;
    }

    /** Adds a node with the usual root and tag rules (for goals that no producer references). */
    void add(String node) {
        if (!nodes.add(node)) {
            return;
        }
        if (node.startsWith("minecraft:")) {
            roots.add(node);
            if (NON_SURVIVAL.contains(node)) {
                nonSurvivalInputs.add(node);
            }
        } else if (node.startsWith("#")) {
            resolveTag(node.substring(1));
        }
    }

    private void root(String node) {
        add(node);
        roots.add(node);
    }

    // Recipes ----------------------------------------------------------------------------------------------

    private void recipes() throws IOException {
        for (Path file : jsonFiles("recipes")) {
            String recipe = recipeId(file);
            JsonObject json = read(file);
            String type = json.get("type").getAsString();
            List<List<String>> slots = new ArrayList<>();
            List<String> outputs = new ArrayList<>();
            switch (type) {
                case "minecraft:crafting_shaped" -> {
                    String pattern = String.join("", json.getAsJsonArray("pattern").asList().stream()
                            .map(JsonElement::getAsString).toList());
                    JsonObject key = json.getAsJsonObject("key");
                    pattern.chars().filter(c -> c != ' ').distinct()
                            .forEach(c -> slots.add(ingredient(key.get(String.valueOf((char) c)), recipe)));
                    outputs.add(result(json.get("result")));
                }
                case "minecraft:crafting_shapeless" -> {
                    json.getAsJsonArray("ingredients").forEach(element -> slots.add(ingredient(element, recipe)));
                    outputs.add(result(json.get("result")));
                }
                case "minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking" -> {
                    slots.add(ingredient(json.get("ingredient"), recipe));
                    outputs.add(result(json.get("result")));
                }
                case "advancedrocketrycommunity:small_plate_press" -> {
                    // ADR-063 section 3: the press works a block that rests on obsidian, on a redstone pulse.
                    slots.add(List.of(id("small_plate_press")));
                    slots.add(List.of("minecraft:obsidian"));
                    slots.add(List.of("minecraft:lever", "minecraft:redstone_block", "minecraft:redstone_torch"));
                    slots.add(ingredient(json.get("ingredient"), recipe));
                    outputs.add(result(json.get("result")));
                }
                case "advancedrocketrycommunity:electrolyzing" -> {
                    codeSources.put("recipe:" + recipe, "machine/electrolyzer/ElectrolyzerBlockEntity.serverTick; "
                            + "ElectrolyzerRecipeResolver; ElectrolyzerRecipe (canister outputs and water/FE inputs)");
                    slots.add(List.of(id("electrolyzer")));
                    machineInputs(json, slots);
                    slots.add(ingredient(json.get("ingredient"), recipe));
                    outputs.add(result(json.get("hydrogen_result")));
                    outputs.add(result(json.get("oxygen_result")));
                }
                case "advancedrocketrycommunity:rolling" -> {
                    slots.add(List.of("machine:" + id("rolling_machine")));
                    machineInputs(json, slots);
                    slots.add(ingredient(json.get("ingredient"), recipe));
                    outputs.add(result(json.get("result")));
                }
                case "advancedrocketrycommunity:precision_assembling" -> {
                    slots.add(List.of("machine:" + id("precision_assembler")));
                    machineInputs(json, slots);
                    json.getAsJsonArray("inputs").forEach(input ->
                            slots.add(ingredient(input.getAsJsonObject().get("ingredient"), recipe)));
                    json.getAsJsonArray("outputs").forEach(output -> outputs.add(result(output)));
                }
                case "advancedrocketrycommunity:lathe", "advancedrocketrycommunity:arc_furnace",
                        "advancedrocketrycommunity:cutting", "advancedrocketrycommunity:crystallizing",
                        "advancedrocketrycommunity:chemical_reacting", "advancedrocketrycommunity:laser_etching",
                        "advancedrocketrycommunity:centrifuging" -> {
                    String machine = switch (type) {
                        case "advancedrocketrycommunity:cutting" -> "cutting_machine";
                        case "advancedrocketrycommunity:crystallizing" -> "crystallizer";
                        case "advancedrocketrycommunity:chemical_reacting" -> "chemical_reactor";
                        case "advancedrocketrycommunity:laser_etching" -> "precision_laser_etcher";
                        case "advancedrocketrycommunity:centrifuging" -> "centrifuge";
                        default -> type.substring(type.indexOf(':') + 1);
                    };
                    slots.add(List.of("machine:" + id(machine)));
                    machineInputs(json, slots);
                    json.getAsJsonArray("item_inputs").forEach(input ->
                            slots.add(ingredient(input.getAsJsonObject().get("ingredient"), recipe)));
                    json.getAsJsonArray("fluid_inputs").forEach(input -> slots.add(fluid(input, recipe)));
                    json.getAsJsonArray("item_outputs").forEach(output -> outputs.add(result(output)));
                    json.getAsJsonArray("fluid_outputs").forEach(output -> outputs.addAll(fluid(output, recipe)));
                    if (json.has("chance_outputs")) {
                        // Reachability is existential, not a yield guarantee or a simulation of the four-success cap.
                        json.getAsJsonArray("chance_outputs").forEach(output -> outputs.add(result(output)));
                    }
                    if (json.has("special")) {
                        // W32: item row 0 is the subject; like the etcher lens it is a requirement that returns,
                        // changed, in the same batch, so the graph never treats it as a new Item.
                        String special = json.get("special").getAsString();
                        switch (special) {
                            case "space_breathing_head", "space_breathing_chest", "space_breathing_legs",
                                    "space_breathing_feet" -> outputs.add(SPACE_BREATHING);
                            case "carbon_cartridge_refresh" -> { }
                            default -> throw new IllegalStateException("Unmodelled special " + special + " in " + recipe);
                        }
                    }
                    // The etcher lens is an input requirement. This monotone graph never consumes any node;
                    // retaining the lens needs no extra output or renewable lens source.
                }
                default -> {
                    unmodelledTypes.add(type);
                    throw new IllegalStateException("Unmodelled recipe type " + type + " in " + recipe);
                }
            }
            if (slots.isEmpty()) {
                throw new IllegalStateException("Recipe without inputs " + recipe);
            }
            produce("recipe:" + recipe, slots, outputs);
        }
    }

    /** Energy (when the recipe uses any) and a single {@code fluid} input of the older machine schemas. */
    private void machineInputs(JsonObject json, List<List<String>> slots) {
        if (json.has("energy_per_tick") && json.get("energy_per_tick").getAsInt() > 0) {
            slots.add(List.of(ENERGY));
        }
        if (json.has("fluid")) {
            slots.add(fluid(json.get("fluid"), "fluid"));
        }
    }

    private List<String> fluid(JsonElement element, String context) {
        JsonObject json = element.getAsJsonObject();
        if (json.has("fluid")) {
            return List.of("fluid:" + json.get("fluid").getAsString());
        }
        throw new IllegalStateException("Unmodelled fluid stack in " + context + ": " + json);
    }

    /** One OR slot: an item, a tag or an array of alternatives. */
    private List<String> ingredient(JsonElement element, String context) {
        List<String> members = new ArrayList<>();
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(alternative -> members.addAll(ingredient(alternative, context)));
        } else {
            JsonObject json = element.getAsJsonObject();
            if (json.has("item") && json.size() == 1) {
                members.add(json.get("item").getAsString());
            } else if (json.has("tag") && json.size() == 1) {
                members.add("#" + json.get("tag").getAsString());
            } else {
                throw new IllegalStateException("Unmodelled ingredient in " + context + ": " + json);
            }
        }
        return members;
    }

    private static String result(JsonElement element) {
        return element.isJsonPrimitive() ? element.getAsString() : element.getAsJsonObject().get("item").getAsString();
    }

    // Tags -------------------------------------------------------------------------------------------------

    /**
     * Every classpath copy of the item tag (this mod, Forge); members of other mods are not survival inputs. A
     * vanilla tag that no packaged file defines holds vanilla items only, so it is a root.
     */
    private void resolveTag(String tag) {
        Set<String> members = tagMembers(tag, "items");
        if (members.isEmpty() && tag.startsWith("minecraft:")) {
            roots.add("#" + tag);
            return;
        }
        for (String member : members) {
            produce("tag", List.of(List.of(member)), List.of("#" + tag));
        }
    }

    static Set<String> tagMembers(String tag, String registry) {
        int colon = tag.indexOf(':');
        String resource = "data/" + tag.substring(0, colon) + "/tags/" + registry + "/" + tag.substring(colon + 1) + ".json";
        Set<String> members = new TreeSet<>();
        try {
            for (URL url : Collections.list(PackagedRecipeGraph.class.getClassLoader().getResources(resource))) {
                try (InputStream input = url.openStream()) {
                    JsonObject json = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                            .getAsJsonObject();
                    for (JsonElement value : json.getAsJsonArray("values")) {
                        String member = value.isJsonPrimitive() ? value.getAsString()
                                : value.getAsJsonObject().get("id").getAsString();
                        String namespace = member.replace("#", "");
                        namespace = namespace.substring(0, namespace.indexOf(':'));
                        if (namespace.equals("minecraft") || namespace.equals(NAMESPACE) || namespace.equals("forge")) {
                            members.add(member);
                        }
                    }
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return members;
    }

    // Machines ---------------------------------------------------------------------------------------------

    /** A formed machine needs every distinct structural cell of its packaged pattern. */
    private void machines() throws IOException {
        for (String machine : CLASSIC_MACHINES) {
            Path file = root.resolve("data/" + NAMESPACE + "/machine_patterns/" + machine + ".json");
            JsonObject pattern = read(file);
            List<List<String>> slots = new ArrayList<>();
            for (JsonElement cell : pattern.getAsJsonArray("cells")) {
                JsonObject matcher = cell.getAsJsonObject().getAsJsonObject("matcher");
                if (matcher.has("controller")) {
                    slots.add(List.of(pattern.get("id").getAsString()));
                } else if (matcher.has("block")) {
                    slots.add(List.of(matcher.get("block").getAsString()));
                } else if (matcher.has("port")) {
                    String channel = matcher.get("port").getAsString();
                    String port = machine.equals("rolling_machine") || machine.equals("precision_assembler")
                            ? machine + "_" + channel + "_port" : HATCHES.get(channel);
                    if (port == null) {
                        throw new IllegalStateException("Unmodelled port " + channel + " in " + machine);
                    }
                    slots.add(List.of(id(port)));
                } else if (matcher.has("tag")) {
                    Set<String> blocks = blockTagItems(matcher.get("tag").getAsString(), new TreeSet<>());
                    if (!blocks.contains("minecraft:air")) {
                        slots.add(List.copyOf(blocks));
                    }
                } else if (matcher.has("optional")) {
                    // An optional cell also matches air, so a formed machine needs no block there (as above, for a
                    // tag that contains air).
                } else if (!matcher.has("air")) {
                    throw new IllegalStateException("Unmodelled pattern matcher in " + machine + ": " + matcher);
                }
            }
            produce("machine:" + machine, slots, List.of("machine:" + pattern.get("id").getAsString()));
        }
    }

    /**
     * Block tag members as their block items (this mod and vanilla); nested tags are expanded. A vanilla block
     * tag that no packaged file defines becomes the root node {@code #block:<tag>}.
     */
    private Set<String> blockTagItems(String tag, Set<String> visiting) {
        Set<String> items = new TreeSet<>();
        if (!visiting.add(tag)) {
            return items;
        }
        Set<String> members = tagMembers(tag, "blocks");
        if (members.isEmpty() && tag.startsWith("minecraft:")) {
            root("#block:" + tag);
            return Set.of("#block:" + tag);
        }
        for (String member : members) {
            if (member.startsWith("#")) {
                items.addAll(blockTagItems(member.substring(1), visiting));
            } else if (!NON_SURVIVAL.contains(member)) {
                // Block tags are OR selectors: unobtainable members cannot witness survival formation.
                items.add(member);
            }
        }
        if (items.isEmpty()) {
            throw new IllegalStateException("Unresolvable block tag " + tag);
        }
        return items;
    }

    // World generation -------------------------------------------------------------------------------------

    /** Ore drops per Level: Overworld biome modifiers, and each packaged planet dimension's biome features. */
    private void ores() throws IOException {
        Path modifiers = root.resolve("data/" + NAMESPACE + "/forge/biome_modifier");
        if (Files.isDirectory(modifiers)) {
            try (Stream<Path> files = Files.list(modifiers)) {
                for (Path file : files.sorted().toList()) {
                    JsonObject json = read(file);
                    if (json.get("type").getAsString().equals("forge:add_features")
                            && json.get("biomes").getAsString().equals("#minecraft:is_overworld")) {
                        strings(json.get("features")).forEach(feature -> placedFeature(feature, OVERWORLD));
                    }
                }
            }
        }
        Path dimensions = root.resolve("data/" + NAMESPACE + "/dimension");
        try (Stream<Path> files = Files.list(dimensions)) {
            for (Path file : files.sorted().toList()) {
                String level = "access:" + file.getFileName().toString().replace(".json", "");
                Set<String> biomes = new TreeSet<>();
                collect(read(file), "biome", biomes);
                for (String biome : biomes) {
                    Path biomeFile = resource(biome, "worldgen/biome");
                    if (Files.isRegularFile(biomeFile)) {
                        for (JsonElement step : read(biomeFile).getAsJsonArray("features")) {
                            strings(step).forEach(feature -> placedFeature(feature, level));
                        }
                    }
                }
            }
        }
        oreDrops.forEach((key, drops) -> produce("ore:" + key, List.of(List.of(key.substring(0, key.indexOf('|')))),
                List.copyOf(drops)));
    }

    private void placedFeature(String placed, String level) {
        Path file = resource(placed, "worldgen/placed_feature");
        if (!Files.isRegularFile(file)) {
            return;
        }
        JsonElement feature = read(file).get("feature");
        if (!feature.isJsonPrimitive()) {
            return;
        }
        Path configured = resource(feature.getAsString(), "worldgen/configured_feature");
        if (!Files.isRegularFile(configured)) {
            return;
        }
        JsonObject json = read(configured);
        if (!json.get("type").getAsString().equals("minecraft:ore")) {
            return;
        }
        for (JsonElement target : json.getAsJsonObject("config").getAsJsonArray("targets")) {
            String block = target.getAsJsonObject().getAsJsonObject("state").get("Name").getAsString();
            Set<String> drops = new TreeSet<>();
            Path loot = resource(block, "loot_tables/blocks");
            if (Files.isRegularFile(loot)) {
                collectLoot(read(loot), drops);
            }
            if (!drops.isEmpty()) {
                oreDrops.computeIfAbsent(level + "|" + block, key -> new TreeSet<>()).addAll(drops);
            }
        }
    }

    private static void collectLoot(JsonElement element, Set<String> drops) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectLoot(child, drops));
        } else if (element.isJsonObject()) {
            JsonObject json = element.getAsJsonObject();
            if (json.has("type") && json.get("type").getAsString().equals("minecraft:item") && json.has("name")) {
                drops.add(json.get("name").getAsString());
            }
            json.entrySet().forEach(entry -> collectLoot(entry.getValue(), drops));
        }
    }

    private static void collect(JsonElement element, String key, Set<String> values) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collect(child, key, values));
        } else if (element.isJsonObject()) {
            element.getAsJsonObject().entrySet().forEach(entry -> {
                if (entry.getKey().equals(key) && entry.getValue().isJsonPrimitive()) {
                    values.add(entry.getValue().getAsString());
                } else {
                    collect(entry.getValue(), key, values);
                }
            });
        }
    }

    private static List<String> strings(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return List.of(element.getAsString());
        }
        List<String> values = new ArrayList<>();
        element.getAsJsonArray().forEach(value -> values.add(value.getAsString()));
        return values;
    }

    // Files ------------------------------------------------------------------------------------------------

    private Path resource(String id, String folder) {
        int colon = id.indexOf(':');
        return root.resolve("data/" + id.substring(0, colon) + "/" + folder + "/" + id.substring(colon + 1) + ".json");
    }

    private List<Path> jsonFiles(String folder) throws IOException {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> namespaces = Files.list(root.resolve("data"))) {
            for (Path namespace : namespaces.sorted().toList()) {
                Path directory = namespace.resolve(folder);
                if (Files.isDirectory(directory)) {
                    try (Stream<Path> files = Files.walk(directory)) {
                        files.filter(file -> file.toString().endsWith(".json")).sorted().forEach(result::add);
                    }
                }
            }
        }
        return result;
    }

    private String recipeId(Path file) {
        Path relative = root.resolve("data").relativize(file);
        String namespace = relative.getName(0).toString();
        String path = relative.subpath(2, relative.getNameCount()).toString().replace('\\', '/');
        return namespace + ":" + path.substring(0, path.length() - ".json".length());
    }

    static JsonObject read(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** The packaged resource root that holds the generated classic root advancement. */
    static Path resourcesRoot() {
        URL url = PackagedRecipeGraph.class.getClassLoader()
                .getResource("data/" + NAMESPACE + "/advancements/classic/root.json");
        if (url == null || !url.getProtocol().equals("file")) {
            throw new IllegalStateException("Packaged resources are not an exploded directory: " + url);
        }
        try {
            return Path.of(url.toURI()).getParent().getParent().getParent().getParent().getParent();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** A copy for the evaluator. */
    List<Producer> producerList() {
        return List.copyOf(producers.values());
    }

    /** Remove forbidden inputs/outputs from producers; callers also remove these nodes from their roots. */
    List<Producer> withoutInputs(Set<String> forbidden) {
        List<Producer> result = new ArrayList<>();
        for (Producer producer : producers.values()) {
            List<List<String>> slots = producer.slots().stream()
                    .map(slot -> slot.stream().filter(node -> !forbidden.contains(node)).toList()).toList();
            List<String> outputs = producer.outputs().stream().filter(node -> !forbidden.contains(node)).toList();
            if (!outputs.isEmpty() && slots.stream().noneMatch(List::isEmpty)) {
                result.add(new Producer(producer.id(), slots, outputs));
            }
        }
        return result;
    }
}
