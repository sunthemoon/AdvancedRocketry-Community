package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternCatalog;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Explicit lifecycle owner that retains the last valid immutable pattern catalog. */
public final class MultiblockPatternCatalogManager {
    public static final int MAX_STATUS_MESSAGE_CHARS = 2_048;

    private final AtomicReference<State> state = new AtomicReference<>(State.empty());

    public Optional<MultiblockPatternCatalog> current() {
        return Optional.ofNullable(state.get().catalog());
    }

    public ReloadStatus status() {
        return state.get().status();
    }

    public synchronized void accept(MultiblockPatternCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        State previous = state.get();
        long generation = Math.addExact(previous.status().generation(), 1L);
        state.set(new State(
                catalog,
                new ReloadStatus(true, true, generation, catalog.size(), "accepted")
        ));
    }

    public synchronized void reject(String message) {
        State previous = state.get();
        state.set(new State(
                previous.catalog(),
                new ReloadStatus(
                        previous.catalog() != null,
                        false,
                        previous.status().generation(),
                        previous.status().definitionCount(),
                        truncate(message)
                )
        ));
    }

    public void clear() {
        state.set(State.empty());
    }

    private static String truncate(String message) {
        String normalized = Objects.requireNonNullElse(message, "unknown reload failure");
        if (normalized.length() <= MAX_STATUS_MESSAGE_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_STATUS_MESSAGE_CHARS - 3) + "...";
    }

    public record ReloadStatus(
            boolean ready,
            boolean lastReloadAccepted,
            long generation,
            int definitionCount,
            String message
    ) {
    }

    private record State(MultiblockPatternCatalog catalog, ReloadStatus status) {
        private static State empty() {
            return new State(
                    null,
                    new ReloadStatus(false, false, 0L, 0, "not loaded")
            );
        }
    }
}
