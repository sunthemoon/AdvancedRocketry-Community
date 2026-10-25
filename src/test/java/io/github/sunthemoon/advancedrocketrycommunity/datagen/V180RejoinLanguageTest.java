package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class V180RejoinLanguageTest {
    @Test void bothLocalesCoverEveryCommandStatusAndProofStage() {
        var en = V180RejoinLanguage.entries(false); var zh = V180RejoinLanguage.entries(true);
        assertEquals(en.keySet(), zh.keySet());
        for (String key : List.of("admitted", "pending", "refused", "unavailable", "permission", "header", "entry",
                "stage.b0", "stage.s1", "stage.b1", "stage.j1", "stage.s2", "stage.placement", "stage.none",
                "seat.pending", "seat.verified", "placement.pending", "placement.verified", "placement.not_required")) {
            String full = "command.advancedrocketrycommunity.rejoin." + key;
            assertNotNull(en.get(full), full); assertNotEquals(en.get(full), zh.get(full), full);
            assertEquals(en.get(full).split("%s", -1).length, zh.get(full).split("%s", -1).length, full);
        }
    }
}
