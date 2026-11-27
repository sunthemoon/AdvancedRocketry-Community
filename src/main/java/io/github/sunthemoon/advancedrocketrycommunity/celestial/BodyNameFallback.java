package io.github.sunthemoon.advancedrocketrycommunity.celestial;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;

/** Presentation fallback only; a naming marker never grants a body capabilities or test privileges. */
public final class BodyNameFallback {
    public static String readable(ResourceLocation id) {
        String path = id.getPath();
        String leaf = path.substring(path.lastIndexOf('/') + 1);
        String words = Arrays.stream(leaf.split("[_.-]+"))
                .filter(word -> !word.isEmpty())
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                .collect(Collectors.joining(" "));
        return (leaf.startsWith("test_") ? "[TEST] " : "[PACK] ") + words;
    }

    private BodyNameFallback() { }
}
