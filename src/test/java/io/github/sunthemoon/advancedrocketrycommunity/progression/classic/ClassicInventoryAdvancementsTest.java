package io.github.sunthemoon.advancedrocketrycommunity.progression.classic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ClassicAdvancementLanguage;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicInventoryAdvancements.Goal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ClassicInventoryAdvancementsTest {
    @Test
    void stableIdsAndTargetsAreTheSixFrozenInventoryGoals() {
        assertEquals(List.of("root", "block_press", "rolling", "electrolysis", "suited_up", "warp_core"),
                java.util.Arrays.stream(Goal.values()).map(Goal::suffix).toList());
        assertEquals(List.of("minecraft:crafting_table"), Goal.ROOT.items());
        assertEquals(List.of("advancedrocketrycommunity:small_plate_press"), Goal.BLOCK_PRESS.items());
        assertEquals(List.of("advancedrocketrycommunity:rolling_machine"), Goal.ROLLING.items());
        assertEquals(List.of("advancedrocketrycommunity:electrolyzer"), Goal.ELECTROLYSIS.items());
        assertEquals(List.of("advancedrocketrycommunity:warp_core"), Goal.WARP_CORE.items());
        assertEquals(List.of("advancedrocketrycommunity:space_suit_helmet",
                "advancedrocketrycommunity:space_suit_chestplate", "advancedrocketrycommunity:space_suit_leggings",
                "advancedrocketrycommunity:space_suit_boots"), Goal.SUITED_UP.items());
        assertEquals(6, java.util.Arrays.stream(Goal.values()).map(Goal::id).distinct().count());
    }

    @Test
    void displayParentsAreTheFrozenConnectedAcyclicTree() {
        assertNull(Goal.ROOT.parentId());
        assertEquals(Goal.ROOT.id(), Goal.BLOCK_PRESS.parentId());
        assertEquals(Goal.BLOCK_PRESS.id(), Goal.ROLLING.parentId());
        assertEquals(Goal.ROLLING.id(), Goal.ELECTROLYSIS.parentId());
        assertEquals(Goal.ELECTROLYSIS.id(), Goal.WARP_CORE.parentId());
        assertEquals(Goal.ROOT.id(), Goal.SUITED_UP.parentId());
        Map<String, Goal> ids = java.util.Arrays.stream(Goal.values()).collect(Collectors.toMap(Goal::id, g -> g));
        for (Goal goal : Goal.values()) {
            Set<String> visited = new HashSet<>();
            for (Goal cursor = goal; cursor != null; cursor = ids.get(cursor.parentId())) {
                assertTrue(visited.add(cursor.id()), "cycle at " + goal);
                assertTrue(cursor.parentId() == null || ids.containsKey(cursor.parentId()));
            }
            assertTrue(visited.contains(Goal.ROOT.id()));
        }
    }

    @Test
    void everyGoalUsesOneVanillaCurrentInventoryCriterionWithExplicitIdentities() {
        for (Goal goal : Goal.values()) {
            JsonObject json = json(goal);
            assertEquals(Set.of(ClassicInventoryAdvancements.CRITERION), json.getAsJsonObject("criteria").keySet());
            JsonObject criterion = json.getAsJsonObject("criteria").getAsJsonObject(ClassicInventoryAdvancements.CRITERION);
            assertEquals(Set.of("trigger", "conditions"), criterion.keySet());
            assertEquals("minecraft:inventory_changed", criterion.get("trigger").getAsString());
            JsonObject conditions = criterion.getAsJsonObject("conditions");
            assertEquals(Set.of("items"), conditions.keySet());
            JsonArray predicates = conditions.getAsJsonArray("items");
            assertEquals(goal.items().size(), predicates.size());
            for (int i = 0; i < predicates.size(); i++) {
                JsonObject predicate = predicates.get(i).getAsJsonObject();
                assertEquals(Set.of("items"), predicate.keySet());
                assertEquals(1, predicate.getAsJsonArray("items").size());
                assertEquals(goal.items().get(i), predicate.getAsJsonArray("items").get(0).getAsString());
            }
        }
    }

    @Test
    void suitIsOneConjunctionNotFourHistoricalCriteriaOrOneAlternativeList() {
        JsonObject suit = json(Goal.SUITED_UP);
        assertEquals(1, suit.getAsJsonObject("criteria").size());
        JsonArray predicates = suit.getAsJsonObject("criteria").getAsJsonObject("has_inventory")
                .getAsJsonObject("conditions").getAsJsonArray("items");
        assertEquals(4, predicates.size());
        assertEquals(4, predicates.asList().stream()
                .map(value -> value.getAsJsonObject().getAsJsonArray("items").get(0).getAsString()).distinct().count());
        assertEquals("[[\"has_inventory\"]]", suit.getAsJsonArray("requirements").toString());
    }

    @Test
    void noTutorialHasRewardsRecipeLocksOrNonInventoryConditions() {
        for (Goal goal : Goal.values()) {
            JsonObject json = json(goal);
            assertEquals(0, json.getAsJsonObject("rewards").size());
            assertEquals("[[\"has_inventory\"]]", json.getAsJsonArray("requirements").toString());
            assertEquals(goal.parentId() == null ? Set.of("display", "criteria", "requirements", "rewards")
                    : Set.of("parent", "display", "criteria", "requirements", "rewards"), json.keySet());
        }
    }

    @Test
    void visibleDisplayUsesExistingItemIconsAndOnlyRootReferencesVanillaBackground() {
        for (Goal goal : Goal.values()) {
            JsonObject display = json(goal).getAsJsonObject("display");
            assertEquals(goal.items().get(0), display.getAsJsonObject("icon").get("item").getAsString());
            assertEquals(goal.titleKey(), display.getAsJsonObject("title").get("translate").getAsString());
            assertEquals(goal.descriptionKey(), display.getAsJsonObject("description").get("translate").getAsString());
            assertEquals("task", display.get("frame").getAsString());
            assertTrue(display.get("show_toast").getAsBoolean());
            assertFalse(display.get("announce_to_chat").getAsBoolean());
            assertFalse(display.get("hidden").getAsBoolean());
            assertEquals(goal == Goal.ROOT, display.has("background"));
        }
        assertEquals(ClassicInventoryAdvancements.BACKGROUND,
                json(Goal.ROOT).getAsJsonObject("display").get("background").getAsString());
    }

    @Test
    void generationIsDeterministicAndReturnedDataDoesNotAliasTemplates() {
        var first = ClassicInventoryAdvancements.files();
        String original = first.get(Goal.ROOT.file()).toString();
        assertEquals(List.of(Goal.values()).stream().map(Goal::file).toList(), List.copyOf(first.keySet()));
        assertEquals(first, ClassicInventoryAdvancements.files());
        first.get(Goal.ROOT.file()).addProperty("untrusted", true);
        assertEquals(original, json(Goal.ROOT).toString());
        assertThrows(UnsupportedOperationException.class, first::clear);
        assertThrows(UnsupportedOperationException.class, () -> Goal.SUITED_UP.items().add("minecraft:stone"));
    }

    @Test
    void bothLanguagesCoverExactlyTheTwelveAcquisitionKeysAndAreImmutable() {
        Set<String> expected = java.util.Arrays.stream(Goal.values())
                .flatMap(goal -> List.of(goal.titleKey(), goal.descriptionKey()).stream()).collect(Collectors.toSet());
        for (boolean chinese : List.of(false, true)) {
            Map<String, String> translations = V180ClassicAdvancementLanguage.translations(chinese);
            assertEquals(expected, translations.keySet());
            assertEquals(12, translations.size());
            translations.values().forEach(value -> assertFalse(value.isBlank()));
            assertThrows(UnsupportedOperationException.class, translations::clear);
            assertEquals(translations, V180ClassicAdvancementLanguage.translations(chinese));
        }
        assertTrue(V180ClassicAdvancementLanguage.translations(false).get(Goal.WARP_CORE.descriptionKey())
                .contains("inventory"));
        assertTrue(V180ClassicAdvancementLanguage.translations(true).get(Goal.SUITED_UP.descriptionKey())
                .contains("同时"));
    }

    private static JsonObject json(Goal goal) {
        return ClassicInventoryAdvancements.files().get(goal.file());
    }
}
