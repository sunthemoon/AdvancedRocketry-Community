package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.research.ResearchPatterns;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ResearchStatus;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class V180ResearchDataTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path output;
    @Test void generatedPatternsMatchFrozenBytesAndAreDeterministic() throws Exception {
        var provider = new V180ResearchData(new PackOutput(output), true, true); provider.run(CachedOutput.NO_CACHE).join();
        for (String id : V180ResearchData.IDS) {
            byte[] expected;
            try (var stream = getClass().getResourceAsStream("/fixtures/v180/machine_patterns/" + id + ".json")) { assertNotNull(stream); expected = stream.readAllBytes(); }
            var file = output.resolve("data/advancedrocketrycommunity/machine_patterns/" + id + ".json");
            assertArrayEquals(expected, Files.readAllBytes(file), id);
            provider.run(CachedOutput.NO_CACHE).join(); assertArrayEquals(expected, Files.readAllBytes(file), id + " repeat");
        }
        assertEquals(125, ResearchPatterns.definition(false).cells().size()); assertEquals(12, ResearchPatterns.definition(true).cells().size());
    }
    @Test void originalArtAndBothLanguagesAreComplete() {
        assertFalse(Arrays.equals(V180ResearchArt.png("observatory"), V180ResearchArt.png("astrobody_data_processor")));
        assertArrayEquals(V180ResearchArt.png("observatory"), V180ResearchArt.png("observatory"));
        for (boolean chinese : new boolean[]{false, true}) {
            var entries = V180ResearchLanguage.entries(chinese);
            for (var status : ResearchStatus.values()) { assertNotNull(entries.get(status.key())); }
            for (var group : java.util.Map.of("phase", 6, "analysis", 4, "owner", 4, "role", 11).entrySet()) {
                for (int code = 0; code < group.getValue(); code++) { assertNotNull(entries.get("screen.advancedrocketrycommunity.research." + group.getKey() + "." + code)); }
            }
        }
    }
}
