package io.github.sunthemoon.advancedrocketrycommunity.progression.classic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicMilestoneAdvancements.Kind;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicMilestoneAdvancements.Milestone;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Strict condition decoding: one known event, nothing else. Plain JUnit cannot build vanilla's loot-backed
 * deserialization context, so this calls the trigger's own decoder; the GameTest loads the real tree.
 */
class ClassicEventTriggerTest {
    private static final ClassicEventTrigger TRIGGER = new ClassicEventTrigger();

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void stableIdAndEveryEventDecodes() {
        assertEquals("advancedrocketrycommunity:classic_event", TRIGGER.getId().toString());
        assertEquals(List.of("formed_multiblock", "seat_on_tnt", "moon_landing", "station_warp"),
                java.util.Arrays.stream(ClassicEvent.values()).map(ClassicEvent::serializedName).toList());
        for (ClassicEvent event : ClassicEvent.values()) {
            ClassicEventTrigger.Instance instance = decode("{\"event\":\"" + event.serializedName() + "\"}");
            assertEquals(event, instance.event());
            assertEquals(ClassicEventTrigger.ID, instance.getCriterion());
            assertEquals(event, ClassicEvent.byName(event.serializedName()).orElseThrow());
        }
    }

    @Test
    void everyGeneratedEventCriterionDecodes() {
        for (Milestone milestone : Milestone.values()) {
            if (milestone.kind() == Kind.EVENT) {
                JsonObject criterion = ClassicMilestoneAdvancements.files().get(milestone.file())
                        .getAsJsonObject("criteria").getAsJsonObject(milestone.criterion());
                assertEquals(milestone.target(), decode(criterion.getAsJsonObject("conditions").toString())
                        .event().serializedName());
            }
        }
    }

    @Test
    void missingUnknownOrExtraConditionsAreRejected() {
        for (String json : List.of("{}", "{\"event\":\"warp\"}", "{\"event\":\"\"}", "{\"event\":7}",
                "{\"event\":\"moon_landing\",\"world_first\":true}", "{\"event\":[\"moon_landing\"]}",
                "{\"event\":\"MOON_LANDING\"}")) {
            assertThrows(JsonSyntaxException.class, () -> decode(json), json);
        }
    }

    private static ClassicEventTrigger.Instance decode(String json) {
        return TRIGGER.createInstance(JsonParser.parseString(json).getAsJsonObject(), ContextAwarePredicate.ANY, null);
    }
}
