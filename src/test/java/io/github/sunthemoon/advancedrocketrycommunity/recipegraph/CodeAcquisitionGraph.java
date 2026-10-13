package io.github.sunthemoon.advancedrocketrycommunity.recipegraph;

import static io.github.sunthemoon.advancedrocketrycommunity.recipegraph.PackagedRecipeGraph.ENERGY;
import static io.github.sunthemoon.advancedrocketrycommunity.recipegraph.PackagedRecipeGraph.OVERWORLD;
import static io.github.sunthemoon.advancedrocketrycommunity.recipegraph.PackagedRecipeGraph.ROCKET;
import static io.github.sunthemoon.advancedrocketrycommunity.recipegraph.PackagedRecipeGraph.id;
import static io.github.sunthemoon.advancedrocketrycommunity.recipegraph.PackagedRecipeGraph.read;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Non-recipe acquisition witnesses. These are existential routes with repeatable research/FE, default enabled
 * world generation, loaded unprotected sources and enough inventory space, not simulations of time or yields.
 * Every producer names the adapter/domain code it mirrors; items and fluids are never promoted to roots.
 */
final class CodeAcquisitionGraph {
    private static final String RESEARCH = "research:lifetime";
    private final PackagedRecipeGraph graph;
    private final Map<String, JsonObject> bodies = new TreeMap<>();

    private CodeAcquisitionGraph(PackagedRecipeGraph graph) { this.graph = graph; }

    static void populate(PackagedRecipeGraph graph) throws IOException {
        CodeAcquisitionGraph models = new CodeAcquisitionGraph(graph);
        models.discovery();
        models.travel();
        models.gasHarvest();
        models.containers();
        models.worldgen();
    }

    private void producer(String name, String citation, List<List<String>> slots, List<String> outputs) {
        String key = graph.produce("code:" + name, slots, outputs);
        graph.codeSources.put(key, citation);
    }

    private void discovery() throws IOException {
        for (Path file : files("celestial_bodies")) {
            JsonObject body = read(file);
            // CelestialDefinitionCodec's pre-schema defaults, via CelestialCapabilities.legacy.
            if (!body.has("discovery_required")) { body.addProperty("discovery_required", false); }
            if (!body.has("capabilities")) {
                JsonObject capabilities = new JsonObject();
                capabilities.addProperty("landable", !body.get("level").getAsString().equals(id("space")));
                capabilities.addProperty("orbitable", true);
                capabilities.addProperty("gas_giant", false);
                body.add("capabilities", capabilities);
            }
            bodies.put(body.get("id").getAsString(), body);
            if (!body.get("discovery_required").getAsBoolean()) {
                producer("known:" + body.get("id").getAsString(), "celestial/service/PlanetaryDiscoveryPolicy.allows",
                        List.of(List.of(OVERWORLD)), List.of(known(body.get("id").getAsString())));
            }
        }
        producer("data_satellite", "satellite/terminal/SatelliteTerminalBlockEntity.assemble/chargeFromRedstone",
                List.of(List.of(id("satellite_terminal")), List.of(id("satellite_chassis")),
                        List.of(id("satellite_solar_module")), List.of(id("data_storage_unit")),
                        List.of(id("satellite_control_chip")), List.of("minecraft:redstone")),
                List.of(id("data_satellite_package")));
        Path data = resource(id("data_satellite"), "satellite_definitions");
        if (Files.isRegularFile(data)) {
            JsonObject definition = read(data);
            if (definition.get("research_yield").getAsInt() > 0) {
                producer("research", "satellite/service/SatelliteManager.launch/startMission/claimCurrent; progression/ResearchAccount",
                        List.of(List.of(id("data_satellite_package")), List.of(id("satellite_terminal")),
                                List.of("minecraft:redstone")), List.of(RESEARCH));
            }
            for (JsonElement target : definition.getAsJsonArray("allowed_targets")) {
                String body = target.getAsString();
                if (bodies.containsKey(body)) {
                    producer("discover:" + body, "satellite/service/SatelliteManager.claimCurrent/applyDiscovery; "
                                    + "satellite/service/DiscoveryClaimRecovery.recover",
                            List.of(List.of(id("data_satellite_package")), List.of(id("satellite_terminal")),
                                    List.of(RESEARCH)), List.of(known(body)));
                }
            }
        }
    }

