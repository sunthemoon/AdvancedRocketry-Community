package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import java.io.IOException;
import java.util.Optional;
import net.minecraft.server.packs.resources.ResourceManager;

/** Shared resource access and bounded diagnostics for the machine and capsule reload listeners. */
public final class ObjModelResources {
    private static final int MAX_DIAGNOSTIC = 512;

    private ObjModelResources() { }

    /** Adapts the active resource packs without changing the caller's per-read byte limit. */
    public static ObjModelSet.Source source(ResourceManager resources) {
        return (location, limit) -> {
            var resource = resources.getResource(location);
            if (resource.isEmpty()) { return Optional.empty(); }
            try (var input = resource.get().open()) {
                return Optional.of(ObjModelCheck.readBounded(input, limit, location.toString()));
            } catch (IOException failure) {
                throw new ObjModelException(location + ": cannot be read");
            }
        };
    }

    /** Keeps the existing single-line, at-most-512-character failure message, before a listener adds its prefix. */
    public static String diagnostic(Throwable failure) {
        String raw = String.valueOf(failure.getMessage()).replace('\n', ' ').replace('\r', ' ');
        return raw.substring(0, Math.min(MAX_DIAGNOSTIC, raw.length()));
    }
}
