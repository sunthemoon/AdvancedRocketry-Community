package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180MotionLanguage;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.motion.SuitMotionService.Reply;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MotionLanguageTest {
    private static final Set<Reply> SILENT = Set.of(Reply.RATE_LIMITED, Reply.IGNORED, Reply.THRUST);

    @Test void bothLanguagesNameTheKeyItsCategoryAndEveryShownStatus() {
        var english = V180MotionLanguage.translations(false);
        var chinese = V180MotionLanguage.translations(true);
        assertEquals(english.keySet(), chinese.keySet());
        assertTrue(english.containsKey("key.advancedrocketrycommunity.toggle_jetpack"));
        assertTrue(english.containsKey("key.categories.advancedrocketrycommunity"));
        for (Reply reply : Reply.values()) {
            assertEquals(!SILENT.contains(reply), english.containsKey(reply.translationKey()), reply.name());
        }
        chinese.values().forEach(value -> assertFalse(value.isBlank()));
        assertEquals(2 + Reply.values().length - SILENT.size(), english.size());
    }
}