    private void travel() throws IOException {
        // Typed surface/orbit targets, not the old three-value RocketDestination enum. In particular earth_mars
        // is used by RocketTargetFlightPlanner.plan/resolve through RouteCatalog.plan and the packaged body Level.
        for (Path file : files("travel_routes")) {
            JsonObject route = read(file);
            route(route.get("id").getAsString(), route.getAsJsonObject("from"), route.getAsJsonObject("to"));
            if (route.get("bidirectional").getAsBoolean()) {
                route(route.get("id").getAsString() + ":reverse", route.getAsJsonObject("to"),
                        route.getAsJsonObject("from"));
            }
        }
        // Stations can warp between known orbitable bodies, including Tau Ceti f. A rocket then takes the
        // packaged f_surface_orbit route down to the alien-forest Level; interstellar rocket edges are not invented.
        for (Map.Entry<String, JsonObject> entry : bodies.entrySet()) {
            String body = entry.getKey();
            if (entry.getValue().getAsJsonObject("capabilities").get("orbitable").getAsBoolean()
                    && !body.equals(id("space"))) {
                producer("warp:" + body, "station/warp/StationWarpService.quote/confirm; "
                                + "celestial/service/StarSystemKnowledge.bodyKnown; station/controller/WarpControllerMenu",
                        List.of(List.of("access:space"), List.of(id("station_deployment_kit")),
                                List.of(id("warp_core")), List.of(id("warp_controller")),
                                List.of(ENERGY), List.of(known(body))),
                        List.of(orbit(body)));
            }
        }
    }

    private void route(String name, JsonObject from, JsonObject to) {
        String body = to.get("body_id").getAsString();
        JsonObject definition = bodies.get(body);
        if (definition == null || !canArrive(to, definition)) { return; }
        String source = anchor(from);
        String destination = anchor(to);
        producer("flight:" + name, "rocket/flight/RocketTargetFlightPlanner.plan/resolve; "
                        + "travel/route/service/RouteCatalog.plan; travel_routes/" + name,
                List.of(List.of(source), List.of(ROCKET), List.of(known(body))), List.of(destination));
    }

    private static boolean canArrive(JsonObject anchor, JsonObject body) {
        return switch (anchor.get("type").getAsString()) {
            case "advancedrocketrycommunity:body_surface" -> body.has("level")
                    && body.getAsJsonObject("capabilities").get("landable").getAsBoolean();
            case "advancedrocketrycommunity:orbit" -> body.getAsJsonObject("capabilities").get("orbitable").getAsBoolean();
            default -> throw new IllegalStateException("Unmodelled travel anchor " + anchor);
        };
    }

    private String anchor(JsonObject anchor) {
        String body = anchor.get("body_id").getAsString();
        if (anchor.get("type").getAsString().equals("advancedrocketrycommunity:orbit")) { return orbit(body); }
        if (!anchor.get("type").getAsString().equals("advancedrocketrycommunity:body_surface")) {
            throw new IllegalStateException("Unmodelled travel anchor " + anchor);
        }
        JsonObject definition = bodies.get(body);
        if (definition == null || !definition.has("level")) {
            throw new IllegalStateException("Surface route without Level " + body);
        }
        String level = definition.get("level").getAsString();
        return level.equals("minecraft:overworld") ? OVERWORLD : "access:" + level.substring(level.indexOf(':') + 1);
    }

