package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.station.notice.StationNoticeKind;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ADR-065 section 5.3 notice strings: every notice key, the login wrapper and both cost classes in English and
 * Chinese, in the v1.8 namespace, each template using every argument of its kind exactly by position.
 */
class V180StationNoticeLanguageTest {
    private static final Path GENERATED = Path.of("src/generated/v1.8/resources");
    private static final Pattern POSITIONAL = Pattern.compile("%(\\d+)\\$s");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void everyNoticeKeyExistsInBothLanguagesAndTheGeneratedFiles() throws Exception {
        Map<String, String> english = V180StationNoticeLanguage.translations(false);
        Map<String, String> chinese = V180StationNoticeLanguage.translations(true);
        assertEquals(english.keySet(), chinese.keySet());
        Set<String> expected = new TreeSet<>(List.of(
                "station.advancedrocketrycommunity.notice.warp_started",
                "station.advancedrocketrycommunity.notice.warp_countdown",
                "station.advancedrocketrycommunity.notice.warp_committed",
                "station.advancedrocketrycommunity.notice.warp_aborted",
                "station.advancedrocketrycommunity.notice.warp_outcome",
                "station.advancedrocketrycommunity.notice.while_away",
                "station.advancedrocketrycommunity.warp_class.in_system",
                "station.advancedrocketrycommunity.warp_class.interstellar"));
        assertEquals(expected, new TreeSet<>(english.keySet()));
        for (String key : expected) {
            assertFalse(chinese.get(key).isBlank() || chinese.get(key).equals(english.get(key))
                    && !key.endsWith("warp_outcome"), key);
        }
        for (String locale : List.of("en_us", "zh_cn")) {
            JsonObject lang = JsonParser.parseString(Files.readString(GENERATED.resolve(
                    "assets/advancedrocketrycommunity_v180/lang/" + locale + ".json"), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Map<String, String> source = locale.equals("en_us") ? english : chinese;
            for (String key : expected) {
                assertTrue(lang.has(key), locale + " lacks " + key);
                assertEquals(source.get(key), lang.get(key).getAsString(), locale + " " + key);
            }
        }
    }

    @Test
    void everyTemplateUsesEachArgumentOfItsKindByPosition() {
        for (boolean chinese : List.of(false, true)) {
            Map<String, String> entries = V180StationNoticeLanguage.translations(chinese);
            for (StationNoticeKind kind : StationNoticeKind.values()) {
                String template = entries.get(kind.key());
                Matcher matcher = POSITIONAL.matcher(template);
                Set<Integer> used = new TreeSet<>();
                while (matcher.find()) {
                    used.add(Integer.parseInt(matcher.group(1)));
                }
                assertEquals(IntStream.rangeClosed(1, kind.roles().size()).boxed().collect(Collectors.toSet()), used,
                        kind + " " + template);
                assertFalse(template.replaceAll(POSITIONAL.pattern(), "").contains("%"), template);
                assertTrue(template.startsWith("[%1$s]"), "the station is named first: " + template);
            }
            assertEquals(1, entries.get(StationNoticeKind.WHILE_AWAY).split("%s", -1).length - 1);
        }
    }
}
