package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.machine.menu.RecipeMenuReason;
import org.junit.jupiter.api.Test;

class V180RecipeMenuLanguageTest {
    @Test
    void bothLocalesCoverExactlyAllBoundedReasonsWithUniqueNonemptyLabels() {
        var english = V180RecipeMenuLanguage.translations(false);
        var chinese = V180RecipeMenuLanguage.translations(true);
        assertEquals(7, english.size());
        assertEquals(english.keySet(), chinese.keySet());
        assertEquals(7, english.values().stream().distinct().count());
        assertEquals(7, chinese.values().stream().distinct().count());
        for (RecipeMenuReason reason : RecipeMenuReason.values()) {
            assertFalse(english.get(reason.translationKey()).isBlank());
            assertFalse(chinese.get(reason.translationKey()).isBlank());
            assertNotEquals(english.get(reason.translationKey()), chinese.get(reason.translationKey()));
        }
        assertThrows(UnsupportedOperationException.class, () -> english.clear());
    }
}
