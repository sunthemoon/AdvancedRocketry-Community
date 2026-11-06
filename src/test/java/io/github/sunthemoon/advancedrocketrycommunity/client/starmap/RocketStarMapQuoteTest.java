package io.github.sunthemoon.advancedrocketrycommunity.client.starmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightQuotes;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W294 (finding 3-7): the star map quotes the route's fuel requirement in the rocket's own units, as the flight console
 * does, never in millibuckets (ADR-065 D1-A). The text is read from every packaged language root, so a key that two
 * roots define, or one that only an old root defines, fails here.
 */
class RocketStarMapQuoteTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void theRouteQuoteNamesNoMillibucketsInEitherLanguage() throws IOException {
        Component label = RocketStarMap.quoteLabel(new RocketFlightQuotes.Quote(660, RocketNavigationStatus.READY));
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, label.getContents());
        assertEquals(2, contents.getArgs().length);
        assertEquals(660, contents.getArgs()[1]);
        for (String locale : List.of("en_us", "zh_cn")) {
            List<String> texts = packagedTexts(locale, contents.getKey());
            assertEquals(1, texts.size(), locale + " defines " + contents.getKey() + " " + texts.size() + " times");
            String text = texts.get(0);
            assertFalse(text.contains("mB"), locale + " quotes abstract route fuel in millibuckets: " + text);
            assertEquals(2, text.split("%s", -1).length - 1, locale + " text lost an argument: " + text);
        }
    }

    @Test
    void anUndiscoveredBodyStillShowsOnlyItsStatus() {
        Component label = RocketStarMap.quoteLabel(new RocketFlightQuotes.Quote(0, RocketNavigationStatus.DISCOVERY_REQUIRED));
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, label.getContents());
        assertEquals(RocketNavigationStatus.DISCOVERY_REQUIRED.translationKey(), contents.getKey());
        assertArrayEquals(new Object[0], contents.getArgs());
    }

    private static List<String> packagedTexts(String locale, String key) throws IOException {
        List<String> texts = new ArrayList<>();
        for (Path root : List.of(Path.of("src/generated"), Path.of("src/main/resources"))) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.getFileName().toString().equals(locale + ".json")
                        && path.getParent().getFileName().toString().equals("lang")).toList()) {
                    var language = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    if (language.has(key)) { texts.add(language.get(key).getAsString()); }
                }
            }
        }
        return texts;
    }
}
