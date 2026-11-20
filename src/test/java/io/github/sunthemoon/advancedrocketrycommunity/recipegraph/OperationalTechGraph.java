package io.github.sunthemoon.advancedrocketrycommunity.recipegraph;

import static io.github.sunthemoon.advancedrocketrycommunity.recipegraph.PackagedRecipeGraph.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicInventoryAdvancements.Goal;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicMilestoneAdvancements.Milestone;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ResearchService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** W420 complementary operational model. No operation is inferred merely from its controller item. */
final class OperationalTechGraph {
    static final String MAIN = "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/";
    final PackagedRecipeGraph graph;
    final Set<String> required = new TreeSet<>();
    final Map<String, String> displayParents = new TreeMap<>();
    final Map<String, String> unmodelled = new TreeMap<>();
    final Map<String, JsonObject> bodies = new TreeMap<>();

    private OperationalTechGraph(PackagedRecipeGraph graph) { this.graph = graph; }

    static OperationalTechGraph load() throws IOException {
        var model = new OperationalTechGraph(PackagedRecipeGraph.load());
        for (var file : model.files("celestial_bodies")) {
            var body = read(file); model.bodies.put(body.get("id").getAsString(), body);
        }
        model.ground();
        model.satellites();
        model.devices();
        model.advancements();
        model.limits();
        return model;
    }

