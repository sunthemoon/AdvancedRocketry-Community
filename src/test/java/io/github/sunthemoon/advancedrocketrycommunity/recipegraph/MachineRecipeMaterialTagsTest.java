package io.github.sunthemoon.advancedrocketrycommunity.recipegraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.config.ClientConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.forcefield.ForceFieldConfig;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Material;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Product;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W57 (CONTRACT-02 section 2.4; the owner decision in OWNER-DECISIONS-02; ADR-061 section 2.2): the tag-based catalog
 * of every packaged recipe of every machine recipe type this mod registers. An ingredient names a material form of
 * the material table only through that form's tag, so other mods' members of the same common tags are accepted, and
 * no configuration value replaces the legacy {@code makeMaterialsForOtherMods} switch.
 */
class MachineRecipeMaterialTagsTest {
    private static final String NS = PackagedRecipeGraph.NAMESPACE;
    private static final String RECIPE_TYPE_REGISTER =
            "net.minecraftforge.registries.DeferredRegister<net.minecraft.world.item.crafting.RecipeType<?>>";
    private static final String LEGACY_KEY = "makeMaterialsForOtherMods";
    private static Set<String> types;
    private static final Map<String, JsonObject> recipes = new TreeMap<>();
    private static final Set<String> unregisteredTypes = new TreeSet<>();
    private static Set<String> formTags;
    /** Each item of a material form, with the form tags that hold it. */
    private static final Map<String, Set<String>> formItems = new TreeMap<>();

