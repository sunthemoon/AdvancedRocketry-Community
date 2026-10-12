package io.github.sunthemoon.advancedrocketrycommunity.progression.classic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ClassicAdvancementLanguage;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ClassicMilestoneLanguage;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicInventoryAdvancements.Goal;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicMilestoneAdvancements.Frame;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicMilestoneAdvancements.Kind;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicMilestoneAdvancements.Milestone;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The whole ADR-066 §6.2 tree: the six inventory tutorials and the eleven milestones together. */
class ClassicAdvancementTreeTest {
    /** The C18c ledger row (legacy name) and the ADR-066 §6.2 stable suffix of each advancement. */
    static final Map<String, String> LEDGER = Map.ofEntries(Map.entry("root", "root"),
            Map.entry("blockpresser", "block_press"), Map.entry("rollin", "rolling"),
            Map.entry("electrifying", "electrolysis"), Map.entry("feeltheheat", "arc_furnace"),
            Map.entry("spindoctor", "lathe"), Map.entry("crystalline", "crystallizer"),
            Map.entry("dilithium", "dilithium"), Map.entry("holographic", "formed_multiblock"),
            Map.entry("suitedup", "suited_up"), Map.entry("warp", "warp_core"), Map.entry("beer", "seat_on_tnt"),
            Map.entry("moonlanding", "moon_landing"), Map.entry("onesmallstep", "one_small_step"),
            Map.entry("wenttothemoon", "moon_surface_visit"), Map.entry("givingitallshesgot", "station_warp"),
            Map.entry("flightofpheonix", "first_station_warp"));
    private static final String PREFIX = "advancedrocketrycommunity:classic/";

    @Test
    void theLedgerRowMapsToSeventeenDistinctStableIds() {
        assertEquals(17, LEDGER.size());
        Set<String> suffixes = Stream.concat(Arrays.stream(Goal.values()).map(Goal::suffix),
                Arrays.stream(Milestone.values()).map(Milestone::suffix)).collect(Collectors.toSet());
        assertEquals(17, suffixes.size(), "Duplicate stable suffix");
        assertEquals(new HashSet<>(LEDGER.values()), suffixes);
        for (Milestone milestone : Milestone.values()) {
            assertEquals(milestone.suffix(), LEDGER.get(milestone.legacyName()), milestone.name());
            assertEquals(PREFIX + milestone.suffix(), milestone.id());
        }
    }

    @Test
    void displayParentsFormOneAcyclicTreeUnderTheCraftingTableRoot() {
        Map<String, String> parents = parents();
        assertNull(parents.get(PREFIX + "root"));
        assertEquals(1, parents.values().stream().filter(java.util.Objects::isNull).count(), "More than one root");
        for (String id : parents.keySet()) {
            Set<String> visited = new HashSet<>();
            String cursor = id;
            while (cursor != null) {
                assertTrue(visited.add(cursor), "Cycle at " + id);
                assertTrue(parents.containsKey(cursor), "Unknown parent " + cursor + " of " + id);
                cursor = parents.get(cursor);
            }
            assertTrue(visited.contains(PREFIX + "root"), id + " is not under root");
        }
        Map<String, String> expected = Map.ofEntries(Map.entry("arc_furnace", "block_press"),
                Map.entry("lathe", "block_press"), Map.entry("crystallizer", "arc_furnace"),
                Map.entry("dilithium", "crystallizer"), Map.entry("formed_multiblock", "rolling"),
                Map.entry("seat_on_tnt", "root"), Map.entry("moon_surface_visit", "suited_up"),
                Map.entry("moon_landing", "moon_surface_visit"), Map.entry("one_small_step", "moon_landing"),
                Map.entry("station_warp", "warp_core"), Map.entry("first_station_warp", "station_warp"));
        for (Milestone milestone : Milestone.values()) {
            assertEquals(PREFIX + expected.get(milestone.suffix()), milestone.parentId(), milestone.name());
        }
    }