    static String cite(String file, String needle) throws IOException {
        List<String> lines = Files.readAllLines(Path.of(MAIN + file + ".java"));
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) { return MAIN + file + ".java:" + (i + 1); }
        }
        throw new IllegalStateException("Missing source citation " + file + ": " + needle);
    }

    void operation(String output, String source, List<List<String>> slots) {
        required.add(output);
        String producer = graph.produce("operational:" + output, slots, List.of(output));
        graph.codeSources.put(producer, source);
    }

    private void op(String output, String source, String... prerequisites) {
        operation(output, source, java.util.Arrays.stream(prerequisites).map(List::of).toList());
    }

    private void ground() throws IOException {
        formed("observatory"); formed("astrobody_data_processor");
        String service = cite("machine/classic/runtime/ResearchService", "private boolean start(");
        String advance = cite("machine/classic/runtime/ResearchService", "boolean advance(");
        String claim = cite("satellite/mission/GroundSurveys", "public Result claim(");
        String paid = cite("satellite/mission/GroundSurveys", "public Result advanceAnalysis(");
        for (String body : bodies.keySet()) {
            var definition = bodies.get(body);
            if (!definition.has("level") || !landable(definition)) { continue; }
            String system = system(body);
            if (candidates(system).isEmpty()) { continue; }
            // A job is a registry data unit, not an obtainable data-storage item.
            String start = "op:ground:start:" + body;
            String ready = "op:ground:data-unit:" + body;
            String analysis = "op:ground:analysis:" + body;
            op(start, service + "; " + data("asteroid_types"), "op:formed:observatory", surface(body));
            op(ready, advance, start, ENERGY);
            for (var type : candidates(system)) {
                op("op:ground:instance:" + body + ":" + type.get("id").getAsString(),
                        cite("satellite/resource/ResourceAlgorithms", "public static List<GeneratedInstance> survey(")
                                + "; positive-weight possible outcome, not every outcome in one job", ready);
            }
            op(analysis, claim + "; " + paid + "; " + advance, ready, "op:formed:astrobody_data_processor", ENERGY);
            op("op:ground:credit:" + body, paid, analysis);
        }
        unmodelled.put("ground", cite("machine/classic/runtime/ResearchService", "ResearchStatus observing(")
                + "; clear lens sky, skyDarken >= 4, no precipitation (surface); station VISIT and matching system (orbit);"
                + " loaded/ticking footprint, paid FE each tick, durable save epochs, unique owned devices, free job/account capacity."
                + " These are temporal/spatial guards, not monotone inventory facts. Ground surface routes above cover each eligible body;"
                + " the optional station bypass of weather is not needed to seed research.");
    }

    private void satellites() throws IOException {
        String terminal = cite("satellite/terminal/SatelliteTerminalBlockEntity", "private void assemble(");
        String builder = cite("satellite/builder/SatelliteBuilderBlockEntity", "Preview evaluate(");
        String blueprint = cite("satellite/component/SatelliteBlueprints", "boolean met =");
        String launch = cite("satellite/service/SatelliteLaunchService", "Validation validate(");
        String mission = cite("satellite/service/SatelliteManager", "public SatelliteOperationResult startMission(");
        String claim = cite("satellite/service/SatelliteManager", "private SatelliteOperationResult claimMission(");
        op("op:data:built", terminal, id("satellite_terminal"), id("satellite_chassis"), id("satellite_solar_module"),
                id("data_storage_unit"), id("satellite_control_chip"), "minecraft:redstone");
        var dataDefinition = read(resource("satellite_definitions", "data_satellite"));
        int yield = dataDefinition.get("research_yield").getAsInt();
        for (String target : strings(dataDefinition.getAsJsonArray("allowed_targets"))) {
            if (!bodies.containsKey(target)) { continue; }
            op("op:data:mission:" + target, launch + "; " + mission, "op:data:built", id("satellite_terminal"), "minecraft:redstone");
            op("op:data:claim:" + target, claim, "op:data:mission:" + target);
        }
        for (var file : files("satellite_definitions")) {
            var definition = read(file);
            if (!definition.has("kind")) { continue; }
            String name = definition.get("id").getAsString();
            String kind = definition.get("kind").getAsString();
            int threshold = definition.get("required_lifetime_research").getAsInt();
            String research = "op:research:lifetime:" + threshold;
            if (!required.contains(research)) {
                if (threshold == 0) { op(research, builder, OVERWORLD); }
                else {
                    // Known Earth missions do not pay discovery cost. Finite repetitions, not infinite free balance.
                    if (yield <= 0 || threshold > SatelliteLimits.MAX_RESEARCH_BALANCE) { throw new IllegalStateException("Research route needs numeric analysis"); }
                    op(research, claim + "; repeat Earth claim " + ((threshold + yield - 1) / yield)
                            + " times at " + yield + " credit; " + cite("progression/ResearchAccount", "public ResearchAccount creditAndSpend("),
                            "op:data:claim:" + id("earth"));
                    String ground = "op:ground:credit:" + id("earth");
                    String key = graph.produce("operational:ground-research:" + threshold, List.of(List.of(ground)), List.of(research));
                    graph.codeSources.put(key, cite("machine/classic/runtime/ResearchService", "public static final int SURVEY_TICKS")
                            + "; repeat fresh paid ground jobs " + ((threshold + ResearchService.CREDIT - 1) / ResearchService.CREDIT)
                            + " times at " + ResearchService.CREDIT + " credit; each job pays "
                            + ResearchService.SURVEY_TICKS + " survey + " + ResearchService.ANALYSIS_TICKS
                            + " analysis ticks at " + ResearchService.ENERGY_PER_TICK + " FE/tick");
                }
            }
            var slots = new ArrayList<List<String>>();
            for (String input : List.of(id("satellite_builder"), id("satellite_chassis"),
                    definition.get("primary_component").getAsString(), id("satellite_control_chip"),
                    id("satellite_solar_module"), "minecraft:redstone", research)) { slots.add(List.of(input)); }
            if (kind.equals("survey")) {
                slots.add(List.of(id("data_storage_unit")));
                // BASE_BATTERY=720 < current scan_energy=1000; use the actual catalog battery module.
                slots.add(List.of(id("satellite_battery")));
            } else if (kind.equals("asteroid_miner") || kind.equals("gas_harvester")) {
                slots.add(List.of(id("satellite_cargo_hold")));
            } else if (!kind.equals("solar")) { throw new IllegalStateException("Unmodelled kind " + kind); }
            validateBlueprint(definition, slots);
            operation("op:built:" + name, builder + "; " + blueprint + "; "
                    + cite("satellite/builder/SatelliteBuilderBlockEntity", "private void chargeFromRedstone(")
                    + "; " + data("satellite_definitions/" + file.getFileName()), slots);
            for (String target : strings(definition.getAsJsonArray("launch_targets"))) {
                String launched = "op:launched:" + name + ":" + target;
                required.add(launched); graph.add(launched);
                if (!bodies.containsKey(target) || !orbitable(bodies.get(target))) { continue; }
                op(launched, launch, "op:built:" + name, research, "known:" + target, id("satellite_terminal"), "minecraft:redstone");
                if (kind.equals("survey")) {
                    if (!candidates(system(target)).isEmpty()) {
                        op("op:survey:instances:" + target, cite("satellite/service/ResourceMissionService", "public SatelliteOperationResult startSurvey(")
                                + "; " + cite("satellite/service/ResourceMissionService", "public SatelliteOperationResult claimSurvey("), launched);
                        for (var type : candidates(system(target))) {
                            op("op:survey:instance:" + target + ":" + type.get("id").getAsString(),
                                    cite("satellite/resource/ResourceAlgorithms", "public static List<GeneratedInstance> survey(")
                                            + "; positive-weight possible outcome, not every outcome in one mission", "op:survey:instances:" + target);
                        }
                    }
                    if (landable(bodies.get(target)) && bodies.get(target).has("level")) {
                        op("op:survey:scan:" + target, cite("satellite/scan/SurveyScanService", "public SatelliteOperationCode request("), launched, surface(target));
                    }
                } else if (kind.equals("asteroid_miner")) {
                    for (var asteroid : candidates(system(target))) {
                        var instanceSources = new ArrayList<String>();
                        for (String other : bodies.keySet()) {
                            if (!system(other).equals(system(target))) { continue; }
                            String ground = "op:ground:instance:" + other + ":" + asteroid.get("id").getAsString();
                            String survey = "op:survey:instance:" + other + ":" + asteroid.get("id").getAsString();
                            if (required.contains(ground)) { instanceSources.add(ground); }
                            // Survey targets are taken from their definition, not producer insertion order.
                            var surveyDefinition = read(resource("satellite_definitions", "survey_satellite"));
                            if (strings(surveyDefinition.getAsJsonArray("launch_targets")).contains(other)) { instanceSources.add(survey); }
                        }
                        operation("op:asteroid:claim:" + target + ":" + asteroid.get("id").getAsString(),
                                cite("satellite/service/ResourceMissionService", "public SatelliteOperationResult startAsteroid(")
                                        + "; " + cite("satellite/service/ResourceMissionService", "public SatelliteOperationResult claimResource("),
                                List.of(List.of(launched), instanceSources, List.of(id("satellite_terminal"))));
                    }
                } else if (kind.equals("gas_harvester")) {
                    for (var table : files("gas_harvest")) {
                        var json = read(table);
                        if (!json.get("body").getAsString().equals(target)) { continue; }
                        for (var product : json.getAsJsonArray("products")) {
                            String item = product.getAsJsonObject().get("item").getAsString();
                            op("op:gas:claim:" + target + ":" + item,
                                    cite("satellite/service/ResourceMissionService", "public SatelliteOperationResult startGas(")
                                            + "; " + cite("satellite/service/ResourceMissionService", "public SatelliteOperationResult claimResource("),
                                    launched, id("satellite_terminal"));
                        }
                    }
                } else {
                    op("op:solar:receive:" + target, cite("satellite/service/SolarLinks", "ReceiverCheck check("), launched, id("microwave_receiver"));
                }
            }
        }
        unmodelled.put("satellites", launch + "; " + claim + "; chip/package identity equality, terminal range/owner, registry health,"
                + " output vacancy, FE charge, durable claim/discovery barriers, deadline passage, free instance/storage capacity,"
                + " scan battery recharge and loaded scan columns, matching receiver link. Reachability does not prove simultaneous or repeatable runtime liveness.");
    }

    private void devices() throws IOException {
        for (String name : List.of("orbital_laser_drill", "black_hole_generator", "railgun", "planetary_beacon")) { formed(name); }
        String station = cite("station/warp/StationWarpService", "private Quoted quote(");
        for (String body : bodies.keySet()) {
            if (!orbitable(bodies.get(body)) || body.equals(id("space"))) { continue; }
            op("op:station:" + body, station, "orbit:" + body, id("station_deployment_kit"));
        }
        for (var file : files("singularities")) {
            var singularity = read(file);
            var fuel = read(resource("black_hole_fuels", singularity.get("fuel_table").getAsString().split(":")[1]));
            var fuels = new ArrayList<String>();
            for (var entry : fuel.getAsJsonArray("entries")) {
                if (entry.getAsJsonObject().get("burn_ticks").getAsInt() > 0) { fuels.add(entry.getAsJsonObject().get("item").getAsString()); }
            }
            operation("op:black-hole:generate:" + singularity.get("body").getAsString(),
                    cite("endgame/blackhole/BlackHoleGeneratorBlockEntity", "private Eligibility eligibility("),
                    List.of(List.of("op:formed:black_hole_generator"), List.of("op:station:" + singularity.get("body").getAsString()), fuels));
        }
        op("op:laser:logical", cite("endgame/laser/LaserDrillOperation", "public static Decision decide("),
                "op:formed:orbital_laser_drill", "op:station:" + id("earth"), id("laser_lens"), ENERGY);
        op("op:laser:physical", cite("endgame/laser/OrbitalLaserDrillBlockEntity", "private void physicalTick("),
                "op:laser:logical", id("laser_target"), "access:overworld");
        op("op:railgun:local", cite("endgame/railgun/RailgunLaunch", "public static EndgameCode refusal("),
                "op:formed:railgun", ENERGY, "minecraft:cobblestone", OVERWORLD);
        op("op:railgun:orbital", cite("endgame/railgun/RailgunRoute", "public static EndgameCode check("),
                "op:railgun:local", "op:station:" + id("earth"));
        op("op:gravity:active", cite("endgame/gravity/GravityFieldBlockEntity", "private EndgameCode check("), id("gravity_field_controller"), ENERGY, OVERWORLD);
        op("op:elevator:bound", cite("endgame/elevator/ElevatorRules", "public static Check bind("),
                "op:station:" + id("earth"), id("elevator_anchor"), id("elevator_terminal"), OVERWORLD);
        op("op:elevator:cargo", cite("endgame/elevator/ElevatorCargo", "static ElevatorRules.Check launch("), "op:elevator:bound", ENERGY, "minecraft:cobblestone");
        op("op:elevator:ride", cite("endgame/elevator/ElevatorRules", "public static Check rideRequest("), "op:elevator:bound", ENERGY);
        op("op:beacon:active", cite("beacon/PlanetaryBeaconBlockEntity", "public BeaconStatus status("), "op:formed:planetary_beacon", OVERWORLD);
        op("op:solar:single", cite("machine/solar/SolarGeneration", "public static Step tick("), id("solar_generator"), OVERWORLD);
        op("op:solar:array", cite("machine/solar/SolarArrayLayout", "public static Evaluation evaluate("),
                id("solar_array"), id("solar_array_panel"), id("power_output_plug"), OVERWORLD);
        unmodelled.put("endgame", cite("endgame/device/EndgameDeviceBlockEntity", "public boolean usable()")
                + "; system enablement, running/redstone state, owner/station OPERATE authority, loaded exact pattern, active admission,"
                + " positive table entry/rate, FE throughput and output space. Station nodes inherit the existing existential orbit/warp"
                + " abstraction, not a demonstrated committed station deployment. Laser physical additionally needs an owned linked"
                + " marker, confirmation, eligible unprotected shaft/target. Railgun needs two distinct owned ACTIVE durable endpoints,"
                + " selected same-system route and free outbox/escrow. These relational/spatial/temporal predicates are not item facts.");
        unmodelled.put("elevator", cite("station/elevator/ElevatorEndpointValidator", "public static Result validate(")
                + "; landable matching loaded Level, station pad column/world border, active unbound endpoints, no warp,"
                + " tether/platform clearance, durable registration, access, passenger state/cooldown and arrival admission are dynamic."
                + " Paired items alone are not a valid elevator.");
        unmodelled.put("solar/beacon", cite("machine/solar/SolarGeneration", "public static Step tick(")
                + "; direct sky exposure, daylight and positive intensity, buffer space; array exact 66-cell layout/outputs,"
                + " enabled valid body context; beacon owner, unique index registration, loaded pattern and finder visibility."
                + " Static availability is conditional; no all-52-device operability claim is made.");
    }

    private void advancements() throws IOException {
        for (Goal goal : Goal.values()) {
            operation("op:tree:" + goal.suffix(), cite("progression/classic/ClassicInventoryAdvancements", "public enum Goal"),
                    goal.items().stream().map(List::of).toList());
            if (goal.parentId() != null) { displayParents.put(goal.id(), goal.parentId()); }
        }
        for (Milestone goal : Milestone.values()) {
            String prerequisite = switch (goal.kind()) {
                case INVENTORY -> goal.target();
                case ARRIVAL -> "access:" + goal.target().split(":")[1];
                case EVENT -> switch (goal.target()) {
                    case "formed_multiblock" -> "machine:" + id("rolling_machine");
                    case "seat_on_tnt" -> "op:tree:seat-tnt";
                    case "moon_landing", "first_moon_landing" -> "access:moon";
                    case "station_warp", "first_station_warp" -> "op:station:" + id("moon");
                    default -> throw new IllegalStateException("Unknown event " + goal.target());
                };
                case PENDING -> throw new IllegalStateException("Pending tree node " + goal.suffix());
            };
            op("op:tree:" + goal.suffix(), cite("progression/classic/ClassicMilestoneAdvancements", "public enum Milestone"), prerequisite);
            displayParents.put(goal.id(), goal.parentId());
        }
        op("op:tree:seat-tnt", cite("progression/classic/ClassicMilestoneAdvancements", "SEAT_ON_TNT("), id("rocket_seat"), "minecraft:tnt");
    }

    private void limits() throws IOException {
        unmodelled.put("research-tree", cite("progression/classic/ClassicMilestoneAdvancements", "Parents control display")
                + "; the code has research accounts, per-kind lifetime thresholds and 17 classic advancement criteria,"
                + " not an additional unlock DAG. Display parents are reported separately and never imposed as gameplay locks."
                + " World-first awards require durable receipts/participation; inventory witnesses do not establish those events.");
        unmodelled.put("base-model", "PackagedRecipeGraph/CodeAcquisitionGraph inherited roots: vanilla survival inputs, water/lava,"
                + " default enabled worldgen, repeatable FE and abstract rocket/orbit/discovery access. Quantities, chance, time,"
                + " gravity, finite world resource depletion and arbitrary datapacks/config combinations are not simulated."
                + " Required reachability is a static necessary dependency check, conditional on these and the per-operation guards."
                + " Exactly the existing ten compatibility dust/tag nodes are optional; no new unavailable node is exempted.");
    }

    private void formed(String name) throws IOException {
        var json = read(resource("machine_patterns", name));
        var slots = new ArrayList<List<String>>(); slots.add(List.of(id(name)));
        for (var cell : json.getAsJsonArray("cells")) {
            var matcher = cell.getAsJsonObject().getAsJsonObject("matcher");
            if (matcher.has("controller") || matcher.has("air") || matcher.has("optional")) { continue; }
            if (matcher.has("block")) { slots.add(List.of(matcher.get("block").getAsString())); }
            else if (matcher.has("port")) {
                if (!matcher.get("port").getAsString().equals("energy_input")) { throw new IllegalStateException("Unknown port " + matcher); }
                slots.add(List.of(id("power_input_plug")));
            } else if (matcher.has("tag")) {
                Set<String> members = blockTag(matcher.get("tag").getAsString(), new TreeSet<>());
                if (!members.contains("minecraft:air")) { slots.add(List.copyOf(members)); }
            } else { throw new IllegalStateException("Unknown matcher " + matcher); }
        }
        operation("op:formed:" + name, cite("machine/multiblock/pattern/MultiblockPatternJsonCodec", "public static MultiblockPatternDefinition decode(")
                + "; " + data("machine_patterns/" + name + ".json"), slots);
    }

    private Set<String> blockTag(String name, Set<String> visiting) throws IOException {
        if (!visiting.add(name)) { throw new IllegalStateException("Cyclic block tag " + name); }
        String[] split = name.split(":");
        Path path = graph.root.resolve("data/" + split[0] + "/tags/blocks/" + split[1] + ".json");
        if (!Files.exists(path) && name.equals("minecraft:slabs")) { visiting.remove(name); return Set.of("minecraft:stone_slab"); }
        var members = new TreeSet<String>();
        var values = read(path).getAsJsonArray("values");
        if (strings(values).contains("minecraft:air")) { visiting.remove(name); return Set.of("minecraft:air"); }
        for (var value : values) {
            String member = value.getAsString();
            if (member.startsWith("#")) { members.addAll(blockTag(member.substring(1), visiting)); }
            else { members.add(member); }
        }
        visiting.remove(name);
        if (members.isEmpty()) { throw new IllegalStateException("Empty block tag " + name); }
        return members;
    }

    private String system(String body) {
        Set<String> seen = new TreeSet<>();
        while (bodies.get(body).has("parent")) {
            if (!seen.add(body)) { throw new IllegalStateException("Body parent cycle " + body); }
            body = bodies.get(body).get("parent").getAsString();
        }
        return body;
    }

    private List<JsonObject> candidates(String system) throws IOException {
        var result = new ArrayList<JsonObject>();
        for (var file : files("asteroid_types")) {
            var json = read(file); var systems = strings(json.getAsJsonArray("systems"));
            if ((systems.isEmpty() || systems.contains(system)) && json.get("weight").getAsInt() > 0) { result.add(json); }
        }
        return result;
    }

    /** Verify the selected existential layout's real numeric guards, rather than assuming modules suffice. */
    private void validateBlueprint(JsonObject definition, List<List<String>> slots) throws IOException {
        var components = new TreeMap<String, JsonObject>();
        for (var file : files("satellite_components")) {
            var json = read(file); components.put(json.get("item").getAsString(), json);
        }
        int power = 0, battery = SatelliteLimits.BASE_BATTERY, data = 0, cargo = 0, modules = 0;
        for (var slot : slots) {
            var component = components.get(slot.get(0));
            if (component == null) { continue; }
            String role = component.get("role").getAsString();
            if (role.equals("primary") || role.equals("chassis")) { continue; }
            modules++;
            power += stat(component, "power_generation"); battery += stat(component, "battery_capacity");
            data += stat(component, "data_capacity"); cargo += stat(component, "cargo_stacks");
        }
        String kind = definition.get("kind").getAsString();
        int rating = stat(components.get(definition.get("primary_component").getAsString()), "primary_rating");
        boolean survey = kind.equals("survey"), resource = kind.equals("asteroid_miner") || kind.equals("gas_harvester");
        if (modules > SatelliteLimits.MAX_MODULES || power < 1 || power > SatelliteLimits.MAX_POWER
                || battery > SatelliteLimits.MAX_BATTERY || data > SatelliteLimits.MAX_DATA || cargo > SatelliteLimits.MAX_CARGO
                || rating > SatelliteLimits.MAX_RATING || survey && (data < 1 || battery < stat(definition, "scan_energy"))
                || resource && cargo < 1) { throw new IllegalStateException("No valid witness blueprint for " + definition.get("id")); }
    }
    private static int stat(JsonObject json, String key) { return json.has(key) ? json.get(key).getAsInt() : 0; }

    private String surface(String body) {
        String level = bodies.get(body).get("level").getAsString();
        return level.equals("minecraft:overworld") ? OVERWORLD : "access:" + level.split(":")[1];
    }
    private static boolean landable(JsonObject body) {
        return body.has("capabilities") ? body.getAsJsonObject("capabilities").get("landable").getAsBoolean()
                : !body.get("id").getAsString().equals(id("space"));
    }
    private static boolean orbitable(JsonObject body) {
        return !body.has("capabilities") || body.getAsJsonObject("capabilities").get("orbitable").getAsBoolean();
    }
    private static List<String> strings(Iterable<JsonElement> array) {
        var values = new ArrayList<String>(); array.forEach(value -> values.add(value.getAsString())); return values;
    }
    private String data(String path) { return "packaged:data/advancedrocketrycommunity/" + path; }
    private Path resource(String folder, String name) { return graph.root.resolve("data/advancedrocketrycommunity/" + folder + "/" + name + ".json"); }
    private List<Path> files(String folder) throws IOException {
        try (var files = Files.walk(graph.root.resolve("data/advancedrocketrycommunity/" + folder))) {
            return files.filter(file -> file.toString().endsWith(".json")).sorted().toList();
        }
    }
}
