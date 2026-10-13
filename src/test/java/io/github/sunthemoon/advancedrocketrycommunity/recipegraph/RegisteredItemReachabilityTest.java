package io.github.sunthemoon.advancedrocketrycommunity.recipegraph;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.recipegraph.RecipeGraphReachability.Success;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import net.minecraftforge.registries.DeferredRegister;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Checks actual deferred item and enchantment registration IDs, including entries unused by any recipe or
 * advancement. An enchantment is the graph node {@code enchantment:<id>}.
 */
class RegisteredItemReachabilityTest {
    private static final String ITEM_REGISTER = "net.minecraftforge.registries.DeferredRegister<net.minecraft.world.item.Item>";
    private static final String ENCHANTMENT_REGISTER =
            "net.minecraftforge.registries.DeferredRegister<net.minecraft.world.item.enchantment.Enchantment>";
    private static PackagedRecipeGraph graph;
    private static Set<String> items;
    private static Set<String> enchantments;
    private static Success baseline;

    @BeforeAll
    static void load() throws ReflectiveOperationException, IOException {
        MinecraftBootstrap.initialize();
        items = registered(ITEM_REGISTER);
        enchantments = registered(ENCHANTMENT_REGISTER);
        graph = PackagedRecipeGraph.load();
        items.forEach(graph::add);
        enchantments.forEach(id -> graph.add("enchantment:" + id));
        baseline = evaluate(graph.roots, graph.producerList());
        StringBuilder report = new StringBuilder("# Registered item acquisition\n\n");
        report.append("Deferred item registrations: ").append(items.size()).append(".\n")
                .append("Deferred enchantment registrations: ").append(enchantments.size()).append(".\n\n")
                .append("## Unavailable required nodes\n\n");
        Set<String> unavailable = new TreeSet<>(baseline.unavailable());
        unavailable.removeAll(PackagedRecipeGraph.OPTIONAL_CROSS_MOD_INPUTS);
        if (unavailable.isEmpty()) { report.append("none\n"); }
        else { unavailable.forEach(node -> report.append("- ").append(node).append('\n')); }
        report.append("\n## Optional cross-mod inputs (TASK-W19-ROOT-DECISION-01)\n\n");
        new TreeSet<>(PackagedRecipeGraph.OPTIONAL_CROSS_MOD_INPUTS).forEach(node ->
                report.append("- ").append(node).append('\n'));
        Files.createDirectories(Path.of("build/reports"));
        Files.writeString(Path.of("build/reports/registered-item-acquisition.md"), report);
    }

    @Test
    void everyRegisteredItemExceptExactlyTheFiveDustsIsStrictlyRequiredAndReachable() {
        assertFalse(items.isEmpty());
        Set<String> required = new TreeSet<>(items);
        required.removeAll(PackagedRecipeGraph.OPTIONAL_CROSS_MOD_INPUTS);
        assertEquals(5, items.size() - required.size(), "Exactly five registered items are optional");
        required.removeAll(baseline.available().keySet());
        assertEquals(Set.of(), required, "Registered items without a survival route; see registered-item-acquisition.md");
    }

    @Test
    void everyRegisteredEnchantmentHasASurvivalRoute() {
        assertEquals(Set.of(PackagedRecipeGraph.NAMESPACE + ":space_breathing"), enchantments);
        for (String id : enchantments) {
            assertTrue(baseline.available().containsKey("enchantment:" + id), "Enchantment without a survival route: " + id);
        }
    }

    @Test
    void noOtherRegisteredItemDependsOnlyOnOptionalInputs() {
        Set<String> suppliedRoots = new TreeSet<>(graph.roots);
        suppliedRoots.addAll(PackagedRecipeGraph.OPTIONAL_CROSS_MOD_INPUTS);
        Success supplied = evaluate(suppliedRoots, graph.producerList());
        Success forbidden = evaluate(graph.roots, graph.withoutInputs(PackagedRecipeGraph.OPTIONAL_CROSS_MOD_INPUTS));
        Set<String> required = new TreeSet<>(supplied.available().keySet());
        required.removeAll(PackagedRecipeGraph.OPTIONAL_CROSS_MOD_INPUTS);
        assertEquals(required, forbidden.available().keySet());
    }

    /** Discover the providers from the real central register method; a newly wired registry cannot be skipped. */
    private static Set<String> registered(String registerType) throws IOException, ReflectiveOperationException {
        String packageName = "io.github.sunthemoon.advancedrocketrycommunity.";
        Path sourceRoot = Path.of("src/main/java");
        String registrations = Files.readString(sourceRoot.resolve(packageName.replace('.', '/')
                + "registry/ModRegistries.java"));
        var calls = Pattern.compile("([A-Za-z0-9_.]+)\\.register\\s*\\(\\s*modBus\\b").matcher(registrations);
        Set<String> result = new TreeSet<>();
        int providers = 0;
        while (calls.find()) {
            String name = calls.group(1);
            if (!name.contains(".")) { name = packageName + "registry." + name; }
            Class<?> provider = Class.forName(name);
            for (Field field : provider.getDeclaredFields()) {
                if (!field.getGenericType().getTypeName().equals(registerType)) { continue; }
                providers++;
                field.setAccessible(true);
                DeferredRegister<?> registry = (DeferredRegister<?>) field.get(null);
                for (var entry : registry.getEntries()) {
                    assertTrue(result.add(entry.getId().toString()), "Duplicate registration " + entry.getId());
                }
            }
        }
        assertTrue(providers > 0, "No " + registerType + " providers found in the central registration boundary");
        return result;
    }

    private static Success evaluate(Set<String> roots, java.util.List<RecipeGraphReachability.Producer> producers) {
        return assertInstanceOf(Success.class, RecipeGraphReachability.evaluate(graph.nodes, roots, producers,
                RecipeGraphReachability.DEFAULT_LIMITS));
    }
}