    private void gasHarvest() throws IOException {
        Path definitionFile = resource(id("gas_harvester"), "satellite_definitions");
        if (!Files.isRegularFile(definitionFile)) { return; }
        JsonObject definition = read(definitionFile);
        List<String> targets = definition.getAsJsonArray("launch_targets").asList().stream()
                .map(JsonElement::getAsString).toList();
        producer("gas_harvester", "satellite/builder/SatelliteBuilderBlockEntity.assemble/evaluate; "
                        + "satellite/component/SatelliteBlueprints.evaluate (power and cargo required)",
                List.of(List.of(id("satellite_builder")), List.of(id("satellite_chassis")),
                        List.of(definition.get("primary_component").getAsString()), List.of(id("satellite_solar_module")),
                        List.of(id("satellite_cargo_hold")), List.of(id("satellite_control_chip")),
                        List.of("minecraft:redstone"), List.of(RESEARCH)),
                List.of("craft:" + id("gas_harvester"), id("satellite_package")));
        for (Path file : files("gas_harvest")) {
            JsonObject table = read(file);
            String body = table.get("body").getAsString();
            JsonObject celestial = bodies.get(body);
            if (celestial == null || !targets.contains(body)
                    || !celestial.getAsJsonObject("capabilities").get("gas_giant").getAsBoolean()
                    || !celestial.getAsJsonObject("capabilities").get("orbitable").getAsBoolean()) { continue; }
            List<String> products = new ArrayList<>();
            for (JsonElement element : table.getAsJsonArray("products")) {
                JsonObject product = element.getAsJsonObject();
                if (product.get("amount_per_1000_ticks").getAsInt() > 0) {
                    products.add(product.get("item").getAsString());
                }
            }
            if (!products.isEmpty()) {
                producer("gas_harvest:" + file.getFileName(), "satellite/service/SatelliteKindLifecycle.launchIdle; "
                                + "ResourceMissionService.startGas/claimResource; satellite/resource/ResourceAlgorithms.gas; "
                                + "gas_harvest/" + file.getFileName(),
                        List.of(List.of("craft:" + id("gas_harvester")), List.of(known(body)),
                                List.of(id("satellite_terminal"))), products);
            }
        }
    }

    private void containers() {
        for (String gas : List.of("hydrogen", "oxygen", "nitrogen")) {
            producer("drain:" + gas, "fluid/GasCanisterCatalog.registered; GasCanisterHandler.drain; "
                            + "machine/tank/TankContainerExchange.plan (detached count-one 1000 mB unit)",
                    List.of(List.of(id(gas + "_canister")), List.of(id("pressurized_tank"))),
                    List.of("fluid:" + id(gas), id("empty_canister")));
            producer("fill:" + gas, "fluid/GasCanisterHandler.fill; machine/tank/TankContainerExchange.plan",
                    List.of(List.of("fluid:" + id(gas)), List.of(id("empty_canister")),
                            List.of(id("pressurized_tank"))), List.of(id(gas + "_canister")));
        }
        for (String liquid : List.of("rocket_fuel", "enriched_lava")) {
            producer("bucket:" + liquid, "fluid/ClassicFluids bucket registrations; "
                            + "machine/tank/TankContainerExchange.plan (Forge whole-bucket transfer)",
                    List.of(List.of("fluid:" + id(liquid)), List.of("minecraft:bucket"),
                            List.of(id("pressurized_tank"))), List.of(id(liquid + "_bucket")));
        }
    }