    @BeforeAll
    static void load() throws IOException, ReflectiveOperationException {
        MinecraftBootstrap.initialize();
        types = RegisteredItemReachabilityTest.registered(RECIPE_TYPE_REGISTER);
        Path root = PackagedRecipeGraph.resourcesRoot();
        try (Stream<Path> files = Files.walk(root.resolve("data"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                Path relative = root.resolve("data").relativize(file);
                if (relative.getNameCount() < 3 || !relative.getName(1).toString().equals("recipes")) {
                    continue;
                }
                JsonObject recipe = PackagedRecipeGraph.read(file);
                String type = recipe.get("type").getAsString();
                String path = relative.subpath(2, relative.getNameCount()).toString().replace('\\', '/');
                String id = relative.getName(0) + ":" + path.substring(0, path.length() - ".json".length());
                if (types.contains(type)) {
                    recipes.put(id, recipe);
                } else if (type.startsWith(NS + ":")) {
                    unregisteredTypes.add(type + " (" + id + ")");
                }
            }
        }
        formTags = formTags(root);
        for (String tag : formTags) {
            members(tag, new HashSet<>()).forEach(item -> formItems.computeIfAbsent(item, key -> new TreeSet<>()).add(tag));
        }
        Files.createDirectories(Path.of("build/reports"));
        Files.writeString(Path.of("build/reports/machine-recipe-material-tags.md"), report(), StandardCharsets.UTF_8);
    }

    @Test
    void theCatalogReadsEveryPackagedRecipeOfEveryRegisteredMachineRecipeType() {
        assertFalse(types.isEmpty(), "no registered recipe types");
        assertEquals(Set.of(), unregisteredTypes, "packaged recipes of a type this mod does not register");
        Set<String> withoutRecipes = new TreeSet<>(types);
        recipes.values().forEach(recipe -> withoutRecipes.remove(recipe.get("type").getAsString()));
        assertEquals(Set.of(), withoutRecipes, "registered machine recipe types without a packaged recipe");
    }

    @Test
    void everyMachineRecipeNamesMaterialFormsOnlyThroughTheirTags() {
        List<String> violations = new ArrayList<>();
        recipes.forEach((id, recipe) -> violations.addAll(violations(id, recipe)));
        assertEquals(List.of(), violations, "see build/reports/machine-recipe-material-tags.md");
    }

    /** The bounded codec disables a kernel or classic recipe whose ingredient resolves outside 1 to 32 items. */
    @Test
    void everyMachineRecipeTagNamesOneTo32Items() {
        List<String> violations = new ArrayList<>();
        recipes.forEach((id, recipe) -> entries(recipe).stream().filter(entry -> entry.has("tag")).forEach(entry -> {
            int size = members(entry.get("tag").getAsString(), new HashSet<>()).size();
            if (size < 1 || size > 32) {
                violations.add(id + ": #" + entry.get("tag").getAsString() + " names " + size + " items");
            }
        }));
        assertEquals(List.of(), violations);
    }

    @Test
    void theRuleCoversTheMaterialTableAndLeavesOtherInputsAlone() {
        for (MaterialCatalog.Entry entry : MaterialCatalog.entries()) {
            assertTrue(formItems.containsKey(NS + ":" + entry.id()), entry.id() + " is in no material form tag");
        }
        // Forge's and vanilla's classpath copies supply the vanilla forms of copper, iron and gold.
        for (Material material : Material.values()) {
            if (material.vanillaIngot()) {
                for (String form : List.of("%s_ingot", "%s_block", "raw_%s", "%s_ore", "deepslate_%s_ore")) {
                    String item = "minecraft:" + form.formatted(material.id());
                    assertTrue(formItems.containsKey(item), item + " is in no material form tag");
                }
            }
        }
        // Form tags that this mod ships outside the material table are forms too.
        assertTrue(formItems.containsKey(NS + ":carbon_brick"), "forge:ingots/carbon");
        assertTrue(formItems.containsKey(NS + ":thermite"), "forge:dusts/thermite");
        // W56's remaining item inputs are not material forms, so they stay allowed.
        for (String item : List.of("minecraft:sand", "minecraft:charcoal", "minecraft:redstone_block", NS + ":silicon_wafer",
                "minecraft:oak_log", "minecraft:bone", "minecraft:ender_eye", NS + ":laser_lens")) {
            assertFalse(formItems.containsKey(item), item + " is held by " + formItems.get(item));
        }
    }

    /** Negative control: every packaged form-tag entry, written as one of its items instead, is found. */
    @Test
    void anItemPlantedInPlaceOfAnyFormTagIsFound() {
        int plants = 0;
        for (Map.Entry<String, JsonObject> recipe : recipes.entrySet()) {
            List<JsonObject> entries = entries(recipe.getValue());
            for (int i = 0; i < entries.size(); i++) {
                JsonObject entry = entries.get(i);
                if (!entry.has("tag") || !formTags.contains(entry.get("tag").getAsString())) {
                    continue;
                }
                String item = members(entry.get("tag").getAsString(), new HashSet<>()).iterator().next();
                JsonObject planted = recipe.getValue().deepCopy();
                JsonObject target = entries(planted).get(i);
                target.remove("tag");
                target.addProperty("item", item);
                plants++;
                assertEquals(1, violations(recipe.getKey(), planted).size(), recipe.getKey() + " with " + item);
                assertTrue(violations(recipe.getKey(), planted).get(0).contains(item));
            }
        }
        assertTrue(plants > 0, "no packaged recipe uses a material form tag");
        JsonArray alternatives = new JsonArray();
        alternatives.add(object("tag", "forge:ingots/gold"));
        alternatives.add(object("item", NS + ":steel_sheet"));
        JsonObject row = new JsonObject();
        row.add("ingredient", alternatives);
        JsonArray rows = new JsonArray();
        rows.add(row);
        JsonObject array = new JsonObject();
        array.add("item_inputs", rows);
        assertEquals(1, violations("planted:alternative", array).size(), "an item among tag alternatives");
        JsonObject custom = new JsonObject();
        custom.add("ingredient", object("type", "forge:nbt"));
        assertEquals(1, violations("planted:custom", custom).size(), "an entry outside the bounded form");
    }

    @Test
    void noConfigurationValueReplacesTheLegacySwitch() throws IOException {
        for (ForgeConfigSpec spec : List.of(CommonConfig.SPEC, ForceFieldConfig.SPEC, ClientConfig.SPEC)) {
            Set<String> keys = new TreeSet<>();
            keys(spec.getValues(), keys);
            assertFalse(keys.isEmpty());
            assertTrue(keys.stream().noneMatch(LEGACY_KEY::equalsIgnoreCase), keys.toString());
        }
        // A spec registered elsewhere would have to name the key in the sources.
        try (Stream<Path> files = Files.walk(Path.of("src/main"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                assertFalse(text.toLowerCase(Locale.ROOT).contains(LEGACY_KEY.toLowerCase(Locale.ROOT)), file.toString());
            }
        }
    }

    /**
     * The form tags: every form folder of the material table (its products' tag folders, ores and raw materials) for
     * every material name. The names are the table's materials and ores and every name this mod ships a form tag for.
     */
    private static Set<String> formTags(Path root) throws IOException {
        Map<String, String> folders = new TreeMap<>();
        for (Product product : Product.values()) {
            folders.put(product.tagFolder(), product.isProjectTag() ? NS : "forge");
        }
        folders.put("ores", "forge");
        folders.put("raw_materials", "forge");
        Set<String> names = new TreeSet<>();
        for (Material material : Material.values()) {
            names.add(material.id());
            material.oreName().ifPresent(names::add);
        }
        for (Map.Entry<String, String> folder : folders.entrySet()) {
            Path directory = root.resolve("data/" + folder.getValue() + "/tags/items/" + folder.getKey());
            if (Files.isDirectory(directory)) {
                try (Stream<Path> files = Files.list(directory)) {
                    files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(".json"))
                            .forEach(name -> names.add(name.substring(0, name.length() - ".json".length())));
                }
            }
        }
        Set<String> tags = new TreeSet<>();
        folders.forEach((folder, namespace) -> names.forEach(name -> tags.add(namespace + ":" + folder + "/" + name)));
        return tags;
    }

    /** Items of an item tag in every classpath copy (this mod, Forge and vanilla), with nested tags expanded. */
    private static Set<String> members(String tag, Set<String> visiting) {
        Set<String> items = new TreeSet<>();
        if (visiting.add(tag)) {
            for (String member : PackagedRecipeGraph.tagMembers(tag, "items")) {
                if (member.startsWith("#")) {
                    items.addAll(members(member.substring(1), visiting));
                } else {
                    items.add(member);
                }
            }
        }
        return items;
    }

    private static List<String> violations(String id, JsonObject recipe) {
        List<String> violations = new ArrayList<>();
        for (JsonObject entry : entries(recipe)) {
            boolean bounded = entry.size() == 1 && (entry.has("item") || entry.has("tag"));
            if (!bounded) {
                violations.add(id + ": ingredient entry outside the bounded form " + entry);
            } else if (entry.has("item") && formItems.containsKey(entry.get("item").getAsString())) {
                violations.add(id + ": names " + entry.get("item").getAsString() + "; use #"
                        + String.join(" or #", formItems.get(entry.get("item").getAsString())));
            }
        }
        return violations;
    }

    /**
     * The entries of every value named {@code ingredient}, the field of all three machine codecs (top level, in
     * {@code inputs} and in {@code item_inputs}); a non-object entry becomes an empty object, which fails the form.
     */
    private static List<JsonObject> entries(JsonElement element) {
        List<JsonObject> entries = new ArrayList<>();
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> entries.addAll(entries(child)));
        } else if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> field : element.getAsJsonObject().entrySet()) {
                if (!field.getKey().equals("ingredient")) {
                    entries.addAll(entries(field.getValue()));
                } else if (field.getValue().isJsonArray()) {
                    field.getValue().getAsJsonArray().forEach(value ->
                            entries.add(value.isJsonObject() ? value.getAsJsonObject() : new JsonObject()));
                } else {
                    entries.add(field.getValue().isJsonObject() ? field.getValue().getAsJsonObject() : new JsonObject());
                }
            }
        }
        return entries;
    }

    private static void keys(UnmodifiableConfig config, Set<String> keys) {
        config.valueMap().forEach((key, value) -> {
            keys.add(key);
            if (value instanceof UnmodifiableConfig nested) {
                keys(nested, keys);
            }
        });
    }

    private static JsonObject object(String key, String value) {
        JsonObject object = new JsonObject();
        object.addProperty(key, value);
        return object;
    }

    private static String report() {
        StringBuilder report = new StringBuilder("# Machine recipe material tags (W57)\n\n");
        Map<String, Integer> counts = new TreeMap<>();
        types.forEach(type -> counts.put(type, 0));
        recipes.values().forEach(recipe -> counts.merge(recipe.get("type").getAsString(), 1, Integer::sum));
        report.append("Registered recipe types: ").append(types.size()).append("; packaged recipes read: ")
                .append(recipes.size()).append("; material form tags: ").append(formTags.size())
                .append(" (").append(formItems.size()).append(" items).\n\n| Type | Recipes |\n|---|---|\n");
        counts.forEach((type, count) -> report.append("| `").append(type).append("` | ").append(count).append(" |\n"));
        Map<String, Set<String>> items = new TreeMap<>();
        Map<String, Set<String>> tags = new TreeMap<>();
        recipes.forEach((id, recipe) -> entries(recipe).forEach(entry -> {
            boolean item = entry.has("item");
            String name = (item ? entry.get("item") : entry.has("tag") ? entry.get("tag") : entry).toString();
            (item ? items : tags).computeIfAbsent(name.replace("\"", ""), key -> new TreeSet<>()).add(id);
        }));
        report.append("\n## Item entries (none may be a material form)\n\n");
        items.forEach((item, ids) -> report.append("- `").append(item).append("`: ").append(ids).append('\n'));
        report.append("\n## Tag entries\n\n");
        tags.forEach((tag, ids) -> report.append("- `#").append(tag).append("` (")
                .append(members(tag, new HashSet<>()).size()).append(" items): ").append(ids.size())
                .append(" recipes\n"));
        return report.toString();
    }
}
