package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpContent;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRegistries;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.jodah.typetools.TypeResolver;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegisterEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W62 slice S4 (ADR-061 sections 1.1 and 5.2): the mod's entries in the Forge registries against the packaged
 * resources ({@code build/resources/main}, after the build's superseded-file exclusions). The registrations come from
 * the real central boundary {@link ModRegistries#register} on a recording event bus, so no deferred register can be
 * skipped. Every check runs before any assertion and writes {@code build/reports/registered-content-resources.md}.
 *
 * <p>Packaged files are matched by exact path, as in the JAR, not through the case-insensitive Windows file system.
 * Recipes, loot tables and advancements are walked field by field; a string field this class does not model fails
 * the reference check, so a new data shape cannot be skipped.
 */
class RegisteredContentResourcesTest {
    private static final String NAMESPACE = ModIdentity.MOD_ID;
    private static final String BLOCKS = "minecraft:block";
    private static final String ITEMS = "minecraft:item";
    private static final String FLUIDS = "minecraft:fluid";
    private static final String SOUNDS = "minecraft:sound_event";
    private static final Path REPORT = Path.of("build", "reports", "registered-content-resources.md");
    private static final String EXEMPTION_FILE = "registered-content-exemptions.txt";
    private static final List<String> CHECKS = List.of("unique_ids", "blockstate", "loot_table", "item_model",
            "item_name", "sound", "references", "exemptions");
    /** The checks an exemption line may name. */
    private static final Set<String> EXEMPTIBLE = Set.of("blockstate", "loot_table", "item_model", "item_name");
    /** An {@code item_name_key <ID> <key> <reason>} line names the key of an item whose block or item overrides it. */
    private static final String NAME_KEY = "item_name_key";
    /**
     * Main sources that override {@code getDescriptionId}, pinned: the name check derives each item's key from its
     * ID, so a new override needs an {@code item_name_key} line for every item it names, then a new pin.
     * AdvancedMachineCasingBlock names the {@code endgame_casing} item; UnlitWallTorchBlock has no item.
     */
    private static final Set<String> DESCRIPTION_OVERRIDES = Set.of("AdvancedMachineCasingBlock.java",
            "UnlitWallTorchBlock.java");
    /** Tag folders under {@code data/<namespace>/tags/} and the kind of their plain entries. */
    private static final Map<String, String> TAG_FOLDERS = Map.of("items", "item", "blocks", "block",
            "fluids", "fluid", "entity_types", "entity_type", "worldgen/biome", "worldgen/biome",
            "damage_type", "damage_type");
    /** String fields that name a type, trigger, text, texture, dimension or criterion: counted, not resolved. */
    private static final Set<String> NOT_REFERENCES = Set.of("type", "condition", "function", "trigger", "formula",
            "random_sequence", "special", "pattern", "category", "group", "translate", "frame", "background",
            "requirements", "to", "event");

    /** Registry name, then ID, then the classes whose {@code register(IEventBus)} installed it. */
    private static final Map<String, Map<String, List<String>>> registered = new TreeMap<>();
    private static final Set<String> files = new TreeSet<>();
    private static final Map<String, String> exemptions = new TreeMap<>();
    private static final Set<String> usedExemptions = new TreeSet<>();
    /** Item ID, then the translation key an {@code item_name_key} line names. */
    private static final Map<String, String> nameKeys = new TreeMap<>();
    /** Event type, then the number of other listeners the boundary adds to the mod bus. */
    private static final Map<String, Integer> listeners = new TreeMap<>();
    private static final Map<String, List<String>> problems = new LinkedHashMap<>();
    private static final Map<String, Integer> checked = new LinkedHashMap<>();
    /** Reference kind, then ID, then the number of fields naming it. */
    private static final Map<String, Map<String, Integer>> references = new TreeMap<>();
    private static final Map<String, Integer> unresolvedFields = new TreeMap<>();
    private static int optionalForeignTagEntries;
    private static Path root;

    @BeforeAll
    static void check() throws IOException, ReflectiveOperationException {
        MinecraftBootstrap.initialize();
        Class.forName("net.minecraft.world.item.enchantment.Enchantments");
        Class.forName("net.minecraft.world.entity.EntityType");
        CHECKS.forEach(check -> problems.put(check, new ArrayList<>()));
        record();
        root = packagedRoot();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .forEach(file -> files.add(root.relativize(file).toString().replace('\\', '/')));
        }
        readExemptions();
        uniqueIds();
        blocks();
        items();
        sounds();
        packagedReferences();
        exemptions.keySet().stream().filter(entry -> !usedExemptions.contains(entry))
                .forEach(entry -> problems.get("exemptions").add("`" + entry + "` exempts nothing; remove it"));
        writeReport();
    }

    @Test
    void idsAreUniqueInEachRegistryAndNoTwoIdsDifferOnlyInCase() {
        for (String registry : List.of(BLOCKS, ITEMS, SOUNDS)) {
            assertTrue(registered.containsKey(registry), "No deferred registrations for " + registry);
        }
        assertProblems("unique_ids");
    }

    @Test
    void everyBlockHasABlockstateAndALootTableOrAnExemption() {
        assertProblems("blockstate");
        assertProblems("loot_table");
    }

    @Test
    void everyItemHasAModelAndEnglishAndChineseNames() {
        assertProblems("item_model");
        assertProblems("item_name");
    }

    @Test
    void everySoundEventIsInSoundsJson() {
        assertProblems("sound");
    }

    @Test
    void everyItemFluidAndTagInPackagedRecipesLootTablesAdvancementsAndTagsResolves() {
        for (String kind : List.of("item", "fluid", "tag/items", "tag/blocks")) {
            assertTrue(references.containsKey(kind), "No packaged " + kind + " references were found");
        }
        assertProblems("references");
    }

    @Test
    void everyExemptionNamesACheckAnIdAndAReasonAndIsUsed() {
        assertProblems("exemptions");
    }

    private static void assertProblems(String check) {
        assertEquals(List.of(), problems.get(check), check + " problems; see " + REPORT);
    }

    // Registrations --------------------------------------------------------------------------------------------

    /** Runs the central registration boundary on a bus that records each deferred register it installs. */
    private static void record() throws ReflectiveOperationException {
        IEventBus bus = (IEventBus) Proxy.newProxyInstance(IEventBus.class.getClassLoader(),
                new Class<?>[] {IEventBus.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "register" -> {
                        install(arguments[0]);
                        yield null;
                    }
                    case "addListener" -> {
                        listen(arguments);
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "recording mod bus";
                    default -> throw new UnsupportedOperationException("Unexpected mod-bus call " + method);
                });
        // PumpContent keeps the COMMON enabled supplier it is given; restore it so no later test sees it.
        Field enabled = PumpContent.class.getDeclaredField("enabled");
        enabled.setAccessible(true);
        Object previous = enabled.get(null);
        try {
            ModRegistries.register(bus);
        } finally {
            enabled.set(null, previous);
        }
    }

    private static void install(Object listener) throws IllegalAccessException {
        Field holder = null;
        for (Field field : listener.getClass().getDeclaredFields()) {
            if (field.getType() == DeferredRegister.class) { holder = field; }
        }
        assertNotNull(holder, "The mod bus received a listener that is not a deferred register: " + listener);
        holder.setAccessible(true);
        DeferredRegister<?> register = (DeferredRegister<?>) holder.get(listener);
        String installer = StackWalker.getInstance().walk(frames -> frames.map(StackWalker.StackFrame::getClassName)
                .dropWhile(name -> !name.equals(DeferredRegister.class.getName()))
                .filter(name -> !name.equals(DeferredRegister.class.getName())).findFirst()).orElseThrow();
        Map<String, List<String>> ids = registered.computeIfAbsent(register.getRegistryName().toString(),
                registry -> new TreeMap<>());
        for (var entry : register.getEntries()) {
            ids.computeIfAbsent(entry.getId().toString(), id -> new ArrayList<>())
                    .add(installer.substring(installer.lastIndexOf('.') + 1));
        }
    }

    /** A plain mod-bus listener; one for {@link RegisterEvent} would register entries outside a deferred register. */
    private static void listen(Object[] arguments) {
        Object consumer = arguments[arguments.length - 1];
        Class<?> event = Stream.of(arguments).filter(Class.class::isInstance).<Class<?>>map(Class.class::cast)
                .findFirst().orElseGet(() -> TypeResolver.resolveRawArgument(Consumer.class, consumer.getClass()));
        assertFalse(event == TypeResolver.Unknown.class || RegisterEvent.class.isAssignableFrom(event),
                "A mod-bus listener may register entries this test cannot see: " + consumer + " for " + event);
        listeners.merge(event.getSimpleName(), 1, Integer::sum);
    }

    private static Map<String, List<String>> ids(String registry) {
        return registered.getOrDefault(registry, Map.of());
    }

    private static void uniqueIds() {
        Map<String, Set<String>> folded = new TreeMap<>();
        registered.forEach((registry, ids) -> ids.forEach((id, installers) -> {
            count("unique_ids");
            if (installers.size() > 1) {
                problems.get("unique_ids").add(registry + " `" + id + "` is registered " + installers.size()
                        + " times, by " + installers);
            }
            if (!id.startsWith(NAMESPACE + ":")) {
                problems.get("unique_ids").add(registry + " `" + id + "` is outside the mod namespace");
            }
            folded.computeIfAbsent(id.toLowerCase(Locale.ROOT), key -> new TreeSet<>()).add(id);
        }));
        folded.values().stream().filter(group -> group.size() > 1)
                .forEach(group -> problems.get("unique_ids").add("IDs differ only in case: " + group));
    }

    // Blocks, items and sounds ---------------------------------------------------------------------------------

    private static void blocks() {
        for (String id : ids(BLOCKS).keySet()) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            require("blockstate", id, file("assets", location, "blockstates"), "has no blockstate");
            require("loot_table", id, file("data", location, "loot_tables/blocks"), "has no block loot table");
        }
    }

    private static void items() throws IOException {
        Set<String> english = languageKeys("en_us"), chinese = languageKeys("zh_cn");
        Set<String> overrides = new TreeSet<>();
        try (Stream<Path> sources = Files.walk(Path.of("src", "main", "java"))) {
            for (Path source : sources.filter(file -> file.toString().endsWith(".java")).toList()) {
                if (Pattern.compile("\\bString\\s+getDescriptionId\\s*\\(").matcher(Files.readString(source)).find()) {
                    overrides.add(source.getFileName().toString());
                }
            }
        }
        if (!overrides.equals(DESCRIPTION_OVERRIDES)) {
            problems.get("item_name").add("`getDescriptionId` overrides " + overrides + " differ from the pinned "
                    + new TreeSet<>(DESCRIPTION_OVERRIDES) + "; give each item they name an `" + NAME_KEY + "` line");
        }
        for (String id : ids(ITEMS).keySet()) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            require("item_model", id, file("assets", location, "models/item"), "has no item model");
            String key = (ids(BLOCKS).containsKey(id) ? "block." : "item.") + location.getNamespace() + "."
                    + location.getPath().replace('/', '.');
            if (nameKeys.containsKey(id)) {
                key = nameKeys.get(id);
                usedExemptions.add(NAME_KEY + " " + id);
            }
            List<String> missing = new ArrayList<>();
            if (!english.contains(key)) { missing.add("en_us"); }
            if (!chinese.contains(key)) { missing.add("zh_cn"); }
            require("item_name", id, missing.isEmpty(), "has no " + String.join(" or ", missing) + " name `"
                    + key + "`");
        }
    }

    /** Every packaged {@code assets/<any namespace>/lang/<language>.json}; the client merges them by key. */
    private static Set<String> languageKeys(String language) throws IOException {
        Set<String> keys = new TreeSet<>();
        for (String file : files) {
            if (file.startsWith("assets/") && file.endsWith("/lang/" + language + ".json")) {
                keys.addAll(read(file).keySet());
            }
        }
        return keys;
    }

    private static void sounds() throws IOException {
        Map<String, Set<String>> defined = new TreeMap<>();
        for (String id : ids(SOUNDS).keySet()) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            String file = "assets/" + location.getNamespace() + "/sounds.json";
            if (!defined.containsKey(file)) {
                defined.put(file, files.contains(file) ? read(file).keySet() : Set.of());
            }
            require("sound", id, defined.get(file).contains(location.getPath()), "is not in `" + file + "`");
        }
    }

    private static void require(String check, String id, boolean present, String problem) {
        count(check);
        if (present) {
            return;
        }
        String exemption = check + " " + id;
        if (exemptions.containsKey(exemption)) {
            usedExemptions.add(exemption);
        } else {
            problems.get(check).add("`" + id + "` " + problem);
        }
    }

    private static void count(String check) {
        checked.merge(check, 1, Integer::sum);
    }

    // Packaged data references ---------------------------------------------------------------------------------

    private static void packagedReferences() throws IOException {
        for (String file : files) {
            String[] parts = file.split("/", 4);
            if (parts.length < 4 || !parts[0].equals("data") || !file.endsWith(".json")) {
                continue;
            }
            switch (parts[2]) {
                case "recipes", "loot_tables", "advancements" -> walk(file, read(file), "", "", null);
                case "tags" -> tag(file, parts[3]);
                default -> { }
            }
        }
        references.forEach((kind, ids) -> ids.keySet().forEach(id -> {
            count("references");
            if (!resolves(kind, id)) {
                problems.get("references").add(kind + " `" + id + "` does not resolve");
            }
        }));
    }

    /** Visits every string field; {@code key} is the nearest field name and {@code parent} its object. */
    private static void walk(String file, JsonElement element, String key, String path, JsonObject parent) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            object.entrySet().forEach(field -> walk(file, field.getValue(), field.getKey(),
                    path + "/" + field.getKey(), object));
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(member -> walk(file, member, key, path + "[]", parent));
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String kind = kind(key, path, parent);
            if (kind == null) {
                problems.get("references").add("`" + file + "` " + path + " is a string field this test does not "
                        + "model: `" + element.getAsString() + "`");
            } else if (kind.isEmpty()) {
                unresolvedFields.merge(path.contains("/properties/") ? "block-state property"
                        : key.equals("name") ? "dynamic loot entry" : key, 1, Integer::sum);
            } else {
                reference(kind, element.getAsString());
            }
        }
    }

    /** The reference kind of a string field, an empty string for a non-reference, or null when unmodelled. */
    private static String kind(String key, String path, JsonObject parent) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.contains("/properties/")) {
            return ""; // a block-state property value in a loot condition
        }
        return switch (key) {
            case "item", "items", "result" -> "item";
            case "fluid" -> "fluid";
            case "block" -> "block";
            case "enchantment" -> "enchantment";
            case "recipe", "recipes" -> "recipe";
            case "parent" -> "advancement";
            case "tag" -> lower.contains("block") || lower.contains("location") || lower.contains("entity") ? null
                    : lower.contains("fluid") ? "tag/fluids" : "tag/items";
            case "name" -> switch (parent.has("type") ? parent.get("type").getAsString() : "") {
                case "minecraft:item" -> "item";
                case "minecraft:tag" -> "tag/items";
                case "minecraft:loot_table" -> "loot_table";
                case "minecraft:dynamic" -> ""; // a drop the block supplies in code, such as the unlit torch's source
                default -> null;
            };
            default -> NOT_REFERENCES.contains(key) ? "" : null;
        };
    }

    /** A tag file: plain entries name the folder's registry, {@code #} entries a tag of the same folder. */
    private static void tag(String file, String path) throws IOException {
        String folder = TAG_FOLDERS.keySet().stream().filter(name -> path.startsWith(name + "/")).findFirst()
                .orElse(null);
        JsonObject json = read(file);
        if (folder == null || !Set.of("values", "replace").containsAll(json.keySet())) {
            problems.get("references").add("`" + file + "` is a tag shape this test does not model");
            return;
        }
        for (JsonElement value : json.getAsJsonArray("values")) {
            boolean required = !value.isJsonObject() || !value.getAsJsonObject().has("required")
                    || value.getAsJsonObject().get("required").getAsBoolean();
            String id = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
            String bare = id.startsWith("#") ? id.substring(1) : id;
            if (!required && !bare.startsWith(NAMESPACE + ":")) {
                optionalForeignTagEntries++;
                continue;
            }
            reference(id.startsWith("#") ? "tag/" + folder : TAG_FOLDERS.get(folder), bare);
        }
    }

    private static void reference(String kind, String id) {
        String bare = kind.startsWith("tag/") && id.startsWith("#") ? id.substring(1) : id;
        references.computeIfAbsent(kind, ignored -> new TreeMap<>()).merge(bare, 1, Integer::sum);
    }

    /** Plain JUnit has no Forge registries; vanilla entries are in Minecraft's (Forge-deprecated) built-in ones. */
    @SuppressWarnings("deprecation")
    private static boolean resolves(String kind, String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return false;
        }
        if (kind.startsWith("tag/")) {
            return data(location, "tags/" + kind.substring("tag/".length()));
        }
        return switch (kind) {
            case "item" -> registered(ITEMS, BuiltInRegistries.ITEM, location);
            case "block" -> registered(BLOCKS, BuiltInRegistries.BLOCK, location);
            case "fluid" -> registered(FLUIDS, BuiltInRegistries.FLUID, location);
            case "enchantment" -> registered("minecraft:enchantment", BuiltInRegistries.ENCHANTMENT, location);
            case "entity_type" -> registered("minecraft:entity_type", BuiltInRegistries.ENTITY_TYPE, location);
            case "worldgen/biome" -> data(location, kind) || vanillaKey(Biomes.class, location);
            case "damage_type" -> data(location, kind) || vanillaKey(DamageTypes.class, location);
            case "recipe" -> data(location, "recipes");
            case "advancement" -> data(location, "advancements");
            case "loot_table" -> data(location, "loot_tables");
            default -> throw new IllegalArgumentException("Unknown reference kind " + kind);
        };
    }

    /** A mod ID must be registered by the mod, a vanilla ID by Minecraft; other mods' IDs never resolve. */
    private static boolean registered(String registry, Registry<?> vanilla, ResourceLocation location) {
        return location.getNamespace().equals(NAMESPACE) ? ids(registry).containsKey(location.toString())
                : location.getNamespace().equals("minecraft") && vanilla.containsKey(location);
    }

    /** A packaged data file, or for another namespace a data file in a dependency JAR (Minecraft or Forge). */
    private static boolean data(ResourceLocation location, String folder) {
        if (file("data", location, folder)) {
            return true;
        }
        if (location.getNamespace().equals(NAMESPACE)) {
            return false;
        }
        String name = "data/" + location.getNamespace() + "/" + folder + "/" + location.getPath() + ".json";
        try {
            return Collections.list(RegisteredContentResourcesTest.class.getClassLoader().getResources(name))
                    .stream().anyMatch(url -> url.getProtocol().equals("jar"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean vanillaKey(Class<?> holder, ResourceLocation location) {
        for (Field field : holder.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == ResourceKey.class) {
                try {
                    if (((ResourceKey<?>) field.get(null)).location().equals(location)) {
                        return true;
                    }
                } catch (IllegalAccessException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        }
        return false;
    }

    private static boolean file(String side, ResourceLocation location, String folder) {
        return files.contains(side + "/" + location.getNamespace() + "/" + folder + "/" + location.getPath()
                + ".json");
    }

    // Exemptions, files and report -----------------------------------------------------------------------------

    /**
     * Lines of {@code <check> <registry ID> <reason>} or {@code item_name_key <item ID> <key> <reason>};
     * {@code #} starts a comment line.
     */
    private static void readExemptions() throws IOException {
        InputStream stream = RegisteredContentResourcesTest.class.getClassLoader().getResourceAsStream(EXEMPTION_FILE);
        assertNotNull(stream, EXEMPTION_FILE + " is not on the test classpath");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                count("exemptions");
                boolean nameKey = line.trim().startsWith(NAME_KEY + " ");
                int fields = nameKey ? 4 : 3;
                String[] parts = line.trim().split("\\s+", fields);
                String entry = parts.length < 2 ? line : parts[0] + " " + parts[1];
                if (parts.length < fields || !(nameKey || EXEMPTIBLE.contains(parts[0]))
                        || ResourceLocation.tryParse(parts[1]) == null || parts[fields - 1].isBlank()) {
                    problems.get("exemptions").add("line " + number + " is not `<check> <ID> <reason>` with a check"
                            + " in " + new TreeSet<>(EXEMPTIBLE) + ", or `" + NAME_KEY + " <ID> <key> <reason>`: `"
                            + line + "`");
                } else if (exemptions.put(entry, parts[fields - 1]) != null) {
                    problems.get("exemptions").add("line " + number + " repeats `" + entry + "`");
                } else if (nameKey) {
                    nameKeys.put(parts[1], parts[2]);
                }
            }
        }
    }

    private static JsonObject read(String file) throws IOException {
        return JsonParser.parseString(Files.readString(root.resolve(file), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** The merged packaged resources that the test runtime classpath and the JAR both use. */
    private static Path packagedRoot() {
        URL url = RegisteredContentResourcesTest.class.getClassLoader()
                .getResource("assets/" + NAMESPACE + "/sounds.json");
        assertNotNull(url, "The packaged mod sounds.json is not on the test classpath");
        assertEquals("file", url.getProtocol(), "Packaged resources are not an exploded directory: " + url);
        try {
            return Path.of(url.toURI()).getParent().getParent().getParent();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void writeReport() throws IOException {
        StringBuilder report = new StringBuilder("# Registered content resources\n\n")
                .append("W62 slice S4 (ADR-061 sections 1.1 and 5.2). The mod's deferred registrations, recorded from ")
                .append("`ModRegistries.register`, against the packaged resources in `build/resources/main`. ")
                .append("Exemptions: `src/test/resources/").append(EXEMPTION_FILE).append("`.\n\n")
                .append("| Check | Checked | Exempt | Problems |\n|---|---:|---:|---:|\n");
        for (String check : CHECKS) {
            long exempt = usedExemptions.stream().filter(entry -> entry.startsWith(check + " ")).count();
            report.append("| ").append(check).append(" | ").append(checked.getOrDefault(check, 0)).append(" | ")
                    .append(exempt).append(" | ").append(problems.get(check).size()).append(" |\n");
        }
        report.append("\n## Problems\n\n");
        if (problems.values().stream().allMatch(List::isEmpty)) {
            report.append("none\n");
        }
        problems.forEach((check, list) -> list.forEach(problem ->
                report.append("- ").append(check).append(": ").append(problem).append('\n')));
        report.append("\n## Exemptions applied\n\n");
        if (usedExemptions.isEmpty()) {
            report.append("none\n");
        }
        usedExemptions.forEach(entry -> report.append("- `").append(entry).append("`: ")
                .append(exemptions.get(entry)).append('\n'));
        report.append("\n## References in packaged recipes, loot tables, advancements and tags\n\n")
                .append("| Kind | Fields | Distinct IDs |\n|---|---:|---:|\n");
        references.forEach((kind, ids) -> report.append("| ").append(kind).append(" | ")
                .append(ids.values().stream().mapToInt(Integer::intValue).sum()).append(" | ").append(ids.size())
                .append(" |\n"));
        report.append("\nString fields counted but not resolved (types, triggers, texts, criteria): ")
                .append(unresolvedFields).append(".\nOptional tag entries of other mods, skipped: ")
                .append(optionalForeignTagEntries).append(".\n\n## Registered IDs\n\n")
                .append("Other mod-bus listeners added by the boundary, by event: ").append(listeners).append(".\n");
        registered.forEach((registry, ids) -> {
            Set<String> installers = new TreeSet<>();
            ids.values().forEach(installers::addAll);
            report.append("\n### ").append(registry).append(" (").append(ids.size()).append(")\n\nInstalled by: ")
                    .append(String.join(", ", installers)).append(".\n\n");
            ids.keySet().forEach(id -> report.append("- ").append(id).append('\n'));
        });
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report, StandardCharsets.UTF_8);
    }
}
