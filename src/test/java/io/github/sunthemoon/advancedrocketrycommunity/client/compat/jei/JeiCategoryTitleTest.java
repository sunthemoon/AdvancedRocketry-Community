package io.github.sunthemoon.advancedrocketrycommunity.client.compat.jei;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class JeiCategoryTitleTest {
    private static final String KEY = "jei.advancedrocketrycommunity.precision_assembler";

    @Test void precisionAssemblerHasAShortV180TitleInBothGeneratedLocales() throws Exception {
        for (String locale : List.of("en_us", "zh_cn")) {
            Path file = Path.of("src/generated/v1.8/resources/assets/advancedrocketrycommunity_v180/lang", locale + ".json");
            var language = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            assertNotNull(language.get(KEY), locale);
            String title = language.get(KEY).getAsString();
            assertEquals(locale.equals("en_us") ? "Precision Assembler" : "精密组装机", title);
            // Conservative default-font advances, with room for both title arrows at GUI scale 3.
            assertTrue(title.codePoints().map(c -> c >= 0x2E80 ? 9 : c == ' ' ? 4 : 6).sum() <= 120);
        }
    }

    @Test void categorySelectsTheNewTitleRatherThanTheHistoricalControllerTitle() throws Exception {
        String source = Files.readString(Path.of("src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/compat/jei/PrecisionAssemblerJeiCategory.java"));
        assertTrue(source.contains("Component.translatable(\"" + KEY + "\")"));
        assertFalse(source.contains("menu.advancedrocketrycommunity.precision_assembler"));
    }
}