    private void worldgen() throws IOException {
        producer("silk_touch", "Minecraft EnchantmentMenu.getEnchantmentList: repeatable vanilla Silk Touch "
                        + "enchanting; vanilla tools/XP/enchanting are survival assumptions, not guaranteed rolls",
                List.of(List.of("minecraft:enchanting_table"), List.of("minecraft:bookshelf"),
                        List.of("minecraft:lapis_lazuli"), List.of("minecraft:diamond_pickaxe")),
                List.of("tool:silk_touch"));
        for (Path dimension : files("dimension")) {
            String level = "access:" + dimension.getFileName().toString().replace(".json", "");
            JsonObject dimensionJson = read(dimension);
            JsonElement settings = dimensionJson.getAsJsonObject("generator").get("settings");
            if (settings != null && settings.isJsonPrimitive()) {
                Path noise = resource(settings.getAsString(), "worldgen/noise_settings");
                if (Files.isRegularFile(noise)) {
                    TreeSet<String> blocks = new TreeSet<>();
                    collect(read(noise).get("surface_rule"), "Name", blocks);
                    for (String block : blocks) {
                        blockDrops("terrain:" + level + "|" + block, level, block,
                                "celestial/surface data: packaged dimension noise_settings/surface_rule; "
                                        + "Minecraft NoiseBasedChunkGenerator.buildSurface; loot_tables/blocks/" + block);
                    }
                }
            }
            TreeSet<String> biomes = new TreeSet<>();
            collect(dimensionJson, "biome", biomes);
            for (String biome : biomes) {
                Path biomeFile = resource(biome, "worldgen/biome");
                if (!Files.isRegularFile(biomeFile)) { continue; }
                TreeSet<String> features = new TreeSet<>();
                collectFeatures(read(biomeFile).getAsJsonArray("features"), features);
                for (String placed : features) {
                    Path placedFile = resource(placed, "worldgen/placed_feature");
                    if (!Files.isRegularFile(placedFile)) { continue; }
                    JsonElement configured = read(placedFile).get("feature");
                    if (!configured.isJsonPrimitive()) { continue; }
                    Path configuredFile = resource(configured.getAsString(), "worldgen/configured_feature");
                    if (!Files.isRegularFile(configuredFile)) { continue; }
                    JsonObject feature = read(configuredFile);
                    String type = feature.get("type").getAsString();
                    if (type.equals(id("lightwood_tree"))) {
                        producer("lightwood:" + level, "celestial/exoplanet/worldgen/LightwoodTreeFeature.place; "
                                        + "worldgen/biome/" + biome + "; loot_tables/blocks/lightwood_log",
                                List.of(List.of(level)), List.of(id("lightwood_log"), "block:" + id("lightwood_leaves")));
                    } else if (type.equals(id("charred_tree"))) {
                        blockDrops("charred_tree:" + level, level, id("charcoal_log"),
                                "celestial/surface/worldgen/CharredTreeFeature.place; loot_tables/blocks/charcoal_log");
                    } else if (type.equals(id("crystal_cluster"))) {
                        for (String colour : List.of("blue", "green", "orange", "red", "violet", "yellow")) {
                            blockDrops("crystal:" + level + "|" + colour, level, id(colour + "_crystal_block"),
                                    "celestial/exoplanet/worldgen/CrystalClusterFeature.place; "
                                            + "ExoplanetShapes.crystalCluster (all six colours); ExoplanetBlocks.CRYSTALS");
                        }
                    } else if (type.equals("minecraft:random_patch")) {
                        // The packaged electric_mushrooms patch uses a nested minecraft:simple_block state provider.
                        TreeSet<String> blocks = new TreeSet<>();
                        collect(feature, "Name", blocks);
                        for (String block : blocks) {
                            blockDrops("patch:" + level + "|" + placed + "|" + block, level, block,
                                    "datagen/V180ExoplanetWorldgen: random_patch/simple_block; "
                                            + "celestial/exoplanet/ElectricMushroomBlock; loot_tables/blocks/" + block);
                        }
                    }
                }
            }
            Path volcano = resource(id("volcano"), "worldgen/structure");
            if (Files.isRegularFile(volcano) && level.equals("access:venus")) {
                if (structureInBiomes("volcano", biomes)) {
                    producer("volcano", "celestial/surface/worldgen/VolcanoStructure.findGenerationPoint; "
                                    + "VolcanoPiece.postProcess (new Venus core/crater pools)",
                            List.of(List.of(level)), List.of("source:" + id("enriched_lava")));
                }
            }
            if (structureInBiomes("geode", biomes)) {
                blockDrops("geode:" + level, level, id("geode_shell"),
                        "celestial/surface/worldgen/GeodeStructure.findGenerationPoint; GeodePiece.postProcess");
            }
        }
        Path leavesLoot = resource(id("lightwood_leaves"), "loot_tables/blocks");
        TreeSet<String> leavesDrops = new TreeSet<>();
        if (Files.isRegularFile(leavesLoot)) { collectLoot(read(leavesLoot), leavesDrops); }
        if (leavesDrops.contains(id("lightwood_leaves"))) {
            producer("lightwood_leaves", "loot_tables/blocks/lightwood_leaves: shears or Silk Touch; "
                            + "celestial/exoplanet/worldgen/LightwoodTreeFeature.place",
                    List.of(List.of("block:" + id("lightwood_leaves")), List.of("minecraft:shears")),
                    List.of(id("lightwood_leaves")));
        }
        if (leavesDrops.contains(id("lightwood_sapling"))) {
            producer("lightwood_sapling", "loot_tables/blocks/lightwood_leaves: positive random_chance "
                            + "without shears/Silk Touch (existential, not guaranteed per leaf)",
                    List.of(List.of("block:" + id("lightwood_leaves"))), List.of(id("lightwood_sapling")));
        }
        producer("pump:enriched_lava", "machine/pump/PumpSources.fluid; PumpBlockEntity.serverTick "
                        + "(loaded source, protection and 100 FE checks)",
                List.of(List.of("source:" + id("enriched_lava")), List.of(id("pump")), List.of(ENERGY)),
                List.of("fluid:" + id("enriched_lava")));
    }

