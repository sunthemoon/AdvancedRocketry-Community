package io.github.sunthemoon.advancedrocketrycommunity.client.compat.jei;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Checks the optional API adapter without adding JEI to the required unit-test runtime. */
class JeiFluidRenderingTest {
    @Test void everyFluidSlotUsesItsRecipeAmountAsCapacityAndShowsOnlyThatAmount() throws Exception {
        Path root = Path.of("src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/compat/jei");
        Pattern renderer = Pattern.compile("\\.setFluidRenderer\\((.*?), (true|false), 16, 16\\)\\s*"
                + "\\.addFluidStack\\((.*), (.*?)\\);");
        int total = 0;
        try (var files = Files.list(root)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith("JeiCategory.java")).toList()) {
                String source = Files.readString(file);
                var matches = renderer.matcher(source);
                int count = 0;
                while (matches.find()) {
                    assertEquals(matches.group(4), matches.group(1), file + ": renderer capacity must equal recipe amount");
                    assertEquals("false", matches.group(2), file + ": a recipe is not a storage tank");
                    count++;
                }
                assertEquals(source.split("\\.setFluidRenderer\\(", -1).length - 1, count,
                        file + ": every renderer must be checked");
                total += count;
            }
        }
        assertEquals(4, total); // Classic input/output, Electrolyzer input, Rolling Machine input.
    }
}