    @Test
    void eachCriterionIsVanillaUnlessTheEventHasNoVanillaTrigger() {
        Map<Kind, Set<String>> byKind = new HashMap<>();
        for (Milestone milestone : Milestone.values()) {
            byKind.computeIfAbsent(milestone.kind(), kind -> new HashSet<>()).add(milestone.suffix());
            JsonObject json = ClassicMilestoneAdvancements.files().get(milestone.file());
            JsonObject criteria = json.getAsJsonObject("criteria");
            assertEquals(Set.of(milestone.criterion()), criteria.keySet(), milestone.name());
            JsonObject criterion = criteria.getAsJsonObject(milestone.criterion());
            String trigger = criterion.get("trigger").getAsString();
            switch (milestone.kind()) {
                case INVENTORY -> {
                    assertEquals("minecraft:inventory_changed", trigger);
                    assertEquals("{\"items\":[{\"items\":[\"" + milestone.target() + "\"]}]}",
                            criterion.getAsJsonObject("conditions").toString());
                }
                case EVENT -> {
                    assertEquals("advancedrocketrycommunity:classic_event", trigger);
                    assertEquals(Set.of("event"), criterion.getAsJsonObject("conditions").keySet());
                    assertTrue(ClassicEvent.byName(milestone.target()).isPresent(), milestone.name());
                }
                case ARRIVAL -> {
                    assertEquals("minecraft:changed_dimension", trigger);
                    assertEquals("{\"to\":\"advancedrocketrycommunity:moon\"}",
                            criterion.getAsJsonObject("conditions").toString());
                }
                case PENDING -> {
                    assertEquals("minecraft:impossible", trigger);
                    assertFalse(criterion.has("conditions"));
                    assertNull(milestone.target());
                }
            }
            assertEquals("[[\"" + milestone.criterion() + "\"]]", json.getAsJsonArray("requirements").toString());
            assertEquals(0, json.getAsJsonObject("rewards").size(), "Advancements grant no rewards or recipes");
        }
        assertEquals(Map.of(Kind.INVENTORY, Set.of("arc_furnace", "lathe", "dilithium"),
                Kind.EVENT, Set.of("formed_multiblock", "seat_on_tnt", "moon_landing", "station_warp"),
                Kind.ARRIVAL, Set.of("moon_surface_visit"),
                Kind.PENDING, Set.of("crystallizer", "one_small_step", "first_station_warp")), byKind);
        // Each custom event backs exactly one advancement.
        assertEquals(EnumSet.allOf(ClassicEvent.class), Arrays.stream(Milestone.values())
                .filter(milestone -> milestone.kind() == Kind.EVENT)
                .map(milestone -> ClassicEvent.byName(milestone.target()).orElseThrow())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ClassicEvent.class))));
    }

    @Test
    void framesAndIconsAreFixedAndEveryIconIsAnOwnedItem() throws IOException {
        Map<String, Frame> frames = new LinkedHashMap<>();
        for (Milestone milestone : Milestone.values()) {
            frames.put(milestone.suffix(), milestone.frame());
            JsonObject display = ClassicMilestoneAdvancements.files().get(milestone.file()).getAsJsonObject("display");
            assertEquals(milestone.frame().serializedName(), display.get("frame").getAsString());
            assertEquals(milestone.icon(), display.getAsJsonObject("icon").get("item").getAsString());
            assertFalse(display.get("hidden").getAsBoolean(), "The tree is visible");
            assertFalse(display.has("background"), "Only the root carries the tab background");
            assertTrue(milestone.icon().startsWith("minecraft:") || itemModelExists(milestone.icon()),
                    "Icon without an existing item model " + milestone.icon());
        }
        assertEquals(Set.of("one_small_step", "first_station_warp"), suffixes(frames, Frame.CHALLENGE));
        assertEquals(Set.of("dilithium", "moon_landing", "station_warp"), suffixes(frames, Frame.GOAL));
    }

    @Test
    void everyAdvancementHasEnglishAndChineseTextInTheGeneratedLanguageFiles() throws IOException {
        for (boolean chinese : List.of(false, true)) {
            Map<String, String> text = new HashMap<>(V180ClassicAdvancementLanguage.translations(chinese));
            text.putAll(V180ClassicMilestoneLanguage.translations(chinese));
            JsonObject generated = JsonParser.parseString(Files.readString(Path.of(
                    "src/generated/v1.8/resources/assets/advancedrocketrycommunity_v180/lang/"
                            + (chinese ? "zh_cn" : "en_us") + ".json"))).getAsJsonObject();
            List<String> keys = Stream.concat(
                    Arrays.stream(Goal.values()).flatMap(goal -> Stream.of(goal.titleKey(), goal.descriptionKey())),
                    Arrays.stream(Milestone.values())
                            .flatMap(milestone -> Stream.of(milestone.titleKey(), milestone.descriptionKey())))
                    .toList();
            assertEquals(34, keys.size());
            for (String key : keys) {
                String value = text.get(key);
                assertTrue(value != null && !value.isBlank(), "Missing " + (chinese ? "zh_cn " : "en_us ") + key);
                assertTrue(chinese == value.chars().anyMatch(c -> c > 0x2E80), "Wrong language for " + key);
                assertTrue(generated.has(key) && generated.get(key).getAsString().equals(value),
                        "Generated language file lacks " + key + "; rerun runData");
            }
        }
    }

    private static Set<String> suffixes(Map<String, Frame> frames, Frame frame) {
        return frames.entrySet().stream().filter(entry -> entry.getValue() == frame).map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static Map<String, String> parents() {
        Map<String, String> parents = new HashMap<>();
        for (Goal goal : Goal.values()) {
            parents.put(goal.id(), goal.parentId());
        }
        for (Milestone milestone : Milestone.values()) {
            assertNull(parents.put(milestone.id(), milestone.parentId()), "Duplicate id " + milestone.id());
        }
        return parents;
    }

    private static boolean itemModelExists(String item) throws IOException {
        String path = "assets/advancedrocketrycommunity/models/item/" + item.substring(item.indexOf(':') + 1) + ".json";
        if (Files.isRegularFile(Path.of("src/main/resources").resolve(path))) {
            return true;
        }
        try (Stream<Path> roots = Files.list(Path.of("src/generated"))) {
            return roots.anyMatch(root -> Files.isRegularFile(root.resolve("resources").resolve(path))
                    || Files.isRegularFile(root.resolve(path)));
        }
    }
}
