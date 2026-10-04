package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Data validation only; no native registry lookup or publication authority. */
final class ClassicValueChecks {
    static ResourceLocation id(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        if (id.toString().length() > 128) { throw new IllegalArgumentException("Identifier exceeds limit"); }
        return id;
    }

    static ResourceLocation id(String text) {
        Objects.requireNonNull(text, "id");
        ResourceLocation id = text.length() <= 128 ? ResourceLocation.tryParse(text) : null;
        if (id == null || !id.toString().equals(text)) {
            throw new IllegalArgumentException("Noncanonical identifier");
        }
        return id;
    }

    static UUID uuid(String text) {
        UUID id = UUID.fromString(text);
        if (text.length() != 36 || !id.toString().equals(text)) {
            throw new IllegalArgumentException("Noncanonical UUID");
        }
        return id;
    }

    static String hash(String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid SHA-256 text");
        }
        return hash;
    }

    static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalArgumentException(message); }
    }

    private ClassicValueChecks() { }
}