    private boolean structureInBiomes(String name, TreeSet<String> biomes) throws IOException {
        Path file = resource(id(name), "worldgen/structure");
        if (!Files.isRegularFile(file) || !read(file).get("type").getAsString().equals(id(name))) { return false; }
        boolean placed = false;
        for (Path set : files("worldgen/structure_set")) {
            for (JsonElement entry : read(set).getAsJsonArray("structures")) {
                JsonObject structure = entry.getAsJsonObject();
                if (structure.get("structure").getAsString().equals(id(name))
                        && structure.get("weight").getAsInt() > 0) { placed = true; }
            }
        }
        String tag = read(file).get("biomes").getAsString();
        if (!placed || !tag.startsWith("#")) { return false; }
        Path tagFile = resource(tag.substring(1), "tags/worldgen/biome");
        if (!Files.isRegularFile(tagFile)) { return false; }
        return read(tagFile).getAsJsonArray("values").asList().stream()
                .map(JsonElement::getAsString).anyMatch(biomes::contains);
    }

    private void blockDrops(String name, String level, String block, String citation) {
        if (!block.startsWith("advancedrocketrycommunity:")) { return; }
        Path loot = resource(block, "loot_tables/blocks");
        if (!Files.isRegularFile(loot)) { return; }
        TreeSet<String> drops = new TreeSet<>();
        collectLoot(read(loot), drops);
        if (!drops.isEmpty()) {
            List<List<String>> slots = new ArrayList<>(List.of(List.of(level), List.of("minecraft:diamond_pickaxe")));
            if (block.equals(id("charcoal_log"))) {
                // This item is not the ordinary charcoal drop: its packaged loot branch requires Silk Touch.
                slots.add(List.of("tool:silk_touch"));
            }
            producer(name, citation + "; packaged block loot (positive chance branches are existential)",
                    slots, List.copyOf(drops));
        }
    }

    private static void collectLoot(JsonElement value, TreeSet<String> output) {
        if (value == null) { return; }
        if (value.isJsonArray()) { value.getAsJsonArray().forEach(child -> collectLoot(child, output)); }
        else if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("rolls") && object.get("rolls").isJsonPrimitive()
                    && object.get("rolls").getAsDouble() <= 0) { return; }
            if (object.has("conditions")) {
                for (JsonElement condition : object.getAsJsonArray("conditions")) {
                    JsonObject predicate = condition.getAsJsonObject();
                    if (predicate.get("condition").getAsString().equals("minecraft:random_chance")
                            && predicate.get("chance").getAsDouble() <= 0) { return; }
                }
            }
            if (object.has("type") && object.get("type").getAsString().equals("minecraft:item") && object.has("name")) {
                output.add(object.get("name").getAsString());
            }
            object.entrySet().forEach(entry -> collectLoot(entry.getValue(), output));
        }
    }

    private static void collect(JsonElement value, String key, TreeSet<String> output) {
        if (value == null) { return; }
        if (value.isJsonArray()) { value.getAsJsonArray().forEach(child -> collect(child, key, output)); }
        else if (value.isJsonObject()) {
            value.getAsJsonObject().entrySet().forEach(entry -> {
                if (entry.getKey().equals(key) && entry.getValue().isJsonPrimitive()) {
                    output.add(entry.getValue().getAsString());
                } else if (entry.getKey().equals(key + "s") && entry.getValue().isJsonArray()) {
                    entry.getValue().getAsJsonArray().forEach(member -> {
                        if (member.isJsonPrimitive()) { output.add(member.getAsString()); }
                        else { collect(member, key, output); }
                    });
                } else { collect(entry.getValue(), key, output); }
            });
        }
    }

    private static void collectFeatures(JsonElement value, TreeSet<String> output) {
        if (value.isJsonArray()) { value.getAsJsonArray().forEach(child -> collectFeatures(child, output)); }
        else if (value.isJsonPrimitive()) { output.add(value.getAsString()); }
    }

    private List<Path> files(String folder) throws IOException {
        Path directory = graph.root.resolve("data/advancedrocketrycommunity/" + folder);
        if (!Files.isDirectory(directory)) { return List.of(); }
        try (var files = Files.walk(directory)) {
            return files.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }
    }

    private Path resource(String name, String folder) {
        int colon = name.indexOf(':');
        return graph.root.resolve("data/" + name.substring(0, colon) + "/" + folder + "/"
                + name.substring(colon + 1) + ".json");
    }

    private static String known(String body) { return "known:" + body; }
    private static String orbit(String body) { return "orbit:" + body; }
}
